package com.example.mycalories.domain

import com.example.mycalories.domain.model.LoggedItem

/** Converts kitchen units to grams and re-scales a logged item when its amount changes. */
object PortionCalculator {

    /** Units the user can pick on the edit screen. */
    val pickerUnits = listOf("piece", "bowl", "katori", "cup", "tbsp", "g")

    private val standard = mapOf(
        "g" to 1.0, "gram" to 1.0, "kg" to 1000.0, "ml" to 1.0, "l" to 1000.0, "litre" to 1000.0,
        "cup" to 240.0, "bowl" to 200.0, "katori" to 150.0, "glass" to 250.0,
        "tbsp" to 15.0, "tablespoon" to 15.0, "tsp" to 5.0, "teaspoon" to 5.0,
    )

    fun normalize(unit: String): String {
        val u = unit.trim().lowercase()
        return if (u.length > 2 && u.endsWith("s") && standard.containsKey(u.dropLast(1))) u.dropLast(1) else u
    }

    /** Grams in one [unit]. Units with no standard weight (piece, slice, plate…) use [fallback]. */
    fun gramsPerUnit(unit: String, fallback: Double): Double = standard[normalize(unit)] ?: fallback

    /** The item re-scaled to a new amount. The item's own grams-per-unit is kept for its own unit. */
    fun withPortion(item: LoggedItem, quantity: Double, unit: String): LoggedItem {
        val q = quantity.coerceAtLeast(0.0)
        val ownGramsPerUnit = if (item.quantity > 0) item.grams / item.quantity else item.grams
        val perUnit = if (normalize(unit) == normalize(item.unit)) ownGramsPerUnit
        else gramsPerUnit(unit, fallback = ownGramsPerUnit)
        return item.copy(quantity = q, unit = unit, grams = q * perUnit, needsReview = false)
    }
}
