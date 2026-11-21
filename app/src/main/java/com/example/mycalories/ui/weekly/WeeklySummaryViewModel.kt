package com.example.mycalories.ui.weekly

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mycalories.domain.GoalCalculator
import com.example.mycalories.domain.HistoryStats
import com.example.mycalories.domain.WeekSummary
import com.example.mycalories.domain.WeeklyInsights
import com.example.mycalories.domain.model.DailyGoal
import com.example.mycalories.domain.repository.LogRepository
import com.example.mycalories.domain.repository.ProfileRepository
import com.example.mycalories.domain.repository.WeightRepository
import com.example.mycalories.ui.today.TodayViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate

data class WeeklyUiState(val summary: WeekSummary? = null, val goal: DailyGoal = TodayViewModel.DEFAULT_GOAL)

class WeeklySummaryViewModel(
    log: LogRepository,
    weights: WeightRepository,
    profiles: ProfileRepository,
    clock: Clock,
) : ViewModel() {
    private val today = LocalDate.now(clock)

    val state: StateFlow<WeeklyUiState> = combine(
        log.observeRange(today.minusDays(13), today),
        log.observeWaterRange(today.minusDays(6), today),
        weights.observeAll(),
        log.observeLoggedDays(),
        profiles.profile,
    ) { items, water, w, days, profile ->
        val goal = profile?.let(GoalCalculator::calculate) ?: TodayViewModel.DEFAULT_GOAL
        WeeklyUiState(WeeklyInsights.build(items, today, goal, water, w, HistoryStats.streak(days, today)), goal)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, WeeklyUiState())
}
