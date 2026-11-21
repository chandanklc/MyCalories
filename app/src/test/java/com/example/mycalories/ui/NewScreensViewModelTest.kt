package com.example.mycalories.ui

import com.example.mycalories.data.remote.AppError
import com.example.mycalories.data.remote.ChatMessage
import com.example.mycalories.data.remote.AiAssistant
import com.example.mycalories.data.remote.AiReply
import com.example.mycalories.data.remote.ParsedFood
import com.example.mycalories.data.remote.ParsedRecipe
import com.example.mycalories.data.remote.RecordingTransport
import com.example.mycalories.domain.Backup
import com.example.mycalories.domain.BackupSerializer
import com.example.mycalories.domain.FoodResolver
import com.example.mycalories.domain.GoalCalculator
import com.example.mycalories.domain.MicroKey
import com.example.mycalories.domain.MicroStatus
import com.example.mycalories.domain.model.ActivityLevel
import com.example.mycalories.domain.model.FoodSource
import com.example.mycalories.domain.model.GoalType
import com.example.mycalories.domain.model.LoggedItem
import com.example.mycalories.domain.model.MealType
import com.example.mycalories.domain.model.Nutrients
import com.example.mycalories.domain.model.Sex
import com.example.mycalories.domain.model.UserProfile
import com.example.mycalories.domain.model.WeightEntry
import com.example.mycalories.domain.repository.AppSettings
import com.example.mycalories.domain.repository.HealthAvailability
import com.example.mycalories.domain.repository.ThemeMode
import com.example.mycalories.fakes.FakeDataManager
import com.example.mycalories.fakes.FakeFoodParser
import com.example.mycalories.fakes.FakeFoodRepository
import com.example.mycalories.fakes.FakeHealth
import com.example.mycalories.fakes.FakeLogRepository
import com.example.mycalories.fakes.FakeOnlineSource
import com.example.mycalories.fakes.FakeProfileRepository
import com.example.mycalories.fakes.FakeSettingsRepository
import com.example.mycalories.fakes.FakeWeightRepository
import com.example.mycalories.fakes.MainDispatcherRule
import com.example.mycalories.fakes.clockAt
import com.example.mycalories.ui.ask.AskViewModel
import com.example.mycalories.ui.export.ExportFormat
import com.example.mycalories.ui.export.ExportRange
import com.example.mycalories.ui.export.ExportViewModel
import com.example.mycalories.ui.micros.MicrosViewModel
import com.example.mycalories.ui.privacy.PrivacyViewModel
import com.example.mycalories.ui.recipe.RecipeStage
import com.example.mycalories.ui.recipe.RecipeViewModel
import com.example.mycalories.ui.settings.SettingsViewModel
import com.example.mycalories.ui.weekly.WeeklySummaryViewModel
import com.example.mycalories.ui.weight.WeightViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

private val TODAY = LocalDate.of(2026, 11, 18)
private val PROFILE = UserProfile(32, Sex.MALE, 172.0, 78.0, ActivityLevel.SEDENTARY, GoalType.LOSE, targetWeightKg = 72.0)

private fun logged(daysAgo: Long, kcal: Double, protein: Double = 0.0, meal: MealType = MealType.LUNCH, n: Nutrients? = null) = LoggedItem(
    0, TODAY.minusDays(daysAgo), meal, "x", 1.0, "g", 100.0, n ?: Nutrients(calories = kcal, protein = protein), FoodSource.LOCAL, false, 0,
)

class RecipeViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val parser = FakeFoodParser()
    private val foods = FakeFoodRepository()
    private fun vm() = RecipeViewModel(parser, FoodResolver(foods, FakeOnlineSource()), foods, clockAt(12))

    private fun ing(name: String, grams: Double, kcal: Double) =
        ParsedFood(name, 1.0, "g", grams, null, Nutrients(calories = kcal, protein = 10.0), false)

    @Test
    fun `typed recipe gives per serving numbers`() = runTest {
        parser.recipe = ParsedRecipe("Mom's dal", 4, "bowl", listOf(ing("Toor dal", 200.0, 343.0), ing("Ghee", 26.0, 900.0)))
        val vm = vm()
        vm.submitText("mom's dal ...")
        val s = vm.state.value
        assertEquals(RecipeStage.Ready, s.stage)
        assertEquals("Mom's dal", s.name)
        assertEquals(4, s.servings)
        assertEquals((686.0 + 234.0) / 4, s.result!!.perServing.calories, 0.01)
        assertEquals(56.5, s.result!!.gramsPerServing, 0.01)
    }

    @Test
    fun `changing servings and removing an ingredient recalculates`() = runTest {
        parser.recipe = ParsedRecipe("Dal", 4, "bowl", listOf(ing("Toor dal", 200.0, 343.0), ing("Ghee", 26.0, 900.0)))
        val vm = vm()
        vm.submitText("dal")
        vm.setServings(2)
        assertEquals(460.0, vm.state.value.result!!.perServing.calories, 0.01)
        vm.removeIngredient(1)
        assertEquals(343.0, vm.state.value.result!!.perServing.calories, 0.01)
        vm.setServings(0)
        assertEquals(1, vm.state.value.servings)
    }

    @Test
    fun `save stores the recipe as a food`() = runTest {
        parser.recipe = ParsedRecipe("Dal", 4, "bowl", listOf(ing("Toor dal", 200.0, 343.0), ing("Ghee", 26.0, 900.0)))
        val vm = vm()
        vm.submitText("dal")
        vm.setName("  Mom's dal ")
        vm.save()
        val saved = foods.findByName("Mom's dal")!!
        assertEquals(FoodSource.USER_RECIPE, saved.source)
        assertEquals("bowl", saved.defaultUnit)
        assertEquals(56.5, saved.gramsPerUnit, 0.01)
        assertEquals(230.0, saved.per100g.forGrams(saved.gramsPerUnit).calories, 0.01)
        assertEquals(RecipeStage.Saved, vm.state.value.stage)
    }

    @Test
    fun `errors are shown`() = runTest {
        parser.error = AppError.NETWORK
        val vm = vm()
        vm.submitText("dal")
        assertEquals(RecipeStage.Error, vm.state.value.stage)
        assertEquals(AppError.NETWORK, vm.state.value.error)
    }
}

class WeightViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val weights = FakeWeightRepository()
    private val profiles = FakeProfileRepository(PROFILE)
    private val health = FakeHealth()
    private val settings = FakeSettingsRepository()
    private fun vm() = WeightViewModel(weights, profiles, clockAt(9), health, settings)

    private suspend fun seedSlowLoss() {
        (0..4).forEach { w -> weights.log(TODAY.minusDays(28L - w * 7), 79.2 - w * 0.2) } // -0.2 kg/week
    }

    @Test
    fun `shows current weight, rate, target and projection`() = runTest {
        seedSlowLoss()
        val s = vm().state.value
        assertEquals(78.4, s.current!!, 0.001)
        assertEquals(-0.2, s.weeklyRate!!, 0.001)
        assertEquals(72.0, s.targetKg!!, 0.0)
        assertEquals(TODAY.plusWeeks(32), s.projectedDate)
    }

    @Test
    fun `slow loss suggests a lower goal and accepting applies it`() = runTest {
        seedSlowLoss()
        val vm = vm()
        val suggestion = vm.state.value.suggestion!!
        assertEquals(-120, suggestion.change)
        vm.acceptSuggestion()
        assertEquals(-120, profiles.profileFlow.value!!.calorieAdjustment)
        assertNull(vm.state.value.suggestion)
    }

    @Test
    fun `keeping the goal hides the suggestion`() = runTest {
        seedSlowLoss()
        val vm = vm()
        vm.dismissSuggestion()
        assertNull(vm.state.value.suggestion)
        assertEquals(0, profiles.profileFlow.value!!.calorieAdjustment)
    }

    @Test
    fun `logging weight saves it and updates the profile`() = runTest {
        val vm = vm()
        vm.log("77,6")
        assertEquals(77.6, weights.entries.value.single().kg, 0.0)
        assertEquals(77.6, profiles.profileFlow.value!!.weightKg, 0.0)
        vm.log("abc")
        assertEquals(1, weights.entries.value.size)
    }

    @Test
    fun `target can be set`() = runTest {
        val vm = vm()
        vm.setTarget("70")
        assertEquals(70.0, profiles.profileFlow.value!!.targetWeightKg!!, 0.0)
    }

    @Test
    fun `a newer weight from a smart scale is imported`() = runTest {
        settings.flow.value = AppSettings(healthConnect = true)
        health.weight = WeightEntry(TODAY, 77.9)
        vm()
        assertEquals(77.9, weights.entries.value.single().kg, 0.0)
    }
}

class WeeklySummaryViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    @Test
    fun `builds the week from log, water and weights`() = runTest {
        val log = FakeLogRepository()
        log.add((0L..6L).map { logged(it, 2000.0, protein = 70.0) })
        log.setWater(TODAY, 8)
        val weights = FakeWeightRepository()
        weights.log(TODAY.minusDays(7), 78.6)
        weights.log(TODAY, 78.0)
        val s = WeeklySummaryViewModel(log, weights, FakeProfileRepository(PROFILE), clockAt(20)).state.value.summary!!
        assertEquals(7, s.daysLogged)
        assertEquals(7, s.lowProteinDays)
        assertEquals(1, s.waterGoalDays)
        assertEquals(-0.6, s.weightChange!!, 0.001)
        assertEquals(7, s.streak)
        assertEquals(GoalCalculator.calculate(PROFILE).protein, WeeklySummaryViewModel(log, weights, FakeProfileRepository(PROFILE), clockAt(20)).state.value.goal.protein)
    }
}

class AskViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    @Test
    fun `asks with today's numbers and keeps the conversation`() = runTest {
        val log = FakeLogRepository()
        log.add(listOf(logged(0, 1149.0, protein = 52.0)))
        val transport = RecordingTransport(AiReply("end_turn", "Try paneer bhurji."))
        val vm = AskViewModel(AiAssistant(transport), log, FakeProfileRepository(PROFILE), clockAt(19))

        vm.send("What should I eat for dinner?")

        val msgs = vm.state.value.messages
        assertEquals(listOf(ChatMessage.user("What should I eat for dinner?"), ChatMessage.assistant("Try paneer bhurji.")), msgs)
        val goal = GoalCalculator.calculate(PROFILE)
        val system = transport.requests.single().system
        assertTrue(system, system.contains("${goal.calories - 1149} kcal left"))
        assertTrue(system, system.contains("protein 52 of ${goal.protein} g"))
        assertFalse(vm.state.value.sending)

        vm.send("And a snack?")
        assertEquals(3, transport.requests.last().messages.size)
    }

    @Test
    fun `blank questions are ignored and errors shown`() = runTest {
        val transport = RecordingTransport(AiReply("refusal", ""))
        val vm = AskViewModel(AiAssistant(transport), FakeLogRepository(), FakeProfileRepository(PROFILE), clockAt(19))
        vm.send("  ")
        assertTrue(transport.requests.isEmpty())
        vm.send("hi")
        assertEquals(AppError.REFUSED, vm.state.value.error)
        assertEquals(1, vm.state.value.messages.size)
    }
}

class MicrosViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    @Test
    fun `today and seven-day average`() = runTest {
        val log = FakeLogRepository()
        log.add(listOf(
            logged(0, 0.0, n = Nutrients(ironMg = 9.0, vitaminB12Ug = 0.5)),
            logged(1, 0.0, n = Nutrients(ironMg = 19.0, vitaminB12Ug = 0.5)),
            logged(2, 0.0, n = Nutrients(ironMg = 20.0, vitaminB12Ug = 0.5)),
        ))
        val vm = MicrosViewModel(log, FakeProfileRepository(PROFILE), clockAt(12))
        assertEquals(MicroStatus.LOW, vm.state.value.rows.first { it.key == MicroKey.IRON }.status)
        vm.showWeek(true)
        assertEquals(16.0, vm.state.value.rows.first { it.key == MicroKey.IRON }.value, 0.001)
        assertEquals(setOf(MicroKey.B12, MicroKey.VITAMIN_D, MicroKey.CALCIUM, MicroKey.VITAMIN_C, MicroKey.POTASSIUM), vm.state.value.lowThisWeek)
    }
}

class SettingsViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val settings = FakeSettingsRepository()
    private val health = FakeHealth(granted = false)
    private val applied = mutableListOf<AppSettings>()
    private fun vm() = SettingsViewModel(settings, health) { applied += it }

    @Test
    fun `toggles are saved and reminders rescheduled`() = runTest {
        val vm = vm()
        vm.setMealReminders(false)
        vm.setWaterReminders(true)
        vm.setTheme(ThemeMode.DARK)
        vm.setLanguage("hi")
        vm.setAllowPhotos(false)
        vm.setKeepTranscript(true)
        val s = settings.flow.value
        assertFalse(s.mealReminders)
        assertTrue(s.waterReminders)
        assertEquals(ThemeMode.DARK, s.theme)
        assertEquals("hi", s.language)
        assertFalse(s.allowPhotos)
        assertTrue(s.keepTranscript)
        assertEquals(s, applied.last())
    }

    @Test
    fun `health connect turns on only when permission is granted`() = runTest {
        val vm = vm()
        assertEquals(HealthAvailability.AVAILABLE, vm.state.value.healthAvailability)
        vm.onHealthPermissionResult(false)
        assertFalse(settings.flow.value.healthConnect)
        health.granted = true
        vm.onHealthPermissionResult(true)
        assertTrue(settings.flow.value.healthConnect)
        vm.disconnectHealth()
        assertFalse(settings.flow.value.healthConnect)
    }
}

class ExportViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    @Test
    fun `csv for the chosen range`() = runTest {
        val log = FakeLogRepository()
        log.add(listOf(logged(0, 500.0), logged(10, 700.0), logged(40, 900.0)))
        val vm = ExportViewModel(log, FakeWeightRepository(), { byteArrayOf(9) }, clockAt(12))
        vm.setRange(ExportRange.DAYS_30)
        vm.setFormat(ExportFormat.CSV)
        val file = vm.build()
        assertEquals("text/csv", file.mimeType)
        assertTrue(file.fileName, file.fileName.endsWith(".csv"))
        val text = String(file.bytes)
        assertEquals(2, text.lines().count { it.startsWith("2026-") && it.contains(",Lunch,") })
        assertTrue(text.contains("date,kcal,protein_g"))
        assertEquals(2, vm.state.value.preview!!.days.size)
    }

    @Test
    fun `pdf uses the report maker and can leave out daily totals`() = runTest {
        val vm = ExportViewModel(FakeLogRepository(), FakeWeightRepository(), { byteArrayOf(1, 2) }, clockAt(12))
        val file = vm.build()
        assertEquals("application/pdf", file.mimeType)
        assertArrayEquals(byteArrayOf(1, 2), file.bytes)
        vm.setFormat(ExportFormat.CSV)
        vm.setIncludeTotals(false)
        assertFalse(String(vm.build().bytes).contains("date,kcal,protein_g"))
    }
}

class PrivacyViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    @Test
    fun `delete needs a confirmation`() = runTest {
        val data = FakeDataManager()
        val vm = PrivacyViewModel(data, FakeSettingsRepository())
        vm.requestDelete()
        assertTrue(vm.state.value.confirmingDelete)
        vm.cancelDelete()
        assertEquals(0, data.deleted)
        vm.requestDelete()
        vm.confirmDelete()
        assertEquals(1, data.deleted)
        assertTrue(vm.state.value.deleted)
    }

    @Test
    fun `backup text and restore`() = runTest {
        val backup = Backup(PROFILE, emptyList(), emptyList(), emptyList(), listOf(WeightEntry(TODAY, 78.0)), emptyMap())
        val data = FakeDataManager(backup)
        val vm = PrivacyViewModel(data, FakeSettingsRepository())
        val text = vm.backupText()
        assertEquals(backup, BackupSerializer.decode(text))
        vm.restore(text)
        assertEquals(backup, data.restored)
        assertTrue(vm.state.value.restored)
        vm.restore("nonsense")
        assertEquals(AppError.INVALID_BACKUP, vm.state.value.error)
    }

    @Test
    fun `photo and transcript switches`() = runTest {
        val settings = FakeSettingsRepository()
        val vm = PrivacyViewModel(FakeDataManager(), settings)
        vm.setAllowPhotos(false)
        vm.setKeepTranscript(true)
        assertFalse(settings.flow.value.allowPhotos)
        assertTrue(settings.flow.value.keepTranscript)
        assertNotNull(vm.state.value.settings)
    }
}
