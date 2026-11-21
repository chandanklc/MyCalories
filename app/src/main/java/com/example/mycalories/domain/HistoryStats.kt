package com.example.mycalories.domain

import java.time.LocalDate

data class DayTotal(val date: LocalDate, val calories: Double)

object HistoryStats {

    /** Consecutive logged days ending today, or ending yesterday if today has nothing yet. */
    fun streak(loggedDays: Set<LocalDate>, today: LocalDate): Int {
        var day = if (today in loggedDays) today else today.minusDays(1)
        var count = 0
        while (day in loggedDays) {
            count++
            day = day.minusDays(1)
        }
        return count
    }

    fun lastSevenDays(totals: Map<LocalDate, Double>, today: LocalDate): List<DayTotal> =
        (6 downTo 0).map { back ->
            val d = today.minusDays(back.toLong())
            DayTotal(d, totals[d] ?: 0.0)
        }

    /** Average calories over days before today that have something logged. */
    fun averageOfCompletedDays(totals: Map<LocalDate, Double>, today: LocalDate): Double {
        val done = totals.filter { (d, kcal) -> d.isBefore(today) && kcal > 0 }.values
        return if (done.isEmpty()) 0.0 else done.average()
    }
}
