package com.example.mycalories.domain

import com.example.mycalories.data.remote.OnlineNutritionSource
import com.example.mycalories.data.remote.ParsedFood
import com.example.mycalories.data.remote.ParsedMeal
import com.example.mycalories.domain.model.Food
import com.example.mycalories.domain.model.FoodSource
import com.example.mycalories.domain.model.LoggedItem
import com.example.mycalories.domain.model.MealType
import com.example.mycalories.domain.repository.FoodRepository
import com.example.mycalories.domain.repository.PortionRepository
import java.time.LocalDate

/**
 * Finds nutrients for each parsed food: the on-device library first, then USDA online for plain
 * foods, then the AI's own estimate. Anything new is saved so next time it is instant and offline.
 */
class FoodResolver(
    private val foods: FoodRepository,
    private val online: OnlineNutritionSource,
    private val portions: PortionRepository? = null,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {

    suspend fun resolve(parsed: ParsedMeal, date: LocalDate, meal: MealType): List<LoggedItem> =
        parsed.items.map { resolveOne(it, date, meal) }

    private suspend fun resolveOne(p: ParsedFood, date: LocalDate, meal: MealType): LoggedItem {
        val local = foods.findByName(p.name)
        val food = local ?: run {
            val fromUsda = p.searchTerm?.let { term -> runCatching { online.lookup(term) }.getOrNull() }
            foods.save(
                Food(
                    name = p.name,
                    per100g = fromUsda ?: p.estimatePer100g,
                    defaultUnit = p.unit,
                    gramsPerUnit = if (p.quantity > 0) p.grams / p.quantity else p.grams,
                    source = if (fromUsda != null) FoodSource.ONLINE_USDA else FoodSource.AI_ESTIMATE,
                ),
            )
        }

        val learned = portions?.find(p.name, p.unit)?.takeIf { PortionLearner.isLearned(it) }
        val sameUnit = local != null && PortionCalculator.normalize(local.defaultUnit) == PortionCalculator.normalize(p.unit)
        val grams = when {
            learned != null -> p.quantity * learned.gramsPerUnit
            sameUnit -> p.quantity * local!!.gramsPerUnit
            else -> p.grams
        }

        return LoggedItem(
            date = date,
            meal = meal,
            name = p.name,
            quantity = p.quantity,
            unit = p.unit,
            grams = grams,
            per100g = food.per100g,
            source = food.source,
            needsReview = p.amountGuessed,
            loggedAtMillis = nowMillis(),
        )
    }
}
