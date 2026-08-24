package com.jiaozi.sz.domain

import org.json.JSONArray
import org.json.JSONObject

/**
 * AI 帮手对话引擎（对话式助手）：把多轮 (role, content) 历史发给 DeepSeek / OpenAI，
 * 返回助手回复文本。网络层复用 HttpURLConnection（与 AiExplainEngine / AiGenEngine 一致），仅设备端运行。
 */
object AiChatEngine {

    /** 系统人设：全学科教资备考助手 */
    private const val SYSTEM_PROMPT = "你是「综合教资备考平台」的 AI 帮手，熟悉中小学教资考试（科一综合素质 / 科二教育知识与能力 / 科三学科）的考点、答题技巧与备课方法。用中文、条理清晰、简短实用地回答用户的备考问题；涉及具体题目时给出思路而非仅给答案。"

    /**
     * 离线兜底回复：未配置 AI Key 时，基于关键词返回内置备考提示，使「AI 帮手」首开即可用，
     * 不再只有报错卡死。对齐网页端 localAIGenerate 的离线样例思路（设备端可运行、不联网）。
     * 明确标注为离线演示，引导用户到「设置」配置 Key 解锁真实 AI。
     */
    fun offlineReply(userText: String): String {
        val t = userText.lowercase()
        val tips = buildList {
            if (t.contains("科一") || t.contains("综合素质")) add(
                "科一《综合素质》重点：职业理念（教育观/学生观/教师观）、教育法律法规、教师职业道德（三爱两人一终身）、文化素养、基本能力（逻辑/阅读/写作）。作文占 50 分，务必练立意与结构。"
            )
            if (t.contains("科二") || t.contains("教育知识") || t.contains("教育能力")) add(
                "科二《教育知识与能力》重点：教育学基础（夸美纽斯/赫尔巴特）、心理学（皮亚杰认知发展、维果茨基最近发展区）、学习动机、德育原则、班级管理。辨析题先判对错再论证。"
            )
            if (t.contains("科三") || t.contains("学科")) add(
                "科三《学科知识与教学能力》重点：学科本体知识 + 教学设计（教学目标/重难点/过程）+ 教学评价。备考时按「导入—新授—巩固—小结—作业」写简案。"
            )
            if (t.contains("面试") || t.contains("说课") || t.contains("试讲")) add(
                "面试（说课/试讲）要点：结构化问答（时政/突发）+ 10 分钟试讲 + 答辩。试讲重「互动感」与板书，教案用「导入—新授—巩固—小结—作业」五段。"
            )
            if (t.contains("教学设计") || t.contains("教案")) add(
                "教学设计模板：① 教学目标（知识与技能/过程与方法/情感态度价值观）；② 重难点；③ 教学过程（复习导入→新授→巩固练习→小结→作业）；④ 板书设计。"
            )
            if (t.contains("简答")) add(
                "简答题作答：分点（1）（2）（3），先核心概念再展开，每点 2-3 行，避免空话。背诵高频考点如教学原则、德育原则。"
            )
            if (t.contains("错") || t.contains("错题")) add(
                "纠错方法：用错题本按「错因（概念不清/审题/计算/惯性）」归类，间隔复习；同类题再练 3 道巩固。"
            )
        }
        val body = if (tips.isNotEmpty()) tips.joinToString("\n\n")
        else "我目前没有联网，无法调用真实 AI。你可以先试试这些备考方向：① 按科一/科二/科三过一遍大纲重点；② 用「错题本」按错因归类复习；③ 在「备课」里按「导入—新授—巩固—小结—作业」写简案。"
        return "（离线模式 · 未配置 AI Key，以下为内置备考提示）\n\n$body\n\n— 到「设置」配置 DeepSeek / OpenAI Key 后，解锁真实 AI 针对你的题目给思路。"
    }

    /**
     * 多轮对话。history 不含 system，按时间顺序的 (role, content)。
     * 返回助手回复文本；无 Key 或网络失败抛异常由调用方处理。
     */
    suspend fun chat(provider: String, apiKey: String, history: List<Pair<String, String>>, modelOverride: String = ""): String {
        val p = AiProvider.get(provider)
        val model = modelOverride.ifBlank { p.defaultModel }
        val msgs = JSONArray().apply {
            put(JSONObject().apply { put("role", "system"); put("content", SYSTEM_PROMPT) })
            history.forEach { (role, content) ->
                put(JSONObject().apply { put("role", role); put("content", content) })
            }
        }
        val body = JSONObject().apply {
            put("model", model)
            put("messages", msgs)
            put("temperature", 0.7)
            put("max_tokens", 1000)
        }.toString()
        return AiProvider.postChat(provider, apiKey, body).takeIf { it.isNotBlank() } ?: "（AI 未返回有效内容）"
    }

    /**
     * 流式版 [chat]：逐块回调 [onChunk]，供 AI 帮手边生成边显示。
     * SSE 由 [AiProvider.postChatStream] 处理，本方法仅负责拼装带 stream 的请求体。
     */
    suspend fun chatStream(
        provider: String,
        apiKey: String,
        history: List<Pair<String, String>>,
        modelOverride: String = "",
        onChunk: (String) -> Unit
    ) {
        val p = AiProvider.get(provider)
        val model = modelOverride.ifBlank { p.defaultModel }
        val msgs = JSONArray().apply {
            put(JSONObject().apply { put("role", "system"); put("content", SYSTEM_PROMPT) })
            history.forEach { (role, content) ->
                put(JSONObject().apply { put("role", role); put("content", content) })
            }
        }
        val body = JSONObject().apply {
            put("model", model)
            put("messages", msgs)
            put("temperature", 0.7)
            put("max_tokens", 1000)
        }.toString()
        AiProvider.postChatStream(provider, apiKey, body, onChunk)
    }
}
