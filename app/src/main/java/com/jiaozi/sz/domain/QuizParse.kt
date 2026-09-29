package com.jiaozi.sz.domain

import com.jiaozi.sz.data.model.Question

/** 从选项文本解析出选项列表。格式如 "A. 面向全体  B. 全面发展  C. ..." */
fun parseOptions(opt: String): List<String> {
    if (opt.isBlank()) return emptyList()
    val re = Regex("([A-H])[.、]\\s*([\\s\\S]*?)(?=(?:[A-H])[.、]|$)")
    return re.findAll(opt).map { it.groupValues[2].trim() }.filter { it.isNotEmpty() }.toList()
}

/**
 * 答案字母 → 选项下标（"D" → 3）。主观题/异常返回 -1。
 *
 * 🔴 客观题答案规范：**整串恰为单个字母 A–H**（如 `"C"`）。
 * 旧实现只取首字符，会把文本型答案误判为选项下标——实测全库 7 例，
 * 例如科二 `"A-诱发事件（Activating event）"`（艾里斯 ABC 理论，主观题）、
 * 科三化学 `"c(...)"`、生物 `"C3植物…"`、物理 `"g=9.8…"` 等。
 * 这些题 opt 为空（主观题）故被 `shuffleQuestionOptions` 的 `opts.size < 2` 早退挡下，
 * 侥幸未触发；一旦有选项不全的脏数据，就会把正确答案错标到 A 选项。
 * ⇒ 收紧为「trim 后长度必须恰为 1」，从根上杜绝误判。
 */
fun answerIndex(answer: String): Int {
    if (answer.isBlank()) return -1
    val a = answer.trim()
    if (a.length != 1) return -1
    val c = a[0].uppercaseChar()
    return if (c in 'A'..'H') c - 'A' else -1
}

/** 选项字母表（上限 H，与 parseOptions 正则一致） */
private val OPT_LETTERS = ('A'..'H').toList()

/**
 * 选项乱序（04 号 practice.setup F3）：把选项文本重排，**并同步把答案字母改写为新位置的字母**。
 *
 * 设计取舍：不引入「字母 ↔ 原下标」第二套映射，而是直接重写题干 opt 与 answer 两个字段。
 * 理由：下游 `parseOptions` / `answerIndex` / `PracticeSession` / `PracticeSummary` / 错题本
 * 全部依赖「字母 ↔ 下标」的直映射（`c - 'A'`），若另立映射表需改动 5 处调用点且易错；
 * 重写文本后所有下游零改动，语义等价且天然一致。
 *
 * 边界：主观题（opt 为空）或选项数 < 2 时原样返回；答案字母越界（异常数据）时保留原 answer。
 */
fun shuffleQuestionOptions(q: Question): Question {
    val opts = parseOptions(q.opt)
    if (opts.size < 2) return q
    val correctIdx = answerIndex(q.answer)
    // order[i] = 新位置 i 上放置的原选项下标
    val order = opts.indices.shuffled()
    val newOpt = order.mapIndexed { i, srcIdx -> "${OPT_LETTERS[i]}. ${opts[srcIdx]}" }
        .joinToString("  ")
    val newAnswer = if (correctIdx in opts.indices) {
        OPT_LETTERS[order.indexOf(correctIdx)].toString()
    } else {
        q.answer
    }
    return q.copy(opt = newOpt, answer = newAnswer)
}
