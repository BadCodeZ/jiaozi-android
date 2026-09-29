package com.jiaozi.sz.domain

import com.jiaozi.sz.data.model.Bank
import com.jiaozi.sz.data.model.Question
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P0 章名归一回归测试（2026-09-29）。
 *
 * 背景：科一 20 题章名 `三、教师职业道德` 漏「规范」；科三 797 题章名不属于
 * 官方大纲 4 章（大纲只登记了「美术」一套）。这些题在 BankScreen / GraphScreen /
 * PracticeHomeParts / PracticeSetupSheet 四处按章查询中**集体静默丢失**。
 */
class BankNormalizeTest {

    private fun q(id: String, subject: String = "科一", chapter: String) =
        Question(id = id, subject = subject, chapter = chapter, q = "题 $id")

    // ── 科一 ──

    @Test
    fun `科一脏章名并入官方章名`() {
        assertEquals("三、教师职业道德规范", BankNormalize.chapter("科一", "三、教师职业道德"))
    }

    @Test
    fun `科一官方章名不受影响`() {
        listOf("一、职业理念", "二、教育法律法规", "三、教师职业道德规范", "四、文化素养", "五、基本能力")
            .forEach { assertEquals(it, BankNormalize.chapter("科一", it)) }
    }

    // ── 科三别名章（595 题）──

    @Test
    fun `科三学科素养并入学科知识`() {
        assertEquals("一、学科知识", BankNormalize.chapter("科三", "三、学科素养"))
    }

    @Test
    fun `科三教学类别名章并入教学设计`() {
        assertEquals("二、教学设计", BankNormalize.chapter("科三", "二、学科教学知识"))
        assertEquals("二、教学设计", BankNormalize.chapter("科三", "二、教学理论与实践"))
        assertEquals("二、教学设计", BankNormalize.chapter("科三", "三、教学设计"))
    }

    // ── 科三细分章（202 题）：前缀规则 ──

    @Test
    fun `科三细分章按前缀并入学科知识`() {
        listOf(
            "一、学科知识·数与代数", "一、学科知识·图形与几何", "一、学科知识·语法",
            "一、学科知识·中国美术史", "一、学科知识·导数", "一、学科知识·修辞",
        ).forEach { assertEquals("一、学科知识", BankNormalize.chapter("科三", it)) }
    }

    @Test
    fun `科三官方章名不受影响`() {
        listOf("一、学科知识", "二、教学设计", "三、教学实施", "四、教学评价")
            .forEach { assertEquals(it, BankNormalize.chapter("科三", it)) }
        // 「一、学科知识」本身不能匹配到前缀规则后被改写（长度不同，天然安全）
        assertEquals("一、学科知识", BankNormalize.chapter("科三", "一、学科知识"))
    }

    // ── 未登记科目 ──

    @Test
    fun `未登记科目原样返回`() {
        assertEquals("任意章", BankNormalize.chapter("科二", "任意章"))
        assertEquals("任意章", BankNormalize.chapter("其他", "任意章"))
    }

    // ── 整库归一 ──

    @Test
    fun `无脏数据时 bank 原样返回同引用`() {
        val b = Bank(
            exam = listOf(q("a", chapter = "一、职业理念"), q("b", chapter = "三、教师职业道德规范")),
            papers = emptyList()
        )
        assertSame(b, BankNormalize.bank(b))
    }

    @Test
    fun `含脏数据时整库归一且不丢题`() {
        val b = Bank(
            exam = listOf(q("a", chapter = "一、职业理念"), q("b", chapter = "三、教师职业道德")),
            papers = emptyList()
        )
        val n = BankNormalize.bank(b)
        assertEquals(2, n.exam.size)
        assertEquals("三、教师职业道德规范", n.exam[1].chapter)
        assertEquals("一、职业理念", n.exam[0].chapter)
        // 干净题保持原对象引用（零额外拷贝）
        assertSame(b.exam[0], n.exam[0])
    }

    @Test
    fun `归一后按官方章名可查到全部题`() {
        val b = Bank(
            exam = listOf(
                q("a", chapter = "三、教师职业道德规范"),
                q("b", chapter = "三、教师职业道德"),
                q("c", chapter = "三、教师职业道德"),
                q("d", subject = "科三", chapter = "三、学科素养"),
                q("e", subject = "科三", chapter = "一、学科知识·数与代数"),
            ),
            papers = emptyList()
        )
        val n = BankNormalize.bank(b)
        assertEquals(3, n.exam.count { it.subject == "科一" && it.chapter == "三、教师职业道德规范" })
        assertEquals(1, n.exam.count { it.subject == "科三" && it.chapter == "一、学科知识" && it.id == "d" })
        assertEquals(1, n.exam.count { it.subject == "科三" && it.chapter == "一、学科知识" && it.id == "e" })
        assertTrue(n.exam.none { it.chapter == "三、教师职业道德" })
        assertTrue(n.exam.none { it.chapter.startsWith("一、学科知识·") })
    }
}
