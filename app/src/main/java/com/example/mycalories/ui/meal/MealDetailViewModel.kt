package com.example.mycalories.ui.meal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mycalories.domain.PortionCalculator
import com.example.mycalories.domain.PortionLearner
import com.example.mycalories.domain.repository.PortionRepository
import com.example.mycalories.domain.model.LoggedItem
import com.example.mycalories.domain.model.MealType
import com.example.mycalories.domain.model.Nutrients
import com.example.mycalories.domain.model.sum
import com.example.mycalories.domain.repository.LogRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

sealed interface MealMessage {
    data class Removed(val name: String) : MealMessage
    data class Moved(val meal: MealType) : MealMessage
    data object CopiedTomorrow : MealMessage
    data class LearnedPortion(val name: String, val unit: String, val gramsPerUnit: Double) : MealMessage
}

data class MealDetailUiState(
    val date: LocalDate,
    val meal: MealType,
    val items: List<LoggedItem> = emptyList(),
    val message: MealMessage? = null,
    val canUndo: Boolean = false,
) {
    val total: Nutrients get() = items.map { it.nutrients }.sum()
}

class MealDetailViewModel(
    private val log: LogRepository,
    private val date: LocalDate,
    private val meal: MealType,
    private val clock: Clock,
    private val portions: PortionRepository? = null,
) : ViewModel() {

    private val message = MutableStateFlow<MealMessage?>(null)
    private val lastDeleted = MutableStateFlow<LoggedItem?>(null)

    val state: StateFlow<MealDetailUiState> = combine(log.observeDay(date), message, lastDeleted) { items, msg, deleted ->
        MealDetailUiState(date, meal, items.filter { it.meal == meal }, msg, deleted != null)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, MealDetailUiState(date, meal))

    fun delete(id: Long) {
        val item = state.value.items.firstOrNull { it.id == id } ?: return
        viewModelScope.launch {
            log.delete(id)
            lastDeleted.value = item
            message.value = MealMessage.Removed(item.name)
        }
    }

    fun undoDelete() {
        val item = lastDeleted.value ?: return
        viewModelScope.launch {
            log.add(listOf(item.copy(id = 0)))
            lastDeleted.value = null
            message.value = null
        }
    }

    fun update(item: LoggedItem, quantity: Double, unit: String) {
        val updated = PortionCalculator.withPortion(item, quantity, unit)
        viewModelScope.launch {
            log.update(updated)
            if (portions != null && PortionLearner.worthLearning(updated.unit, updated.quantity, updated.grams)) {
                val pref = portions.record(updated.name, updated.unit, updated.grams / updated.quantity)
                if (PortionLearner.justLearned(pref)) {
                    lastDeleted.value = null
                    message.value = MealMessage.LearnedPortion(pref.foodName, pref.unit, pref.gramsPerUnit)
                }
            }
        }
    }

    fun move(id: Long, to: MealType) {
        viewModelScope.launch { log.move(id, to) }
    }

    fun moveAll(to: MealType) {
        val ids = state.value.items.map { it.id }
        viewModelScope.launch {
            ids.forEach { log.move(it, to) }
            message.value = MealMessage.Moved(to)
        }
    }

    fun repeatTomorrow() {
        val items = state.value.items
        if (items.isEmpty()) return
        val now = clock.millis()
        viewModelScope.launch {
            log.add(items.map { it.copy(id = 0, date = date.plusDays(1), loggedAtMillis = now) })
            message.value = MealMessage.CopiedTomorrow
        }
    }

    fun messageShown() {
        message.value = null
        lastDeleted.value = null
    }
}
