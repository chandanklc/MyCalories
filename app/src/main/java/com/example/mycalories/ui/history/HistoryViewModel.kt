package com.example.mycalories.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mycalories.domain.DayTotal
import com.example.mycalories.domain.GoalCalculator
import com.example.mycalories.domain.HistoryStats
import com.example.mycalories.domain.model.DailyGoal
import com.example.mycalories.domain.repository.LogRepository
import com.example.mycalories.domain.repository.ProfileRepository
import com.example.mycalories.ui.today.TodayViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate

data class HistoryUiState(
    val days: List<DayTotal> = emptyList(),
    val goal: DailyGoal = TodayViewModel.DEFAULT_GOAL,
    val averageCalories: Double = 0.0,
    val averageProtein: Double = 0.0,
    val averageFiber: Double = 0.0,
    val streak: Int = 0,
    val weightKg: Double? = null,
) {
    val goalCalories: Int get() = goal.calories
}

class HistoryViewModel(
    log: LogRepository,
    profiles: ProfileRepository,
    clock: Clock,
) : ViewModel() {

    private val today = LocalDate.now(clock)

    val state: StateFlow<HistoryUiState> = combine(
        log.observeRange(today.minusDays(6), today),
        log.observeLoggedDays(),
        profiles.profile,
    ) { items, loggedDays, profile ->
        val byDay = items.groupBy { it.date }
        val kcalByDay = byDay.mapValues { (_, l) -> l.sumOf { it.nutrients.calories } }
        val completed = byDay.filterKeys { it.isBefore(today) }.filterValues { l -> l.sumOf { it.nutrients.calories } > 0 }

        fun avg(pick: (com.example.mycalories.domain.model.LoggedItem) -> Double) =
            if (completed.isEmpty()) 0.0 else completed.values.map { l -> l.sumOf(pick) }.average()

        HistoryUiState(
            days = HistoryStats.lastSevenDays(kcalByDay, today),
            goal = profile?.let(GoalCalculator::calculate) ?: TodayViewModel.DEFAULT_GOAL,
            averageCalories = HistoryStats.averageOfCompletedDays(kcalByDay, today),
            averageProtein = avg { it.nutrients.protein },
            averageFiber = avg { it.nutrients.fiber },
            streak = HistoryStats.streak(loggedDays, today),
            weightKg = profile?.weightKg,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HistoryUiState())
}
