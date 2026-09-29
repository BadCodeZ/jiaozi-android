package com.jiaozi.sz.domain

import com.jiaozi.sz.data.local.DailyStatEntity
import com.jiaozi.sz.data.local.ProgressEntity
import com.jiaozi.sz.data.model.Question
import kotlin.math.roundToInt

/**
 * 一组题目的作答汇总（🔴 全工程正确率口径的唯一权威载体）。
 *
 * **分母恒为「作答次数」= right + wrong**，不是「已练题数」——一题做 3 次对 2 次错 1 次，
 * 参与统计的是 3 次作答。这是 [StatsCalculator.overallAccuracy] 的原口径，
 * 任何派生统计（科目分布 / 章节掌握度 / 需要加强）都必须与它一致，
 * 否则会出现「正确率 342%」这类分母偏小导致的越界值（历史 bug，2026-09-29 修）。
 */
data class AttemptStat(
    /** 答对次数（累加，非去重题数） */
    val right: Int,
    /** 答错次数（累加，非去重题数） */
    val wrong: Int,
    /** 已练题数：至少作答过一次的题目数（去重） */
    val practiced: Int
) {
    /** 作答次数 = 正确率分母 */
    val attempts: Int get() = right + wrong

    /** 正确率 ∈ [0,1]；无作答时为 0（调用方自行判断是否「未练」） */
    val acc: Float get() = if (attempts == 0) 0f else right.toFloat() / attempts

    /** 正确率百分数（四舍五入取整，用于 `xx%` 文案） */
    val accPercent: Int get() = (acc * 100f).roundToInt()
}

/**
 * 统计计算（移植自 HTML 统计页）。纯函数，便于测试。
 */
object StatsCalculator {

    /**
     * 汇总一组题目的作答情况。**全工程正确率计算的唯一入口**。
     *
     * @param questions 待统计的题（调用方已完成科目 / 学段 / 章节过滤）
     * @param progress  qid → 进度
     */
    fun attemptStat(
        questions: List<Question>,
        progress: Map<String, ProgressEntity>
    ): AttemptStat {
        var right = 0
        var wrong = 0
        var practiced = 0
        for (q in questions) {
            val p = progress[q.id] ?: continue
            val attempts = p.right + p.wrong
            if (attempts == 0) continue
            right += p.right
            wrong += p.wrong
            practiced++
        }
        return AttemptStat(right = right, wrong = wrong, practiced = practiced)
    }

    /** 近 n 天练习趋势（right/wrong） */
    fun trend(daily: List<DailyStatEntity>): List<DailyStatEntity> = daily.sortedBy { it.date }

    /** 总体正确率（基于全部进度） */
    fun overallAccuracy(progress: Map<String, ProgressEntity>): Float {
        var r = 0; var t = 0
        for (p in progress.values) { t += p.right + p.wrong; r += p.right }
        return if (t == 0) 0f else r.toFloat() / t
    }

    /** 已练习题数（至少做过一次） */
    fun totalPracticed(progress: Map<String, ProgressEntity>): Int =
        progress.values.count { it.right + it.wrong > 0 }
}
