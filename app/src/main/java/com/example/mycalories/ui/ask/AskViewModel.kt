package com.example.mycalories.ui.ask

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mycalories.data.remote.AppError
import com.example.mycalories.data.remote.ChatMessage
import com.example.mycalories.data.remote.AiAssistant
import com.example.mycalories.data.remote.FoodParserException
import com.example.mycalories.domain.DaySummary
import com.example.mycalories.domain.GoalCalculator
import com.example.mycalories.domain.model.LoggedItem
import com.example.mycalories.domain.repository.LogRepository
import com.example.mycalories.domain.repository.ProfileRepository
import com.example.mycalories.ui.today.TodayViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import kotlin.math.roundToInt

data class AskUiState(
    val messages: List<ChatMessage> = emptyList(),
    val sending: Boolean = false,
    val error: AppError? = null,
)

object AskContext {
    /** A short plain-text summary of today and the last week, sent with each question. */
    fun build(summary: DaySummary, week: List<LoggedItem>): String = buildString {
        val g = summary.goal
        val e = summary.eaten
        appendLine("Today so far: ${e.calories.roundToInt()} kcal eaten of ${g.calories} goal, ${summary.remainingCalories} kcal left; " +
            "protein ${e.protein.roundToInt()} of ${g.protein} g; carbs ${e.carbs.roundToInt()} of ${g.carbs} g; " +
            "fat ${e.fat.roundToInt()} of ${g.fat} g; fiber ${e.fiber.roundToInt()} of ${g.fiber} g.")
        summary.meals.forEach { m ->
            appendLine("${m.meal.label}: " + if (m.items.isEmpty()) "not logged" else m.items.joinToString { "${it.name} (${it.nutrients.calories.roundToInt()} kcal)" })
        }
        val days = week.groupBy { it.date }
        if (days.isNotEmpty()) {
            appendLine("Last 7 days: average ${days.values.map { l -> l.sumOf { it.nutrients.calories } }.average().roundToInt()} kcal and " +
                "${days.values.map { l -> l.sumOf { it.nutrients.protein } }.average().roundToInt()} g protein a day.")
            val top = week.groupingBy { it.name }.eachCount().entries.sortedByDescending { it.value }.take(8).joinToString { it.key }
            appendLine("Foods they often eat: $top.")
        }
    }
}

class AskViewModel(
    private val assistant: AiAssistant,
    private val log: LogRepository,
    private val profiles: ProfileRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(AskUiState())
    val state: StateFlow<AskUiState> = _state.asStateFlow()

    fun send(text: String) {
        val q = text.trim()
        if (q.isEmpty() || _state.value.sending) return
        val conversation = _state.value.messages + ChatMessage.user(q)
        _state.update { it.copy(messages = conversation, sending = true, error = null) }
        viewModelScope.launch {
            try {
                val today = LocalDate.now(clock)
                val goal = profiles.profile.first()?.let(GoalCalculator::calculate) ?: TodayViewModel.DEFAULT_GOAL
                val summary = DaySummary.of(today, log.observeDay(today).first(), goal)
                val week = log.observeRange(today.minusDays(7), today.minusDays(1)).first()
                val answer = assistant.ask(AskContext.build(summary, week), conversation)
                _state.update { it.copy(messages = it.messages + ChatMessage.assistant(answer), sending = false) }
            } catch (e: FoodParserException) {
                _state.update { it.copy(sending = false, error = e.error) }
            } catch (e: Exception) {
                _state.update { it.copy(sending = false, error = AppError.GENERIC) }
            }
        }
    }

    fun errorShown() = _state.update { it.copy(error = null) }
}
