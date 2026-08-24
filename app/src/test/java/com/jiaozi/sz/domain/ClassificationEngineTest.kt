package com.jiaozi.sz.domain

import com.jiaozi.sz.data.model.AutoSyllSubj
import com.jiaozi.sz.data.model.AutoSyllRule
import com.jiaozi.sz.data.model.Question
import com.jiaozi.sz.data.model.SyllabusChapter
import com.jiaozi.sz.data.model.SyllabusSubject
import org.junit.Assert.assertEquals
import org.junit.Test

open class ClassificationEngineTest {
    private fun engine(): ClassificationEngine {
        val auto = listOf(
            AutoSyllSubj("科一", listOf(
                AutoSyllRule("一、职业理念", "教育观", listOf("素质教育", "面向全体"))
            )),
            AutoSyllSubj("科二", listOf(
                AutoSyllRule("一、教育基础", "教育起源", listOf("生物起源", "劳动起源"))
            ))
        )
        val syll = listOf(
            SyllabusSubject("科三", "科三", listOf(
                SyllabusChapter("一、学科知识", listOf("中国美术史", "外国美术史"))
            ))
        )
        return ClassificationEngine(auto, syll)
    }

    @Test
    fun `科三保留已有归类`() {
        val e = Question(id = "x", subject = "科三", chapter = "一、学科知识", section = "中国美术史", q = "某画作赏析")
        val r = engine().classifyQuestion(e, "科三")
        assertEquals("科三", r.subj)
        assertEquals("一、学科知识", r.ch)
        assertEquals("中国美术史", r.sec)
    }

    @Test
    fun `科二保留已有归类`() {
        val e = Question(id = "x", subject = "科二", chapter = "一、教育基础", section = "教育起源", q = "教育起源说")
        val r = engine().classifyQuestion(e, "科二")
        assertEquals("科二", r.subj)
    }

    @Test
    fun `科一素质教育命中教育观`() {
        val r = engine().classifyText("素质教育强调面向全体学生", "科一")
        assertEquals("科一", r?.subj)
        assertEquals("教育观", r?.sec)
    }
}
