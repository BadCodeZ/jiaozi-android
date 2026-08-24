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
}
