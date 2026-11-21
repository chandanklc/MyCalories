package com.example.mycalories.ui.weight

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mycalories.domain.GoalAdjuster
import com.example.mycalories.domain.GoalCalculator
import com.example.mycalories.domain.GoalSuggestion
import com.example.mycalories.domain.WeightTrend
import com.example.mycalories.domain.model.UserProfile
import com.example.mycalories.domain.model.WeightEntry
import com.example.mycalories.domain.repository.HealthDataSource
import com.example.mycalories.domain.repository.ProfileRepository
import com.example.mycalories.domain.repository.SettingsRepository
import com.example.mycalories.domain.repository.WeightRepository
import com.example.mycalories.ui.profile.ProfileUiState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class WeightUiState(
    val entries: List<WeightEntry> = emptyList(),
    val current: Double? = null,
    val targetKg: Double? = null,
    val weeklyRate: Double? = null,
    val projectedDate: LocalDate? = null,
    val goalCalories: Int? = null,
    val suggestion: GoalSuggestion? = null,
)

class WeightViewModel(
    private val weights: WeightRepository,
    private val profiles: ProfileRepository,
    private val clock: Clock,
    private val health: HealthDataSource,
    private val settings: SettingsRepository,
) : ViewModel() {

    private val today get() = LocalDate.now(clock)

    val state: StateFlow<WeightUiState> = combine(weights.observeAll(), profiles.profile) { entries, profile ->
        val rate = WeightTrend.weeklyRate(entries, today)
        val current = entries.lastOrNull()?.kg
        WeightUiState(
            entries = entries,
            current = current,
            targetKg = profile?.targetWeightKg,
            weeklyRate = rate,
            projectedDate = if (current != null && rate != null && profile?.targetWeightKg != null)
                WeightTrend.projectedDate(current, profile.targetWeightKg, rate, today) else null,
            goalCalories = profile?.let { GoalCalculator.calculate(it).calories },
            suggestion = profile?.let { suggestionFor(it, entries) },
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, WeightUiState())

    init {
        viewModelScope.launch {
            if (!settings.settings.first().healthConnect) return@launch
            val scale = runCatching { health.latestWeight() }.getOrNull() ?: return@launch
            val last = weights.observeAll().first().lastOrNull()
            if (last == null || scale.date.isAfter(last.date)) log(scale.date, scale.kg)
        }
    }

    /** Only weights since the last review count, so a suggestion waits for 3 weeks of new data. */
    private fun suggestionFor(p: UserProfile, entries: List<WeightEntry>): GoalSuggestion? {
        val since = entries.filter { p.goalReviewedOn == null || it.date.isAfter(p.goalReviewedOn) }
        val rate = WeightTrend.weeklyRate(since, today) ?: return null
        val weeks = (ChronoUnit.DAYS.between(since.first().date, since.last().date) / 7).toInt()
        return GoalAdjuster.suggest(p.goal, GoalCalculator.calculate(p).calories, rate, weeks)
    }

    fun log(text: String) {
        val kg = ProfileUiState.parseDecimal(text)?.takeIf { it in 30.0..300.0 } ?: return
        viewModelScope.launch { log(today, kg) }
    }

    private suspend fun log(date: LocalDate, kg: Double) {
        weights.log(date, kg)
        profiles.profile.first()?.let { profiles.save(it.copy(weightKg = kg)) }
    }

    fun setTarget(text: String) {
        val kg = ProfileUiState.parseDecimal(text)?.takeIf { it in 30.0..300.0 } ?: return
        viewModelScope.launch { profiles.profile.first()?.let { profiles.save(it.copy(targetWeightKg = kg)) } }
    }

    fun acceptSuggestion() {
        val s = state.value.suggestion ?: return
        viewModelScope.launch {
            profiles.profile.first()?.let {
                profiles.save(it.copy(calorieAdjustment = it.calorieAdjustment + s.change, goalReviewedOn = today))
            }
        }
    }

    fun dismissSuggestion() {
        viewModelScope.launch { profiles.profile.first()?.let { profiles.save(it.copy(goalReviewedOn = today)) } }
    }
}
