package com.jiaozi.sz.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

open class SpacedRepetitionTest {
    @Test
    fun `答错明天复习`() {
        val base = 1_000_000L
        val due = SpacedRepetition.nextDue("wrong", 0, base)
        assertEquals(base + 86_400_000L, due)
    }

    @Test
    fun `连对间隔递增`() {
        val base = 1_000_000L
        val d1 = SpacedRepetition.nextDue("right", 0, base)
        val d3 = SpacedRepetition.nextDue("right", 2, base)
        assertTrue(d3 > d1)
        assertEquals(base + 7L * 86_400_000L, d3) // INTERVALS[2]=7
    }

    @Test
    fun `连对计数`() {
        assertEquals(3, SpacedRepetition.nextStreak("right", 2))
        assertEquals(0, SpacedRepetition.nextStreak("wrong", 5))
    }
}
