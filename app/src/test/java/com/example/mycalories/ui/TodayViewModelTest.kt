package com.example.mycalories.ui

import com.example.mycalories.domain.GoalCalculator
import com.example.mycalories.domain.model.ActivityLevel
import com.example.mycalories.domain.model.FoodSource
import com.example.mycalories.domain.model.GoalType
import com.example.mycalories.domain.model.LoggedItem
import com.example.mycalories.domain.model.MealType
import com.example.mycalories.domain.model.Nutrients
import com.example.mycalories.domain.model.Sex
import com.example.mycalories.domain.model.UserProfile
import com.example.mycalories.fakes.FakeHealth
import com.example.mycalories.fakes.FakeLogRepository
import com.example.mycalories.fakes.FakeSettingsRepository
import com.example.mycalories.domain.repository.DayActivity
import com.example.mycalories.ui.today.TodayMessage
import com.example.mycalories.fakes.FakeProfileRepository
import com.example.mycalories.fakes.MainDispatcherRule
import com.example.mycalories.fakes.clockAt
import com.example.mycalories.ui.today.TodayViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class TodayViewModelTest {

    @get:Rule val main = MainDispatcherRule()

    private val today = LocalDate.of(2026, 11, 18)
    private val profile = UserProfile(32, Sex.MALE, 172.0, 78.0, ActivityLevel.LIGHT, GoalType.LOSE)
    private val log = FakeLogRepository()
    private val profiles = FakeProfileRepository(profile)

    private val health = FakeHealth()
    private val settings = FakeSettingsRepository()
    private fun vm() = TodayViewModel(log, profiles, clockAt(13, 20), health, settings)

    private fun item(meal: MealType, kcal: Double, date: LocalDate = today) =
        LoggedItem(0, date, meal, "x", 1.0, "g", 100.0, Nutrients(calories = kcal), FoodSource.LOCAL, false, 0)

    @Test
    fun `starts on today with the goal from the profile`() = runTest {
        val state = vm().state.value
        assertEquals(today, state.date)
        assertTrue(state.isToday)
        assertEquals(GoalCalculator.calculate(profile), state.summary.goal)
        assertFalse(state.needsProfile)
    }

    @Test
    fun `asks for a profile when there is none`() = runTest {
        profiles.profileFlow.value = null
        val state = vm().state.value
        assertTrue(state.needsProfile)
        assertEquals(TodayViewModel.DEFAULT_GOAL, state.summary.goal)
    }

    @Test
    fun `shows today's meals and updates live`() = runTest {
        log.add(listOf(item(MealType.BREAKFAST, 384.0), item(MealType.LUNCH, 660.0, today.minusDays(1))))
        val vm = vm()
        assertEquals(384.0, vm.state.value.summary.eaten.calories, 0.0)

        log.add(listOf(item(MealType.SNACK, 105.0)))
        assertEquals(489.0, vm.state.value.summary.eaten.calories, 0.0)
    }

    @Test
    fun `can go back a day and forward again but not into the future`() = runTest {
        log.add(listOf(item(MealType.LUNCH, 660.0, today.minusDays(1))))
        val vm = vm()
        vm.previousDay()
        assertEquals(today.minusDays(1), vm.state.value.date)
        assertFalse(vm.state.value.isToday)
        assertEquals(660.0, vm.state.value.summary.eaten.calories, 0.0)

        vm.nextDay()
        vm.nextDay()
        assertEquals(today, vm.state.value.date)
    }

    @Test
    fun `go to today jumps back`() = runTest {
        val vm = vm()
        repeat(3) { vm.previousDay() }
        vm.goToToday()
        assertEquals(today, vm.state.value.date)
    }

    @Test
    fun `water goes up and down but not below zero`() = runTest {
        val vm = vm()
        vm.addWater()
        vm.addWater()
        assertEquals(2, vm.state.value.waterGlasses)
        vm.removeWater()
        vm.removeWater()
        vm.removeWater()
        assertEquals(0, vm.state.value.waterGlasses)
    }

    @Test
    fun `burned calories from health connect add to what is left`() = runTest {
        settings.flow.value = settings.flow.value.copy(healthConnect = true)
        health.activity = mapOf(today to DayActivity(activeCalories = 320.0, steps = 6450))
        log.add(listOf(item(MealType.LUNCH, 1149.0)))
        val s = vm().state.value
        assertEquals(320, s.burnedCalories)
        assertEquals(6450L, s.steps)
        assertEquals(GoalCalculator.calculate(profile).calories - 1149 + 320, s.remainingCalories)
    }

    @Test
    fun `health connect off means nothing burned`() = runTest {
        health.activity = mapOf(today to DayActivity(320.0, 6450))
        assertEquals(0, vm().state.value.burnedCalories)
    }

    @Test
    fun `yesterday's meals are offered and can be copied`() = runTest {
        val yesterday = today.minusDays(1)
        log.add(listOf(item(MealType.BREAKFAST, 384.0, yesterday), item(MealType.DINNER, 610.0, yesterday)))
        val vm = vm()
        assertEquals(mapOf(MealType.BREAKFAST to 384.0, MealType.DINNER to 610.0), vm.state.value.yesterdayMeals)

        vm.copyFromYesterday(MealType.BREAKFAST)
        val todayItems = log.items.value.filter { it.date == today }
        assertEquals(1, todayItems.size)
        assertEquals(MealType.BREAKFAST, todayItems.single().meal)
        assertEquals(TodayMessage.CopiedMeal(MealType.BREAKFAST), vm.state.value.message)
    }

    @Test
    fun `copy whole day`() = runTest {
        val yesterday = today.minusDays(1)
        log.add(listOf(item(MealType.BREAKFAST, 384.0, yesterday), item(MealType.DINNER, 610.0, yesterday)))
        val vm = vm()
        vm.copyFromYesterday(null)
        assertEquals(2, log.items.value.count { it.date == today })
        assertEquals(TodayMessage.CopiedDay, vm.state.value.message)
        vm.messageShown()
        assertEquals(null, vm.state.value.message)
    }
}
