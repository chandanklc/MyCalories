package com.example.mycalories.ui.recipe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.mycalories.data.remote.AppError
import com.example.mycalories.data.remote.FoodParser
import com.example.mycalories.data.remote.FoodParserException
import com.example.mycalories.data.remote.ParsedMeal
import com.example.mycalories.domain.FoodResolver
import com.example.mycalories.domain.RecipeCalculator
import com.example.mycalories.domain.RecipeResult
import com.example.mycalories.domain.model.Food
import com.example.mycalories.domain.model.FoodSource
import com.example.mycalories.domain.model.LoggedItem
import com.example.mycalories.domain.model.MealType
import com.example.mycalories.domain.repository.FoodRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate

enum class RecipeStage { Idle, Processing, Ready, Saved, Error }

data class RecipeUiState(
    val stage: RecipeStage = RecipeStage.Idle,
    val transcript: String = "",
    val name: String = "",
    val servings: Int = 4,
    val servingUnit: String = "bowl",
    val ingredients: List<LoggedItem> = emptyList(),
    val error: AppError? = null,
) {
    val result: RecipeResult?
        get() = if (ingredients.isEmpty()) null else RecipeCalculator.calculate(ingredients.map { it.grams to it.per100g }, servings)
}

/** Typed: "Mom's dal: one cup toor dal, two tbsp ghee… makes four bowls" → a saved food with per-bowl nutrients. */
class RecipeViewModel(
    private val parser: FoodParser,
    private val resolver: FoodResolver,
    private val foods: FoodRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(RecipeUiState())
    val state: StateFlow<RecipeUiState> = _state.asStateFlow()
    fun submitText(text: String) {
        _state.update { it.copy(stage = RecipeStage.Processing, transcript = text, error = null) }
        viewModelScope.launch {
            try {
                val recipe = parser.parseRecipe(text)
                if (recipe.ingredients.isEmpty()) return@launch fail(AppError.NO_FOOD)
                val items = resolver.resolve(ParsedMeal(null, recipe.ingredients), LocalDate.now(clock), MealType.SNACK)
                _state.update {
                    it.copy(stage = RecipeStage.Ready, name = recipe.name, servings = recipe.servings,
                        servingUnit = recipe.servingUnit, ingredients = items)
                }
            } catch (e: FoodParserException) {
                fail(e.error)
            } catch (e: Exception) {
                fail(AppError.GENERIC)
            }
        }
    }

    private fun fail(error: AppError) = _state.update { it.copy(stage = RecipeStage.Error, error = error) }

    fun setName(name: String) = _state.update { it.copy(name = name) }
    fun setServings(n: Int) = _state.update { it.copy(servings = n.coerceAtLeast(1)) }
    fun removeIngredient(index: Int) = _state.update { s -> s.copy(ingredients = s.ingredients.filterIndexed { i, _ -> i != index }) }

    fun save() {
        val s = _state.value
        val result = s.result ?: return
        viewModelScope.launch {
            foods.save(
                Food(
                    name = s.name.trim().ifBlank { "My recipe" },
                    per100g = result.per100g,
                    defaultUnit = s.servingUnit,
                    gramsPerUnit = result.gramsPerServing,
                    source = FoodSource.USER_RECIPE,
                ),
            )
            _state.update { it.copy(stage = RecipeStage.Saved) }
        }
    }

}
