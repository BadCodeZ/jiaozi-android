package com.jiaozi.sz.domain

import com.jiaozi.sz.data.local.ProgressEntity
import com.jiaozi.sz.data.model.Question
import kotlin.math.roundToInt

/**
 * 练习配置（对齐网页端 PS 状态）。
 */
data class PracticeConfig(
    val mode: String = "随机全科",          // 随机全科 / 按科目 / 章节练习 / 薄弱优先 / 仅复习 / 错因强化
    val subj: String? = null,              // 科目
    val chapter: String? = null,           // 章
    val section: String? = null,           // 节
    val num: Int = 20,                     // 题量
    val interleave: Boolean = false,       // 穿插混合
    val cause: String? = null,             // 错因强化目标
    val disc: String? = null,              // 科三学科
    val timeLimitSec: Int? = null          // 模考限时（秒），非模考为 null
)

/**
 * 抽题引擎（移植自 HTML 的章节练习 / 薄弱优先 / 错题本 / 全科模考蓝图 / 错因强化）。
 * 纯函数，便于单元测试。progress 为 qid → ProgressEntity 的快照。
 */
object PracticeEngine {

    /** 统一入口 */
    fun build(
        all: List<Question>,
        config: PracticeConfig,
        progress: Map<String, ProgressEntity>
    ): List<Question> {
        val pool = when (config.mode) {
            "按科目" -> all.filter { it.subject == config.subj }
            "章节练习" -> all.filter {
                it.subject == config.subj && it.chapter == config.chapter &&
                    (config.section == null || it.section == config.section)
            }
            "薄弱优先" -> weak(all, progress, Int.MAX_VALUE)
            "仅复习" -> due(all, progress)
            "错题本" -> wrong(all, progress)
            "错因强化" -> cause(all, progress, config.cause ?: "")
            "随机全科" -> all
            else -> all
        }.filter {
            // 科三始终只取当前学科
            it.subject != "科三" || it.disc == config.disc
        }

        val limited = pool.shuffled().take(config.num.coerceAtLeast(1))
        return if (config.interleave && config.mode in listOf("随机全科", "按科目")) {
            interleave(limited)
        } else {
            limited
        }
    }

    fun chapter(questions: List<Question>, limit: Int = 30): List<Question> =
        questions.shuffled().take(limit)

    /** 薄弱优先：按薄弱分降序 */
    fun weak(questions: List<Question>, progress: Map<String, ProgressEntity>, limit: Int = 30): List<Question> =
        questions
            .map { q -> q to WeaknessScorer.score(progress[q.id]) }
            .sortedByDescending { it.second }
            .map { it.first }
            .take(limit)

    /** 仅复习：已到期的题 */
    fun due(questions: List<Question>, progress: Map<String, ProgressEntity>): List<Question> {
        val now = System.currentTimeMillis()
        return questions.filter { (progress[it.id]?.due ?: 0) in 1..now }
    }

    /** 错因强化：抽取标有指定错因的错题 */
    fun cause(
        questions: List<Question>,
        progress: Map<String, ProgressEntity>,
        cause: String,
        limit: Int = 30
    ): List<Question> = questions.filter {
        progress[it.id]?.wrongBook == true && progress[it.id]?.cause?.contains(cause) == true
    }.shuffled().take(limit)

    /** 错题本：wrongBook 标记的题 */
    fun wrong(questions: List<Question>, progress: Map<String, ProgressEntity>, limit: Int = 200): List<Question> =
        questions.filter { progress[it.id]?.wrongBook == true }.shuffled().take(limit)

    /**
     * 章节配置键：唯一标识「科目-学科-章节」。科三区分 disc（不同学科同名章不冲突）。
     * 与 AppViewModel.saveChapterConfig / ChaptersScreen 保存键保持一致。
     */
    fun chapterKey(subject: String, disc: String?, chapter: String): String =
        if (subject == "科三" && disc != null) "科三|$disc|$chapter" else "$subject|$chapter"

    /**
     * 全科模考蓝图：按章节权重抽 count 题，科一/科二/科三 ≈ 33/33/34%。
     * 科三取当前 disc 且不串其他学科；卷内无重复。
     * weights 为空（用户未配置）时退化为均匀 shuffle，行为与旧版一致。
     */
    fun blueprint(questions: List<Question>, disc: String, count: Int = 50, weights: Map<String, Double> = emptyMap()): List<Question> {
        val k1 = questions.filter { it.subject == "科一" }
        val k2 = questions.filter { it.subject == "科二" }
        val k3 = questions.filter { it.subject == "科三" && it.disc == disc }
        val n1 = (count * 0.33f).toInt()
        val n2 = (count * 0.33f).toInt()
        val n3 = count - n1 - n2
        return pickWeighted(k1, n1, weights, null) +
               pickWeighted(k2, n2, weights, null) +
               pickWeighted(k3, n3, weights, disc)
    }

    /**
     * 按章节权重在该科目内分配配额并抽样；权重为空则均匀 shuffle。
     * 配额按章权重比例取整，差额随机补足，保证总题数接近 total。
     */
    private fun pickWeighted(pool: List<Question>, total: Int, weights: Map<String, Double>, disc: String?): List<Question> {
        if (total <= 0 || pool.isEmpty()) return emptyList()
        if (weights.isEmpty()) return pool.shuffled().take(total)
        val groups = pool.groupBy { chapterKey(it.subject, it.disc, it.chapter) }
        val W = groups.keys.sumOf { weights[it] ?: 1.0 }.coerceAtLeast(1e-9)
        val quotas = groups.mapValues { (k, _) -> maxOf(0, (total * (weights[k] ?: 1.0) / W).roundToInt()) }
        val picked = groups.map { (k, list) ->
            list.shuffled().take((quotas[k] ?: 0).coerceAtMost(list.size))
        }.flatten().toMutableList()
        var deficit = total - picked.size
        if (deficit > 0) {
            val remain = pool.filter { it !in picked }
            picked.addAll(remain.shuffled().take(deficit))
        }
        return picked.shuffled()
    }

    /** 穿插混合：按科目交替排列，提升区分力 */
    private fun interleave(questions: List<Question>): List<Question> {
        val bySubj = questions.groupBy { it.subject }
        val iterators = bySubj.values.map { it.iterator() }
        val out = mutableListOf<Question>()
        while (iterators.any { it.hasNext() }) {
            for (it in iterators) if (it.hasNext()) out.add(it.next())
        }
        return out
    }

}

