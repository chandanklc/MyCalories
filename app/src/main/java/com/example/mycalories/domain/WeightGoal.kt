package com.example.mycalories.domain

import com.example.mycalories.domain.model.GoalType
import com.example.mycalories.domain.model.WeightEntry
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToLong

object WeightTrend {
    private const val WINDOW_DAYS = 56L

    /** Kilograms per week from a straight-line fit of the last 8 weeks. Null until there is a week of data. */
    fun weeklyRate(entries: List<WeightEntry>, today: LocalDate): Double? {
        val recent = entries.filter { !it.date.isBefore(today.minusDays(WINDOW_DAYS)) && !it.date.isAfter(today) }
        if (recent.size < 2) return null
        val xs = recent.map { ChronoUnit.DAYS.between(today, it.date).toDouble() }
        if (xs.max() - xs.min() < 7) return null
        val ys = recent.map { it.kg }
        val mx = xs.average()
        val my = ys.average()
        val num = xs.indices.sumOf { (xs[it] - mx) * (ys[it] - my) }
        val den = xs.sumOf { (it - mx) * (it - mx) }
        return if (den == 0.0) null else num / den * 7
    }

    /** When [target] is reached at [weeklyRate], or null if not heading there. */
    fun projectedDate(current: Double, target: Double, weeklyRate: Double, today: LocalDate): LocalDate? {
        val diff = target - current
        if (weeklyRate == 0.0 || diff * weeklyRate <= 0) return null
        val weeks = diff / weeklyRate
        return today.plusDays((weeks * 7).roundToLong())
    }
}

data class GoalSuggestion(val currentCalories: Int, val newCalories: Int) {
    val change: Int get() = newCalories - currentCalories
}

/** Suggests a small calorie change when real weight change is off the plan for 3+ weeks. */
object GoalAdjuster {
    fun suggest(goal: GoalType, currentCalories: Int, weeklyRate: Double, weeksOfData: Int): GoalSuggestion? {
        if (weeksOfData < 3) return null
        val change = when (goal) {
            GoalType.LOSE -> when {
                weeklyRate > -0.3 -> -120
                weeklyRate < -1.0 -> 150
                else -> 0
            }
            GoalType.GAIN -> when {
                weeklyRate < 0.1 -> 120
                weeklyRate > 0.5 -> -120
                else -> 0
            }
            GoalType.MAINTAIN -> when {
                weeklyRate > 0.25 -> -100
                weeklyRate < -0.25 -> 100
                else -> 0
            }
        }
        val newCalories = max(GoalCalculator.MIN_CALORIES, currentCalories + change)
        return if (abs(newCalories - currentCalories) < 1) null else GoalSuggestion(currentCalories, newCalories)
    }
}
