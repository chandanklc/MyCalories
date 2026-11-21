package com.example.mycalories.domain

import com.example.mycalories.domain.model.FoodSource
import com.example.mycalories.domain.model.LoggedItem
import com.example.mycalories.domain.model.MealType
import com.example.mycalories.domain.model.Nutrients
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class PortionCalculatorTest {

    @Test
    fun `grams in a unit use standard kitchen measures`() {
        assertEquals(1.0, PortionCalculator.gramsPerUnit("g", fallback = 99.0), 0.0)
        assertEquals(1000.0, PortionCalculator.gramsPerUnit("kg", fallback = 99.0), 0.0)
        assertEquals(1.0, PortionCalculator.gramsPerUnit("ml", fallback = 99.0), 0.0)
        assertEquals(240.0, PortionCalculator.gramsPerUnit("cup", fallback = 99.0), 0.0)
        assertEquals(200.0, PortionCalculator.gramsPerUnit("bowl", fallback = 99.0), 0.0)
        assertEquals(150.0, PortionCalculator.gramsPerUnit("katori", fallback = 99.0), 0.0)
        assertEquals(15.0, PortionCalculator.gramsPerUnit("tbsp", fallback = 99.0), 0.0)
        assertEquals(5.0, PortionCalculator.gramsPerUnit("tsp", fallback = 99.0), 0.0)
    }

    @Test
    fun `unit names are case and plural insensitive`() {
        assertEquals(240.0, PortionCalculator.gramsPerUnit("Cups", fallback = 99.0), 0.0)
        assertEquals(200.0, PortionCalculator.gramsPerUnit(" BOWLS ", fallback = 99.0), 0.0)
    }

    @Test
    fun `unknown units like piece use the food's own weight`() {
        assertEquals(40.0, PortionCalculator.gramsPerUnit("piece", fallback = 40.0), 0.0)
    }

    private val roti = LoggedItem(
        id = 7, date = LocalDate.of(2026, 11, 18), meal = MealType.LUNCH, name = "Roti",
        quantity = 2.0, unit = "piece", grams = 80.0,
        per100g = Nutrients(calories = 300.0, protein = 7.5, carbs = 45.0, fat = 9.25, fiber = 6.75),
        source = FoodSource.LOCAL, needsReview = false, loggedAtMillis = 0,
    )

    @Test
    fun `logged item nutrients come from grams`() {
        assertEquals(240.0, roti.nutrients.calories, 0.001)
    }

    @Test
    fun `changing quantity keeps grams per unit`() {
        val three = PortionCalculator.withPortion(roti, quantity = 3.0, unit = "piece")
        assertEquals(120.0, three.grams, 0.001)
        assertEquals(360.0, three.nutrients.calories, 0.001)
    }

    @Test
    fun `changing to grams sets grams directly`() {
        val g = PortionCalculator.withPortion(roti, quantity = 50.0, unit = "g")
        assertEquals(50.0, g.grams, 0.001)
        assertEquals(150.0, g.nutrients.calories, 0.001)
    }

    @Test
    fun `changing to a kitchen unit uses that unit`() {
        val bowl = PortionCalculator.withPortion(roti, quantity = 0.5, unit = "bowl")
        assertEquals(100.0, bowl.grams, 0.001)
    }

    @Test
    fun `editing clears the needs review flag`() {
        val flagged = roti.copy(needsReview = true)
        assertEquals(false, PortionCalculator.withPortion(flagged, 2.0, "piece").needsReview)
    }

    @Test
    fun `quantity below zero is clamped to zero`() {
        assertEquals(0.0, PortionCalculator.withPortion(roti, -1.0, "piece").grams, 0.0)
    }
}
