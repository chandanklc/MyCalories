package com.example.mycalories.ui.logmeal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mycalories.data.remote.AppError
import com.example.mycalories.data.remote.FoodParser
import com.example.mycalories.data.remote.FoodParserException
import com.example.mycalories.data.remote.ImageInput
import com.example.mycalories.data.remote.PackagedFoodSource
import com.example.mycalories.data.remote.ParsedMeal
import com.example.mycalories.domain.FoodResolver
import com.example.mycalories.domain.MealTimeSuggester
import com.example.mycalories.domain.PortionCalculator
import com.example.mycalories.domain.PortionLearner
import com.example.mycalories.domain.TypedQuery
import com.example.mycalories.domain.model.Food
import com.example.mycalories.domain.model.FoodSource
import com.example.mycalories.domain.model.LoggedItem
import com.example.mycalories.domain.model.MealType
import com.example.mycalories.domain.model.Nutrients
import com.example.mycalories.domain.model.PortionPreference
import com.example.mycalories.domain.model.SavedMeal
import com.example.mycalories.domain.model.SavedMealItem
import com.example.mycalories.domain.model.sum
import com.example.mycalories.domain.repository.FoodRepository
import com.example.mycalories.domain.repository.LogRepository
import com.example.mycalories.domain.repository.PortionRepository
import com.example.mycalories.domain.repository.SavedMealRepository
import com.example.mycalories.domain.repository.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime

enum class Stage { Search, Processing, Review, Saved, Error }

/** A food from the user's library matching what they typed, with the amount they typed. */
data class FoodSuggestion(val food: Food, val quantity: Double) {
    val grams: Double get() = food.gramsPerUnit * quantity
    val calories: Double get() = food.per100g.calories * grams / 100.0
}

data class LogMealUiState(
    val stage: Stage = Stage.Search,
    val query: String = "",
    val suggestions: List<FoodSuggestion> = emptyList(),
    val items: List<LoggedItem> = emptyList(),
    val meal: MealType = MealType.LUNCH,
    /** What was last sent to the AI; kept as a note when the user allows it. */
    val transcript: String = "",
    val error: AppError? = null,
    val saveAsFavorite: Boolean = false,
    val favoriteName: String = "",
    /** Set once when a portion size has just been learned, so the screen can say so. */
    val learned: PortionPreference? = null,
) {
    val total: Nutrients get() = items.map { it.nutrients }.sum()
}

