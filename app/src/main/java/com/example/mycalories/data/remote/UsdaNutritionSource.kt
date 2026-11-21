package com.example.mycalories.data.remote

import com.example.mycalories.domain.model.Nutrients
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Looks up plain foods in USDA FoodData Central. Values are per 100 g. */
class UsdaNutritionSource(
    private val client: HttpClient,
    private val apiKey: String,
) : OnlineNutritionSource {

    @Serializable
    private data class SearchResponse(val foods: List<FoodDto> = emptyList())

    @Serializable
    private data class FoodDto(val description: String = "", val foodNutrients: List<NutrientDto> = emptyList())

    @Serializable
    private data class NutrientDto(val nutrientId: Int = 0, val value: Double = 0.0, val unitName: String = "")

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun lookup(term: String): Nutrients? {
        if (term.isBlank()) return null
        return try {
            val response = client.get(URL) {
                parameter("query", term.trim())
                parameter("pageSize", 1)
                parameter("dataType", "Foundation,SR Legacy")
                parameter("api_key", apiKey)
            }
            if (!response.status.isSuccess()) return null
            val food = json.decodeFromString(SearchResponse.serializer(), response.bodyAsText()).foods.firstOrNull()
                ?: return null
            toNutrients(food.foodNutrients)
        } catch (e: Exception) {
            null
        }
    }

    private fun toNutrients(list: List<NutrientDto>): Nutrients {
        fun v(id: Int) = list.firstOrNull { it.nutrientId == id }?.value
        fun kcal(id: Int) = list.firstOrNull { it.nutrientId == id && it.unitName.equals("KCAL", ignoreCase = true) }?.value

        val protein = v(PROTEIN) ?: 0.0
        val carbs = v(CARBS) ?: 0.0
        val fat = v(FAT) ?: 0.0
        val energy = kcal(ENERGY) ?: kcal(ENERGY_ATWATER_GENERAL) ?: kcal(ENERGY_ATWATER_SPECIFIC)
            ?: (protein * 4 + carbs * 4 + fat * 9)
        return Nutrients(
            calories = energy, protein = protein, carbs = carbs, fat = fat,
            fiber = v(FIBER) ?: 0.0, sugar = v(SUGAR) ?: 0.0, sodiumMg = v(SODIUM) ?: 0.0,
            ironMg = v(IRON) ?: 0.0, calciumMg = v(CALCIUM) ?: 0.0, vitaminB12Ug = v(B12) ?: 0.0,
            vitaminDUg = v(VITAMIN_D) ?: 0.0, vitaminCMg = v(VITAMIN_C) ?: 0.0, potassiumMg = v(POTASSIUM) ?: 0.0,
        )
    }

    companion object {
        const val URL = "https://api.nal.usda.gov/fdc/v1/foods/search"
        private const val ENERGY = 1008
        private const val ENERGY_ATWATER_GENERAL = 2047
        private const val ENERGY_ATWATER_SPECIFIC = 2048
        private const val PROTEIN = 1003
        private const val FAT = 1004
        private const val CARBS = 1005
        private const val FIBER = 1079
        private const val SUGAR = 2000
        private const val SODIUM = 1093
        private const val IRON = 1089
        private const val CALCIUM = 1087
        private const val B12 = 1178
        private const val VITAMIN_D = 1114
        private const val VITAMIN_C = 1162
        private const val POTASSIUM = 1092
    }
}
