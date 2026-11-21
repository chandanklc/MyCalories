package com.example.mycalories.domain

import com.example.mycalories.domain.model.DailyGoal
import com.example.mycalories.domain.model.FoodSource
import com.example.mycalories.domain.model.LoggedItem
import com.example.mycalories.domain.model.MealType
import com.example.mycalories.domain.model.Nutrients
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DaySummaryTest {

    private val day = LocalDate.of(2026, 11, 18)
    private val goal = DailyGoal(calories = 2000, protein = 100, carbs = 250, fat = 70, fiber = 30)

    private fun item(meal: MealType, kcalPer100: Double, grams: Double, name: String = "x") = LoggedItem(
        id = 0, date = day, meal = meal, name = name, quantity = 1.0, unit = "g", grams = grams,
        per100g = Nutrients(calories = kcalPer100, protein = 10.0), source = FoodSource.LOCAL,
        needsReview = false, loggedAtMillis = 0,
    )

    @Test
    fun `groups items by meal in breakfast lunch snack dinner order`() {
        val summary = DaySummary.of(day, listOf(item(MealType.DINNER, 100.0, 100.0), item(MealType.BREAKFAST, 100.0, 100.0)), goal)
        assertEquals(MealType.entries.toList(), summary.meals.map { it.meal })
        assertEquals(1, summary.meals.first { it.meal == MealType.BREAKFAST }.items.size)
        assertTrue(summary.meals.first { it.meal == MealType.LUNCH }.items.isEmpty())
    }

    @Test
    fun `totals and remaining`() {
        val summary = DaySummary.of(day, listOf(item(MealType.LUNCH, 200.0, 300.0), item(MealType.SNACK, 100.0, 100.0)), goal)
        assertEquals(700.0, summary.eaten.calories, 0.001)
        assertEquals(1300, summary.remainingCalories)
        assertEquals(40.0, summary.eaten.protein, 0.001)
    }

    @Test
    fun `remaining goes negative when over`() {
        val summary = DaySummary.of(day, listOf(item(MealType.DINNER, 250.0, 1000.0)), goal)
        assertEquals(-500, summary.remainingCalories)
    }

    @Test
    fun `meal summary total and short description`() {
        val summary = DaySummary.of(day, listOf(item(MealType.LUNCH, 100.0, 100.0, "Roti"), item(MealType.LUNCH, 100.0, 50.0, "Dal")), goal)
        val lunch = summary.meals.first { it.meal == MealType.LUNCH }
        assertEquals(150.0, lunch.total.calories, 0.001)
        assertEquals("Roti, Dal", lunch.description)
    }

    @Test
    fun `progress is eaten over goal and capped at one`() {
        val summary = DaySummary.of(day, listOf(item(MealType.LUNCH, 100.0, 1000.0)), goal)
        assertEquals(0.5f, summary.calorieProgress, 0.001f)
        val over = DaySummary.of(day, listOf(item(MealType.LUNCH, 100.0, 5000.0)), goal)
        assertEquals(1f, over.calorieProgress, 0.0f)
    }
}
