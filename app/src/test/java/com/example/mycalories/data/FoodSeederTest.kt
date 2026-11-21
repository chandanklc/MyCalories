package com.example.mycalories.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.mycalories.data.local.AppDatabase
import com.example.mycalories.data.local.FoodSeeder
import com.example.mycalories.data.repository.RoomFoodRepository
import com.example.mycalories.domain.model.FoodSource
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FoodSeederTest {

    private lateinit var db: AppDatabase
    private val ctx = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    private val twoFoods = """[
        {"name":"Roti","unit":"piece","g":40,"kcal":297,"p":9.8,"c":55.8,"f":3.7,"fib":9.7,"fe":3.6,"ca":34,"k":300},
        {"name":"Idli","unit":"piece","g":40,"kcal":135,"p":4.5,"c":28,"f":0.5}
    ]"""

    @Test
    fun `seeds an empty library`() = runTest {
        val added = FoodSeeder(db.foodDao()) { twoFoods }.seedIfEmpty()
        assertEquals(2, added)
        val roti = RoomFoodRepository(db).findByName("roti")!!
        assertEquals(297.0, roti.per100g.calories, 0.0)
        assertEquals(40.0, roti.gramsPerUnit, 0.0)
        assertEquals(FoodSource.LOCAL, roti.source)
        assertEquals(3.6, roti.per100g.ironMg, 0.0)
        assertEquals(34.0, roti.per100g.calciumMg, 0.0)
        assertEquals(300.0, roti.per100g.potassiumMg, 0.0)
    }

    @Test
    fun `does nothing when foods already exist`() = runTest {
        val seeder = FoodSeeder(db.foodDao()) { twoFoods }
        seeder.seedIfEmpty()
        assertEquals(0, seeder.seedIfEmpty())
    }

    @Test
    fun `the bundled seed file is valid and sensible`() {
        val json = ctx.assets.open("foods_seed.json").bufferedReader().readText()
        val foods = FoodSeeder(db.foodDao()) { json }.parse(json)
        assertTrue("expected a useful starter list", foods.size >= 50)
        assertEquals("names must be unique", foods.size, foods.map { it.nameKey }.toSet().size)
        foods.forEach {
            assertTrue("${it.name} kcal", it.per100g.calories in 0.0..900.0)
            assertTrue("${it.name} grams per unit", it.gramsPerUnit > 0)
            // macros can't weigh more than 100 g per 100 g
            assertTrue("${it.name} macros", it.per100g.protein + it.per100g.carbs + it.per100g.fat <= 101.0)
        }
    }
}
