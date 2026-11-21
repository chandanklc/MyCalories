package com.example.mycalories.ui

import com.example.mycalories.data.remote.AppError
import com.example.mycalories.data.remote.ImageInput
import com.example.mycalories.data.remote.PackagedFood
import com.example.mycalories.data.remote.ParsedFood
import com.example.mycalories.data.remote.ParsedMeal
import com.example.mycalories.domain.FoodResolver
import com.example.mycalories.domain.model.Food
import com.example.mycalories.domain.model.FoodSource
import com.example.mycalories.domain.model.MealType
import com.example.mycalories.domain.model.Nutrients
import com.example.mycalories.fakes.FakeFoodParser
import com.example.mycalories.fakes.FakeFoodRepository
import com.example.mycalories.fakes.FakeLogRepository
import com.example.mycalories.fakes.FakeOnlineSource
import com.example.mycalories.fakes.FakePackagedFoods
import com.example.mycalories.fakes.FakePortionRepository
import com.example.mycalories.fakes.FakeSavedMealRepository
import com.example.mycalories.fakes.FakeSettingsRepository
import com.example.mycalories.fakes.MainDispatcherRule
import com.example.mycalories.fakes.clockAt
import com.example.mycalories.ui.logmeal.FoodSuggestion
import com.example.mycalories.ui.logmeal.LogMealViewModel
import com.example.mycalories.ui.logmeal.Stage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class LogMealViewModelTest {

    @get:Rule val main = MainDispatcherRule()

    private val today = LocalDate.of(2026, 11, 18)
    private val roti = Food(1, "Roti", Nutrients(calories = 300.0, protein = 7.5), "piece", 40.0, FoodSource.LOCAL)
    private val paratha = Food(2, "Paneer paratha", Nutrients(calories = 290.0), "piece", 90.0, FoodSource.LOCAL)
    private val tikka = Food(3, "Paneer tikka", Nutrients(calories = 220.0), "plate", 150.0, FoodSource.LOCAL)

    private val parser = FakeFoodParser()
    private val foods = FakeFoodRepository(listOf(roti, paratha, tikka))
    private val log = FakeLogRepository()
    private val saved = FakeSavedMealRepository()
    private val settings = FakeSettingsRepository()
    private val portions = FakePortionRepository()
    private val crackers = PackagedFood("Wheat crackers", "Acme", Nutrients(calories = 450.0, protein = 9.0), servingGrams = 30.0)

    private fun vm(hour: Int = 13, meal: MealType? = null) = LogMealViewModel(
        parser = parser, resolver = FoodResolver(foods, FakeOnlineSource(), portions), foods = foods,
        log = log, savedMeals = saved, clock = clockAt(hour, 20), date = today,
        packaged = FakePackagedFoods(mapOf("890123" to crackers)), settings = settings, portions = portions,
        initialMeal = meal,
    )

    private fun food(name: String, qty: Double = 1.0, unit: String = "piece", grams: Double = 100.0, guessed: Boolean = false) =
        ParsedFood(name, qty, unit, grams, null, Nutrients(calories = 100.0), guessed)

    // ---- starting ----

    @Test
    fun `starts on search with the meal picked by the clock`() {
        assertEquals(Stage.Search, vm(hour = 8).state.value.stage)
        assertEquals(MealType.BREAKFAST, vm(hour = 8).state.value.meal)
        assertEquals(MealType.DINNER, vm(hour = 20).state.value.meal)
    }

    @Test
    fun `a meal passed in wins over the clock`() {
        assertEquals(MealType.SNACK, vm(hour = 8, meal = MealType.SNACK).state.value.meal)
    }

    @Test
    fun `before typing it shows your foods`() {
        val names = vm().state.value.suggestions.map { it.food.name }
        assertTrue(names.containsAll(listOf("Roti", "Paneer paratha", "Paneer tikka")))
    }

    // ---- searching ----

    @Test
    fun `typing shows matching foods with the typed amount`() {
        val vm = vm()
        vm.setQuery("2 pan")
        val s = vm.state.value
        assertEquals("2 pan", s.query)
        assertEquals(listOf("Paneer paratha", "Paneer tikka"), s.suggestions.map { it.food.name })
        assertTrue(s.suggestions.all { it.quantity == 2.0 })
        assertEquals(522.0, s.suggestions[0].calories, 0.001) // 2 × 90 g × 290 kcal/100 g
    }

    @Test
    fun `adding a suggestion puts it in the meal and clears the search`() {
        val vm = vm(hour = 20)
        vm.setQuery("2 pan")
        vm.addSuggestion(vm.state.value.suggestions.first())
        val s = vm.state.value
        val item = s.items.single()
        assertEquals("Paneer paratha", item.name)
        assertEquals(2.0, item.quantity, 0.0)
        assertEquals("piece", item.unit)
        assertEquals(180.0, item.grams, 0.0)
        assertEquals(MealType.DINNER, item.meal)
        assertEquals(FoodSource.LOCAL, item.source)
        assertEquals("", s.query)
        assertEquals("stays on search so more can be added", Stage.Search, s.stage)
    }

    @Test
    fun `a learned portion size is used when adding from search`() = runTest {
        repeat(2) { portions.record("Roti", "piece", 50.0) }
        val vm = vm()
        vm.addSuggestion(FoodSuggestion(roti, 2.0))
        assertEquals(100.0, vm.state.value.items.single().grams, 0.0)
    }

    // ---- AI ----

    @Test
    fun `find with AI sends the typed meal and goes to review`() {
        parser.result = ParsedMeal(null, listOf(food("Roti", 2.0), food("Dal")))
        val vm = vm(hour = 13)
        vm.setQuery("2 roti with dal")
        vm.findWithAi()
        assertEquals(listOf("2 roti with dal"), parser.calls)
        val s = vm.state.value
        assertEquals(Stage.Review, s.stage)
        assertEquals(listOf("Roti", "Dal"), s.items.map { it.name })
        assertEquals("no 'which meal' question: the picked meal is used", MealType.LUNCH, s.meal)
        assertEquals("", s.query)
    }

    @Test
    fun `a meal named in the text wins`() {
        parser.result = ParsedMeal(MealType.DINNER, listOf(food("Roti", 2.0)))
        val vm = vm(hour = 13)
        vm.setQuery("two roti for dinner")
        vm.findWithAi()
        assertEquals(MealType.DINNER, vm.state.value.meal)
        assertTrue(vm.state.value.items.all { it.meal == MealType.DINNER })
    }

    @Test
    fun `AI results are added to foods already picked from search`() {
        parser.result = ParsedMeal(null, listOf(food("Curd")))
        val vm = vm()
        vm.addSuggestion(FoodSuggestion(roti, 2.0))
        vm.setQuery("a bowl of curd")
        vm.findWithAi()
        assertEquals(listOf("Roti", "Curd"), vm.state.value.items.map { it.name })
    }

    @Test
    fun `blank text is not sent`() {
        val vm = vm()
        vm.setQuery("   ")
        vm.findWithAi()
        assertTrue(parser.calls.isEmpty())
        assertEquals(Stage.Search, vm.state.value.stage)
    }

    @Test
    fun `nothing understood shows an error and try again brings the text back`() {
        parser.result = ParsedMeal(null, emptyList())
        val vm = vm()
        vm.setQuery("hello there")
        vm.findWithAi()
        assertEquals(Stage.Error, vm.state.value.stage)
        assertEquals(AppError.NO_FOOD, vm.state.value.error)
        vm.retry()
        assertEquals(Stage.Search, vm.state.value.stage)
        assertEquals("hello there", vm.state.value.query)
    }

    @Test
    fun `parser errors are shown`() {
        parser.error = AppError.FREE_LIMIT
        val vm = vm()
        vm.setQuery("dal")
        vm.findWithAi()
        assertEquals(Stage.Error, vm.state.value.stage)
        assertEquals(AppError.FREE_LIMIT, vm.state.value.error)
    }

    // ---- review ----

    @Test
    fun `review and add more move between the two screens`() {
        val vm = vm()
        vm.review()
        assertEquals("nothing to review yet", Stage.Search, vm.state.value.stage)
        vm.addSuggestion(FoodSuggestion(roti, 1.0))
        vm.review()
        assertEquals(Stage.Review, vm.state.value.stage)
        vm.addMore()
        assertEquals(Stage.Search, vm.state.value.stage)
        assertEquals(1, vm.state.value.items.size)
    }

    @Test
    fun `changing the meal moves every item`() {
        val vm = vm()
        vm.addSuggestion(FoodSuggestion(roti, 1.0))
        vm.setMeal(MealType.DINNER)
        assertEquals(MealType.DINNER, vm.state.value.meal)
        assertTrue(vm.state.value.items.all { it.meal == MealType.DINNER })
    }

    @Test
    fun `editing an item rescales it`() {
        val vm = vm()
        vm.addSuggestion(FoodSuggestion(roti, 2.0))
        vm.updateItem(0, quantity = 3.0, unit = "piece")
        assertEquals(120.0, vm.state.value.items[0].grams, 0.001)
        assertEquals(360.0, vm.state.value.total.calories, 0.001)
    }

    @Test
    fun `removing an item`() {
        val vm = vm()
        vm.addSuggestion(FoodSuggestion(roti, 1.0))
        vm.addSuggestion(FoodSuggestion(tikka, 1.0))
        vm.removeItem(0)
        assertEquals(listOf("Paneer tikka"), vm.state.value.items.map { it.name })
    }

    // ---- saving ----

    @Test
    fun `save logs every item on the day`() {
        val vm = vm(hour = 13)
        vm.addSuggestion(FoodSuggestion(roti, 2.0))
        vm.addSuggestion(FoodSuggestion(tikka, 1.0))
        vm.save()
        assertEquals(Stage.Saved, vm.state.value.stage)
        assertEquals(listOf("Roti", "Paneer tikka"), log.items.value.map { it.name })
        assertTrue(log.items.value.all { it.date == today && it.meal == MealType.LUNCH })
        assertTrue(saved.meals.value.isEmpty())
    }

    @Test
    fun `save can also keep it as a favourite meal`() {
        val vm = vm()
        vm.addSuggestion(FoodSuggestion(roti, 2.0))
        vm.setSaveAsFavorite(true)
        vm.setFavoriteName("Usual lunch")
        vm.save()
        val fav = saved.meals.value.single()
        assertEquals("Usual lunch", fav.name)
        assertEquals(80.0, fav.items.single().grams, 0.001)
    }

    @Test
    fun `favourite with a blank name uses the meal name`() {
        val vm = vm(hour = 13)
        vm.addSuggestion(FoodSuggestion(roti, 1.0))
        vm.setSaveAsFavorite(true)
        vm.setFavoriteName("  ")
        vm.save()
        assertEquals("My lunch", saved.meals.value.single().name)
    }

    @Test
    fun `save with no items does nothing`() {
        val vm = vm()
        vm.save()
        assertTrue(log.items.value.isEmpty())
        assertEquals(Stage.Search, vm.state.value.stage)
    }

    @Test
    fun `what was typed for the AI is kept only when allowed`() {
        parser.result = ParsedMeal(null, listOf(food("Roti")))
        val vm = vm()
        vm.setQuery("one roti")
        vm.findWithAi()
        vm.save()
        assertNull(log.items.value.single().note)

        settings.flow.value = settings.flow.value.copy(keepTranscript = true)
        val vm2 = vm()
        vm2.setQuery("two roti please")
        vm2.findWithAi()
        vm2.save()
        assertEquals("two roti please", log.items.value.last().note)
    }

    // ---- photo and barcode ----

    @Test
    fun `a photo goes to review`() {
        parser.result = ParsedMeal(null, listOf(food("Roti", 2.0), food("Dal")))
        val vm = vm()
        vm.submitPhoto(ImageInput(byteArrayOf(1), "image/jpeg"))
        assertEquals(1, parser.images.size)
        assertEquals(Stage.Review, vm.state.value.stage)
        assertEquals(listOf("Roti", "Dal"), vm.state.value.items.map { it.name })
    }

    @Test
    fun `a known barcode adds one serving from the label`() {
        val vm = vm(hour = 17)
        vm.submitBarcode("890123")
        val item = vm.state.value.items.single()
        assertEquals("Wheat crackers", item.name)
        assertEquals(30.0, item.grams, 0.0)
        assertEquals("serving", item.unit)
        assertEquals(135.0, item.nutrients.calories, 0.001)
        assertEquals(FoodSource.PACKAGED, item.source)
        assertEquals(MealType.SNACK, vm.state.value.meal)
        assertEquals(Stage.Review, vm.state.value.stage)
    }

    @Test
    fun `unknown barcode shows not found`() {
        val vm = vm()
        vm.submitBarcode("000")
        assertEquals(Stage.Error, vm.state.value.stage)
        assertEquals(AppError.BARCODE_NOT_FOUND, vm.state.value.error)
    }

    // ---- learned portions ----

    @Test
    fun `the second same edit teaches the portion and says so once`() {
        parser.result = ParsedMeal(MealType.LUNCH, listOf(food("Dal", 1.0, "bowl", 200.0)))
        val vm = vm()
        vm.setQuery("bowl of dal"); vm.findWithAi()
        vm.updateItem(0, 1.0, "bowl") // keeps 200 g per bowl
        assertNull(vm.state.value.learned)

        vm.reset()
        vm.setQuery("bowl of dal"); vm.findWithAi()
        vm.updateItem(0, 1.0, "bowl")
        assertEquals("Dal", vm.state.value.learned!!.foodName)
        assertEquals(200.0, vm.state.value.learned!!.gramsPerUnit, 0.0)
        vm.learnedShown()
        assertNull(vm.state.value.learned)
    }
}
