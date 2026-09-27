package com.jiaozi.sz.domain

import com.jiaozi.sz.data.model.Question
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A2 选项乱序：验证「重排选项文本 + 同步改写答案字母」的语义等价性。
 * 核心不变量：乱序后 answer 指向的**选项文本**必须与乱序前一致（否则判分错乱）。
 */
class QuizParseTest {

    private fun q(opt: String, answer: String) = Question(
        id = "t1", subject = "科一", chapter = "c", q = "题干", opt = opt, answer = answer
    )

    private val opt4 = "A. 面向全体  B. 全面发展  C. 因材施教  D. 循序渐进"

    @Test
    fun `解析四个选项`() {
        assertEquals(listOf("面向全体", "全面发展", "因材施教", "循序渐进"), parseOptions(opt4))
    }

    @Test
    fun `答案字母转下标`() {
        assertEquals(0, answerIndex("A"))
        assertEquals(3, answerIndex("D"))
        assertEquals(-1, answerIndex(""))
        assertEquals(-1, answerIndex("X"))
    }

    /** 随机 200 轮：每次乱序后，answer 指向的选项文本必须保持不变 */
    @Test
    fun `选项乱序保持答案语义`() {
        val original = parseOptions(opt4)
        repeat(200) {
            val shuffled = shuffleQuestionOptions(q(opt4, "C"))
            val opts = parseOptions(shuffled.opt)
            // 1. 选项数量不变
            assertEquals(original.size, opts.size)
            // 2. 选项文本集合不变（无丢字/错位）
            assertEquals(original.toSet(), opts.toSet())
            // 3. 答案字母仍指向「因材施教」
            assertEquals("因材施教", opts[answerIndex(shuffled.answer)])
            // 4. 字母甲板按序重排 A/B/C/D
            assertEquals(opts.size, shuffled.opt.count { it in 'A'..'H' })
        }
    }

    /** 至少能观察到一次真实的重排（排除「实现为空转动」的假绿） */
    @Test
    fun `选项乱序确实改变了顺序`() {
        val changed = (1..50).any { shuffleQuestionOptions(q(opt4, "A")).opt != opt4 }
        assertTrue("50 轮内应至少出现一次不同顺序", changed)
    }

    @Test
    fun `答案字母随选项同步改写`() {
        // 遍历 40 轮，凡顺序变化的样本，其答案字母必须随之变化或仍指向原文本
        repeat(40) {
            val s = shuffleQuestionOptions(q(opt4, "A"))
            val opts = parseOptions(s.opt)
            assertEquals("面向全体", opts[answerIndex(s.answer)])
            if (s.opt != opt4) assertNotEquals("A", s.answer)
        }
    }

    @Test
    fun `主观题与单选项原样返回`() {
        val subj = q("", "")
        assertTrue(subj === shuffleQuestionOptions(subj))

        val one = q("A. 唯一选项", "A")
        assertTrue(one === shuffleQuestionOptions(one))
    }

    /** 异常答案字母（越界）时保留原 answer，不崩溃 */
    @Test
    fun `答案越界保留原值`() {
        val s = shuffleQuestionOptions(q(opt4, "Z"))
        assertEquals("Z", s.answer)
        assertEquals(4, parseOptions(s.opt).size)
    }
}
