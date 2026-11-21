package com.example.mycalories.domain.repository

import com.example.mycalories.domain.Backup
import com.example.mycalories.domain.model.LoggedItem
import com.example.mycalories.domain.model.MealType
import com.example.mycalories.domain.model.WeightEntry
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalTime

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val mealReminders: Boolean = true,
    val waterReminders: Boolean = false,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    /** "en" or "hi". */
    val language: String = "en",
    val allowPhotos: Boolean = true,
    /** Keep what the user said next to each logged food. */
    val keepTranscript: Boolean = false,
    val healthConnect: Boolean = false,
)

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun update(change: (AppSettings) -> AppSettings)

    // Reminder bookkeeping, so each reminder is shown at most once a day.
    suspend fun remindedMeals(date: LocalDate): Set<MealType>
    suspend fun markReminded(date: LocalDate, meal: MealType)
    suspend fun lastWaterReminder(date: LocalDate): LocalTime?
    suspend fun setLastWaterReminder(date: LocalDate, time: LocalTime)
}

enum class HealthAvailability { AVAILABLE, NEEDS_UPDATE, NOT_SUPPORTED }

data class DayActivity(val activeCalories: Double, val steps: Long)

/** Health Connect: steps and workouts in, meals out. */
interface HealthDataSource {
    suspend fun availability(): HealthAvailability
    suspend fun hasPermissions(): Boolean
    suspend fun activity(date: LocalDate): DayActivity?
    suspend fun latestWeight(): WeightEntry?
    suspend fun writeMeals(items: List<LoggedItem>)
}

/** No Health Connect: used when it's switched off. */
object NoHealthData : HealthDataSource {
    override suspend fun availability() = HealthAvailability.NOT_SUPPORTED
    override suspend fun hasPermissions() = false
    override suspend fun activity(date: LocalDate): DayActivity? = null
    override suspend fun latestWeight(): WeightEntry? = null
    override suspend fun writeMeals(items: List<LoggedItem>) = Unit
}

/** Whole-app data: backup, restore and delete everything. */
interface DataManager {
    suspend fun export(): Backup
    suspend fun restore(backup: Backup)
    suspend fun deleteEverything()
}

/** Starts or stops the background reminder checks to match the settings. */
fun interface ReminderScheduler {
    fun apply(settings: AppSettings)
}
