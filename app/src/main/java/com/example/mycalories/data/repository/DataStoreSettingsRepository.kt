package com.example.mycalories.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.example.mycalories.domain.model.MealType
import com.example.mycalories.domain.repository.AppSettings
import com.example.mycalories.domain.repository.SettingsRepository
import com.example.mycalories.domain.repository.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalTime

class DataStoreSettingsRepository(private val store: DataStore<Preferences>) : SettingsRepository {

    private object K {
        val meal = booleanPreferencesKey("meal_reminders")
        val water = booleanPreferencesKey("water_reminders")
        val theme = stringPreferencesKey("theme")
        val language = stringPreferencesKey("language")
        val photos = booleanPreferencesKey("allow_photos")
        val transcript = booleanPreferencesKey("keep_transcript")
        val health = booleanPreferencesKey("health_connect")
        val reminded = stringSetPreferencesKey("reminded_meals") // "2026-11-18:LUNCH"
        val lastWater = stringPreferencesKey("last_water_reminder") // "2026-11-18T15:00"
    }

    private val defaults = AppSettings()

    override val settings: Flow<AppSettings> = store.data.map { p ->
        AppSettings(
            mealReminders = p[K.meal] ?: defaults.mealReminders,
            waterReminders = p[K.water] ?: defaults.waterReminders,
            theme = ThemeMode.entries.firstOrNull { it.name == p[K.theme] } ?: defaults.theme,
            language = p[K.language] ?: defaults.language,
            allowPhotos = p[K.photos] ?: defaults.allowPhotos,
            keepTranscript = p[K.transcript] ?: defaults.keepTranscript,
            healthConnect = p[K.health] ?: defaults.healthConnect,
        )
    }

    override suspend fun update(change: (AppSettings) -> AppSettings) {
        val s = change(settings.first())
        store.edit {
            it[K.meal] = s.mealReminders
            it[K.water] = s.waterReminders
            it[K.theme] = s.theme.name
            it[K.language] = s.language
            it[K.photos] = s.allowPhotos
            it[K.transcript] = s.keepTranscript
            it[K.health] = s.healthConnect
        }
    }

    override suspend fun remindedMeals(date: LocalDate): Set<MealType> =
        store.data.first()[K.reminded].orEmpty()
            .filter { it.startsWith("$date:") }
            .mapNotNull { MealType.fromString(it.substringAfter(':')) }
            .toSet()

    override suspend fun markReminded(date: LocalDate, meal: MealType) {
        store.edit { p ->
            // Keep only today's marks so the set never grows.
            p[K.reminded] = p[K.reminded].orEmpty().filter { it.startsWith("$date:") }.toSet() + "$date:${meal.name}"
        }
    }

    override suspend fun lastWaterReminder(date: LocalDate): LocalTime? =
        store.data.first()[K.lastWater]?.takeIf { it.startsWith("${date}T") }?.let { LocalTime.parse(it.substringAfter('T')) }

    override suspend fun setLastWaterReminder(date: LocalDate, time: LocalTime) {
        store.edit { it[K.lastWater] = "${date}T${time.withSecond(0).withNano(0)}" }
    }

    suspend fun clear() = store.edit { it.clear() }
}
