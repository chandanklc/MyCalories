package com.example.mycalories.domain

import com.example.mycalories.domain.model.MealType
import java.time.LocalTime

/** Guesses which meal the user means from the time of day. */
object MealTimeSuggester {
    fun suggest(time: LocalTime): MealType = when (time.hour) {
        in 4..10 -> MealType.BREAKFAST
        in 11..15 -> MealType.LUNCH
        in 16..18 -> MealType.SNACK
        else -> MealType.DINNER
    }
}
