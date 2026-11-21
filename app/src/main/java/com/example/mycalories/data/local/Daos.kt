package com.example.mycalories.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface FoodDao {
    @Query("SELECT * FROM foods WHERE nameKey = :key LIMIT 1")
    suspend fun findByKey(key: String): FoodEntity?

    @Query("SELECT * FROM foods WHERE nameKey LIKE '%' || :query || '%' ORDER BY name LIMIT 50")
    fun search(query: String): Flow<List<FoodEntity>>

    @Query("SELECT name FROM foods ORDER BY name LIMIT :limit")
    suspend fun names(limit: Int): List<String>

    @Query("SELECT * FROM foods ORDER BY name")
    suspend fun all(): List<FoodEntity>

    @Query("SELECT COUNT(*) FROM foods")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(food: FoodEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(foods: List<FoodEntity>)
}

@Dao
interface LogDao {
    @Query("SELECT * FROM log_entries WHERE epochDay = :day ORDER BY loggedAtMillis, id")
    fun observeDay(day: Long): Flow<List<LogEntryEntity>>

    @Query("SELECT * FROM log_entries WHERE epochDay BETWEEN :from AND :to ORDER BY epochDay, loggedAtMillis, id")
    fun observeRange(from: Long, to: Long): Flow<List<LogEntryEntity>>

    @Query("SELECT * FROM log_entries ORDER BY epochDay, loggedAtMillis, id")
    suspend fun all(): List<LogEntryEntity>

    @Query("SELECT DISTINCT epochDay FROM log_entries")
    fun observeDays(): Flow<List<Long>>

    @Insert
    suspend fun insertAll(entries: List<LogEntryEntity>)

    @Update
    suspend fun update(entry: LogEntryEntity)

    @Query("DELETE FROM log_entries WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE log_entries SET meal = :meal WHERE id = :id")
    suspend fun move(id: Long, meal: String)
}

@Dao
interface SavedMealDao {
    @Transaction
    @Query("SELECT * FROM saved_meals ORDER BY name")
    fun observeAll(): Flow<List<SavedMealWithItems>>

    @Insert
    suspend fun insertMeal(meal: SavedMealEntity): Long

    @Insert
    suspend fun insertItems(items: List<SavedMealItemEntity>)

    @Transaction
    suspend fun insert(meal: SavedMealEntity, items: List<SavedMealItemEntity>): Long {
        val id = insertMeal(meal)
        insertItems(items.map { it.copy(savedMealId = id) })
        return id
    }

    @Query("DELETE FROM saved_meals WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface WaterDao {
    @Query("SELECT glasses FROM water WHERE epochDay = :day")
    fun observe(day: Long): Flow<Int?>

    @Query("SELECT * FROM water")
    suspend fun all(): List<WaterEntity>

    @Query("SELECT * FROM water WHERE epochDay BETWEEN :from AND :to")
    fun observeRange(from: Long, to: Long): Flow<List<WaterEntity>>

    @Upsert
    suspend fun upsert(water: WaterEntity)
}

@Dao
interface WeightDao {
    @Query("SELECT * FROM weights ORDER BY epochDay")
    fun observeAll(): Flow<List<WeightEntity>>

    @Upsert
    suspend fun upsert(entry: WeightEntity)

    @Query("DELETE FROM weights WHERE epochDay = :day")
    suspend fun delete(day: Long)
}

@Dao
interface PortionPrefDao {
    @Query("SELECT * FROM portion_prefs WHERE nameKey = :key AND unit = :unit")
    suspend fun find(key: String, unit: String): PortionPrefEntity?

    @Upsert
    suspend fun upsert(pref: PortionPrefEntity)
}
