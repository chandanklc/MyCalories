package com.example.mycalories.ui.logmeal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mycalories.data.remote.AppError
import com.example.mycalories.data.remote.FoodParser
import com.example.mycalories.data.remote.FoodParserException
import com.example.mycalories.data.remote.ImageInput
import com.example.mycalories.data.remote.PackagedFoodSource
import com.example.mycalories.data.remote.ParsedMeal
import com.example.mycalories.domain.PortionLearner
import com.example.mycalories.domain.model.FoodSource
import com.example.mycalories.domain.model.PortionPreference
import com.example.mycalories.domain.repository.PortionRepository
import com.example.mycalories.domain.repository.SettingsRepository
import com.example.mycalories.domain.FoodResolver
import com.example.mycalories.domain.MealTimeSuggester
import com.example.mycalories.domain.PortionCalculator
import com.example.mycalories.domain.model.LoggedItem
import com.example.mycalories.domain.model.MealType
import com.example.mycalories.domain.model.Nutrients
import com.example.mycalories.domain.model.SavedMeal
import com.example.mycalories.domain.model.SavedMealItem
import com.example.mycalories.domain.model.sum
import com.example.mycalories.domain.repository.LogRepository
import com.example.mycalories.domain.repository.ProfileRepository
import com.example.mycalories.domain.repository.SavedMealRepository
import com.example.mycalories.voice.VoiceEvent
import com.example.mycalories.voice.VoiceInput
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime

enum class Stage { Idle, Listening, Processing, ChooseMeal, Review, Saved, Error }

data class LogMealUiState(
    val stage: Stage = Stage.Idle,
    val transcript: String = "",
    val items: List<LoggedItem> = emptyList(),
    val meal: MealType? = null,
    val suggestedMeal: MealType = MealType.LUNCH,
    val error: AppError? = null,
    val saveAsFavorite: Boolean = false,
    val favoriteName: String = "",
    /** Set once when a portion size has just been learned, so the screen can say so. */
    val learned: PortionPreference? = null,
) {
    val total: Nutrients get() = items.map { it.nutrients }.sum()
}

