package com.example.mycalories.domain

import com.example.mycalories.domain.model.ActivityLevel
import com.example.mycalories.domain.model.DailyGoal
import com.example.mycalories.domain.model.FoodSource
import com.example.mycalories.domain.model.GoalType
import com.example.mycalories.domain.model.LoggedItem
import com.example.mycalories.domain.model.MealType
import com.example.mycalories.domain.model.Nutrients
import com.example.mycalories.domain.model.Sex
import com.example.mycalories.domain.model.UserProfile
import com.example.mycalories.domain.model.WeightEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class MicroNeedsTest {

    @Test
    fun `women need more iron than men`() {
        assertEquals(19.0, MicroNeeds.forSex(Sex.MALE).ironMg, 0.0)
        assertEquals(29.0, MicroNeeds.forSex(Sex.FEMALE).ironMg, 0.0)
        assertEquals(80.0, MicroNeeds.forSex(Sex.MALE).vitaminCMg, 0.0)
        assertEquals(65.0, MicroNeeds.forSex(Sex.FEMALE).vitaminCMg, 0.0)
    }

    @Test
    fun `report marks low, ok, met and over`() {
        val eaten = Nutrients(ironMg = 9.0, calciumMg = 620.0, vitaminB12Ug = 0.8, vitaminDUg = 2.0, vitaminCMg = 85.0, potassiumMg = 3600.0, sodiumMg = 2950.0)
        val rows = MicroNeeds.report(eaten, MicroNeeds.forSex(Sex.MALE)).associateBy { it.key }
        assertEquals(MicroStatus.LOW, rows.getValue(MicroKey.IRON).status)      // 47 %
        assertEquals(MicroStatus.OK, rows.getValue(MicroKey.CALCIUM).status)    // 62 %
        assertEquals(MicroStatus.LOW, rows.getValue(MicroKey.B12).status)
        assertEquals(MicroStatus.MET, rows.getValue(MicroKey.VITAMIN_C).status)
        assertEquals(MicroStatus.MET, rows.getValue(MicroKey.POTASSIUM).status)
        assertEquals(MicroStatus.OVER, rows.getValue(MicroKey.SODIUM).status)
        assertEquals(1f, rows.getValue(MicroKey.SODIUM).progress, 0f)
        assertEquals(0.47f, rows.getValue(MicroKey.IRON).progress, 0.01f)
    }

    @Test
    fun `sodium under the limit is fine`() {
        val row = MicroNeeds.report(Nutrients(sodiumMg = 1500.0), MicroNeeds.forSex(Sex.MALE)).first { it.key == MicroKey.SODIUM }
        assertEquals(MicroStatus.OK, row.status)
    }
}

class WeightTrendTest {

    private val today = LocalDate.of(2026, 11, 18)
    private fun w(daysAgo: Long, kg: Double) = WeightEntry(today.minusDays(daysAgo), kg)

    @Test
    fun `weekly rate is the straight-line slope`() {
        val entries = (0..7).map { week -> w(49 - week * 7L, 80.0 - week * 0.3) }
        assertEquals(-0.3, WeightTrend.weeklyRate(entries, today)!!, 0.001)
    }

    @Test
    fun `needs at least a week of data`() {
        assertNull(WeightTrend.weeklyRate(listOf(w(3, 80.0), w(0, 79.5)), today))
        assertNull(WeightTrend.weeklyRate(listOf(w(0, 80.0)), today))
    }

    @Test
    fun `only the last eight weeks count`() {
        val entries = listOf(w(200, 95.0), w(14, 80.0), w(7, 79.5), w(0, 79.0))
        assertEquals(-0.5, WeightTrend.weeklyRate(entries, today)!!, 0.001)
    }

    @Test
    fun `projects the date a target is reached`() {
        assertEquals(today.plusWeeks(20), WeightTrend.projectedDate(78.0, 72.0, -0.3, today))
    }

    @Test
    fun `no projection when moving away from the target`() {
        assertNull(WeightTrend.projectedDate(78.0, 72.0, 0.2, today))
        assertNull(WeightTrend.projectedDate(78.0, 72.0, 0.0, today))
    }
}

class GoalAdjusterTest {

    @Test
    fun `losing too slowly suggests 120 fewer calories`() {
        val s = GoalAdjuster.suggest(GoalType.LOSE, currentCalories = 1840, weeklyRate = -0.25, weeksOfData = 4)!!
        assertEquals(1720, s.newCalories)
        assertEquals(-120, s.change)
    }

    @Test
    fun `losing too fast suggests 150 more`() {
        assertEquals(150, GoalAdjuster.suggest(GoalType.LOSE, 1800, -1.2, 4)!!.change)
    }

    @Test
    fun `on pace means no change`() {
        assertNull(GoalAdjuster.suggest(GoalType.LOSE, 1840, -0.5, 4))
    }

    @Test
    fun `waits for three weeks of data`() {
        assertNull(GoalAdjuster.suggest(GoalType.LOSE, 1840, 0.0, 2))
    }

    @Test
    fun `never suggests below the safe minimum`() {
        assertNull(GoalAdjuster.suggest(GoalType.LOSE, GoalCalculator.MIN_CALORIES, 0.0, 4))
        assertEquals(GoalCalculator.MIN_CALORIES, GoalAdjuster.suggest(GoalType.LOSE, 1250, 0.0, 4)!!.newCalories)
    }

    @Test
    fun `gaining too slowly or too fast`() {
        assertEquals(120, GoalAdjuster.suggest(GoalType.GAIN, 2600, 0.0, 4)!!.change)
        assertEquals(-120, GoalAdjuster.suggest(GoalType.GAIN, 2600, 0.7, 4)!!.change)
    }

