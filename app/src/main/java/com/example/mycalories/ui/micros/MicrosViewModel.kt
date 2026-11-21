package com.example.mycalories.ui.micros

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mycalories.domain.MicroKey
import com.example.mycalories.domain.MicroNeeds
import com.example.mycalories.domain.MicroRow
import com.example.mycalories.domain.MicroStatus
import com.example.mycalories.domain.model.LoggedItem
import com.example.mycalories.domain.model.Nutrients
import com.example.mycalories.domain.model.sum
import com.example.mycalories.domain.repository.LogRepository
import com.example.mycalories.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate

data class MicrosUiState(
    val week: Boolean = false,
    val rows: List<MicroRow> = emptyList(),
    /** Nutrients that were low on average over the last 7 days. */
    val lowThisWeek: Set<MicroKey> = emptySet(),
)

class MicrosViewModel(log: LogRepository, profiles: ProfileRepository, clock: Clock) : ViewModel() {
    private val today = LocalDate.now(clock)
    private val week = MutableStateFlow(false)

    val state: StateFlow<MicrosUiState> = combine(log.observeRange(today.minusDays(6), today), profiles.profile, week) { items, profile, showWeek ->
        val needs = MicroNeeds.forSex(profile?.sex)
        val todayTotal = items.filter { it.date == today }.map { it.nutrients }.sum()
        val weekAverage = dailyAverage(items)
        MicrosUiState(
            week = showWeek,
            rows = MicroNeeds.report(if (showWeek) weekAverage else todayTotal, needs),
            lowThisWeek = MicroNeeds.report(weekAverage, needs).filter { it.status == MicroStatus.LOW }.map { it.key }.toSet(),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, MicrosUiState())

    fun showWeek(on: Boolean) { week.value = on }

    private fun dailyAverage(items: List<LoggedItem>): Nutrients {
        val days = items.groupBy { it.date }
        if (days.isEmpty()) return Nutrients.ZERO
        return days.values.map { l -> l.map { it.nutrients }.sum() }.sum() * (1.0 / days.size)
    }
}
