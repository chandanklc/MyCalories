package com.example.mycalories.domain.repository

import com.example.mycalories.domain.model.Food
import com.example.mycalories.domain.model.LoggedItem
import com.example.mycalories.domain.model.MealType
import com.example.mycalories.domain.model.PortionPreference
import com.example.mycalories.domain.model.SavedMeal
import com.example.mycalories.domain.model.UserProfile
import com.example.mycalories.domain.model.WeightEntry
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** The on-device food library. */
interface FoodRepository {
    suspend fun findByName(name: String): Food?
    suspend fun save(food: Food): Food
    fun search(query: String): Flow<List<Food>>
    suspend fun allNames(limit: Int = 300): List<String>
}

/** What the user ate, plus water. */
interface LogRepository {
    fun observeDay(date: LocalDate): Flow<List<LoggedItem>>
    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<LoggedItem>>
    fun observeLoggedDays(): Flow<Set<LocalDate>>
    suspend fun add(items: List<LoggedItem>)
    suspend fun update(item: LoggedItem)
    suspend fun delete(id: Long)
    suspend fun move(id: Long, meal: MealType)
    fun observeWater(date: LocalDate): Flow<Int>
    fun observeWaterRange(from: LocalDate, to: LocalDate): Flow<Map<LocalDate, Int>>
    suspend fun setWater(date: LocalDate, glasses: Int)
}

interface SavedMealRepository {
    fun observeAll(): Flow<List<SavedMeal>>
    suspend fun save(meal: SavedMeal): Long
    suspend fun delete(id: Long)
}

interface ProfileRepository {
    val profile: Flow<UserProfile?>
    /** When true, a meal with no meal name is filed by time of day without asking. */
    val autoPickMeal: Flow<Boolean>
    suspend fun save(profile: UserProfile)
    suspend fun setAutoPickMeal(enabled: Boolean)
    suspend fun clear()
}

interface WeightRepository {
    /** All entries, oldest first. */
    fun observeAll(): Flow<List<WeightEntry>>
    /** One entry per day: logging again the same day replaces it. */
    suspend fun log(date: LocalDate, kg: Double)
    suspend fun delete(date: LocalDate)
}

/** Remembers portion sizes the user corrects to, so the next log uses them. */
interface PortionRepository {
    suspend fun find(foodName: String, unit: String): PortionPreference?
    /** Records one edit. Returns the updated preference. */
    suspend fun record(foodName: String, unit: String, gramsPerUnit: Double): PortionPreference
}
