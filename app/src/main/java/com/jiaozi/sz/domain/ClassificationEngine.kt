package com.jiaozi.sz.domain

import com.jiaozi.sz.data.model.AutoSyllSubj
import com.jiaozi.sz.data.model.Question
import com.jiaozi.sz.data.model.SyllabusSubject

data class ClassResult(val subj: String, val ch: String, val sec: String)

/**
 * 自动归类（移植自 HTML autoClassify / classifyText）。
 * 规则：科一/科二 用 AUTO_SYLL 关键词命中；科三 无关键词规则，靠 disc+chapter 预归类
 * （与 HTML 一致），仅当文本含大纲章节名时回退匹配。
 */
class ClassificationEngine(
    private val autoSyll: List<AutoSyllSubj>,
    private val syllabus: List<SyllabusSubject>
) {
    fun classifyText(text: String, subjHint: String? = null): ClassResult? {
        val t = text.lowercase()
        val subjects = if (subjHint != null) autoSyll.filter { it.subj == subjHint } else autoSyll
        var best: ClassResult? = null
        var bestScore = 0
        for (s in subjects) {
            for (r in s.rules) {
                var score = 0
                for (kw in r.kws) if (t.contains(kw.lowercase())) score++
                if (score > bestScore) {
                    bestScore = score
                    best = ClassResult(s.subj, r.ch, r.sec)
                }
            }
        }
        if (bestScore > 0) return best
        // 科三回退：文本含大纲章节名
        if (subjHint == "科三") {
            val s3 = syllabus.find { it.subject == "科三" } ?: return null
            val tt = t
            for (c in s3.chapters) for (sec in c.sections) {
                if (tt.contains(sec.lowercase())) return ClassResult("科三", c.name, sec)
            }
        }
        return if (subjHint != null) ClassResult(subjHint, "", "") else null
    }

    /** 归类单题：已有归类优先保留，否则按关键词/章节回退 */
    fun classifyQuestion(e: Question, subjHint: String? = null): ClassResult {
        if (!e.subject.isBlank() && !e.chapter.isBlank() && !e.section.isNullOrBlank()) {
            return ClassResult(e.subject, e.chapter, e.section)
        }
        val hint = subjHint ?: e.subject
        if (hint == "科三") {
            val s3 = syllabus.find { it.subject == "科三" }
            if (s3 != null) {
                val t = (e.q).lowercase()
                for (c in s3.chapters) for (sec in c.sections) {
                    if (t.contains(sec.lowercase())) return ClassResult("科三", c.name, sec)
                }
            }
            return ClassResult("科三", e.chapter, e.section ?: "")
        }
        return classifyText(e.q, hint) ?: ClassResult(hint, e.chapter, e.section ?: "")
    }
}
