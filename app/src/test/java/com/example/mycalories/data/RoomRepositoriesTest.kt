package com.example.mycalories.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import com.example.mycalories.data.local.AppDatabase
import com.example.mycalories.data.repository.RoomFoodRepository
import com.example.mycalories.data.repository.RoomLogRepository
import com.example.mycalories.data.repository.RoomSavedMealRepository
import com.example.mycalories.domain.model.Food
import com.example.mycalories.domain.model.FoodSource
import com.example.mycalories.domain.model.LoggedItem
import com.example.mycalories.domain.model.MealType
import com.example.mycalories.domain.model.Nutrients
import com.example.mycalories.domain.model.SavedMeal
import com.example.mycalories.domain.model.SavedMealItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class RoomRepositoriesTest {

    private lateinit var db: AppDatabase
    private lateinit var foods: RoomFoodRepository
    private lateinit var log: RoomLogRepository
    private lateinit var saved: RoomSavedMealRepository

    private val today = LocalDate.of(2026, 11, 18)
    private val dalPer100 = Nutrients(90.0, 4.5, 12.0, 2.5, 3.0, 1.0, 290.0)

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        foods = RoomFoodRepository(db)
        log = RoomLogRepository(db)
        saved = RoomSavedMealRepository(db)
    }

    @After
    fun tearDown() = db.close()

    private fun item(name: String, meal: MealType = MealType.LUNCH, date: LocalDate = today, grams: Double = 200.0) =
        LoggedItem(0, date, meal, name, 1.0, "bowl", grams, dalPer100, FoodSource.LOCAL, false, 1L)

    // ---- foods ----

    @Test
    fun `saved food is found by name ignoring case and spaces`() = runTest {
        foods.save(Food(name = "Dal  Tadka", per100g = dalPer100, defaultUnit = "bowl", gramsPerUnit = 200.0, source = FoodSource.LOCAL))
        val found = foods.findByName("  dal tadka ")!!
        assertEquals("Dal  Tadka".trim(), found.name)
        assertEquals(dalPer100, found.per100g)
        assertEquals(FoodSource.LOCAL, found.source)
        assertTrue(found.id > 0)
    }

    @Test
    fun `saving the same name again replaces it and keeps the id`() = runTest {
        val first = foods.save(Food(name = "Rice", per100g = dalPer100, defaultUnit = "cup", gramsPerUnit = 158.0, source = FoodSource.AI_ESTIMATE))
        val second = foods.save(Food(name = "rice", per100g = Nutrients(calories = 130.0), defaultUnit = "cup", gramsPerUnit = 158.0, source = FoodSource.ONLINE_USDA))
        assertEquals(first.id, second.id)
        assertEquals(130.0, foods.findByName("RICE")!!.per100g.calories, 0.0)
        assertEquals(1, foods.allNames().size)
    }

    @Test
    fun `unknown food is null`() = runTest {
        assertNull(foods.findByName("unicorn"))
    }

    @Test
    fun `search matches part of a name`() = runTest {
        foods.save(Food(name = "Paneer bhurji", per100g = dalPer100, defaultUnit = "plate", gramsPerUnit = 150.0, source = FoodSource.LOCAL))
        foods.save(Food(name = "Palak paneer", per100g = dalPer100, defaultUnit = "bowl", gramsPerUnit = 200.0, source = FoodSource.LOCAL))
        foods.save(Food(name = "Roti", per100g = dalPer100, defaultUnit = "piece", gramsPerUnit = 40.0, source = FoodSource.LOCAL))
        assertEquals(listOf("Palak paneer", "Paneer bhurji"), foods.search("PANEER").first().map { it.name })
    }

    // ---- log ----

    @Test
    fun `logged items come back for their day only`() = runTest {
        log.add(listOf(item("Dal"), item("Roti"), item("Old", date = today.minusDays(1))))
        val day = log.observeDay(today).first()
        assertEquals(listOf("Dal", "Roti"), day.map { it.name })
        assertEquals(180.0, day.first().nutrients.calories, 0.001)
    }

    @Test
    fun `day flow updates when items are added and deleted`() = runTest {
        log.observeDay(today).test {
            assertEquals(0, awaitItem().size)
            log.add(listOf(item("Dal")))
            val added = awaitItem().single()
            log.delete(added.id)
            assertEquals(0, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `update changes an item`() = runTest {
        log.add(listOf(item("Dal")))
        val dal = log.observeDay(today).first().single()
        log.update(dal.copy(grams = 300.0, quantity = 1.5))
        assertEquals(300.0, log.observeDay(today).first().single().grams, 0.0)
    }

    @Test
    fun `move puts an item in another meal`() = runTest {
        log.add(listOf(item("Dal", MealType.LUNCH)))
        val dal = log.observeDay(today).first().single()
        log.move(dal.id, MealType.DINNER)
        assertEquals(MealType.DINNER, log.observeDay(today).first().single().meal)
    }

    @Test
    fun `range includes both ends`() = runTest {
        log.add((0..8).map { item("d$it", date = today.minusDays(it.toLong())) })
        val week = log.observeRange(today.minusDays(6), today).first()
        assertEquals(7, week.size)
    }

    @Test
    fun `logged days are distinct dates`() = runTest {
        log.add(listOf(item("a"), item("b"), item("c", date = today.minusDays(2))))
        assertEquals(setOf(today, today.minusDays(2)), log.observeLoggedDays().first())
    }

    @Test
    fun `water defaults to zero and can be set`() = runTest {
        assertEquals(0, log.observeWater(today).first())
        log.setWater(today, 5)
        assertEquals(5, log.observeWater(today).first())
        log.setWater(today, -2)
        assertEquals(0, log.observeWater(today).first())
    }

    @Test
    fun `water for a range of days`() = runTest {
        log.setWater(today, 6)
        log.setWater(today.minusDays(2), 8)
        log.setWater(today.minusDays(20), 3)
        assertEquals(mapOf(today to 6, today.minusDays(2) to 8), log.observeWaterRange(today.minusDays(6), today).first())
    }

    // ---- saved meals ----

    @Test
    fun `saved meals keep their items in order`() = runTest {
        val items = listOf(
            SavedMealItem("Roti", 2.0, "piece", 80.0, Nutrients(calories = 300.0), FoodSource.LOCAL),
            SavedMealItem("Dal", 1.0, "bowl", 200.0, dalPer100, FoodSource.USER_RECIPE),
        )
        val id = saved.save(SavedMeal(name = " Usual lunch ", items = items))
        val meal = saved.observeAll().first().single()
        assertEquals(id, meal.id)
        assertEquals("Usual lunch", meal.name)
        assertEquals(listOf("Roti", "Dal"), meal.items.map { it.name })
        assertEquals(420.0, meal.total.calories, 0.001)
    }

    @Test
    fun `deleting a saved meal removes its items`() = runTest {
        val id = saved.save(SavedMeal(name = "x", items = listOf(SavedMealItem("a", 1.0, "g", 1.0, dalPer100, FoodSource.LOCAL))))
        saved.delete(id)
        assertTrue(saved.observeAll().first().isEmpty())
    }
}
