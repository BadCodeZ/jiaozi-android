package com.jiaozi.sz.domain

/** 从选项文本解析出选项列表。格式如 "A. 面向全体  B. 全面发展  C. ..." */
fun parseOptions(opt: String): List<String> {
    if (opt.isBlank()) return emptyList()
    val re = Regex("([A-H])[.、]\\s*([\\s\\S]*?)(?=(?:[A-H])[.、]|$)")
    return re.findAll(opt).map { it.groupValues[2].trim() }.filter { it.isNotEmpty() }.toList()
}

/** 答案字母 → 选项下标（"D" → 3）。主观题/异常返回 -1。 */
fun answerIndex(answer: String): Int {
    if (answer.isBlank()) return -1
    val c = answer.first().uppercaseChar()
    return if (c in 'A'..'H') c - 'A' else -1
}
