package com.example.mycalories.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mycalories.domain.DaySummary
import com.example.mycalories.domain.GoalCalculator
import com.example.mycalories.domain.model.DailyGoal
import com.example.mycalories.domain.model.MealType
import com.example.mycalories.domain.repository.AppSettings
import com.example.mycalories.domain.repository.DayActivity
import com.example.mycalories.domain.repository.HealthDataSource
import com.example.mycalories.domain.repository.LogRepository
import com.example.mycalories.domain.repository.NoHealthData
import com.example.mycalories.domain.repository.ProfileRepository
import com.example.mycalories.domain.repository.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import kotlin.math.roundToInt

sealed interface TodayMessage {
    data class CopiedMeal(val meal: MealType) : TodayMessage
    data object CopiedDay : TodayMessage
}

data class TodayUiState(
    val date: LocalDate,
    val isToday: Boolean,
    val summary: DaySummary,
    val waterGlasses: Int = 0,
    val needsProfile: Boolean = false,
    val loading: Boolean = true,
    val burnedCalories: Int = 0,
    val steps: Long? = null,
    /** Calories per meal logged the day before, for "same as yesterday". */
    val yesterdayMeals: Map<MealType, Double> = emptyMap(),
    val message: TodayMessage? = null,
) {
    val remainingCalories: Int get() = summary.remainingCalories + burnedCalories
}

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(
    private val log: LogRepository,
    profiles: ProfileRepository,
    private val clock: Clock,
    private val health: HealthDataSource = NoHealthData,
    settings: SettingsRepository? = null,
) : ViewModel() {

    private val today get() = LocalDate.now(clock)
    private val date = MutableStateFlow(today)
    private val message = MutableStateFlow<TodayMessage?>(null)
    private val settingsFlow = settings?.settings ?: flowOf(AppSettings())

    private val activity = combine(date, settingsFlow) { d, s -> d to s.healthConnect }.flatMapLatest { (d, on) ->
        flowOf(if (on) runCatching { health.activity(d) }.getOrNull() else null)
    }

    private val yesterday = date.flatMapLatest { d -> log.observeDay(d.minusDays(1)) }
        .map { items -> items.groupBy { it.meal }.mapValues { (_, l) -> l.sumOf { it.nutrients.calories } } }

    private data class Day(val d: LocalDate, val summary: DaySummary, val water: Int, val noProfile: Boolean)

    private val day = combine(
        date,
        profiles.profile,
        date.flatMapLatest { log.observeDay(it) },
        date.flatMapLatest { log.observeWater(it) },
    ) { d, profile, items, water ->
        val goal = profile?.let(GoalCalculator::calculate) ?: DEFAULT_GOAL
        Day(d, DaySummary.of(d, items, goal), water, profile == null)
    }

    val state: StateFlow<TodayUiState> = combine(day, activity, yesterday, message) { day, act: DayActivity?, y, msg ->
        TodayUiState(
            date = day.d, isToday = day.d == today, summary = day.summary, waterGlasses = day.water,
            needsProfile = day.noProfile, loading = false,
            burnedCalories = act?.activeCalories?.roundToInt() ?: 0, steps = act?.steps,
            yesterdayMeals = y, message = msg,
        )
    }.stateIn(
        viewModelScope, SharingStarted.Eagerly,
        TodayUiState(today, true, DaySummary.of(today, emptyList(), DEFAULT_GOAL)),
    )

    fun previousDay() { date.value = date.value.minusDays(1) }

    fun nextDay() {
        if (date.value.isBefore(today)) date.value = date.value.plusDays(1)
    }

    fun goToToday() { date.value = today }

    fun addWater() = changeWater(+1)
    fun removeWater() = changeWater(-1)

    private fun changeWater(delta: Int) {
        val d = date.value
        viewModelScope.launch {
            val current = log.observeWater(d).first()
            log.setWater(d, (current + delta).coerceAtLeast(0))
        }
    }

    /** Copies one meal (or the whole day when [meal] is null) from the day before into the shown day. */
    fun copyFromYesterday(meal: MealType?) {
        val d = date.value
        val now = clock.millis()
        viewModelScope.launch {
            val items = log.observeDay(d.minusDays(1)).first().filter { meal == null || it.meal == meal }
            if (items.isEmpty()) return@launch
            log.add(items.map { it.copy(id = 0, date = d, loggedAtMillis = now) })
            message.value = if (meal == null) TodayMessage.CopiedDay else TodayMessage.CopiedMeal(meal)
        }
    }

    fun messageShown() { message.value = null }

    companion object {
        val DEFAULT_GOAL = DailyGoal(calories = 2000, protein = 100, carbs = 250, fat = 67, fiber = 28)
    }
}
