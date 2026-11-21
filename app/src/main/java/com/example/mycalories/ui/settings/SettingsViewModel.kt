package com.example.mycalories.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mycalories.domain.repository.AppSettings
import com.example.mycalories.domain.repository.HealthAvailability
import com.example.mycalories.domain.repository.HealthDataSource
import com.example.mycalories.domain.repository.ReminderScheduler
import com.example.mycalories.domain.repository.SettingsRepository
import com.example.mycalories.domain.repository.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val healthAvailability: HealthAvailability = HealthAvailability.NOT_SUPPORTED,
)

class SettingsViewModel(
    private val repo: SettingsRepository,
    private val health: HealthDataSource,
    private val scheduler: ReminderScheduler,
) : ViewModel() {

    private val availability = MutableStateFlow(HealthAvailability.NOT_SUPPORTED)

    val state: StateFlow<SettingsUiState> = combine(repo.settings, availability) { s, a -> SettingsUiState(s, a) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsUiState())

    init {
        viewModelScope.launch { availability.value = runCatching { health.availability() }.getOrDefault(HealthAvailability.NOT_SUPPORTED) }
    }

    private fun change(f: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            repo.update(f)
            scheduler.apply(repo.settings.first())
        }
    }

    fun setMealReminders(on: Boolean) = change { it.copy(mealReminders = on) }
    fun setWaterReminders(on: Boolean) = change { it.copy(waterReminders = on) }
    fun setTheme(mode: ThemeMode) = change { it.copy(theme = mode) }
    fun setLanguage(tag: String) = change { it.copy(language = tag) }
    fun setAllowPhotos(on: Boolean) = change { it.copy(allowPhotos = on) }
    fun setKeepTranscript(on: Boolean) = change { it.copy(keepTranscript = on) }

    /** Called with the result of Health Connect's own permission screen. */
    fun onHealthPermissionResult(granted: Boolean) {
        viewModelScope.launch {
            val ok = granted && runCatching { health.hasPermissions() }.getOrDefault(false)
            repo.update { it.copy(healthConnect = ok) }
        }
    }

    fun disconnectHealth() = change { it.copy(healthConnect = false) }
}