    @Test
    fun `maintaining but drifting`() {
        assertEquals(-100, GoalAdjuster.suggest(GoalType.MAINTAIN, 2300, 0.4, 4)!!.change)
        assertEquals(100, GoalAdjuster.suggest(GoalType.MAINTAIN, 2300, -0.4, 4)!!.change)
        assertNull(GoalAdjuster.suggest(GoalType.MAINTAIN, 2300, 0.1, 4))
    }

    @Test
    fun `adjustment is applied on top of the calculated goal`() {
        val p = UserProfile(32, Sex.MALE, 172.0, 78.0, ActivityLevel.SEDENTARY, GoalType.LOSE, calorieAdjustment = -120)
        assertEquals(1420, GoalCalculator.calculate(p).calories) // 1540 - 120
    }
}

class WeeklyInsightsTest {

    private val today = LocalDate.of(2026, 11, 18)
    private val goal = DailyGoal(2000, 120, 250, 67, 30)

    private fun item(daysAgo: Long, kcal: Double, protein: Double, fiber: Double = 25.0) = LoggedItem(
        0, today.minusDays(daysAgo), MealType.LUNCH, "x", 1.0, "g", 100.0,
        Nutrients(calories = kcal, protein = protein, fiber = fiber), FoodSource.LOCAL, false, 0,
    )

    @Test
    fun `summarises the last seven days`() {
        val week = (0L..6L).map { item(it, if (it == 3L) 2600.0 else 2000.0, if (it < 5) 80.0 else 130.0) }
        val previous = (7L..13L).map { item(it, 2000.0, 100.0, fiber = 20.0) }
        val water = (0L..6L).associate { today.minusDays(it) to if (it == 0L) 4 else 8 }
        val weights = listOf(WeightEntry(today.minusDays(7), 78.6), WeightEntry(today, 78.0))

        val s = WeeklyInsights.build(week + previous, today, goal, water, weights, streak = 12)

        assertEquals(today.minusDays(6), s.from)
        assertEquals(today, s.to)
        assertEquals(2085.7, s.averageCalories, 0.1)
        assertEquals(6, s.daysOnTarget)
        assertEquals(7, s.daysLogged)
        assertEquals(5, s.lowProteinDays)
        assertEquals(5.0, s.fiberChange!!, 0.001)
        assertEquals(6, s.waterGoalDays)
        assertEquals(-0.6, s.weightChange!!, 0.001)
        assertEquals(12, s.streak)
        assertTrue(s.proteinTips.isNotEmpty())
    }

    @Test
    fun `no protein tips when protein is fine`() {
        val week = (0L..6L).map { item(it, 2000.0, 125.0) }
        val s = WeeklyInsights.build(week, today, goal, emptyMap(), emptyList(), streak = 0)
        assertEquals(0, s.lowProteinDays)
        assertTrue(s.proteinTips.isEmpty())
        assertNull(s.fiberChange)
        assertNull(s.weightChange)
    }

    @Test
    fun `empty week`() {
        val s = WeeklyInsights.build(emptyList(), today, goal, emptyMap(), emptyList(), 0)
        assertEquals(0, s.daysLogged)
        assertEquals(0.0, s.averageCalories, 0.0)
        assertFalse(s.hasData)
    }
}

class RecipeCalculatorTest {

    @Test
    fun `per serving divides the whole pot`() {
        val ingredients = listOf(
            200.0 to Nutrients(calories = 343.0, protein = 22.0, ironMg = 5.0),
            26.0 to Nutrients(calories = 900.0, fat = 99.5),
            110.0 to Nutrients(calories = 40.0),
            120.0 to Nutrients(calories = 18.0),
        )
        val r = RecipeCalculator.calculate(ingredients, servings = 4)
        assertEquals(985.6, r.total.calories, 0.1)
        assertEquals(456.0, r.totalGrams, 0.0)
        assertEquals(114.0, r.gramsPerServing, 0.0)
        assertEquals(246.4, r.perServing.calories, 0.1)
        assertEquals(11.0, r.perServing.protein, 0.001)
        // per 100 g, so a "bowl" of 114 g gives back the per-serving numbers
        assertEquals(r.perServing.calories, r.per100g.forGrams(r.gramsPerServing).calories, 0.001)
    }

    @Test
    fun `servings below one count as one`() {
        val r = RecipeCalculator.calculate(listOf(100.0 to Nutrients(calories = 100.0)), servings = 0)
        assertEquals(100.0, r.perServing.calories, 0.0)
    }

    @Test
    fun `no ingredients is all zero`() {
        val r = RecipeCalculator.calculate(emptyList(), 4)
        assertEquals(Nutrients.ZERO, r.per100g)
    }
}

class PortionLearnerTest {

    @Test
    fun `a portion is used after two matching edits`() {
        assertFalse(PortionLearner.isLearned(null))
        assertFalse(PortionLearner.isLearned(com.example.mycalories.domain.model.PortionPreference("Dal", "bowl", 250.0, 1)))
        assertTrue(PortionLearner.isLearned(com.example.mycalories.domain.model.PortionPreference("Dal", "bowl", 250.0, 2)))
    }

    @Test
    fun `it says so only the moment it is learned`() {
        assertTrue(PortionLearner.justLearned(com.example.mycalories.domain.model.PortionPreference("Dal", "bowl", 250.0, 2)))
        assertFalse(PortionLearner.justLearned(com.example.mycalories.domain.model.PortionPreference("Dal", "bowl", 250.0, 3)))
    }

    @Test
    fun `grams are never learned for gram units`() {
        assertFalse(PortionLearner.worthLearning("g", 1.0, 150.0))
        assertFalse(PortionLearner.worthLearning("bowl", 0.0, 0.0))
        assertTrue(PortionLearner.worthLearning("bowl", 1.0, 250.0))
    }
}
