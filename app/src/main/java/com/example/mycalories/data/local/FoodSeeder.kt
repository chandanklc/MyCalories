package com.example.mycalories.data.local

import com.example.mycalories.domain.model.FoodSource
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Fills an empty food library with common foods so the first logs work offline. */
class FoodSeeder(private val dao: FoodDao, private val readSeedJson: () -> String) {

    @Serializable
    private data class SeedFood(
        val name: String, val unit: String, val g: Double,
        val kcal: Double, val p: Double, val c: Double, val f: Double,
        val fib: Double = 0.0, val sug: Double = 0.0, val na: Double = 0.0,
        val fe: Double = 0.0, val ca: Double = 0.0, val b12: Double = 0.0, val vd: Double = 0.0, val vc: Double = 0.0, val k: Double = 0.0,
    )

    /** @return how many foods were added (0 if the library already had foods). */
    suspend fun seedIfEmpty(): Int {
        if (dao.count() > 0) return 0
        val foods = parse(readSeedJson())
        dao.insertAll(foods)
        return foods.size
    }

    fun parse(json: String): List<FoodEntity> =
        Json.decodeFromString(ListSerializer(SeedFood.serializer()), json).map {
            FoodEntity(
                name = it.name, nameKey = nameKey(it.name),
                per100g = NutrientColumns(it.kcal, it.p, it.c, it.f, it.fib, it.sug, it.na, it.fe, it.ca, it.b12, it.vd, it.vc, it.k),
                defaultUnit = it.unit, gramsPerUnit = it.g, source = FoodSource.LOCAL.name,
            )
        }
}
