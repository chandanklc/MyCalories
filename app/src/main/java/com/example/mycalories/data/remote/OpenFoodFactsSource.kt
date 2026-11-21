package com.example.mycalories.data.remote

import com.example.mycalories.domain.model.Nutrients
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Looks up packaged food by barcode in Open Food Facts (free, open data). */
class OpenFoodFactsSource(private val client: HttpClient) : PackagedFoodSource {

    @Serializable private data class Response(val status: Int = 0, val product: Product? = null)

    @Serializable private data class Product(
        @SerialName("product_name") val name: String? = null,
        val brands: String? = null,
        @SerialName("serving_quantity") val servingQuantity: Double? = null,
        val nutriments: Map<String, kotlinx.serialization.json.JsonElement> = emptyMap(),
    )

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override suspend fun lookup(barcode: String): PackagedFood? {
        val code = barcode.trim()
        if (code.isEmpty() || !code.all(Char::isDigit)) return null
        return try {
            val response = client.get("$URL/$code.json") {
                parameter("fields", "product_name,brands,serving_quantity,nutriments")
                header(HttpHeaders.UserAgent, USER_AGENT)
            }
            if (!response.status.isSuccess()) return null
            val r = json.decodeFromString(Response.serializer(), response.bodyAsText())
            val p = r.product ?: return null
            if (r.status != 1) return null
            fun n(key: String): Double? = (p.nutriments[key] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toDoubleOrNull()
            val kcal = n("energy-kcal_100g") ?: n("energy_100g")?.let { it / 4.184 } ?: 0.0
            PackagedFood(
                name = p.name?.trim().orEmpty().ifBlank { "Packaged food" },
                brand = p.brands?.split(',')?.firstOrNull()?.trim()?.ifBlank { null },
                servingGrams = p.servingQuantity?.takeIf { it > 0 },
                per100g = Nutrients(
                    calories = kcal,
                    protein = n("proteins_100g") ?: 0.0,
                    carbs = n("carbohydrates_100g") ?: 0.0,
                    fat = n("fat_100g") ?: 0.0,
                    fiber = n("fiber_100g") ?: 0.0,
                    sugar = n("sugars_100g") ?: 0.0,
                    sodiumMg = (n("sodium_100g") ?: 0.0) * 1000,
                    ironMg = (n("iron_100g") ?: 0.0) * 1000,
                    calciumMg = (n("calcium_100g") ?: 0.0) * 1000,
                    vitaminCMg = (n("vitamin-c_100g") ?: 0.0) * 1000,
                    potassiumMg = (n("potassium_100g") ?: 0.0) * 1000,
                    vitaminB12Ug = (n("vitamin-b12_100g") ?: 0.0) * 1_000_000,
                    vitaminDUg = (n("vitamin-d_100g") ?: 0.0) * 1_000_000,
                ),
            )
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        const val URL = "https://world.openfoodfacts.org/api/v2/product"
        /** Open Food Facts asks apps to identify themselves; no personal details are sent. */
        const val USER_AGENT = "MyCalories Android app"
    }
}
