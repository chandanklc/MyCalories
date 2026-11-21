package com.example.mycalories.domain

import com.example.mycalories.domain.model.ActivityLevel
import com.example.mycalories.domain.model.GoalType
import com.example.mycalories.domain.model.Sex
import com.example.mycalories.domain.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalCalculatorTest {

    private val male = UserProfile(age = 32, sex = Sex.MALE, heightCm = 172.0, weightKg = 78.0,
        activity = ActivityLevel.SEDENTARY, goal = GoalType.MAINTAIN)

    @Test
    fun `bmr uses Mifflin St Jeor for men`() {
        // 10*78 + 6.25*172 - 5*32 + 5 = 1700
        assertEquals(1700.0, GoalCalculator.bmr(male), 0.01)
    }

    @Test
    fun `bmr uses Mifflin St Jeor for women`() {
        val female = male.copy(sex = Sex.FEMALE)
        // 1700 - 5 - 161 = 1534
        assertEquals(1534.0, GoalCalculator.bmr(female), 0.01)
    }

    @Test
    fun `maintenance calories multiply bmr by activity`() {
        assertEquals(2040, GoalCalculator.calculate(male).calories)               // 1700 * 1.2
        assertEquals(2338, GoalCalculator.calculate(male.copy(activity = ActivityLevel.LIGHT)).calories) // 2337.5
        assertEquals(2635, GoalCalculator.calculate(male.copy(activity = ActivityLevel.MODERATE)).calories)
        assertEquals(2933, GoalCalculator.calculate(male.copy(activity = ActivityLevel.VERY_ACTIVE)).calories)
    }

    @Test
    fun `lose weight removes 500 and gain adds 300`() {
        assertEquals(1540, GoalCalculator.calculate(male.copy(goal = GoalType.LOSE)).calories)
        assertEquals(2340, GoalCalculator.calculate(male.copy(goal = GoalType.GAIN)).calories)
    }

    @Test
    fun `calories never go below the safe minimum`() {
        val small = UserProfile(age = 70, sex = Sex.FEMALE, heightCm = 145.0, weightKg = 40.0,
            activity = ActivityLevel.SEDENTARY, goal = GoalType.LOSE)
        assertEquals(GoalCalculator.MIN_CALORIES, GoalCalculator.calculate(small).calories)
    }

    @Test
    fun `protein is 1_2 g per kg to maintain and 1_6 g per kg otherwise`() {
        assertEquals(94, GoalCalculator.calculate(male).protein)        // 93.6
        assertEquals(125, GoalCalculator.calculate(male.copy(goal = GoalType.LOSE)).protein) // 124.8
    }

    @Test
    fun `fat is 30 percent and carbs fill the rest`() {
        val goal = GoalCalculator.calculate(male) // 2040 kcal, 94 g protein
        assertEquals(68, goal.fat)    // 2040*0.3/9 = 68
        // (2040 - 94*4 - 68*9) / 4 = 263
        assertEquals(263, goal.carbs)
    }

    @Test
    fun `fiber is 14 g per 1000 kcal`() {
        assertEquals(29, GoalCalculator.calculate(male).fiber) // 28.56
    }

    @Test
    fun `carbs are never negative`() {
        val heavyLoser = male.copy(weightKg = 160.0, heightCm = 150.0, age = 80, sex = Sex.FEMALE, goal = GoalType.LOSE)
        assertTrue(GoalCalculator.calculate(heavyLoser).carbs >= 0)
    }
}