/** Drives the speak → pick meal → review → save flow. */
class LogMealViewModel(
    private val voice: VoiceInput,
    private val parser: FoodParser,
    private val resolver: FoodResolver,
    private val log: LogRepository,
    private val profiles: ProfileRepository,
    private val savedMeals: SavedMealRepository,
    private val clock: Clock,
    private val date: LocalDate,
    private val packaged: PackagedFoodSource,
    private val settings: SettingsRepository,
    private val portions: PortionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LogMealUiState(suggestedMeal = suggestion()))
    val state: StateFlow<LogMealUiState> = _state.asStateFlow()

    private var listenJob: Job? = null
    /** True when the user is adding more food to a meal already on the review screen. */
    private var appending = false

    private fun suggestion() = MealTimeSuggester.suggest(LocalTime.now(clock))

    fun startListening() {
        appending = _state.value.stage == Stage.Review && _state.value.items.isNotEmpty()
        listenJob?.cancel()
        _state.update { it.copy(stage = Stage.Listening, transcript = "", error = null) }
        listenJob = viewModelScope.launch {
            voice.listen().collect { event ->
                when (event) {
                    VoiceEvent.Ready -> Unit
                    is VoiceEvent.Partial -> _state.update { it.copy(transcript = event.text) }
                    is VoiceEvent.Final ->
                        if (event.text.isBlank()) fail(AppError.NO_SPEECH)
                        else process(event.text)
                    is VoiceEvent.Error -> fail(event.error)
                }
            }
        }
    }

    fun stopListening() = voice.stop()

    /** Typed input, or "say more" by keyboard. */
    fun submitText(text: String) {
        appending = _state.value.stage == Stage.Review && _state.value.items.isNotEmpty()
        process(text)
    }

    private fun process(text: String) = run(text) { parser.parse(text) }

    /** A photo of the plate goes through the same pick-meal and review steps. */
    fun submitPhoto(image: ImageInput) {
        appending = _state.value.stage == Stage.Review && _state.value.items.isNotEmpty()
        run("") { parser.parseImage(image) }
    }

    /** A scanned barcode: one serving from the package label. */
    fun submitBarcode(code: String) {
        appending = _state.value.stage == Stage.Review && _state.value.items.isNotEmpty()
        val append = appending
        _state.update { it.copy(stage = Stage.Processing, transcript = "", error = null) }
        viewModelScope.launch {
            val product = runCatching { packaged.lookup(code) }.getOrNull()
            if (product == null) {
                fail(AppError.BARCODE_NOT_FOUND)
                return@launch
            }
            val suggested = suggestion()
            val meal = mealFor(null, append, suggested)
            val item = LoggedItem(
                date = date, meal = meal ?: suggested,
                name = product.name,
                quantity = 1.0, unit = if (product.servingGrams != null) "serving" else "g",
                grams = product.servingGrams ?: 100.0, per100g = product.per100g,
                source = FoodSource.PACKAGED, needsReview = false, loggedAtMillis = clock.millis(),
            ).let { if (product.servingGrams == null) it.copy(quantity = 100.0) else it }
            show(listOf(item), meal, suggested, append)
        }
    }

    private fun run(text: String, parse: suspend () -> ParsedMeal) {
        val append = appending
        _state.update { it.copy(stage = Stage.Processing, transcript = text, error = null) }
        viewModelScope.launch {
            try {
                val parsed = parse()
                if (parsed.items.isEmpty()) {
                    fail(AppError.NO_FOOD)
                    return@launch
                }
                val suggested = suggestion()
                val meal = mealFor(parsed.meal, append, suggested)
                show(resolver.resolve(parsed, date, meal ?: suggested), meal, suggested, append)
            } catch (e: FoodParserException) {
                fail(e.error)
            } catch (e: Exception) {
                fail(AppError.GENERIC)
            }
        }
    }

    private suspend fun mealFor(said: MealType?, append: Boolean, suggested: MealType): MealType? = when {
        append -> _state.value.meal
        said != null -> said
        profiles.autoPickMeal.first() -> suggested
        else -> null
    }

    private fun show(resolved: List<LoggedItem>, meal: MealType?, suggested: MealType, append: Boolean) {
        _state.update {
            it.copy(
                items = if (append) it.items + resolved else resolved,
                meal = meal,
                suggestedMeal = suggested,
                stage = if (meal == null) Stage.ChooseMeal else Stage.Review,
            )
        }
    }

    private fun fail(error: AppError) {
        _state.update { it.copy(stage = Stage.Error, error = error) }
    }

    fun chooseMeal(meal: MealType, remember: Boolean) {
        _state.update { s -> s.copy(meal = meal, items = s.items.map { it.copy(meal = meal) }, stage = Stage.Review) }
        if (remember) viewModelScope.launch { profiles.setAutoPickMeal(true) }
    }

    fun changeMeal(meal: MealType) {
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
        val meal = s.meal ?: return
        if (s.items.isEmpty()) return
        viewModelScope.launch {
            val now = clock.millis()
            val note = s.transcript.trim().takeIf { it.isNotEmpty() && settings.settings.first().keepTranscript }
            log.add(s.items.map { it.copy(id = 0, date = date, meal = meal, loggedAtMillis = now, note = note) })
            if (s.saveAsFavorite) {
                savedMeals.save(
                    SavedMeal(
                        name = s.favoriteName.trim().ifBlank { "My ${meal.label.lowercase()}" },
                        items = s.items.map { SavedMealItem(it.name, it.quantity, it.unit, it.grams, it.per100g, it.source) },
                    ),
                )
            }
            _state.update { it.copy(stage = Stage.Saved) }
        }
    }

    fun reset() {
        listenJob?.cancel()
        appending = false
        _state.value = LogMealUiState(suggestedMeal = suggestion())
    }

    override fun onCleared() {
        voice.stop()
    }
}
