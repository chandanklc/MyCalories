package com.example.mycalories.data.repository

import com.example.mycalories.data.local.AppDatabase
import com.example.mycalories.data.local.PortionPrefEntity
import com.example.mycalories.data.local.SavedMealEntity
import com.example.mycalories.data.local.WeightEntity
import com.example.mycalories.data.local.unitKey
import com.example.mycalories.data.local.WaterEntity
import com.example.mycalories.data.local.nameKey
import com.example.mycalories.data.local.toDomain
import com.example.mycalories.data.local.toEntity
import com.example.mycalories.domain.model.Food
import com.example.mycalories.domain.model.LoggedItem
import com.example.mycalories.domain.model.MealType
import com.example.mycalories.domain.model.PortionPreference
import com.example.mycalories.domain.model.SavedMeal
import com.example.mycalories.domain.model.WeightEntry
import com.example.mycalories.domain.repository.PortionRepository
import com.example.mycalories.domain.repository.WeightRepository
import com.example.mycalories.domain.repository.FoodRepository
import com.example.mycalories.domain.repository.LogRepository
import com.example.mycalories.domain.repository.SavedMealRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class RoomFoodRepository(db: AppDatabase) : FoodRepository {
    private val dao = db.foodDao()

    override suspend fun findByName(name: String): Food? = dao.findByKey(nameKey(name))?.toDomain()

    /** Saves a food. A food with the same name is replaced, keeping its id. */
    override suspend fun save(food: Food): Food {
        val existing = dao.findByKey(nameKey(food.name))
        val entity = food.toEntity().copy(id = existing?.id ?: food.id)
        val id = dao.upsert(entity).takeIf { it > 0 } ?: entity.id
        return entity.copy(id = id).toDomain()
    }

    override fun search(query: String): Flow<List<Food>> =
        dao.search(nameKey(query)).map { list -> list.map { it.toDomain() } }

    override suspend fun allNames(limit: Int): List<String> = dao.names(limit)
}

class RoomLogRepository(db: AppDatabase) : LogRepository {
    private val dao = db.logDao()
    private val water = db.waterDao()

    override fun observeDay(date: LocalDate) =
        dao.observeDay(date.toEpochDay()).map { l -> l.map { it.toDomain() } }

    override fun observeRange(from: LocalDate, to: LocalDate) =
        dao.observeRange(from.toEpochDay(), to.toEpochDay()).map { l -> l.map { it.toDomain() } }

    override fun observeLoggedDays() = dao.observeDays().map { days -> days.map(LocalDate::ofEpochDay).toSet() }

    override suspend fun add(items: List<LoggedItem>) = dao.insertAll(items.map { it.toEntity().copy(id = 0) })
    override suspend fun update(item: LoggedItem) = dao.update(item.toEntity())
    override suspend fun delete(id: Long) = dao.delete(id)
    override suspend fun move(id: Long, meal: MealType) = dao.move(id, meal.name)

    override fun observeWater(date: LocalDate) = water.observe(date.toEpochDay()).map { it ?: 0 }
    override fun observeWaterRange(from: LocalDate, to: LocalDate) =
        water.observeRange(from.toEpochDay(), to.toEpochDay()).map { l -> l.associate { LocalDate.ofEpochDay(it.epochDay) to it.glasses } }
    override suspend fun setWater(date: LocalDate, glasses: Int) =
        water.upsert(WaterEntity(date.toEpochDay(), glasses.coerceAtLeast(0)))
}

class RoomSavedMealRepository(db: AppDatabase) : SavedMealRepository {
    private val dao = db.savedMealDao()

    override fun observeAll(): Flow<List<SavedMeal>> = dao.observeAll().map { l -> l.map { it.toDomain() } }

    override suspend fun save(meal: SavedMeal): Long =
        dao.insert(SavedMealEntity(name = meal.name.trim()), meal.items.mapIndexed { i, item -> item.toEntity(i) })

    override suspend fun delete(id: Long) = dao.delete(id)
}

class RoomWeightRepository(db: AppDatabase) : WeightRepository {
    private val dao = db.weightDao()
    override fun observeAll() = dao.observeAll().map { l -> l.map { WeightEntry(LocalDate.ofEpochDay(it.epochDay), it.kg) } }
    override suspend fun log(date: LocalDate, kg: Double) = dao.upsert(WeightEntity(date.toEpochDay(), kg))
    override suspend fun delete(date: LocalDate) = dao.delete(date.toEpochDay())
}

class RoomPortionRepository(db: AppDatabase) : PortionRepository {
    private val dao = db.portionPrefDao()

    override suspend fun find(foodName: String, unit: String): PortionPreference? =
        dao.find(nameKey(foodName), unitKey(unit))?.let { PortionPreference(foodName, unit, it.gramsPerUnit, it.timesSeen) }

    override suspend fun record(foodName: String, unit: String, gramsPerUnit: Double): PortionPreference {
        val key = nameKey(foodName)
        val u = unitKey(unit)
        val old = dao.find(key, u)
        // Same size as before (within 5%) counts as a repeat; a different size starts over.
        val same = old != null && kotlin.math.abs(old.gramsPerUnit - gramsPerUnit) <= old.gramsPerUnit * 0.05
        val entity = PortionPrefEntity(key, u, gramsPerUnit, if (same) old!!.timesSeen + 1 else 1)
        dao.upsert(entity)
        return PortionPreference(foodName, unit, entity.gramsPerUnit, entity.timesSeen)
    }
}
