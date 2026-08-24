package com.jiaozi.sz.domain

import com.jiaozi.sz.data.local.ProgressEntity
import org.junit.Assert.assertEquals
import org.junit.Test

open class WeaknessScorerTest {
    @Test
    fun `未练过给基线05`() {
        assertEquals(0.5f, WeaknessScorer.score(null))
        assertEquals(0.5f, WeaknessScorer.score(0, 0, 0))
    }

    @Test
    fun `高错且逾期接近1`() {
        val now = System.currentTimeMillis()
        val s = WeaknessScorer.score(right = 1, wrong = 19, dueTs = now - 1000)
        // errRate=0.95*0.65 + 1*0.35 = 0.6175+0.35 = 0.9675? 实际接近1
        assert(s > 0.9f) { "期望接近1，实际 $s" }
    }

    @Test
    fun `全对且无逾期最低`() {
        val s = WeaknessScorer.score(right = 20, wrong = 0, dueTs = 0)
        assertEquals(0f, s)
    }
}
