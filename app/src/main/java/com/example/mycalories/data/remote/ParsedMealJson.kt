package com.example.mycalories.data.remote

import com.example.mycalories.domain.PortionCalculator
import com.example.mycalories.domain.model.MealType
import com.example.mycalories.domain.model.Nutrients
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The JSON shape the AI returns, and how it maps to [ParsedMeal]. */
object ParsedMealJson {

    @Serializable
    private data class MealDto(val meal: String? = null, val items: List<ItemDto> = emptyList())

    @Serializable
    internal data class ItemDto(
        val name: String,
        val quantity: Double = 1.0,
        val unit: String = "piece",
        val grams: Double = 0.0,
        @SerialName("amount_guessed") val amountGuessed: Boolean = false,
        @SerialName("usda_search_term") val usdaSearchTerm: String? = null,
        @SerialName("per_100g") val per100g: NutrientsDto = NutrientsDto(),
    )

    @Serializable
    internal data class NutrientsDto(
        val calories: Double = 0.0,
        val protein: Double = 0.0,
        val carbs: Double = 0.0,
        val fat: Double = 0.0,
        val fiber: Double = 0.0,
        val sugar: Double = 0.0,
        @SerialName("sodium_mg") val sodiumMg: Double = 0.0,
        @SerialName("iron_mg") val ironMg: Double = 0.0,
        @SerialName("calcium_mg") val calciumMg: Double = 0.0,
        @SerialName("vitamin_b12_ug") val vitaminB12Ug: Double = 0.0,
        @SerialName("vitamin_d_ug") val vitaminDUg: Double = 0.0,
        @SerialName("vitamin_c_mg") val vitaminCMg: Double = 0.0,
        @SerialName("potassium_mg") val potassiumMg: Double = 0.0,
    ) {
        fun toNutrients() = Nutrients(
            calories = calories.coerceAtLeast(0.0),
            protein = protein.coerceAtLeast(0.0),
            carbs = carbs.coerceAtLeast(0.0),
            fat = fat.coerceAtLeast(0.0),
            fiber = fiber.coerceAtLeast(0.0),
            sugar = sugar.coerceAtLeast(0.0),
            sodiumMg = sodiumMg.coerceAtLeast(0.0),
            ironMg = ironMg.coerceAtLeast(0.0),
            calciumMg = calciumMg.coerceAtLeast(0.0),
            vitaminB12Ug = vitaminB12Ug.coerceAtLeast(0.0),
            vitaminDUg = vitaminDUg.coerceAtLeast(0.0),
            vitaminCMg = vitaminCMg.coerceAtLeast(0.0),
            potassiumMg = potassiumMg.coerceAtLeast(0.0),
        )
    }

    internal val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun decode(text: String): ParsedMeal {
        val dto = try {
            json.decodeFromString(MealDto.serializer(), text)
        } catch (e: Exception) {
            throw FoodParserException(AppError.BAD_REPLY, e)
        }
        val items = dto.items.filter { it.name.isNotBlank() }.map { it.toParsed() }
        return ParsedMeal(MealType.fromString(dto.meal), items)
    }

    internal fun ItemDto.toParsed(): ParsedFood {
        val q = quantity.coerceAtLeast(0.0)
        val g = if (grams > 0) grams else q * PortionCalculator.gramsPerUnit(unit, fallback = 100.0)
        return ParsedFood(
            name = name.trim(),
            quantity = q,
            unit = unit.trim().ifBlank { "piece" },
            grams = g,
            searchTerm = usdaSearchTerm?.trim()?.ifBlank { null },
            estimatePer100g = per100g.toNutrients(),
            amountGuessed = amountGuessed,
        )
    }

    private fun num() = mapOf("type" to "number")

    val nutrientKeys = listOf(
        "calories", "protein", "carbs", "fat", "fiber", "sugar", "sodium_mg",
        "iron_mg", "calcium_mg", "vitamin_b12_ug", "vitamin_d_ug", "vitamin_c_mg", "potassium_mg",
    )

    val nutrientSchema: Map<String, Any> = mapOf(
        "type" to "object",
        "properties" to nutrientKeys.associateWith { num() },
        "required" to nutrientKeys,
        "additionalProperties" to false,
    )

    /** One food: name, amount and nutrients per 100 g. */
    val itemSchema: Map<String, Any> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "name" to mapOf("type" to "string"),
            "quantity" to num(),
            "unit" to mapOf("type" to "string"),
            "grams" to num(),
            "amount_guessed" to mapOf("type" to "boolean"),
            "usda_search_term" to mapOf("type" to listOf("string", "null")),
            "per_100g" to nutrientSchema,
        ),
        "required" to listOf("name", "quantity", "unit", "grams", "amount_guessed", "usda_search_term", "per_100g"),
        "additionalProperties" to false,
    )

    /** JSON Schema sent to the AI so its reply always has this exact shape. */
    val schema: Map<String, Any> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "meal" to mapOf("type" to listOf("string", "null"), "description" to "breakfast, lunch, snack or dinner; null if not said"),
            "items" to mapOf("type" to "array", "items" to itemSchema),
        ),
        "required" to listOf("meal", "items"),
        "additionalProperties" to false,
    )
}
