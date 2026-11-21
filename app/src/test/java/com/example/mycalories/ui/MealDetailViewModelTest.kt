package com.example.mycalories.ui

import com.example.mycalories.domain.model.FoodSource
import com.example.mycalories.domain.model.LoggedItem
import com.example.mycalories.domain.model.MealType
import com.example.mycalories.domain.model.Nutrients
import com.example.mycalories.fakes.FakeLogRepository
import com.example.mycalories.fakes.MainDispatcherRule
import com.example.mycalories.fakes.clockAt
import com.example.mycalories.ui.meal.MealDetailViewModel
import com.example.mycalories.ui.meal.MealMessage
import com.example.mycalories.fakes.FakePortionRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class MealDetailViewModelTest {

    @get:Rule val main = MainDispatcherRule()

    private val today = LocalDate.of(2026, 11, 18)
    private val log = FakeLogRepository()

    private fun item(name: String, meal: MealType = MealType.LUNCH, qty: Double = 1.0, grams: Double = 100.0) =
        LoggedItem(0, today, meal, name, qty, "piece", grams, Nutrients(calories = 100.0, protein = 10.0), FoodSource.LOCAL, false, 0)

    @Before
    fun seed() = runTest {
        log.add(listOf(item("Roti", qty = 2.0, grams = 80.0), item("Dal", grams = 200.0), item("Eggs", MealType.BREAKFAST)))
    }

    private val portions = FakePortionRepository()
    private fun vm() = MealDetailViewModel(log, today, MealType.LUNCH, clockAt(14), portions)

    @Test
    fun `shows only this meal's items and total`() {
        val s = vm().state.value
        assertEquals(listOf("Roti", "Dal"), s.items.map { it.name })
        assertEquals(280.0, s.total.calories, 0.001)
        assertEquals(MealType.LUNCH, s.meal)
    }

    @Test
    fun `delete removes the item`() = runTest {
        val vm = vm()
        vm.delete(vm.state.value.items.first { it.name == "Dal" }.id)
        assertEquals(listOf("Roti"), vm.state.value.items.map { it.name })
        assertEquals(MealMessage.Removed("Dal"), vm.state.value.message)
    }

    @Test
    fun `undo delete puts it back`() = runTest {
        val vm = vm()
        vm.delete(vm.state.value.items.first { it.name == "Dal" }.id)
        vm.undoDelete()
        assertEquals(setOf("Roti", "Dal"), vm.state.value.items.map { it.name }.toSet())
    }

    @Test
    fun `update rescales and saves the item`() = runTest {
        val vm = vm()
        val roti = vm.state.value.items.first { it.name == "Roti" }
        vm.update(roti, quantity = 3.0, unit = "piece")
        assertEquals(120.0, log.items.value.first { it.name == "Roti" }.grams, 0.001)
    }

    @Test
    fun `move sends an item to another meal`() = runTest {
        val vm = vm()
        val dal = vm.state.value.items.first { it.name == "Dal" }
        vm.move(dal.id, MealType.DINNER)
        assertEquals(listOf("Roti"), vm.state.value.items.map { it.name })
        assertEquals(MealType.DINNER, log.items.value.first { it.name == "Dal" }.meal)
    }

    @Test
    fun `move whole meal moves every item`() = runTest {
        val vm = vm()
        vm.moveAll(MealType.DINNER)
        assertTrue(vm.state.value.items.isEmpty())
        assertEquals(2, log.items.value.count { it.meal == MealType.DINNER })
    }

    @Test
    fun `repeat tomorrow copies the meal to the next day`() = runTest {
        val vm = vm()
        vm.repeatTomorrow()
        val tomorrow = log.items.value.filter { it.date == today.plusDays(1) }
        assertEquals(listOf("Roti", "Dal"), tomorrow.map { it.name })
        assertTrue(tomorrow.all { it.meal == MealType.LUNCH })
        assertEquals(MealMessage.CopiedTomorrow, vm.state.value.message)
        vm.messageShown()
        assertEquals(null, vm.state.value.message)
    }

    @Test
    fun `editing a portion twice the same way teaches it`() = runTest {
        val vm = vm()
        val roti = vm.state.value.items.first { it.name == "Roti" }
        vm.update(roti, 2.0, "piece")
        vm.update(vm.state.value.items.first { it.name == "Roti" }, 2.0, "piece")
        assertEquals(MealMessage.LearnedPortion("Roti", "piece", 40.0), vm.state.value.message)
    }
}
