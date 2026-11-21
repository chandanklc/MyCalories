package com.example.mycalories.domain

import com.example.mycalories.domain.model.MealType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

class MealTimeSuggesterTest {

    private fun at(h: Int, m: Int = 0) = MealTimeSuggester.suggest(LocalTime.of(h, m))

    @Test fun `early morning is breakfast`() = assertEquals(MealType.BREAKFAST, at(4))
    @Test fun `ten fifty nine is still breakfast`() = assertEquals(MealType.BREAKFAST, at(10, 59))
    @Test fun `eleven is lunch`() = assertEquals(MealType.LUNCH, at(11))
    @Test fun `one twenty pm is lunch`() = assertEquals(MealType.LUNCH, at(13, 20))
    @Test fun `four pm is snack`() = assertEquals(MealType.SNACK, at(16))
    @Test fun `six fifty nine pm is snack`() = assertEquals(MealType.SNACK, at(18, 59))
    @Test fun `seven pm is dinner`() = assertEquals(MealType.DINNER, at(19))
    @Test fun `midnight is dinner`() = assertEquals(MealType.DINNER, at(0))
    @Test fun `three fifty nine am is dinner`() = assertEquals(MealType.DINNER, at(3, 59))
}
