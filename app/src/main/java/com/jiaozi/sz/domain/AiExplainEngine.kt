package com.jiaozi.sz.domain

import com.jiaozi.sz.data.Repository
import org.json.JSONArray
import org.json.JSONObject

/**
 * AI 讲评引擎（对齐网页端 v5.14 的 aiExplainQuestion 错因讲评 + 纵向回顾）。
 * 输入当次错题清单（题干 + 解析 + 错因），调用 DeepSeek 生成一段
 * "错因讲评 + 纵向回顾（关联相关章节/知识点）"的文字，返回纯文本。
 * 网络层复用 HttpURLConnection（与 AiGenEngine 一致），仅在设备端运行。
 */
object AiExplainEngine {

    /** 单条错题 */
    data class WrongItem(val q: String, val analysis: String?, val cause: String)

    /**
     * 生成讲评文本。返回模型输出的讲解。
     * @param items 错题清单（最多截断到 15 条，避免超长上下文）
     */
    suspend fun explain(
        provider: String,
        apiKey: String,
        items: List<WrongItem>,
        modelOverride: String = ""
    ): String {
        val prompt = buildPrompt(items.take(15))
        val model = modelOverride.ifBlank { AiProvider.get(provider).defaultModel }
        val body = buildBody(model, prompt)
        return AiProvider.postChat(provider, apiKey, body).takeIf { it.isNotBlank() } ?: "（AI 未返回有效讲解）"
    }

    private fun buildPrompt(items: List<WrongItem>): String {
        val lines = items.mapIndexed { i, it ->
            "${i + 1}. 题干：${it.q}\n   参考答案/解析：${it.analysis ?: "（无）"}\n   我的错因：${it.cause}"
        }.joinToString("\n")
        return """
请基于以下我的错题，做一份「错因讲评 + 纵向回顾」：
$lines

要求：
- 先逐题点出关键错因与正确思路（不要只复述解析）；
- 再做「纵向回顾」：把这些错题关联到的共同知识点/章节提炼出来，告诉我后续应重点复习哪些模块；
- 中文，条理清晰，用短句，控制在 400 字以内。
        """.trimIndent()
    }

    private fun buildBody(model: String, prompt: String): String =
        JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().apply {
                put(JSONObject().apply { put("role", "user"); put("content", prompt) })
            })
            put("temperature", 0.6)
            put("max_tokens", 800)
        }.toString()

    /**
     * 离线讲评（P2-A）：无 Key 时基于错因列表生成通用复习建议模板，使「AI 讲评」首开可用、不再报错。
     */
    fun offlineExplain(items: List<WrongItem>): String {
        val causes = items.flatMap { it.cause.split(",").map { c -> c.trim() }.filter { it.isNotBlank() } }
            .groupingBy { it }.eachCount().toList().sortedByDescending { it.second }
        val top = if (causes.isNotEmpty()) causes.joinToString("、") { "${it.first}(${it.second})" } else "暂未归类"
        return buildString {
            append("（离线模式 · 未配置 AI Key，以下为通用复习建议）\n\n")
            append("一、错因概览：本次 ${items.size} 道错题，高频错因依次为：$top。\n")
            append("二、复习建议：① 针对高频错因回归对应章节精讲，先厘清概念再刷题；② 同类题再练 3 道巩固；③ 用「错题本」按错因归类、间隔复习。\n")
            append("三、纵向回顾：把错题关联到共同知识点后，优先补强薄弱模块。配置 AI Key 后可由模型给出逐题精准讲评。")
        }
    }
}
