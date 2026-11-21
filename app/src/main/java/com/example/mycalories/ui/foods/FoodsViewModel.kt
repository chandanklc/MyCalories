package com.example.mycalories.ui.foods

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mycalories.domain.MealTimeSuggester
import com.example.mycalories.domain.model.Food
import com.example.mycalories.domain.model.LoggedItem
import com.example.mycalories.domain.model.MealType
import com.example.mycalories.domain.model.SavedMeal
import com.example.mycalories.domain.repository.FoodRepository
import com.example.mycalories.domain.repository.LogRepository
import com.example.mycalories.domain.repository.SavedMealRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime

sealed interface FoodsMessage {
    data class Added(val name: String, val meal: MealType) : FoodsMessage
}

data class FoodsUiState(
    val query: String = "",
    val foods: List<Food> = emptyList(),
    val savedMeals: List<SavedMeal> = emptyList(),
    val message: FoodsMessage? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class FoodsViewModel(
    foods: FoodRepository,
    private val saved: SavedMealRepository,
    private val log: LogRepository,
    private val clock: Clock,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val message = MutableStateFlow<FoodsMessage?>(null)

    val state: StateFlow<FoodsUiState> = combine(
        query, query.flatMapLatest { foods.search(it.trim()) }, saved.observeAll(), message,
    ) { q, list, meals, msg -> FoodsUiState(q, list, meals, msg) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, FoodsUiState())

    fun onQuery(text: String) { query.value = text }

    /** Logs every item of a saved meal to today, in the meal that fits the time of day. */
    fun logSavedMeal(meal: SavedMeal) {
        val mealType = MealTimeSuggester.suggest(LocalTime.now(clock))
        val now = clock.millis()
        val today = LocalDate.now(clock)
        viewModelScope.launch {
            log.add(meal.items.map {
                LoggedItem(0, today, mealType, it.name, it.quantity, it.unit, it.grams, it.per100g, it.source, false, now)
            })
            message.value = FoodsMessage.Added(meal.name, mealType)
        }
    }

    /** Logs one default serving of a food. */
    fun logFood(food: Food, meal: MealType) {
        val now = clock.millis()
        viewModelScope.launch {
            log.add(listOf(LoggedItem(0, LocalDate.now(clock), meal, food.name, 1.0, food.defaultUnit, food.gramsPerUnit,
                food.per100g, food.source, false, now)))
            message.value = FoodsMessage.Added(food.name, meal)
        }
    }

    fun deleteSavedMeal(id: Long) {
        viewModelScope.launch { saved.delete(id) }
    }

    fun messageShown() { message.value = null }
}
