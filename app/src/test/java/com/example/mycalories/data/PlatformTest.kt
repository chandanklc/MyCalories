package com.example.mycalories.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.mycalories.data.local.AppDatabase
import com.example.mycalories.data.repository.DataStoreSettingsRepository
import com.example.mycalories.data.repository.RoomDataManager
import com.example.mycalories.data.repository.RoomLogRepository
import com.example.mycalories.domain.Backup
import com.example.mycalories.domain.ExportReport
import com.example.mycalories.domain.Notifier
import com.example.mycalories.domain.ReminderChecker
import com.example.mycalories.domain.ReportDay
import com.example.mycalories.domain.model.ActivityLevel
import com.example.mycalories.domain.model.Food
import com.example.mycalories.domain.model.FoodSource
import com.example.mycalories.domain.model.GoalType
import com.example.mycalories.domain.model.LoggedItem
import com.example.mycalories.domain.model.MealType
import com.example.mycalories.domain.model.Nutrients
import com.example.mycalories.domain.model.SavedMeal
import com.example.mycalories.domain.model.SavedMealItem
import com.example.mycalories.domain.model.Sex
import com.example.mycalories.domain.model.UserProfile
import com.example.mycalories.domain.model.WeightEntry
import com.example.mycalories.domain.repository.AppSettings
import com.example.mycalories.domain.repository.ThemeMode
import com.example.mycalories.fakes.FakeLogRepository
import com.example.mycalories.fakes.FakeProfileRepository
import com.example.mycalories.fakes.FakeSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset

class ReminderCheckerTest {

    private val today = LocalDate.of(2026, 11, 18)
    private fun clock(h: Int, m: Int) = Clock.fixed(LocalDateTime.of(today, LocalTime.of(h, m)).toInstant(ZoneOffset.UTC), ZoneOffset.UTC)

    private class RecordingNotifier : Notifier {
        val meals = mutableListOf<MealType>()
        val water = mutableListOf<Int>()
        override fun mealReminder(meal: MealType) { meals += meal }
        override fun waterReminder(glasses: Int, goal: Int) { water += glasses }
    }

    private fun item(date: LocalDate, meal: MealType, h: Int, m: Int = 0) = LoggedItem(
        0, date, meal, "x", 1.0, "g", 1.0, Nutrients(), FoodSource.LOCAL, false,
        LocalDateTime.of(date, LocalTime.of(h, m)).toInstant(ZoneOffset.UTC).toEpochMilli(),
    )

    @Test
    fun `reminds once for a meal not logged by its usual time`() = runTest {
        val log = FakeLogRepository()
        // Usually has lunch around 12:30.
        log.add((1L..5L).map { item(today.minusDays(it), MealType.LUNCH, 12, 30) })
        val settings = FakeSettingsRepository()
        val notifier = RecordingNotifier()

        ReminderChecker(log, settings, notifier, clock(13, 20)).run()
        assertEquals(listOf(MealType.LUNCH), notifier.meals.filter { it == MealType.LUNCH })

        ReminderChecker(log, settings, notifier, clock(13, 50)).run()
        assertEquals(1, notifier.meals.count { it == MealType.LUNCH })
    }

    @Test
    fun `no reminder when the meal is logged or reminders are off`() = runTest {
        val log = FakeLogRepository()
        log.add(listOf(item(today, MealType.LUNCH, 13, 0)))
        val notifier = RecordingNotifier()
        ReminderChecker(log, FakeSettingsRepository(), notifier, clock(14, 30)).run()
        assertTrue(MealType.LUNCH !in notifier.meals)

        val off = FakeSettingsRepository(AppSettings(mealReminders = false))
        ReminderChecker(FakeLogRepository(), off, notifier, clock(14, 30)).run()
        assertTrue(MealType.LUNCH !in notifier.meals)
    }

    @Test
    fun `water reminder every two hours while below goal`() = runTest {
        val log = FakeLogRepository()
        log.setWater(today, 3)
        val settings = FakeSettingsRepository(AppSettings(mealReminders = false, waterReminders = true))
        val notifier = RecordingNotifier()
        ReminderChecker(log, settings, notifier, clock(11, 0)).run()
        ReminderChecker(log, settings, notifier, clock(12, 0)).run()
        ReminderChecker(log, settings, notifier, clock(13, 5)).run()
        assertEquals(listOf(3, 3), notifier.water)
    }
}

class DataStoreSettingsRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val repo by lazy { DataStoreSettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { tmp.newFile("s.preferences_pb") }) }

    @After fun tearDown() = scope.cancel()

    @Test
    fun `defaults then saved values`() = runTest {
        assertEquals(AppSettings(), repo.settings.first())
        repo.update { it.copy(theme = ThemeMode.DARK, language = "hi", keepTranscript = true, waterReminders = true) }
        val s = repo.settings.first()
        assertEquals(ThemeMode.DARK, s.theme)
        assertEquals("hi", s.language)
        assertTrue(s.keepTranscript)
        assertTrue(s.waterReminders)
    }

    @Test
    fun `reminder marks are per day`() = runTest {
        val d = LocalDate.of(2026, 11, 18)
        repo.markReminded(d, MealType.LUNCH)
        repo.markReminded(d, MealType.DINNER)
        assertEquals(setOf(MealType.LUNCH, MealType.DINNER), repo.remindedMeals(d))
        repo.markReminded(d.plusDays(1), MealType.BREAKFAST)
        assertEquals(setOf(MealType.BREAKFAST), repo.remindedMeals(d.plusDays(1)))
        assertTrue("yesterday's marks are dropped", repo.remindedMeals(d).isEmpty())
    }

    @Test
    fun `last water reminder is per day`() = runTest {
        val d = LocalDate.of(2026, 11, 18)
        assertNull(repo.lastWaterReminder(d))
        repo.setLastWaterReminder(d, LocalTime.of(11, 0, 31))
        assertEquals(LocalTime.of(11, 0), repo.lastWaterReminder(d))
        assertNull(repo.lastWaterReminder(d.plusDays(1)))
    }
}

@RunWith(RobolectricTestRunner::class)
class RoomDataManagerTest {

    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
    private val day = LocalDate.of(2026, 11, 18)

    @After fun tearDown() = db.close()

    private val n = Nutrients(calories = 90.0, ironMg = 1.0)
    private val backup = Backup(
        profile = UserProfile(32, Sex.MALE, 172.0, 78.0, ActivityLevel.LIGHT, GoalType.LOSE),
        foods = listOf(Food(0, "Mom's dal", n, "bowl", 200.0, FoodSource.USER_RECIPE)),
        log = listOf(LoggedItem(0, day, MealType.LUNCH, "Mom's dal", 1.0, "bowl", 200.0, n, FoodSource.USER_RECIPE, false, 1)),
        savedMeals = listOf(SavedMeal(0, "Usual", listOf(SavedMealItem("Mom's dal", 1.0, "bowl", 200.0, n, FoodSource.USER_RECIPE)))),
        weights = listOf(WeightEntry(day, 78.0)),
        water = mapOf(day to 6),
    )

    @Test
    fun `restore then export gives the same data`() = runTest {
        var settingsCleared = 0
        val profiles = FakeProfileRepository()
        val manager = RoomDataManager(db, profiles) { settingsCleared++ }
        manager.restore(backup)
        val out = manager.export()
        assertEquals(backup.profile, out.profile)
        assertEquals(listOf("Mom's dal"), out.foods.map { it.name })
        assertEquals(backup.log.map { it.copy(id = 0) }, out.log.map { it.copy(id = 0) })
        assertEquals(backup.savedMeals.map { it.copy(id = 0) }, out.savedMeals.map { it.copy(id = 0) })
        assertEquals(backup.weights, out.weights)
        assertEquals(backup.water, out.water)
    }

    @Test
    fun `delete everything empties the app`() = runTest {
        var settingsCleared = 0
        val profiles = FakeProfileRepository()
        val manager = RoomDataManager(db, profiles) { settingsCleared++ }
        manager.restore(backup)
        manager.deleteEverything()
        assertTrue(RoomLogRepository(db).observeDay(day).first().isEmpty())
        assertNull(profiles.profileFlow.value)
        assertEquals(1, settingsCleared)
    }
}

class PdfLinesTest {
    @Test
    fun `report lines have a title, summary and one row per day`() {
        val d = LocalDate.of(2026, 11, 18)
        val report = ExportReport(d.minusDays(1), d, listOf(ReportDay(d, 1800.0, 90.0, 220.0, 60.0, 25.0)), 1800.0, 90.0, 78.6, 78.0, 6)
        val lines = com.example.mycalories.platform.PdfLines.build(report, "Food diary", java.util.Locale.UK).map { it.text }
        assertEquals("Food diary · 17 Nov 2026 – 18 Nov 2026", lines[0])
        assertTrue(lines[1], lines[1].contains("1800 kcal") && lines[1].contains("90 g protein") && lines[1].contains("1 logged days"))
        assertEquals("Weight 78.6 → 78.0 kg", lines[2])
        assertTrue(lines.last(), lines.last().startsWith("18 Nov 2026") && lines.last().contains("1800"))
    }

    @Test
    fun `no weight line without weights`() {
        val d = LocalDate.of(2026, 11, 18)
        val lines = com.example.mycalories.platform.PdfLines.build(ExportReport(d, d, emptyList(), 0.0, 0.0, null, null, 0), "x").map { it.text }
        assertTrue(lines.none { it.startsWith("Weight") })
    }
}
