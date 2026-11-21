package com.example.mycalories.ui

import com.example.mycalories.domain.model.FoodSource
import com.example.mycalories.domain.model.LoggedItem
import com.example.mycalories.domain.model.MealType
import com.example.mycalories.domain.model.Nutrients
import com.example.mycalories.fakes.FakeLogRepository
import com.example.mycalories.fakes.FakeProfileRepository
import com.example.mycalories.fakes.MainDispatcherRule
import com.example.mycalories.fakes.clockAt
import com.example.mycalories.ui.history.HistoryViewModel
import com.example.mycalories.ui.today.TodayViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class HistoryViewModelTest {

    @get:Rule val main = MainDispatcherRule()

    private val today = LocalDate.of(2026, 11, 18)
    private val log = FakeLogRepository()

    private fun item(daysAgo: Long, kcal: Double, protein: Double = 0.0, fiber: Double = 0.0) = LoggedItem(
        0, today.minusDays(daysAgo), MealType.LUNCH, "x", 1.0, "g", 100.0,
        Nutrients(calories = kcal, protein = protein, fiber = fiber), FoodSource.LOCAL, false, 0,
    )

    private fun vm() = HistoryViewModel(log, FakeProfileRepository(), clockAt(12))

    @Test
    fun `shows seven days with totals`() = runTest {
        log.add(listOf(item(0, 500.0), item(0, 600.0), item(2, 1800.0)))
        val s = vm().state.value
        assertEquals(7, s.days.size)
        assertEquals(1100.0, s.days.last().calories, 0.0)
        assertEquals(1800.0, s.days[4].calories, 0.0)
        assertEquals(TodayViewModel.DEFAULT_GOAL.calories, s.goalCalories)
    }

    @Test
    fun `average and streak`() = runTest {
        log.add(listOf(item(0, 400.0), item(1, 2000.0), item(2, 1800.0), item(5, 2200.0)))
        val s = vm().state.value
        assertEquals(2000.0, s.averageCalories, 0.001) // (2000 + 1800 + 2200) / 3
        assertEquals(3, s.streak)
    }

    @Test
    fun `average protein and fiber per completed day`() = runTest {
        log.add(listOf(item(1, 2000.0, protein = 80.0, fiber = 20.0), item(2, 2000.0, protein = 100.0, fiber = 30.0), item(0, 10.0, protein = 999.0)))
        val s = vm().state.value
        assertEquals(90.0, s.averageProtein, 0.001)
        assertEquals(25.0, s.averageFiber, 0.001)
    }

    @Test
    fun `empty history is all zeros`() = runTest {
        val s = vm().state.value
        assertEquals(0.0, s.averageCalories, 0.0)
        assertEquals(0, s.streak)
    }
}
