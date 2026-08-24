package com.jiaozi.sz.domain

import com.jiaozi.sz.data.local.DailyStatEntity
import com.jiaozi.sz.data.local.ProgressEntity
import com.jiaozi.sz.data.model.Question

/**
 * 统计计算（移植自 HTML 统计页）。纯函数，便于测试。
 */
object StatsCalculator {
    /** 各科目正确率（基于进度） */
    fun accuracyBySubject(
        questions: List<Question>,
        progress: Map<String, ProgressEntity>
    ): Map<String, Float> {
        val acc = mutableMapOf<String, Pair<Int, Int>>() // subject -> (right, total)
        for (q in questions) {
            val p = progress[q.id] ?: continue
            val total = p.right + p.wrong
            if (total == 0) continue
            val cur = acc.getOrPut(q.subject) { 0 to 0 }
            acc[q.subject] = cur.first + p.right to cur.second + total
        }
        return acc.mapValues { (_, v) -> if (v.second == 0) 0f else v.first.toFloat() / v.second }
    }

    /** 章节正确率（由低到高，用于薄弱排行） */
    fun chapterRanking(
        questions: List<Question>,
        progress: Map<String, ProgressEntity>
    ): List<Pair<String, Float>> {
        val byChapter = questions.groupBy { it.subject to it.chapter }
        val list = byChapter.mapNotNull { (key, qs) ->
            val (right, total) = qs.fold(0 to 0) { acc, q ->
                val p = progress[q.id] ?: return@fold acc
                acc.first + p.right to acc.second + p.right + p.wrong
            }
            if (total == 0) null else (key.second to right.toFloat() / total)
        }
        return list.sortedBy { it.second }
    }

    /** 错因分布 */
    fun causeDistribution(progress: Map<String, ProgressEntity>): Map<String, Int> {
        val dist = mutableMapOf<String, Int>()
        for (p in progress.values) {
            val c = p.cause ?: continue
            // cause 可能是 "概念不清,审题偏差" 多个，逗号分隔
            for (part in c.split(",")) {
                val t = part.trim()
                if (t.isNotEmpty()) dist[t] = dist.getOrDefault(t, 0) + 1
            }
        }
        return dist
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

    /** 掌握度（正确率 ≥ 80% 的题占比，近似） */
    fun masteryRate(progress: Map<String, ProgressEntity>): Float {
        val done = progress.values.filter { it.right + it.wrong > 0 }
        if (done.isEmpty()) return 0f
        val mastered = done.count { it.right.toFloat() / (it.right + it.wrong) >= 0.8f }
        return mastered.toFloat() / done.size
    }
}
