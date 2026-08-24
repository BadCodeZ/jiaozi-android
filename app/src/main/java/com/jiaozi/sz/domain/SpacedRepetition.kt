package com.jiaozi.sz.domain

/**
 * 间隔复习（简化 Leitner）。移植自 HTML 的复习调度。
 * 答错/未练 → 明天；连续答对 → 间隔递增 1/3/7/15/30 天。
 */
object SpacedRepetition {
    private val INTERVALS = intArrayOf(1, 3, 7, 15, 30) // 天
    private const val DAY = 86_400_000L

    fun nextDue(lastResult: String?, streak: Int, fromTs: Long = System.currentTimeMillis()): Long {
        if (lastResult != "right") return fromTs + DAY
        val idx = minOf(streak, INTERVALS.lastIndex)
        return fromTs + INTERVALS[idx] * DAY
    }

    /** 根据对错返回新的连续答对计数 */
    fun nextStreak(lastResult: String?, prevStreak: Int): Int =
        if (lastResult == "right") prevStreak + 1 else 0
}
