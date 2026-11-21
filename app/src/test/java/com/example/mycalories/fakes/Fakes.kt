package com.example.mycalories.fakes

import com.example.mycalories.data.remote.AppError
import com.example.mycalories.data.remote.FoodParser
import com.example.mycalories.data.remote.FoodParserException
import com.example.mycalories.data.remote.ImageInput
import com.example.mycalories.data.remote.PackagedFood
import com.example.mycalories.data.remote.PackagedFoodSource
import com.example.mycalories.data.remote.ParsedRecipe
import com.example.mycalories.data.remote.OnlineNutritionSource
import com.example.mycalories.data.remote.ParsedMeal
import com.example.mycalories.domain.model.Food
import com.example.mycalories.domain.model.LoggedItem
import com.example.mycalories.domain.model.MealType
import com.example.mycalories.domain.model.Nutrients
import com.example.mycalories.domain.model.SavedMeal
import com.example.mycalories.domain.model.UserProfile
import com.example.mycalories.domain.repository.FoodRepository
import com.example.mycalories.domain.repository.LogRepository
import com.example.mycalories.domain.repository.ProfileRepository
import com.example.mycalories.domain.repository.SavedMealRepository
import com.example.mycalories.voice.VoiceEvent
import com.example.mycalories.voice.VoiceInput
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class FakeFoodRepository(initial: List<Food> = emptyList()) : FoodRepository {
    val foods = MutableStateFlow(initial)
    private var nextId = 100L

    override suspend fun findByName(name: String): Food? =
        foods.value.firstOrNull { it.name.equals(name.trim(), ignoreCase = true) }

    override suspend fun save(food: Food): Food {
        val saved = food.copy(id = food.id.takeIf { it != 0L } ?: nextId++)
        foods.value = foods.value.filterNot { it.name.equals(food.name, true) } + saved
        return saved
    }

    override fun search(query: String): Flow<List<Food>> =
        foods.map { list -> list.filter { it.name.contains(query, ignoreCase = true) } }

    override suspend fun allNames(limit: Int): List<String> = foods.value.map { it.name }.take(limit)
}

class FakeLogRepository : LogRepository {
    val items = MutableStateFlow<List<LoggedItem>>(emptyList())
    val water = MutableStateFlow<Map<LocalDate, Int>>(emptyMap())
    private var nextId = 1L

    override fun observeDay(date: LocalDate) = items.map { l -> l.filter { it.date == date } }
    override fun observeRange(from: LocalDate, to: LocalDate) =
        items.map { l -> l.filter { !it.date.isBefore(from) && !it.date.isAfter(to) } }
    override fun observeLoggedDays() = items.map { l -> l.map { it.date }.toSet() }

    override suspend fun add(items: List<LoggedItem>) {
        this.items.value = this.items.value + items.map { it.copy(id = nextId++) }
    }
    override suspend fun update(item: LoggedItem) {
        items.value = items.value.map { if (it.id == item.id) item else it }
    }
    override suspend fun delete(id: Long) {
        items.value = items.value.filterNot { it.id == id }
    }
    override suspend fun move(id: Long, meal: MealType) {
        items.value = items.value.map { if (it.id == id) it.copy(meal = meal) else it }
    }
    override fun observeWater(date: LocalDate) = water.map { it[date] ?: 0 }
    override fun observeWaterRange(from: LocalDate, to: LocalDate) =
        water.map { m -> m.filterKeys { !it.isBefore(from) && !it.isAfter(to) } }
    override suspend fun setWater(date: LocalDate, glasses: Int) {
        water.value = water.value + (date to glasses)
    }
}

class FakeSavedMealRepository : SavedMealRepository {
    val meals = MutableStateFlow<List<SavedMeal>>(emptyList())
    private var nextId = 1L
    override fun observeAll(): Flow<List<SavedMeal>> = meals
    override suspend fun save(meal: SavedMeal): Long {
        val id = nextId++
        meals.value = meals.value + meal.copy(id = id)
        return id
    }
    override suspend fun delete(id: Long) {
        meals.value = meals.value.filterNot { it.id == id }
    }
}

class FakeProfileRepository(profile: UserProfile? = null, autoPick: Boolean = false) : ProfileRepository {
    val profileFlow = MutableStateFlow(profile)
    val autoPickFlow = MutableStateFlow(autoPick)
    override val profile: Flow<UserProfile?> = profileFlow
    override val autoPickMeal: Flow<Boolean> = autoPickFlow
    override suspend fun save(profile: UserProfile) { profileFlow.value = profile }
    override suspend fun setAutoPickMeal(enabled: Boolean) { autoPickFlow.value = enabled }
    override suspend fun clear() { profileFlow.value = null; autoPickFlow.value = false }
}

class FakeFoodParser(var result: ParsedMeal? = null, var error: AppError? = null) : FoodParser {
    val calls = mutableListOf<String>()
    val images = mutableListOf<ImageInput>()
    var recipe: ParsedRecipe? = null
    override suspend fun parse(text: String): ParsedMeal {
        calls += text
        error?.let { throw FoodParserException(it) }
        return result ?: ParsedMeal(null, emptyList())
    }
    override suspend fun parseImage(image: ImageInput): ParsedMeal {
        images += image
        error?.let { throw FoodParserException(it) }
        return result ?: ParsedMeal(null, emptyList())
    }
    override suspend fun parseRecipe(text: String): ParsedRecipe {
        calls += text
        error?.let { throw FoodParserException(it) }
        return recipe ?: ParsedRecipe("Recipe", 1, "serving", emptyList())
    }
}

