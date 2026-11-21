package com.example.mycalories.domain

import com.example.mycalories.domain.model.DailyGoal
import com.example.mycalories.domain.model.LoggedItem
import com.example.mycalories.domain.model.WeightEntry
import java.time.LocalDate
import kotlin.math.abs

data class WeekSummary(
    val from: LocalDate,
    val to: LocalDate,
    val averageCalories: Double,
    val averageProtein: Double,
    val daysLogged: Int,
    val daysOnTarget: Int,
    val lowProteinDays: Int,
    /** Average daily fiber this week minus last week; null without last week's data. */
    val fiberChange: Double?,
    val waterGoalDays: Int,
    val weightChange: Double?,
    val streak: Int,
    val proteinTips: List<ProteinTip>,
) {
    val hasData: Boolean get() = daysLogged > 0
}

data class ProteinTip(val food: String, val grams: Int)

object WeeklyInsights {
    const val WATER_GOAL = 8

    private val tips = listOf(ProteinTip("A katori of curd", 5), ProteinTip("2 boiled eggs", 13), ProteinTip("50 g paneer", 9))

    fun build(
        items: List<LoggedItem>,
        today: LocalDate,
        goal: DailyGoal,
        water: Map<LocalDate, Int>,
        weights: List<WeightEntry>,
        streak: Int,
    ): WeekSummary {
        val from = today.minusDays(6)
        fun inRange(d: LocalDate, a: LocalDate, b: LocalDate) = !d.isBefore(a) && !d.isAfter(b)
        val week = items.filter { inRange(it.date, from, today) }.groupBy { it.date }
        val previous = items.filter { inRange(it.date, from.minusDays(7), from.minusDays(1)) }.groupBy { it.date }

        val kcal = week.mapValues { (_, l) -> l.sumOf { it.nutrients.calories } }
        val protein = week.mapValues { (_, l) -> l.sumOf { it.nutrients.protein } }
        val fiber = week.values.map { l -> l.sumOf { it.nutrients.fiber } }
        val prevFiber = previous.values.map { l -> l.sumOf { it.nutrients.fiber } }

        val weightNow = weights.filter { !it.date.isAfter(today) }.maxByOrNull { it.date }
        val weightBefore = weights.filter { !it.date.isAfter(today.minusDays(7)) }.maxByOrNull { it.date }
        val lowProtein = protein.values.count { it < goal.protein * 0.8 }

        return WeekSummary(
            from = from,
            to = today,
            averageCalories = if (kcal.isEmpty()) 0.0 else kcal.values.average(),
            averageProtein = if (protein.isEmpty()) 0.0 else protein.values.average(),
            daysLogged = week.size,
            daysOnTarget = kcal.values.count { abs(it - goal.calories) <= goal.calories * 0.1 },
            lowProteinDays = lowProtein,
            fiberChange = if (fiber.isEmpty() || prevFiber.isEmpty()) null else fiber.average() - prevFiber.average(),
            waterGoalDays = water.count { (d, g) -> inRange(d, from, today) && g >= WATER_GOAL },
            weightChange = if (weightNow != null && weightBefore != null && weightNow != weightBefore) weightNow.kg - weightBefore.kg else null,
            streak = streak,
            proteinTips = if (lowProtein >= 3) tips else emptyList(),
        )
    }
}
