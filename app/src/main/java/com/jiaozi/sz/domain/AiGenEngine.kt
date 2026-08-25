package com.jiaozi.sz.domain

import com.jiaozi.sz.data.Repository
import com.jiaozi.sz.data.local.UserQuestionEntity
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * AI 出题引擎（实用可用版）。
 * 复用网页同款口径：调用 DeepSeek / OpenAI 兼容接口 → 抽取 JSON 数组 →
 * 归一化(normalizeGen) → 自动归类(autoClassify) → 落「用户题库」Room 表。
 * 网络层用 HttpURLConnection（不引入额外依赖）。仅在设备端运行。
 */
object AiGenEngine {

    /**
     * 生成题目并直接落库（兼容旧调用）。返回实际落库条数。
     * 新流程建议用 [previewGenerate] + [commitGenerated]，先在 UI 预览审阅再入库。
     * @param subject 科一/科二/科三
     * @param scope 科三传学科(disc)，其余传章节名或空
     */
    suspend fun generate(
        repo: Repository,
        provider: String,
        apiKey: String,
        subject: String,
        scope: String,
        count: Int,
        modelOverride: String = ""
    ): Int {
        val list = previewGenerate(repo, provider, apiKey, subject, scope, count, modelOverride)
        commitGenerated(repo, list)
        return list.size
    }

    /**
     * 仅生成、不落库，返回待审阅的题目列表（对齐网页端「AI 生题后先预览」）。
     * UI 展示后由用户勾选，再调用 [commitGenerated] 入库。
     */
    suspend fun previewGenerate(
        repo: Repository,
        provider: String,
        apiKey: String,
        subject: String,
        scope: String,
        count: Int,
        modelOverride: String = ""
    ): List<UserQuestionEntity> {
        val engine = ClassificationEngine(repo.autoSyll, repo.syllabus)
        val prompt = buildPrompt(subject, scope, count)
        val model = modelOverride.ifBlank { AiProvider.get(provider).defaultModel }
        val body = buildBody(model, prompt)
        val raw = AiProvider.postChat(provider, apiKey, body, readTimeoutMs = 180_000)
        val items = extractItems(raw)
        val out = mutableListOf<UserQuestionEntity>()
        for (item in items) {
            val cr = engine.classifyQuestion(
                com.jiaozi.sz.data.model.Question(
                    id = "__ai_" + UUID.randomUUID(),
                    subject = subject,
                    chapter = item.chapter ?: "",
                    section = item.section,
                    q = item.q,
                    opt = item.opt ?: "",
                    answer = item.answer ?: "",
                    analysis = item.analysis,
                    disc = if (subject == "科三") scope else null
                ),
                subjHint = subject
            )
            val analysis = item.analysis?.takeIf { it.isNotBlank() } ?: "（AI 生成，暂无解析）"
            out.add(
                UserQuestionEntity(
                    id = "__ai_" + UUID.randomUUID(),
                    subject = cr.subj,
                    chapter = cr.ch.ifBlank { item.chapter ?: "" },
                    section = cr.sec.takeIf { it.isNotBlank() } ?: item.section,
                    q = item.q,
                    opt = item.opt ?: "",
                    answer = item.answer ?: "",
                    analysis = analysis,
                    disc = if (cr.subj == "科三") scope else null,
                    _mt = System.currentTimeMillis()
                )
            )
        }
        return out
    }

    /** 将审阅通过的题目批量入库 */
    suspend fun commitGenerated(repo: Repository, list: List<UserQuestionEntity>) {
        for (e in list) repo.upsertUserQuestion(e)
    }

