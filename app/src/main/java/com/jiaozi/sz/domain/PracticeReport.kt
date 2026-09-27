package com.jiaozi.sz.domain

import com.jiaozi.sz.data.model.Question

/**
 * 单题作答记录（一次练习/模考中的最小结算单元）。
 *
 * 原先定义在 `ui/PracticeViewModel.kt`，但它本身是**纯数据**、无任何 UI 依赖，
 * 却被 domain 层的结算逻辑需要。故上移到 domain，`ui` 侧保留同名 typealias 以零改动调用点。
 */
data class AnswerRecord(
    val correct: Boolean,
    val cause: List<String>,
    val subject: String = "",   // 所属科目（模考分科报告用）
    val draft: String? = null,  // 主观题作答草稿（复盘可见）
    val selected: Int = -1      // 客观题所选下标（复盘显示"我选了 X"）
)

/** 单次会话的结算统计：正确率 + 错因分布。 */
data class SessionSummary(
    val total: Int,
    val correct: Int,
    val accuracy: Float,
    val causeCounts: Map<String, Int>
)

/**
 * 练习/模考的结算计算（纯函数，无状态、无 Android 依赖）。
 *
 * 原先是 `PracticeViewModel` 上的成员方法，直接读 `_state.value`；上移到 domain 后
 * 入参显式、可单测，ViewModel 侧只做取状态 + 委托，对外签名保持不变。
 */
object PracticeReport {

    /** 结算：正确率 + 主要错因分布。 */
    fun summary(results: Collection<AnswerRecord>): SessionSummary {
        val total = results.size
        val right = results.count { it.correct }
        val acc = if (total == 0) 0f else right.toFloat() / total
        val cause = mutableMapOf<String, Int>()
        for (r in results) for (c in r.cause) cause[c] = cause.getOrDefault(c, 0) + 1
        return SessionSummary(total = total, correct = right, accuracy = acc, causeCounts = cause)
    }

    /**
     * 模考分科报告：科一/科二/科三 各自 (正确, 总数)。
     *
     * 记录里没存科目时，用题目本身的科目回退。
     */
    fun bySubject(
        results: Map<String, AnswerRecord>,
        questions: List<Question>
    ): Map<String, Pair<Int, Int>> {
        val out = mutableMapOf<String, Pair<Int, Int>>()
        for ((id, r) in results) {
            val subj = r.subject.ifBlank { questions.find { it.id == id }?.subject ?: "" }
            if (subj.isBlank()) continue
            val (rt, tot) = out.getOrDefault(subj, 0 to 0)
            out[subj] = (rt + if (r.correct) 1 else 0) to (tot + 1)
        }
        return out
    }

    /** 分数预估（百分制）：正确率 × 100，模考用。 */
    fun scoreEstimate(summary: SessionSummary): Int = (summary.accuracy * 100).toInt()

    /** 错题清单（题 + 错因），供 AI 讲评使用。 */
    fun wrongItems(
        questions: List<Question>,
        results: Map<String, AnswerRecord>
    ): List<Pair<Question, String>> = questions.mapNotNull { q ->
        val r = results[q.id] ?: return@mapNotNull null
        if (r.correct) null else q to r.cause.joinToString("、")
    }
}
