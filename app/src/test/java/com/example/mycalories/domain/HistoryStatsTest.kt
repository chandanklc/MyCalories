package com.example.mycalories.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class HistoryStatsTest {

    private val today = LocalDate.of(2026, 11, 18)

    @Test
    fun `streak counts consecutive logged days ending today`() {
        val days = setOf(today, today.minusDays(1), today.minusDays(2), today.minusDays(4))
        assertEquals(3, HistoryStats.streak(days, today))
    }

    @Test
    fun `streak still counts from yesterday when today is not logged yet`() {
        val days = setOf(today.minusDays(1), today.minusDays(2))
        assertEquals(2, HistoryStats.streak(days, today))
    }

    @Test
    fun `streak is zero when neither today nor yesterday logged`() {
        assertEquals(0, HistoryStats.streak(setOf(today.minusDays(3)), today))
    }

    @Test
    fun `week has seven days ending today with zero for gaps`() {
        val totals = mapOf(today to 1149.0, today.minusDays(2) to 1850.0)
        val week = HistoryStats.lastSevenDays(totals, today)
        assertEquals(7, week.size)
        assertEquals(today.minusDays(6), week.first().date)
        assertEquals(today, week.last().date)
        assertEquals(1850.0, week[4].calories, 0.0)
        assertEquals(0.0, week[5].calories, 0.0)
    }

    @Test
    fun `average ignores days with nothing logged and today`() {
        val totals = mapOf(today to 500.0, today.minusDays(1) to 2000.0, today.minusDays(2) to 1800.0)
        assertEquals(1900.0, HistoryStats.averageOfCompletedDays(totals, today), 0.001)
    }

    @Test
    fun `average is zero with no completed days`() {
        assertEquals(0.0, HistoryStats.averageOfCompletedDays(mapOf(today to 900.0), today), 0.0)
    }
}
