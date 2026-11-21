package com.example.mycalories.domain

import com.example.mycalories.domain.model.PortionPreference

/** Learns the user's own portion sizes from repeated edits ("my bowl is 250 g"). */
object PortionLearner {
    const val LEARN_AFTER = 2

    fun isLearned(pref: PortionPreference?): Boolean = pref != null && pref.timesSeen >= LEARN_AFTER

    /** True only on the edit that makes it learned, so the app says so once. */
    fun justLearned(pref: PortionPreference): Boolean = pref.timesSeen == LEARN_AFTER

    /** Gram and kg amounts are exact already; only household units are worth learning. */
    fun worthLearning(unit: String, quantity: Double, grams: Double): Boolean {
        val u = PortionCalculator.normalize(unit)
        return quantity > 0 && grams > 0 && u !in setOf("g", "gram", "kg", "ml", "l", "litre")
    }
}
