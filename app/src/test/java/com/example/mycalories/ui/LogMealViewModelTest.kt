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
import com.example.mycalories.fakes.FakeSettingsRepository
import com.example.mycalories.fakes.FakeProfileRepository
import com.example.mycalories.fakes.FakeSavedMealRepository
import com.example.mycalories.fakes.FakeVoiceInput
import com.example.mycalories.fakes.MainDispatcherRule
import com.example.mycalories.fakes.clockAt
import com.example.mycalories.ui.logmeal.LogMealViewModel
import com.example.mycalories.ui.logmeal.Stage
import com.example.mycalories.voice.VoiceEvent
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

    private val voice = FakeVoiceInput()
    private val parser = FakeFoodParser()
    private val foods = FakeFoodRepository(listOf(roti))
    private val log = FakeLogRepository()
    private val profiles = FakeProfileRepository()
    private val saved = FakeSavedMealRepository()
    private val settings = FakeSettingsRepository()
    private val portions = FakePortionRepository()
    private val crackers = PackagedFood("Wheat crackers", "Acme", Nutrients(calories = 450.0, protein = 9.0), servingGrams = 30.0)

    private fun vm(hour: Int = 13) = LogMealViewModel(
        voice = voice, parser = parser, resolver = FoodResolver(foods, FakeOnlineSource(), portions),
        log = log, profiles = profiles, savedMeals = saved, clock = clockAt(hour, 20), date = today,
        packaged = FakePackagedFoods(mapOf("890123" to crackers)), settings = settings, portions = portions,
    )

    private fun food(name: String, qty: Double = 1.0, unit: String = "piece", grams: Double = 100.0, guessed: Boolean = false) =
        ParsedFood(name, qty, unit, grams, null, Nutrients(calories = 100.0), guessed)

    @Test
    fun `starts idle`() {
        assertEquals(Stage.Idle, vm().state.value.stage)
    }

    @Test
    fun `listening shows partial words`() = runTest {
        val vm = vm()
        vm.startListening()
        assertEquals(1, voice.started)
        voice.events.emit(VoiceEvent.Partial("two roti"))
        assertEquals(Stage.Listening, vm.state.value.stage)
        assertEquals("two roti", vm.state.value.transcript)
    }

    @Test
    fun `final words are parsed and go to review when the meal was said`() = runTest {
        parser.result = ParsedMeal(MealType.DINNER, listOf(food("roti", 2.0)))
        val vm = vm()
        vm.startListening()
        voice.events.emit(VoiceEvent.Final("two roti for dinner"))

        assertEquals(listOf("two roti for dinner"), parser.calls)
        val s = vm.state.value
        assertEquals(Stage.Review, s.stage)
        assertEquals(MealType.DINNER, s.meal)
        assertEquals(240.0, s.total.calories, 0.001)
        assertTrue(s.items.all { it.meal == MealType.DINNER })
    }

    @Test
    fun `no meal said asks which meal and suggests one by the clock`() = runTest {
        parser.result = ParsedMeal(null, listOf(food("Dal")))
        val vm = vm(hour = 13)
        vm.submitText("one bowl dal")
        assertEquals(Stage.ChooseMeal, vm.state.value.stage)
        assertEquals(MealType.LUNCH, vm.state.value.suggestedMeal)
    }

    @Test
    fun `choosing a meal goes to review with that meal`() = runTest {
        parser.result = ParsedMeal(null, listOf(food("Dal")))
        val vm = vm()
        vm.submitText("dal")
        vm.chooseMeal(MealType.SNACK, remember = false)
        assertEquals(Stage.Review, vm.state.value.stage)
        assertEquals(MealType.SNACK, vm.state.value.meal)
        assertTrue(vm.state.value.items.all { it.meal == MealType.SNACK })
        assertEquals(false, profiles.autoPickFlow.value)
    }

    @Test
    fun `remember choice turns on auto pick`() = runTest {
        parser.result = ParsedMeal(null, listOf(food("Dal")))
        val vm = vm()
        vm.submitText("dal")
        vm.chooseMeal(MealType.LUNCH, remember = true)
        assertTrue(profiles.autoPickFlow.value)
    }

    @Test
    fun `with auto pick on it skips the question`() = runTest {
        profiles.autoPickFlow.value = true
        parser.result = ParsedMeal(null, listOf(food("Dal")))
        val vm = vm(hour = 20)
        vm.submitText("dal")
        assertEquals(Stage.Review, vm.state.value.stage)
        assertEquals(MealType.DINNER, vm.state.value.meal)
    }

    @Test
    fun `nothing understood shows an error`() = runTest {
        parser.result = ParsedMeal(null, emptyList())
        val vm = vm()
        vm.submitText("hello there")
        assertEquals(Stage.Error, vm.state.value.stage)
        assertEquals(AppError.NO_FOOD, vm.state.value.error)
    }

    @Test
    fun `parser errors are shown`() = runTest {
        parser.error = AppError.NETWORK
        val vm = vm()
        vm.submitText("dal")
        assertEquals(Stage.Error, vm.state.value.stage)
        assertEquals(AppError.NETWORK, vm.state.value.error)
    }

    @Test
    fun `voice errors are shown`() = runTest {
        val vm = vm()
        vm.startListening()
        voice.events.emit(VoiceEvent.Error(AppError.NO_SPEECH))
        assertEquals(Stage.Error, vm.state.value.stage)
        assertEquals(AppError.NO_SPEECH, vm.state.value.error)
    }

    @Test
    fun `stop listening stops the mic`() = runTest {
        val vm = vm()
        vm.startListening()
        vm.stopListening()
        assertEquals(1, voice.stopped)
    }

    @Test
    fun `editing an item rescales it`() = runTest {
        parser.result = ParsedMeal(MealType.LUNCH, listOf(food("Roti", 2.0)))
        val vm = vm()
        vm.submitText("2 roti")
        vm.updateItem(0, quantity = 3.0, unit = "piece")
        assertEquals(120.0, vm.state.value.items[0].grams, 0.001)
        assertEquals(360.0, vm.state.value.total.calories, 0.001)
    }

    @Test
    fun `removing an item`() = runTest {
        parser.result = ParsedMeal(MealType.LUNCH, listOf(food("Roti"), food("Dal")))
        val vm = vm()
        vm.submitText("roti dal")
        vm.removeItem(0)
        assertEquals(listOf("Dal"), vm.state.value.items.map { it.name })
    }

    @Test
    fun `saying more adds to the same review`() = runTest {
        parser.result = ParsedMeal(MealType.LUNCH, listOf(food("Roti")))
        val vm = vm()
        vm.submitText("roti")
        parser.result = ParsedMeal(null, listOf(food("Salad")))
        vm.submitText("and salad")
        assertEquals(Stage.Review, vm.state.value.stage)
        assertEquals(listOf("Roti", "Salad"), vm.state.value.items.map { it.name })
        assertTrue(vm.state.value.items.all { it.meal == MealType.LUNCH })
    }

    @Test
    fun `changing meal in review moves every item`() = runTest {
        parser.result = ParsedMeal(MealType.LUNCH, listOf(food("Roti")))
        val vm = vm()
        vm.submitText("roti")
        vm.changeMeal(MealType.DINNER)
        assertTrue(vm.state.value.items.all { it.meal == MealType.DINNER })
    }

    @Test
    fun `save logs every item on the day`() = runTest {
        parser.result = ParsedMeal(MealType.LUNCH, listOf(food("Roti", 2.0), food("Dal")))
        val vm = vm()
        vm.submitText("x")
        vm.save()
        assertEquals(Stage.Saved, vm.state.value.stage)
        assertEquals(listOf("Roti", "Dal"), log.items.value.map { it.name })
        assertTrue(log.items.value.all { it.date == today && it.meal == MealType.LUNCH })
        assertTrue(saved.meals.value.isEmpty())
    }

    @Test
    fun `save can also keep it as a favourite meal`() = runTest {
        parser.result = ParsedMeal(MealType.LUNCH, listOf(food("Roti", 2.0)))
        val vm = vm()
        vm.submitText("x")
        vm.setSaveAsFavorite(true)
        vm.setFavoriteName("Usual lunch")
        vm.save()
        val fav = saved.meals.value.single()
        assertEquals("Usual lunch", fav.name)
        assertEquals(80.0, fav.items.single().grams, 0.001)
    }

    @Test
    fun `favourite with a blank name uses the meal name`() = runTest {
        parser.result = ParsedMeal(MealType.LUNCH, listOf(food("Roti")))
        val vm = vm()
        vm.submitText("x")
        vm.setSaveAsFavorite(true)
        vm.setFavoriteName("  ")
        vm.save()
        assertEquals("My lunch", saved.meals.value.single().name)
    }

    @Test
    fun `save with no items does nothing`() = runTest {
        val vm = vm()
        vm.save()
        assertTrue(log.items.value.isEmpty())
        assertEquals(Stage.Idle, vm.state.value.stage)
    }

    @Test
    fun `try again resets to idle`() = runTest {
        parser.error = AppError.GENERIC
        val vm = vm()
        vm.submitText("x")
        vm.reset()
        assertEquals(Stage.Idle, vm.state.value.stage)
        assertTrue(vm.state.value.items.isEmpty())
    }

    // ---- photo ----

    @Test
    fun `a photo goes through the same review`() = runTest {
        parser.result = ParsedMeal(null, listOf(food("Roti", 2.0), food("Dal")))
        val vm = vm()
        vm.submitPhoto(ImageInput(byteArrayOf(1), "image/jpeg"))
        assertEquals(1, parser.images.size)
        assertEquals(Stage.ChooseMeal, vm.state.value.stage)
        assertEquals(listOf("Roti", "Dal"), vm.state.value.items.map { it.name })
    }

    // ---- barcode ----

    @Test
    fun `a known barcode adds one serving from the label`() = runTest {
        profiles.autoPickFlow.value = true
        val vm = vm(hour = 17)
        vm.submitBarcode("890123")
        val item = vm.state.value.items.single()
        assertEquals("Wheat crackers", item.name)
        assertEquals(30.0, item.grams, 0.0)
        assertEquals("serving", item.unit)
        assertEquals(135.0, item.nutrients.calories, 0.001)
        assertEquals(com.example.mycalories.domain.model.FoodSource.PACKAGED, item.source)
        assertEquals(MealType.SNACK, vm.state.value.meal)
        assertEquals(Stage.Review, vm.state.value.stage)
    }

    @Test
    fun `unknown barcode shows not found`() = runTest {
        val vm = vm()
        vm.submitBarcode("000")
        assertEquals(Stage.Error, vm.state.value.stage)
        assertEquals(AppError.BARCODE_NOT_FOUND, vm.state.value.error)
    }

    // ---- keep what I said ----

    @Test
    fun `what was said is kept only when allowed`() = runTest {
        parser.result = ParsedMeal(MealType.LUNCH, listOf(food("Roti")))
        val vm = vm()
        vm.submitText("one roti")
        vm.save()
        assertNull(log.items.value.single().note)

        settings.flow.value = settings.flow.value.copy(keepTranscript = true)
        val vm2 = vm()
        vm2.submitText("two roti please")
        vm2.save()
        assertEquals("two roti please", log.items.value.last().note)
    }

    // ---- learned portions ----

    @Test
    fun `the second same edit teaches the portion and says so once`() = runTest {
        parser.result = ParsedMeal(MealType.LUNCH, listOf(food("Dal", 1.0, "bowl", 200.0)))
        val vm = vm()
        vm.submitText("bowl of dal")
        vm.updateItem(0, 1.0, "bowl") // keeps 200 g per bowl
        assertNull(vm.state.value.learned)

        vm.reset()
        vm.submitText("bowl of dal")
        vm.updateItem(0, 1.0, "bowl")
        assertEquals("Dal", vm.state.value.learned!!.foodName)
        assertEquals(200.0, vm.state.value.learned!!.gramsPerUnit, 0.0)
        vm.learnedShown()
        assertNull(vm.state.value.learned)
    }

    @Test
    fun `switching to typing stops listening so a silent mic can't show an error`() = runTest {
        val vm = vm()
        vm.startListening()
        vm.cancelListening()
        assertEquals(1, voice.stopped)
        assertEquals(Stage.Idle, vm.state.value.stage)
        voice.events.emit(VoiceEvent.Error(AppError.NO_SPEECH))
        assertEquals("late voice events are ignored", Stage.Idle, vm.state.value.stage)
    }
}
