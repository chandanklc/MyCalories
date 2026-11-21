package com.example.mycalories.data.local

import com.example.mycalories.domain.model.Food
import com.example.mycalories.domain.model.FoodSource
import com.example.mycalories.domain.model.LoggedItem
import com.example.mycalories.domain.model.MealType
import com.example.mycalories.domain.model.Nutrients
import com.example.mycalories.domain.model.SavedMeal
import com.example.mycalories.domain.model.SavedMealItem
import java.time.LocalDate

fun nameKey(name: String): String = name.trim().lowercase().replace(Regex("\\s+"), " ")

fun Nutrients.toColumns() = NutrientColumns(
    calories, protein, carbs, fat, fiber, sugar, sodiumMg, ironMg, calciumMg, vitaminB12Ug, vitaminDUg, vitaminCMg, potassiumMg,
)
fun NutrientColumns.toDomain() = Nutrients(
    calories, protein, carbs, fat, fiber, sugar, sodiumMg, ironMg, calciumMg, vitaminB12Ug, vitaminDUg, vitaminCMg, potassiumMg,
)

private fun sourceOf(value: String) = FoodSource.entries.firstOrNull { it.name == value } ?: FoodSource.AI_ESTIMATE

fun FoodEntity.toDomain() = Food(id, name, per100g.toDomain(), defaultUnit, gramsPerUnit, sourceOf(source))
fun Food.toEntity() = FoodEntity(id, name.trim(), nameKey(name), per100g.toColumns(), defaultUnit, gramsPerUnit, source.name)

fun LogEntryEntity.toDomain() = LoggedItem(
    id = id, date = LocalDate.ofEpochDay(epochDay), meal = MealType.fromString(meal) ?: MealType.SNACK,
    name = name, quantity = quantity, unit = unit, grams = grams, per100g = per100g.toDomain(),
    source = sourceOf(source), needsReview = needsReview, loggedAtMillis = loggedAtMillis, note = note,
)

fun LoggedItem.toEntity() = LogEntryEntity(
    id = id, epochDay = date.toEpochDay(), meal = meal.name, name = name, quantity = quantity, unit = unit,
    grams = grams, per100g = per100g.toColumns(), source = source.name, needsReview = needsReview,
    loggedAtMillis = loggedAtMillis, note = note,
)

fun SavedMealWithItems.toDomain() = SavedMeal(
    id = meal.id, name = meal.name,
    items = items.sortedBy { it.position }.map {
        SavedMealItem(it.name, it.quantity, it.unit, it.grams, it.per100g.toDomain(), sourceOf(it.source))
    },
)

fun SavedMealItem.toEntity(position: Int) = SavedMealItemEntity(
    savedMealId = 0, position = position, name = name, quantity = quantity, unit = unit, grams = grams,
    per100g = per100g.toColumns(), source = source.name,
)

/** Units are matched ignoring case, spaces and a plural "s" (bowl = Bowls). */
fun unitKey(unit: String): String = com.example.mycalories.domain.PortionCalculator.normalize(unit)
