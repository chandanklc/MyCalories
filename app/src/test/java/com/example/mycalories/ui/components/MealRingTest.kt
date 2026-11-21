package com.example.mycalories.ui.components

import com.example.mycalories.domain.model.MealType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MealRingTest {

    @Test
    fun `nothing eaten gives no coloured parts`() {
        assertTrue(ringSegments(emptyMap(), 2000.0).isEmpty())
    }

    @Test
    fun `each meal takes its share of the day's budget, in meal order`() {
        val parts = ringSegments(mapOf(MealType.DINNER to 500.0, MealType.BREAKFAST to 400.0, MealType.LUNCH to 600.0), 2000.0)
        assertEquals(listOf(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER), parts.map { it.meal })
        assertEquals(0f, parts[0].start, 1e-4f)
        assertEquals(0.2f, parts[0].sweep, 1e-4f)
        assertEquals(0.2f, parts[1].start, 1e-4f)
        assertEquals(0.3f, parts[1].sweep, 1e-4f)
        assertEquals(0.5f, parts[2].start, 1e-4f)
        assertEquals(0.25f, parts[2].sweep, 1e-4f)
    }

    @Test
    fun `meals with no calories are left out`() {
        val parts = ringSegments(mapOf(MealType.SNACK to 0.0, MealType.LUNCH to 500.0), 2000.0)
        assertEquals(listOf(MealType.LUNCH), parts.map { it.meal })
    }

    @Test
    fun `over the budget the parts fill the whole ring in proportion`() {
        val parts = ringSegments(mapOf(MealType.LUNCH to 1500.0, MealType.DINNER to 1500.0), 2000.0)
        assertEquals(0.5f, parts[0].sweep, 1e-4f)
        assertEquals(0.5f, parts[1].start, 1e-4f)
        assertEquals(1f, parts.sumOf { it.sweep.toDouble() }.toFloat(), 1e-4f)
    }

    @Test
    fun `no budget set still shows each meal's share`() {
        val parts = ringSegments(mapOf(MealType.LUNCH to 300.0, MealType.SNACK to 100.0), 0.0)
        assertEquals(0.75f, parts[0].sweep, 1e-4f)
        assertEquals(0.25f, parts[1].sweep, 1e-4f)
    }

    @Test
    fun `every meal has its own colour`() {
        val colors = MealType.entries.map { MealColors.of(it) }
        assertEquals(4, colors.toSet().size)
    }
}
