package com.example.mycalories.domain.model

import java.time.LocalDate

enum class MealType(val label: String) {
    BREAKFAST("Breakfast"), LUNCH("Lunch"), SNACK("Snack"), DINNER("Dinner");

    companion object {
        fun fromString(value: String?): MealType? =
            entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) }
    }
}

/** Where a food's numbers came from. Shown as a badge so the user knows how much to trust them. */
enum class FoodSource(val label: String) {
    LOCAL("On device"),
    USER_RECIPE("My recipe"),
    ONLINE_USDA("Online · USDA"),
    AI_ESTIMATE("Estimated"),
    PACKAGED("Package label"),
}

/** Nutrients for some amount of food. Used both "per 100 g" and for an eaten portion. */
data class Nutrients(
    val calories: Double = 0.0,
    val protein: Double = 0.0,
    val carbs: Double = 0.0,
    val fat: Double = 0.0,
    val fiber: Double = 0.0,
    val sugar: Double = 0.0,
    val sodiumMg: Double = 0.0,
    val ironMg: Double = 0.0,
    val calciumMg: Double = 0.0,
    val vitaminB12Ug: Double = 0.0,
    val vitaminDUg: Double = 0.0,
    val vitaminCMg: Double = 0.0,
    val potassiumMg: Double = 0.0,
) {
    operator fun plus(o: Nutrients) = Nutrients(
        calories + o.calories, protein + o.protein, carbs + o.carbs, fat + o.fat,
        fiber + o.fiber, sugar + o.sugar, sodiumMg + o.sodiumMg,
        ironMg + o.ironMg, calciumMg + o.calciumMg, vitaminB12Ug + o.vitaminB12Ug,
        vitaminDUg + o.vitaminDUg, vitaminCMg + o.vitaminCMg, potassiumMg + o.potassiumMg,
    )

    operator fun times(f: Double) = Nutrients(
        calories * f, protein * f, carbs * f, fat * f, fiber * f, sugar * f, sodiumMg * f,
        ironMg * f, calciumMg * f, vitaminB12Ug * f, vitaminDUg * f, vitaminCMg * f, potassiumMg * f,
    )

    /** Treat this as a per-100 g value and scale it to [grams]. */
    fun forGrams(grams: Double): Nutrients = if (grams == 0.0) ZERO else this * (grams / 100.0)

    /** Share of macro calories coming from protein, carbs and fat (4/4/9 kcal per gram). */
    fun macroCalorieShare(): MacroShare {
        val p = protein * 4
        val c = carbs * 4
        val f = fat * 9
        val total = p + c + f
        if (total <= 0.0) return MacroShare(0.0, 0.0, 0.0)
        return MacroShare(p / total, c / total, f / total)
    }

    companion object {
        val ZERO = Nutrients()
    }
}

data class MacroShare(val protein: Double, val carbs: Double, val fat: Double)

fun Iterable<Nutrients>.sum(): Nutrients = fold(Nutrients.ZERO) { acc, n -> acc + n }

/** A food in the local library. Nutrients are per 100 g; [gramsPerUnit] is the weight of one [defaultUnit]. */
data class Food(
    val id: Long = 0,
    val name: String,
    val per100g: Nutrients,
    val defaultUnit: String,
    val gramsPerUnit: Double,
    val source: FoodSource,
)

/** One food the user ate, in one meal on one day. */
data class LoggedItem(
    val id: Long = 0,
    val date: LocalDate,
    val meal: MealType,
    val name: String,
    val quantity: Double,
    val unit: String,
    val grams: Double,
    val per100g: Nutrients,
    val source: FoodSource,
    val needsReview: Boolean,
    val loggedAtMillis: Long,
    /** What the user said or typed, kept only if they allow it. */
    val note: String? = null,
) {
    val nutrients: Nutrients get() = per100g.forGrams(grams)
}

enum class Sex { MALE, FEMALE }

enum class ActivityLevel(val label: String, val multiplier: Double) {
    SEDENTARY("Desk job", 1.2),
    LIGHT("Lightly active", 1.375),
    MODERATE("Active", 1.55),
    VERY_ACTIVE("Very active", 1.725),
}

enum class GoalType(val label: String, val calorieDelta: Int) {
    LOSE("Lose weight", -500), MAINTAIN("Maintain", 0), GAIN("Gain muscle", 300),
}

data class UserProfile(
    val age: Int,
    val sex: Sex,
    val heightCm: Double,
    val weightKg: Double,
    val activity: ActivityLevel,
    val goal: GoalType,
    /** Extra calories added to the calculated goal after weight-trend suggestions (negative = fewer). */
    val calorieAdjustment: Int = 0,
    val targetWeightKg: Double? = null,
    /** When the goal was last adjusted or a suggestion declined; the next suggestion waits for 3 weeks of new weights. */
    val goalReviewedOn: LocalDate? = null,
)

data class DailyGoal(
    val calories: Int,
    val protein: Int,
    val carbs: Int,
    val fat: Int,
    val fiber: Int,
)

/** A saved group of foods the user can log again in one tap ("Usual lunch"). */
data class SavedMeal(
    val id: Long = 0,
    val name: String,
    val items: List<SavedMealItem>,
) {
    val total: Nutrients get() = items.map { it.per100g.forGrams(it.grams) }.sum()
}

data class SavedMealItem(
    val name: String,
    val quantity: Double,
    val unit: String,
    val grams: Double,
    val per100g: Nutrients,
    val source: FoodSource,
)

data class WeightEntry(val date: LocalDate, val kg: Double)

/** A portion size the user keeps choosing, e.g. their "bowl" of dal is 250 g. */
data class PortionPreference(val foodName: String, val unit: String, val gramsPerUnit: Double, val timesSeen: Int)
