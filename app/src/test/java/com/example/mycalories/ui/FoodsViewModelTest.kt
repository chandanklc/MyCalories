package com.example.mycalories.ui

import com.example.mycalories.domain.model.Food
import com.example.mycalories.domain.model.FoodSource
import com.example.mycalories.domain.model.MealType
import com.example.mycalories.domain.model.Nutrients
import com.example.mycalories.domain.model.SavedMeal
import com.example.mycalories.domain.model.SavedMealItem
import com.example.mycalories.fakes.FakeFoodRepository
import com.example.mycalories.fakes.FakeLogRepository
import com.example.mycalories.fakes.FakeSavedMealRepository
import com.example.mycalories.fakes.MainDispatcherRule
import com.example.mycalories.fakes.clockAt
import com.example.mycalories.ui.foods.FoodsMessage
import com.example.mycalories.ui.foods.FoodsViewModel
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class FoodsViewModelTest {

    @get:Rule val main = MainDispatcherRule()

    private val today = LocalDate.of(2026, 11, 18)
    private val per100 = Nutrients(calories = 100.0)
    private val foods = FakeFoodRepository(listOf(
        Food(1, "Paneer bhurji", per100, "plate", 150.0, FoodSource.LOCAL),
        Food(2, "Roti", per100, "piece", 40.0, FoodSource.LOCAL),
        Food(3, "Mom's dal", per100, "bowl", 200.0, FoodSource.USER_RECIPE),
    ))
    private val saved = FakeSavedMealRepository()
    private val log = FakeLogRepository()

    private fun vm() = FoodsViewModel(foods, saved, log, clockAt(13))

    @Test
    fun `lists all foods and filters by search`() = runTest {
        val vm = vm()
        assertEquals(3, vm.state.value.foods.size)
        vm.onQuery("ROTI")
        advanceUntilIdle()
        assertEquals(listOf("Roti"), vm.state.value.foods.map { it.name })
    }

    @Test
    fun `shows saved meals`() = runTest {
        saved.save(SavedMeal(name = "Usual lunch", items = emptyList()))
        assertEquals(listOf("Usual lunch"), vm().state.value.savedMeals.map { it.name })
    }

    @Test
    fun `logging a saved meal adds its items to today in the suggested meal`() = runTest {
        val items = listOf(
            SavedMealItem("Roti", 2.0, "piece", 80.0, per100, FoodSource.LOCAL),
            SavedMealItem("Dal", 1.0, "bowl", 200.0, per100, FoodSource.LOCAL),
        )
        saved.save(SavedMeal(name = "Usual lunch", items = items))
        val vm = vm()
        vm.logSavedMeal(vm.state.value.savedMeals.single())
        val logged = log.items.value
        assertEquals(listOf("Roti", "Dal"), logged.map { it.name })
        assertTrue(logged.all { it.date == today && it.meal == MealType.LUNCH })
        assertEquals(280.0, logged.sumOf { it.nutrients.calories }, 0.001)
        assertEquals(FoodsMessage.Added("Usual lunch", MealType.LUNCH), vm.state.value.message)
    }

    @Test
    fun `logging a single food uses one default unit`() = runTest {
        val vm = vm()
        vm.logFood(vm.state.value.foods.first { it.name == "Roti" }, MealType.SNACK)
        val roti = log.items.value.single()
        assertEquals(MealType.SNACK, roti.meal)
        assertEquals(40.0, roti.grams, 0.0)
        assertEquals("piece", roti.unit)
    }

    @Test
    fun `deleting a saved meal`() = runTest {
        saved.save(SavedMeal(name = "x", items = emptyList()))
        val vm = vm()
        vm.deleteSavedMeal(vm.state.value.savedMeals.single().id)
        assertTrue(vm.state.value.savedMeals.isEmpty())
    }
}
