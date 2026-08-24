package com.jiaozi.sz.domain

import com.jiaozi.sz.data.model.Question
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

open class PracticeEngineTest {
    private fun q(id: String, subject: String, disc: String? = null): Question =
        Question(id = id, subject = subject, chapter = "c", q = "q$id", disc = disc)

    private val bank = (0 until 200).map { q("k1_$it", "科一") } +
            (0 until 200).map { q("k2_$it", "科二") } +
            (0 until 200).map { q("k3_$it", "科三", "美术") }

    @Test
    fun `蓝图50题三科覆盖无重复`() {
        val r = PracticeEngine.blueprint(bank, "美术", 50)
        assertEquals(50, r.size)
        assertEquals(50, r.distinctBy { it.id }.size)
        val subj = r.groupBy { it.subject }.mapValues { it.value.size }
        assertTrue(subj.containsKey("科一"))
        assertTrue(subj.containsKey("科二"))
        assertTrue(subj.containsKey("科三"))
    }

    @Test
    fun `蓝图科三不串其他学科`() {
        val r = PracticeEngine.blueprint(bank, "美术", 50)
        val k3 = r.filter { it.subject == "科三" }
        assertTrue(k3.all { it.disc == "美术" })
        assertTrue(k3.size >= 10)
    }

    @Test
    fun `章节练习限制数量`() {
        val r = PracticeEngine.chapter(bank.filter { it.subject == "科一" }, 10)
        assertEquals(10, r.size)
    }
}
