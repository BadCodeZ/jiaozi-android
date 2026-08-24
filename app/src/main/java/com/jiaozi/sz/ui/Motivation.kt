package com.jiaozi.sz.ui

import kotlin.math.abs

/**
 * 鼓励语库（与网页端「综合教资备考工作台」同款备考陪伴语气）。
 * 部分为双端互通语境的 slogan，让 App UI 显式呼应网页端。
 */
object Motivation {
    /** 引用网页端、强调双端陪伴的 slogan */
    private val WEB_PHRASES = listOf(
        "网页端「综合教资备考工作台」也在陪你一起进步～",
        "手机随手练，网页端深复盘——双端陪你上岸。",
        "今天在网页端整理过的知识，这里也能接着练。",
        "你不是一个人在备考，网页端也在为你记着进度。",
    )

    /** 通用鼓励语（学习科学陪伴语气） */
    private val GENERIC = listOf(
        "每一道题，都是离讲台更近一步。",
        "今天的小坚持，是考场的底气。",
        "慢一点没关系，重要的是一直往前。",
        "把不会的变成会的，就是进步。",
        "你正在成为更好的老师，别急。",
        "错题不是失败，是下一次做对的路标。",
        "稳住节奏，上岸只是时间问题。",
        "复习的每一分钟，都在为未来加成。",
        "再小的进步，乘以每天都不算小。",
    )

    private val POOL = WEB_PHRASES + GENERIC

    /** 每日轮换一句（按日期稳定，避免重组时闪烁） */
    fun dailyPhrase(): String {
        val day = (System.currentTimeMillis() / 86400000L).toInt()
        return pick(POOL, day)
    }

    /**
     * 今日页上下文鼓励语：结合连续打卡 / 倒计时 / 正确率给出更贴切的一句。
     * @param streak 连续打卡天数
     * @param daysLeft 距考天数（null 表示未设置目标日）
     * @param acc 总正确率（-1 表示尚未练习）
     * @param practiced 已练习题数
     */
    fun todayPhrase(streak: Int, daysLeft: Int?, acc: Float, practiced: Int): String {
        val ctx = when {
            daysLeft != null && daysLeft in 0..30 ->
                "距离教资考试只剩 $daysLeft 天，再冲一把就上岸！"
            streak >= 3 ->
                "连续打卡 $streak 天，节奏稳住了，继续保持！"
            practiced > 0 && acc in 0f..0.6f ->
                "正确率还在爬坡，错题正是下次做对的路标。"
            practiced == 0 ->
                "今天还没开练，先做 5 道题热热身吧～"
            else -> null
        }
        return ctx ?: dailyPhrase()
    }

    private fun pick(pool: List<String>, seed: Int): String =
        pool.getOrElse(abs(seed) % pool.size) { pool[0] }
}
