package com.example.mycalories.domain

import com.example.mycalories.domain.model.ActivityLevel
import com.example.mycalories.domain.model.Food
import com.example.mycalories.domain.model.FoodSource
import com.example.mycalories.domain.model.GoalType
import com.example.mycalories.domain.model.LoggedItem
import com.example.mycalories.domain.model.MealType
import com.example.mycalories.domain.model.Nutrients
import com.example.mycalories.domain.model.SavedMeal
import com.example.mycalories.domain.model.SavedMealItem
import com.example.mycalories.domain.model.Sex
import com.example.mycalories.domain.model.UserProfile
import com.example.mycalories.domain.model.WeightEntry
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate

/** Everything the user has, for "Download my data" and restoring on a new phone. */
data class Backup(
    val profile: UserProfile?,
    val foods: List<Food>,
    val log: List<LoggedItem>,
    val savedMeals: List<SavedMeal>,
    val weights: List<WeightEntry>,
    val water: Map<LocalDate, Int>,
)

object BackupSerializer {
    private const val VERSION = 1
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    @Serializable private data class N(
        val kcal: Double, val p: Double, val c: Double, val f: Double, val fib: Double, val sug: Double, val na: Double,
        val fe: Double = 0.0, val ca: Double = 0.0, val b12: Double = 0.0, val vd: Double = 0.0, val vc: Double = 0.0, val k: Double = 0.0,
    )
    @Serializable private data class ProfileDto(val age: Int, val sex: String, val heightCm: Double, val weightKg: Double,
        val activity: String, val goal: String, val calorieAdjustment: Int = 0, val targetWeightKg: Double? = null, val goalReviewedOn: String? = null)
    @Serializable private data class FoodDto(val name: String, val n: N, val unit: String, val gramsPerUnit: Double, val source: String)
    @Serializable private data class LogDto(val date: String, val meal: String, val name: String, val quantity: Double, val unit: String,
        val grams: Double, val n: N, val source: String, val needsReview: Boolean, val loggedAt: Long, val note: String? = null)
    @Serializable private data class SavedItemDto(val name: String, val quantity: Double, val unit: String, val grams: Double, val n: N, val source: String)
    @Serializable private data class SavedMealDto(val name: String, val items: List<SavedItemDto>)
    @Serializable private data class WeightDto(val date: String, val kg: Double)
    @Serializable private data class File(
        val app: String = "MyCalories", val version: Int? = null, val profile: ProfileDto? = null,
        val foods: List<FoodDto> = emptyList(), val log: List<LogDto> = emptyList(),
        val savedMeals: List<SavedMealDto> = emptyList(), val weights: List<WeightDto> = emptyList(),
        val water: Map<String, Int> = emptyMap(),
    )

    private fun Nutrients.dto() = N(calories, protein, carbs, fat, fiber, sugar, sodiumMg, ironMg, calciumMg, vitaminB12Ug, vitaminDUg, vitaminCMg, potassiumMg)
    private fun N.domain() = Nutrients(kcal, p, c, f, fib, sug, na, fe, ca, b12, vd, vc, k)
    private fun src(s: String) = FoodSource.entries.firstOrNull { it.name == s } ?: FoodSource.AI_ESTIMATE

    fun encode(b: Backup): String = json.encodeToString(
        File.serializer(),
        File(
            version = VERSION,
            profile = b.profile?.let { ProfileDto(it.age, it.sex.name, it.heightCm, it.weightKg, it.activity.name, it.goal.name, it.calorieAdjustment, it.targetWeightKg, it.goalReviewedOn?.toString()) },
            foods = b.foods.map { FoodDto(it.name, it.per100g.dto(), it.defaultUnit, it.gramsPerUnit, it.source.name) },
            log = b.log.map { LogDto(it.date.toString(), it.meal.name, it.name, it.quantity, it.unit, it.grams, it.per100g.dto(), it.source.name, it.needsReview, it.loggedAtMillis, it.note) },
            savedMeals = b.savedMeals.map { m -> SavedMealDto(m.name, m.items.map { SavedItemDto(it.name, it.quantity, it.unit, it.grams, it.per100g.dto(), it.source.name) }) },
            weights = b.weights.map { WeightDto(it.date.toString(), it.kg) },
            water = b.water.mapKeys { it.key.toString() },
        ),
    )

    /** @throws IllegalArgumentException if the text isn't a MyCalories backup. */
    fun decode(text: String): Backup {
        val f = try { json.decodeFromString(File.serializer(), text) } catch (e: Exception) {
            throw IllegalArgumentException("This isn't a MyCalories backup file.", e)
        }
        require(f.version != null && f.version <= VERSION) { "This isn't a MyCalories backup file." }
        return Backup(
            profile = f.profile?.let {
                UserProfile(it.age, Sex.valueOf(it.sex), it.heightCm, it.weightKg, ActivityLevel.valueOf(it.activity), GoalType.valueOf(it.goal), it.calorieAdjustment, it.targetWeightKg, it.goalReviewedOn?.let(LocalDate::parse))
            },
            foods = f.foods.map { Food(0, it.name, it.n.domain(), it.unit, it.gramsPerUnit, src(it.source)) },
            log = f.log.map {
                LoggedItem(0, LocalDate.parse(it.date), MealType.fromString(it.meal) ?: MealType.SNACK, it.name, it.quantity, it.unit,
                    it.grams, it.n.domain(), src(it.source), it.needsReview, it.loggedAt, it.note)
            },
            savedMeals = f.savedMeals.map { m -> SavedMeal(0, m.name, m.items.map { SavedMealItem(it.name, it.quantity, it.unit, it.grams, it.n.domain(), src(it.source)) }) },
            weights = f.weights.map { WeightEntry(LocalDate.parse(it.date), it.kg) },
            water = f.water.mapKeys { LocalDate.parse(it.key) },
        )
    }
}