class FakePackagedFoods(private val products: Map<String, PackagedFood> = emptyMap()) : PackagedFoodSource {
    override suspend fun lookup(barcode: String): PackagedFood? = products[barcode]
}

class FakeOnlineSource(private val results: Map<String, Nutrients> = emptyMap(), private val fail: Boolean = false) :
    OnlineNutritionSource {
    val lookups = mutableListOf<String>()
    override suspend fun lookup(term: String): Nutrients? {
        lookups += term
        if (fail) throw java.io.IOException("offline")
        return results[term]
    }
}

class FakeVoiceInput : VoiceInput {
    val events = MutableSharedFlow<VoiceEvent>(extraBufferCapacity = 16)
    var started = 0
    var stopped = 0
    override fun listen(): Flow<VoiceEvent> {
        started++
        return events
    }
    override fun stop() { stopped++ }
}

class FakeSettingsRepository(initial: com.example.mycalories.domain.repository.AppSettings = com.example.mycalories.domain.repository.AppSettings()) :
    com.example.mycalories.domain.repository.SettingsRepository {
    val flow = MutableStateFlow(initial)
    val reminded = mutableMapOf<LocalDate, MutableSet<MealType>>()
    val water = mutableMapOf<LocalDate, java.time.LocalTime>()
    override val settings: Flow<com.example.mycalories.domain.repository.AppSettings> = flow
    override suspend fun update(change: (com.example.mycalories.domain.repository.AppSettings) -> com.example.mycalories.domain.repository.AppSettings) {
        flow.value = change(flow.value)
    }
    override suspend fun remindedMeals(date: LocalDate): Set<MealType> = reminded[date].orEmpty()
    override suspend fun markReminded(date: LocalDate, meal: MealType) { reminded.getOrPut(date) { mutableSetOf() } += meal }
    override suspend fun lastWaterReminder(date: LocalDate) = water[date]
    override suspend fun setLastWaterReminder(date: LocalDate, time: java.time.LocalTime) { water[date] = time }
}

class FakeHealth(
    var available: com.example.mycalories.domain.repository.HealthAvailability = com.example.mycalories.domain.repository.HealthAvailability.AVAILABLE,
    var granted: Boolean = true,
    var activity: Map<LocalDate, com.example.mycalories.domain.repository.DayActivity> = emptyMap(),
    var weight: com.example.mycalories.domain.model.WeightEntry? = null,
) : com.example.mycalories.domain.repository.HealthDataSource {
    val written = mutableListOf<LoggedItem>()
    override suspend fun availability() = available
    override suspend fun hasPermissions() = granted
    override suspend fun activity(date: LocalDate) = activity[date]
    override suspend fun latestWeight() = weight
    override suspend fun writeMeals(items: List<LoggedItem>) { written += items }
}

class FakeWeightRepository : com.example.mycalories.domain.repository.WeightRepository {
    val entries = MutableStateFlow<List<com.example.mycalories.domain.model.WeightEntry>>(emptyList())
    override fun observeAll(): Flow<List<com.example.mycalories.domain.model.WeightEntry>> = entries.map { l -> l.sortedBy { it.date } }
    override suspend fun log(date: LocalDate, kg: Double) {
        entries.value = entries.value.filterNot { it.date == date } + com.example.mycalories.domain.model.WeightEntry(date, kg)
    }
    override suspend fun delete(date: LocalDate) { entries.value = entries.value.filterNot { it.date == date } }
}

class FakePortionRepository : com.example.mycalories.domain.repository.PortionRepository {
    val prefs = mutableMapOf<Pair<String, String>, com.example.mycalories.domain.model.PortionPreference>()
    private fun key(n: String, u: String) = n.trim().lowercase() to com.example.mycalories.domain.PortionCalculator.normalize(u)
    override suspend fun find(foodName: String, unit: String) = prefs[key(foodName, unit)]
    override suspend fun record(foodName: String, unit: String, gramsPerUnit: Double): com.example.mycalories.domain.model.PortionPreference {
        val old = prefs[key(foodName, unit)]
        val same = old != null && kotlin.math.abs(old.gramsPerUnit - gramsPerUnit) <= old.gramsPerUnit * 0.05
        val p = com.example.mycalories.domain.model.PortionPreference(foodName, unit, gramsPerUnit, if (same) old!!.timesSeen + 1 else 1)
        prefs[key(foodName, unit)] = p
        return p
    }
}

class FakeDataManager(var backup: com.example.mycalories.domain.Backup = com.example.mycalories.domain.Backup(null, emptyList(), emptyList(), emptyList(), emptyList(), emptyMap())) :
    com.example.mycalories.domain.repository.DataManager {
    var deleted = 0
    var restored: com.example.mycalories.domain.Backup? = null
    override suspend fun export() = backup
    override suspend fun restore(backup: com.example.mycalories.domain.Backup) { restored = backup }
    override suspend fun deleteEverything() { deleted++ }
}
