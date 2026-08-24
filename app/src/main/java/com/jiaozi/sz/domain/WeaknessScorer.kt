package com.jiaozi.sz.domain

import com.jiaozi.sz.data.local.ProgressEntity

/**
 * 薄弱评分（移植自 HTML weakness）。
 * weakness = 错误率 × 0.65 + 临期 × 0.35
 * 未练过给 0.5 基线（确保新题也能进入"薄弱优先"）。
 */
object WeaknessScorer {
    fun score(right: Int, wrong: Int, dueTs: Long): Float {
        val total = right + wrong
        if (total == 0) return 0.5f
        val errRate = wrong.toFloat() / total
        val overdue = dueTs > 0 && dueTs <= System.currentTimeMillis()
        val dueWeight = if (overdue) 1f else 0f
        return (errRate * 0.65f + dueWeight * 0.35f).coerceAtMost(1f)
    }

    fun score(p: ProgressEntity?): Float =
        if (p == null) 0.5f else score(p.right, p.wrong, p.due)
}
