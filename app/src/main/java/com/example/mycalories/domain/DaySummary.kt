package com.example.mycalories.domain

import com.example.mycalories.domain.model.DailyGoal
import com.example.mycalories.domain.model.LoggedItem
import com.example.mycalories.domain.model.MealType
import com.example.mycalories.domain.model.Nutrients
import com.example.mycalories.domain.model.sum
import java.time.LocalDate
import kotlin.math.roundToInt

data class MealSummary(val meal: MealType, val items: List<LoggedItem>) {
    val total: Nutrients get() = items.map { it.nutrients }.sum()
    val description: String get() = items.joinToString(", ") { it.name }
}

/** Everything the Today screen needs for one day. */
data class DaySummary(
    val date: LocalDate,
    val goal: DailyGoal,
    val meals: List<MealSummary>,
) {
    val eaten: Nutrients get() = meals.map { it.total }.sum()
    val remainingCalories: Int get() = goal.calories - eaten.calories.roundToInt()
    val calorieProgress: Float
        get() = if (goal.calories <= 0) 0f else (eaten.calories / goal.calories).toFloat().coerceIn(0f, 1f)

    companion object {
        fun of(date: LocalDate, items: List<LoggedItem>, goal: DailyGoal): DaySummary {
            val byMeal = items.groupBy { it.meal }
            return DaySummary(date, goal, MealType.entries.map { MealSummary(it, byMeal[it].orEmpty()) })
        }
    }
}
