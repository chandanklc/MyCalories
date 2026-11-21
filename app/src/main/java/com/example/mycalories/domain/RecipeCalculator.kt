package com.example.mycalories.domain

import com.example.mycalories.domain.model.Nutrients
import com.example.mycalories.domain.model.sum

data class RecipeResult(
    val total: Nutrients,
    val totalGrams: Double,
    val servings: Int,
) {
    val gramsPerServing: Double get() = totalGrams / servings
    val perServing: Nutrients get() = total * (1.0 / servings)
    /** Per 100 g, so the recipe can be stored like any other food. */
    val per100g: Nutrients get() = if (totalGrams <= 0) Nutrients.ZERO else total * (100.0 / totalGrams)
}

object RecipeCalculator {
    /** @param ingredients grams used and nutrients per 100 g for each ingredient. */
    fun calculate(ingredients: List<Pair<Double, Nutrients>>, servings: Int): RecipeResult = RecipeResult(
        total = ingredients.map { (g, per100) -> per100.forGrams(g) }.sum(),
        totalGrams = ingredients.sumOf { it.first },
        servings = servings.coerceAtLeast(1),
    )
}
