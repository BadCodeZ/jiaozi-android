package com.jiaozi.sz.domain

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * AI 服务商统一配置与网络层（对齐网页端 AI_PRESETS：deepseek / openai / moonshot）。
 * 三个引擎（对话 / 出题 / 讲评）共用同一份端点和「chat/completions」调用，
 * 仅消息组装不同；本项目只调用 OpenAI 兼容的 /v1/chat/completions，故统一在此发请求。
 *
 * 之前安卓端硬编码 deepseek 且无 moonshot，导致用户填了其他厂商 Key 直接失败。
 * 现在：设置页可选 provider，本类按 provider 路由到正确 endpoint 与默认模型，
 * 并对 401/429/5xx 等给出中文可读错误，方便用户定位「无法使用」的根因。
 */
object AiProvider {

    data class Provider(
        val id: String,
        val label: String,
        val url: String,
        val defaultModel: String,
        val hint: String = ""
    )

    val PROVIDERS = listOf(
        Provider(
            id = "deepseek",
            label = "DeepSeek",
            url = "https://api.deepseek.com/v1/chat/completions",
            defaultModel = "deepseek-chat",
            hint = "https://platform.deepseek.com 申请的 Key"
        ),
        Provider(
            id = "openai",
            label = "OpenAI",
            url = "https://api.openai.com/v1/chat/completions",
            defaultModel = "gpt-4o-mini",
            hint = "https://platform.openai.com 申请的 Key"
        ),
        Provider(
            id = "moonshot",
            label = "Moonshot (Kimi)",
            url = "https://api.moonshot.cn/v1/chat/completions",
            defaultModel = "moonshot-v1-8k",
            hint = "https://platform.moonshot.cn 申请的 Key"
        )
    )

    const val DEFAULT = "deepseek"

    fun get(id: String): Provider = PROVIDERS.firstOrNull { it.id == id } ?: PROVIDERS.first()

    /** provider 下拉选项：(id, label) */
    fun options(): List<Pair<String, String>> = PROVIDERS.map { it.id to it.label }

    /**
     * 发起一次 chat/completions 请求，返回首个 choice 的 message.content 纯文本。
     * 任何失败都抛 [AiApiException]（中文可读），由调用方上屏。
     */
    suspend fun postChat(providerId: String, apiKey: String, bodyJson: String, readTimeoutMs: Int = 120_000): String {
        val p = get(providerId)
        // 必须切到 IO 线程：viewModelScope 默认在 Main 调度器，直接在主线做阻塞网络 I/O
        // 会抛 NetworkOnMainThreadException（StrictMode），表现就是「AI 怎么调都不通」。
        return withContext(Dispatchers.IO) {
            val conn = try {
                URL(p.url).openConnection() as HttpURLConnection
            } catch (e: Exception) {
                throw AiApiException(-1, p.label, "无法连接 ${p.label} 服务：${e.message?.take(120)}")
            }
            try {
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.setRequestProperty("Authorization", "Bearer $apiKey")
                conn.doOutput = true
                conn.connectTimeout = 30_000
                conn.readTimeout = readTimeoutMs
                conn.outputStream.use { it.write(bodyJson.toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                val text = if (code in 200..299) {
                    conn.inputStream.bufferedReader().readText()
                } else {
                    conn.errorStream?.bufferedReader()?.readText() ?: "HTTP $code"
                }
                if (code !in 200..299) {
                    throw AiApiException(code, p.label, text)
                }
                val obj = JSONObject(text)
                val arr = obj.getJSONArray("choices")
                val content = arr.getJSONObject(0).getJSONObject("message").getString("content").trim()
                if (content.isBlank()) throw AiApiException(0, p.label, "${p.label} 返回了空内容，请检查模型或重试")
                content
            } catch (e: AiApiException) {
                throw e
            } catch (e: Exception) {
                if (e is AiApiException) throw e
                throw AiApiException(-2, p.label, "网络异常（${p.label}）：${e.message?.take(160) ?: e.javaClass.simpleName}")
            } finally {
                try { conn.disconnect() } catch (_: Exception) {}
            }
        }
    }

    /**
     * 流式对话（SSE）：与 [postChat] 同端点，但请求体带 stream=true，
     * 逐块读取 `data:` 事件并回调 [onChunk]，让 AI 帮手「边生成边显示」，
     * 消除整段等待的空白感。deepseek / openai / moonshot 均兼容 OpenAI SSE。
     */
    suspend fun postChatStream(
        providerId: String,
        apiKey: String,
        bodyJson: String,
        onChunk: (String) -> Unit,
        readTimeoutMs: Int = 180_000
    ) {
        val p = get(providerId)
        withContext(Dispatchers.IO) {
            val conn = try {
                URL(p.url).openConnection() as HttpURLConnection
            } catch (e: Exception) {
                throw AiApiException(-1, p.label, "无法连接 ${p.label} 服务：${e.message?.take(120)}")
            }
            try {
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.setRequestProperty("Authorization", "Bearer $apiKey")
                conn.doOutput = true
                conn.connectTimeout = 30_000
                conn.readTimeout = readTimeoutMs
                // 注入 stream=true（调用方 body 未必带），兼容非流式拼装的请求体
                val streamBody = runCatching {
                    val jo = JSONObject(bodyJson); jo.put("stream", true); jo.toString()
                }.getOrElse { bodyJson }
                conn.outputStream.use { it.write(streamBody.toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                if (code !in 200..299) {
                    val err = conn.errorStream?.bufferedReader()?.readText() ?: "HTTP $code"
                    throw AiApiException(code, p.label, err)
                }
                val reader = conn.inputStream.bufferedReader()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val l = line ?: continue
                    if (!l.startsWith("data:")) continue
                    val data = l.removePrefix("data:").trim()
                    if (data == "[DONE]") break
                    try {
                        val obj = JSONObject(data)
                        val arr = obj.optJSONArray("choices") ?: continue
                        if (arr.length() == 0) continue
                        val delta = arr.getJSONObject(0).optJSONObject("delta")
                        val piece = delta?.optString("content") ?: ""
                        if (piece.isNotEmpty()) onChunk(piece)
                    } catch (_: Exception) { /* 跳过心跳/非 JSON 行 */ }
                }
            } catch (e: AiApiException) {
                throw e
            } catch (e: Exception) {
                if (e is AiApiException) throw e
                throw AiApiException(-2, p.label, "流式读取异常（${p.label}）：${e.message?.take(160) ?: e.javaClass.simpleName}")
            } finally {
                try { conn.disconnect() } catch (_: Exception) {}
            }
        }
    }
}

/** 带可读中文消息的 AI 接口异常，直接上屏给用户 */
class AiApiException(code: Int, provider: String, raw: String) : RuntimeException(
    when {
        code == 401 -> "$provider 鉴权失败：API Key 无效或已过期（请到「设置」检查 Key 是否与厂商匹配）"
        code == 403 -> "$provider 拒绝访问（403）：Key 无权限或账户受限"
        code == 404 -> "$provider 接口地址不正确（404），可能是该厂商端点变更"
        code == 429 -> "$provider 请求过于频繁或额度不足（429），请稍后重试或换 Key"
        code in 500..599 -> "$provider 服务端错误（$code），稍后重试"
        code == -1 -> raw
        code == -2 -> raw
        else -> "$provider 接口返回 $code：${raw.take(160)}"
    }
)
