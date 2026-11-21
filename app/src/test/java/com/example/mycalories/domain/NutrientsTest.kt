package com.example.mycalories.domain

import com.example.mycalories.domain.model.Nutrients
import com.example.mycalories.domain.model.sum
import org.junit.Assert.assertEquals
import org.junit.Test

class NutrientsTest {

    private val dal = Nutrients(calories = 90.0, protein = 4.5, carbs = 12.0, fat = 2.5, fiber = 3.0, sugar = 1.0, sodiumMg = 300.0)

    @Test
    fun `plus adds every field`() {
        val sum = dal + dal
        assertEquals(Nutrients(180.0, 9.0, 24.0, 5.0, 6.0, 2.0, 600.0), sum)
    }

    @Test
    fun `times scales every field`() {
        assertEquals(Nutrients(180.0, 9.0, 24.0, 5.0, 6.0, 2.0, 600.0), dal * 2.0)
    }

    @Test
    fun `forGrams scales a per 100 g value`() {
        val twoHundredGrams = dal.forGrams(200.0)
        assertEquals(180.0, twoHundredGrams.calories, 0.001)
        assertEquals(9.0, twoHundredGrams.protein, 0.001)
    }

    @Test
    fun `forGrams of zero is zero`() {
        assertEquals(Nutrients.ZERO, dal.forGrams(0.0))
    }

    @Test
    fun `sum of empty list is zero`() {
        assertEquals(Nutrients.ZERO, emptyList<Nutrients>().sum())
    }

    @Test
    fun `sum adds a list`() {
        assertEquals(dal * 3.0, listOf(dal, dal, dal).sum())
    }

    @Test
    fun `calorie split by macro uses 4-4-9 rule`() {
        val n = Nutrients(calories = 0.0, protein = 25.0, carbs = 50.0, fat = 100.0 / 9.0)
        val split = n.macroCalorieShare()
        assertEquals(0.25, split.protein, 0.001)
        assertEquals(0.50, split.carbs, 0.001)
        assertEquals(0.25, split.fat, 0.001)
    }

    @Test
    fun `calorie split of zero macros is all zero`() {
        val split = Nutrients.ZERO.macroCalorieShare()
        assertEquals(0.0, split.protein + split.carbs + split.fat, 0.0)
    }
}
