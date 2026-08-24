package com.jiaozi.sz.data.model

import kotlinx.serialization.Serializable

/** 内置题库（bank.json）：exam + papers */
@Serializable
data class Bank(
    val exam: List<Question>,
    val papers: List<Paper>
)

/**
 * 单题。字段与 HTML 的 window.__BUILTIN_BANK__ 对齐。
 * 可选字段给默认值（源数据中存在缺 section / 缺 analysis 的题，应用需容错）。
 */
@Serializable
data class Question(
    val id: String,
    val subject: String,            // 科一 / 科二 / 科三
    val chapter: String,
    val section: String? = null,
    val point: String? = null,
    val level: String? = null,
    val q: String,
    val opt: String = "",          // 选项文本；主观题为空
    val answer: String = "",       // 客观题答案；主观题可空（按对/错评判）
    val analysis: String? = null,
    val wrongBook: Boolean = false,
    val wrongReason: String? = null,
    val topic: String? = null,
    val disc: String? = null,       // 科三学科
    val cause: String? = null,      // 错因
    val _init: Boolean = true,
    val _mt: Long = 0,
    val _del: Boolean = false,
    val flag: String? = null,        // 校订标记，如 "待审"（v5.17 质量护栏）
    val flagMsg: String? = null      // 校订提示（缺题型/解析过短等）
) {
    /** 是否为主观题（无选项） */
    val isSubjective: Boolean get() = opt.isBlank()
}

@Serializable
data class Paper(
    val id: String,
    val name: String,
    val subject: String,
    val dur: Int = 0,
    val items: List<PaperItem> = emptyList()
)

@Serializable
data class PaperItem(val qid: String, val score: Int = 0)

/** 大纲（default_syllabus.json）：科目 → 章 → 节 */
@Serializable
data class SyllabusSubject(
    val subject: String,
    val name: String,
    val chapters: List<SyllabusChapter>
)

@Serializable
data class SyllabusChapter(
    val name: String,
    val sections: List<String>
)

/** 自动归类规则（auto_syll.json）：科目 → 章/节 + 关键词 */
@Serializable
data class AutoSyllSubj(
    val subj: String,
    val rules: List<AutoSyllRule>
)

@Serializable
data class AutoSyllRule(
    val ch: String,
    val sec: String,
    val kws: List<String>
)

/** 知识卡（knowledge.json） */
@Serializable
data class Knowledge(
    val id: String,
    val cat: String,
    val title: String,
    val content: String,
    val link: String = "",
    val tags: String = "",
    val favAt: String? = null,
    val due: String? = null
)
