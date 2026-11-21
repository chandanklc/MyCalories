package com.example.mycalories.domain

import com.example.mycalories.domain.model.DailyGoal
import com.example.mycalories.domain.model.GoalType
import com.example.mycalories.domain.model.Sex
import com.example.mycalories.domain.model.UserProfile
import kotlin.math.max
import kotlin.math.roundToInt

/** Works out a daily calorie and macro target from body stats (Mifflin-St Jeor). */
object GoalCalculator {

    const val MIN_CALORIES = 1200

    fun bmr(p: UserProfile): Double {
        val base = 10 * p.weightKg + 6.25 * p.heightCm - 5 * p.age
        return if (p.sex == Sex.MALE) base + 5 else base - 161
    }

    fun calculate(p: UserProfile): DailyGoal {
        val maintenance = bmr(p) * p.activity.multiplier
        val calories = max(MIN_CALORIES, (maintenance + p.goal.calorieDelta).roundToInt() + p.calorieAdjustment)

        val proteinPerKg = if (p.goal == GoalType.MAINTAIN) 1.2 else 1.6
        val protein = (p.weightKg * proteinPerKg).roundToInt()
        val fat = (calories * 0.30 / 9).roundToInt()
        val carbs = max(0, ((calories - protein * 4 - fat * 9) / 4.0).roundToInt())
        val fiber = (calories / 1000.0 * 14).roundToInt()

        return DailyGoal(calories = calories, protein = protein, carbs = carbs, fat = fat, fiber = fiber)
    }
}
