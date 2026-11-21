package com.example.mycalories.data.remote

import com.example.mycalories.domain.model.MealType
import com.example.mycalories.domain.model.Nutrients

/** Turns a sentence like "two roti and a bowl of dal for lunch" into foods and amounts. */
interface FoodParser {
    /** @throws FoodParserException with a message that can be shown to the user. */
    suspend fun parse(text: String): ParsedMeal

    /** Foods seen in a photo of a plate. */
    suspend fun parseImage(image: ImageInput): ParsedMeal

    /** A recipe described by voice: ingredients and how many servings it makes. */
    suspend fun parseRecipe(text: String): ParsedRecipe
}

data class ParsedRecipe(val name: String, val servings: Int, val servingUnit: String, val ingredients: List<ParsedFood>)

/** A packaged food found by barcode. Nutrients are per 100 g. */
data class PackagedFood(val name: String, val brand: String?, val per100g: Nutrients, val servingGrams: Double?)

interface PackagedFoodSource {
    suspend fun lookup(barcode: String): PackagedFood?
}

data class ParsedMeal(val meal: MealType?, val items: List<ParsedFood>)

data class ParsedFood(
    val name: String,
    val quantity: Double,
    val unit: String,
    val grams: Double,
    /** A USDA search phrase for plain foods; null for mixed dishes. */
    val searchTerm: String?,
    /** The AI's own estimate, used when nothing better is found. */
    val estimatePer100g: Nutrients,
    /** True when the user gave no amount and it was guessed. */
    val amountGuessed: Boolean,
)

/** Why something failed, as a code; screens turn it into words in the user's language. */
enum class AppError {
    NO_SPEECH, NO_FOOD, NETWORK, REFUSED, TOO_LONG, BAD_REPLY, NOT_CONFIGURED,
    MIC_PERMISSION, SPEECH_UNAVAILABLE, BARCODE_NOT_FOUND, INVALID_BACKUP, BAD_KEY, NO_CREDIT, FREE_LIMIT, MODEL_UNAVAILABLE, BUSY, GENERIC,
}

class FoodParserException(val error: AppError, cause: Throwable? = null) : Exception(error.name, cause)

/** Looks a plain food up online. Returns nutrients per 100 g, or null if not found. */
interface OnlineNutritionSource {
    suspend fun lookup(term: String): Nutrients?
}