/**
 * Drives the type → pick or find → review → save flow. Typing searches the user's own foods as they type
 * (offline); "Find calories" sends the whole sentence to the AI. The meal is picked up front by the clock.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LogMealViewModel(
    private val parser: FoodParser,
    private val resolver: FoodResolver,
    private val foods: FoodRepository,
    private val log: LogRepository,
    private val savedMeals: SavedMealRepository,
    private val clock: Clock,
    private val date: LocalDate,
    private val packaged: PackagedFoodSource,
    private val settings: SettingsRepository,
    private val portions: PortionRepository,
    initialMeal: MealType? = null,
) : ViewModel() {

    private val startMeal = initialMeal ?: MealTimeSuggester.suggest(LocalTime.now(clock))
    private val _state = MutableStateFlow(LogMealUiState(meal = startMeal))
    val state: StateFlow<LogMealUiState> = _state.asStateFlow()

    private val query = MutableStateFlow("")

    init {
        viewModelScope.launch {
            query.map { TypedQuery.parse(it) }
                .flatMapLatest { q ->
                    foods.search(q.term).map { list ->
                        val shown = if (q.term.isBlank()) list.take(RECENT_COUNT) else list.take(MATCH_COUNT)
                        shown.map { FoodSuggestion(it, q.quantity) }
                    }
                }
                .collect { list -> _state.update { it.copy(suggestions = list) } }
        }
    }

    fun setQuery(text: String) {
        _state.update { it.copy(query = text) }
        query.value = text
    }

    /** Adds a food from the search list, using a portion size the user has taught the app when there is one. */
    fun addSuggestion(s: FoodSuggestion) {
        viewModelScope.launch {
            val food = s.food
            val learned = portions.find(food.name, food.defaultUnit)?.takeIf { PortionLearner.isLearned(it) }
            val grams = s.quantity * (learned?.gramsPerUnit ?: food.gramsPerUnit)
            val item = LoggedItem(
                date = date, meal = _state.value.meal, name = food.name, quantity = s.quantity, unit = food.defaultUnit,
                grams = grams, per100g = food.per100g, source = food.source, needsReview = false, loggedAtMillis = clock.millis(),
            )
            _state.update { it.copy(items = it.items + item) }
            setQuery("")
        }
    }

    /** Sends everything typed to the AI, e.g. "2 roti with dal and a bowl of curd". */
    fun findWithAi() {
        val text = _state.value.query.trim()
        if (text.isEmpty()) return
        setQuery("")
        run(text) { parser.parse(text) }
    }

    /** A photo of the plate goes through the same review. */
    fun submitPhoto(image: ImageInput) = run("") { parser.parseImage(image) }

    /** A scanned barcode: one serving from the package label. */
    fun submitBarcode(code: String) {
        _state.update { it.copy(stage = Stage.Processing, error = null) }
        viewModelScope.launch {
            val product = runCatching { packaged.lookup(code) }.getOrNull()
            if (product == null) {
                fail(AppError.BARCODE_NOT_FOUND)
                return@launch
            }
            val item = LoggedItem(
                date = date, meal = _state.value.meal,
                name = product.name,
                quantity = 1.0, unit = if (product.servingGrams != null) "serving" else "g",
                grams = product.servingGrams ?: 100.0, per100g = product.per100g,
                source = FoodSource.PACKAGED, needsReview = false, loggedAtMillis = clock.millis(),
            ).let { if (product.servingGrams == null) it.copy(quantity = 100.0) else it }
            show(listOf(item), null)
        }
    }

    private fun run(text: String, parse: suspend () -> ParsedMeal) {
        _state.update { it.copy(stage = Stage.Processing, transcript = text.ifEmpty { it.transcript }, error = null) }
        viewModelScope.launch {
            try {
                val parsed = parse()
                if (parsed.items.isEmpty()) {
                    fail(AppError.NO_FOOD)
                    return@launch
                }
                val meal = parsed.meal ?: _state.value.meal
                show(resolver.resolve(parsed, date, meal), parsed.meal)
            } catch (e: FoodParserException) {
                fail(e.error)
            } catch (e: Exception) {
                fail(AppError.GENERIC)
            }
        }
    }

    /** Adds found foods to the meal and opens the review. A meal named in the text moves everything to it. */
    private fun show(found: List<LoggedItem>, saidMeal: MealType?) {
        _state.update { s ->
            val meal = saidMeal ?: s.meal
            s.copy(items = (s.items + found).map { it.copy(meal = meal) }, meal = meal, stage = Stage.Review)
        }
    }

    private fun fail(error: AppError) {
        _state.update { it.copy(stage = Stage.Error, error = error) }
    }

    /** After an error, go back to the search with the text they typed, so they can fix it or try again. */
    fun retry() {
        val text = _state.value.transcript
        _state.update { it.copy(stage = Stage.Search, error = null) }
        setQuery(text)
    }

    fun review() {
        if (_state.value.items.isNotEmpty()) _state.update { it.copy(stage = Stage.Review) }
    }

    fun addMore() = _state.update { it.copy(stage = Stage.Search) }

    fun setMeal(meal: MealType) {
        _state.update { s -> s.copy(meal = meal, items = s.items.map { it.copy(meal = meal) }) }
    }

    fun updateItem(index: Int, quantity: Double, unit: String) {
        val item = _state.value.items.getOrNull(index) ?: return
        val updated = PortionCalculator.withPortion(item, quantity, unit)
        _state.update { s -> s.copy(items = s.items.mapIndexed { i, it -> if (i == index) updated else it }) }
        if (PortionLearner.worthLearning(updated.unit, updated.quantity, updated.grams)) {
            viewModelScope.launch {
                val pref = portions.record(updated.name, updated.unit, updated.grams / updated.quantity)
                if (PortionLearner.justLearned(pref)) _state.update { it.copy(learned = pref) }
            }
        }
    }

    fun learnedShown() = _state.update { it.copy(learned = null) }

    fun removeItem(index: Int) {
        _state.update { s -> s.copy(items = s.items.filterIndexed { i, _ -> i != index }) }
    }

    fun setSaveAsFavorite(enabled: Boolean) = _state.update { it.copy(saveAsFavorite = enabled) }
    fun setFavoriteName(name: String) = _state.update { it.copy(favoriteName = name) }

    fun save() {
        val s = _state.value
        if (s.items.isEmpty()) return
        viewModelScope.launch {
            val now = clock.millis()
            val note = s.transcript.trim().takeIf { it.isNotEmpty() && settings.settings.first().keepTranscript }
            log.add(s.items.map { it.copy(id = 0, date = date, meal = s.meal, loggedAtMillis = now, note = note) })
            if (s.saveAsFavorite) {
                savedMeals.save(
                    SavedMeal(
                        name = s.favoriteName.trim().ifBlank { "My ${s.meal.label.lowercase()}" },
                        items = s.items.map { SavedMealItem(it.name, it.quantity, it.unit, it.grams, it.per100g, it.source) },
                    ),
                )
            }
            _state.update { it.copy(stage = Stage.Saved) }
        }
    }

    /** Starts over: nothing picked, same meal. */
    fun reset() {
        _state.update { LogMealUiState(meal = it.meal, suggestions = it.suggestions) }
        setQuery("")
    }

    private companion object {
        const val RECENT_COUNT = 8
        const val MATCH_COUNT = 20
    }
}