    /**
     * 离线出题样例（P2-A：对齐网页端 localAIGenerate 离线分支）。无 AI Key 时返回 2 道内置示例题，
     * 标注「离线样例」，使 AI 题库首开即可试用、不再只有报错。覆盖所选科目/章节的通用考点。
     */
    fun offlineGenerate(subject: String, scope: String, count: Int): List<UserQuestionEntity> {
        val where = if (subject == "科三") scope.ifBlank { "学科" } else subject
        val tag = "【离线样例·$where】"
        val samples = listOf(
            UserQuestionEntity(
                id = "__off_" + System.currentTimeMillis() + "_1",
                subject = subject,
                chapter = if (subject == "科三") "" else scope.ifBlank { "通用" },
                section = null,
                q = "$tag 下列关于${where}核心概念的简述，哪一项最符合教育学的经典表述？",
                opt = "A. 选项一 B. 选项二 C. 选项三 D. 选项四",
                answer = "C",
                analysis = "（离线样例，仅供体验出题格式）真实题目需配置 AI Key 后由模型生成，解析会更贴合具体考点。",
                disc = if (subject == "科三") scope.ifBlank { null } else null,
                flag = "待审", flagMsg = "离线样例", _mt = System.currentTimeMillis()
            ),
            UserQuestionEntity(
                id = "__off_" + System.currentTimeMillis() + "_2",
                subject = subject,
                chapter = if (subject == "科三") "" else scope.ifBlank { "通用" },
                section = null,
                q = "$tag 请简述${where}中「因材施教」原则在教学中的具体体现（主观题，自判）。",
                opt = "",
                answer = "",
                analysis = "（离线样例）答题要点：依据学生个别差异调整内容/方法/进度；真实题目由 AI 生成并附参考答案。",
                disc = if (subject == "科三") scope.ifBlank { null } else null,
                flag = "待审", flagMsg = "离线样例", _mt = System.currentTimeMillis()
            )
        )
        return samples.take(count.coerceAtLeast(1))
    }

    private fun buildPrompt(subject: String, scope: String, count: Int): String {
        val where = if (subject == "科三") "学科「$scope」（科三）" else "科目「$subject」${if (scope.isNotBlank()) "章节「$scope」" else ""}"
        return """
请围绕$where，生成 $count 道教师资格证考试练习题，严格只输出一个 JSON 数组，不要任何解释。
数组元素字段：
- q: 题干（必填）
- opt: 选项文本，格式如 "A. 选项一 B. 选项二 C. 选项三 D. 选项四"；若为简答题/论述题等主观题则留空字符串 ""
- answer: 客观题答案字母（如 "C"）；主观题留空 ""
- analysis: 解析（必填，至少一句）
- chapter: 所属章节名（可选）
- section: 所属节名（可选）
客观题需有 opt 与 answer；主观题 opt 与 answer 为空，由学习者自判。
        """.trimIndent()
    }

    private fun buildBody(model: String, prompt: String): String =
        JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().apply {
                put(JSONObject().apply { put("role", "user"); put("content", prompt) })
            })
            put("temperature", 0.8)
        }.toString()

    /** 容错抽取 JSON 数组 */
    private fun extractItems(text: String): List<AiRaw> {
        val s = text.indexOf('[')
        val e = text.lastIndexOf(']')
        if (s < 0 || e < 0 || e <= s) return emptyList()
        val arr = JSONArray(text.substring(s, e + 1))
        val out = mutableListOf<AiRaw>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(
                AiRaw(
                    q = pick(o, "q", "题干") ?: continue,
                    opt = pick(o, "opt", "选项"),
                    answer = pick(o, "answer", "答案"),
                    analysis = pick(o, "analysis", "解析"),
                    chapter = pick(o, "chapter", "章节"),
                    section = pick(o, "section", "节")
                )
            )
        }
        return out
    }

    private fun pick(o: JSONObject, vararg keys: String): String? {
        for (k in keys) if (o.has(k) && !o.isNull(k)) {
            val v = o.getString(k)
            if (v.isNotBlank()) return v
        }
        return null
    }

    private data class AiRaw(
        val q: String,
        val opt: String?,
        val answer: String?,
        val analysis: String?,
        val chapter: String?,
        val section: String?
    )
}
