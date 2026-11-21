package com.example.mycalories.domain

import com.example.mycalories.data.remote.ParsedFood
import com.example.mycalories.data.remote.ParsedMeal
import com.example.mycalories.domain.model.Food
import com.example.mycalories.domain.model.FoodSource
import com.example.mycalories.domain.model.MealType
import com.example.mycalories.domain.model.Nutrients
import com.example.mycalories.fakes.FakeFoodRepository
import com.example.mycalories.fakes.FakeOnlineSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class FoodResolverTest {

    private val date = LocalDate.of(2026, 11, 18)
    private val aiDal = Nutrients(calories = 100.0, protein = 5.0)
    private val localRoti = Food(1, "Roti", Nutrients(calories = 300.0, protein = 7.5), "piece", 40.0, FoodSource.LOCAL)
    private val usdaRice = Nutrients(calories = 130.0, protein = 2.7, carbs = 28.0)

    private fun parsed(
        name: String, quantity: Double = 1.0, unit: String = "piece", grams: Double = 100.0,
        term: String? = null, guessed: Boolean = false, estimate: Nutrients = aiDal,
    ) = ParsedFood(name, quantity, unit, grams, term, estimate, guessed)

    @Test
    fun `uses the local library first`() = runTest {
        val online = FakeOnlineSource()
        val resolver = FoodResolver(FakeFoodRepository(listOf(localRoti)), online, nowMillis = { 5L })

        val items = resolver.resolve(ParsedMeal(null, listOf(parsed("roti", quantity = 2.0, grams = 70.0, term = "bread"))), date, MealType.LUNCH)

        val roti = items.single()
        assertEquals(FoodSource.LOCAL, roti.source)
        assertEquals(300.0, roti.per100g.calories, 0.0)
        assertEquals("same unit uses the library weight", 80.0, roti.grams, 0.0)
        assertEquals(MealType.LUNCH, roti.meal)
        assertEquals(date, roti.date)
        assertEquals(5L, roti.loggedAtMillis)
        assertTrue("local hit never goes online", online.lookups.isEmpty())
    }

    @Test
    fun `local food with a different unit keeps Claude's grams`() = runTest {
        val resolver = FoodResolver(FakeFoodRepository(listOf(localRoti)), FakeOnlineSource())
        val item = resolver.resolve(ParsedMeal(null, listOf(parsed("Roti", unit = "g", grams = 55.0))), date, MealType.LUNCH).single()
        assertEquals(55.0, item.grams, 0.0)
    }

    @Test
    fun `goes online for plain foods and saves the result locally`() = runTest {
        val foods = FakeFoodRepository()
        val resolver = FoodResolver(foods, FakeOnlineSource(mapOf("rice, white, cooked" to usdaRice)))

        val rice = resolver.resolve(
            ParsedMeal(null, listOf(parsed("White rice", unit = "cup", grams = 158.0, term = "rice, white, cooked"))),
            date, MealType.LUNCH,
        ).single()

        assertEquals(FoodSource.ONLINE_USDA, rice.source)
        assertEquals(130.0, rice.per100g.calories, 0.0)
        val saved = foods.findByName("White rice")!!
        assertEquals(FoodSource.ONLINE_USDA, saved.source)
        assertEquals("cup", saved.defaultUnit)
        assertEquals(158.0, saved.gramsPerUnit, 0.0)
    }

    @Test
    fun `uses Claude's estimate when online has nothing and saves it`() = runTest {
        val foods = FakeFoodRepository()
        val resolver = FoodResolver(foods, FakeOnlineSource())
        val dal = resolver.resolve(ParsedMeal(null, listOf(parsed("Dal tadka", unit = "bowl", grams = 200.0, term = "lentils"))), date, MealType.LUNCH).single()

        assertEquals(FoodSource.AI_ESTIMATE, dal.source)
        assertEquals(100.0, dal.per100g.calories, 0.0)
        assertEquals(FoodSource.AI_ESTIMATE, foods.findByName("Dal tadka")!!.source)
    }

    @Test
    fun `mixed dishes with no search term skip the online lookup`() = runTest {
        val online = FakeOnlineSource()
        FoodResolver(FakeFoodRepository(), online).resolve(ParsedMeal(null, listOf(parsed("Dal tadka"))), date, MealType.LUNCH)
        assertTrue(online.lookups.isEmpty())
    }

    @Test
    fun `offline falls back to Claude's estimate`() = runTest {
        val resolver = FoodResolver(FakeFoodRepository(), FakeOnlineSource(fail = true))
        val item = resolver.resolve(ParsedMeal(null, listOf(parsed("Banana", term = "banana raw"))), date, MealType.SNACK).single()
        assertEquals(FoodSource.AI_ESTIMATE, item.source)
    }

    @Test
    fun `guessed amounts are flagged for review`() = runTest {
        val resolver = FoodResolver(FakeFoodRepository(), FakeOnlineSource())
        val items = resolver.resolve(
            ParsedMeal(null, listOf(parsed("Salad", guessed = true), parsed("Dal", guessed = false))), date, MealType.LUNCH,
        )
        assertTrue(items[0].needsReview)
        assertFalse(items[1].needsReview)
    }

    @Test
    fun `keeps the spoken order`() = runTest {
        val resolver = FoodResolver(FakeFoodRepository(listOf(localRoti)), FakeOnlineSource())
        val names = resolver.resolve(ParsedMeal(null, listOf(parsed("Dal"), parsed("Roti"), parsed("Salad"))), date, MealType.LUNCH).map { it.name }
        assertEquals(listOf("Dal", "Roti", "Salad"), names)
    }

    @Test
    fun `a learned portion replaces the usual size`() = runTest {
        val portions = com.example.mycalories.fakes.FakePortionRepository()
        portions.record("Dal tadka", "bowl", 250.0)
        portions.record("Dal tadka", "bowl", 250.0)
        val resolver = FoodResolver(FakeFoodRepository(), FakeOnlineSource(), portions)
        val dal = resolver.resolve(ParsedMeal(null, listOf(parsed("Dal tadka", quantity = 2.0, unit = "bowls", grams = 400.0))), date, MealType.LUNCH).single()
        assertEquals(500.0, dal.grams, 0.0)
    }

    @Test
    fun `a portion seen once is not used yet`() = runTest {
        val portions = com.example.mycalories.fakes.FakePortionRepository()
        portions.record("Dal tadka", "bowl", 250.0)
        val dal = FoodResolver(FakeFoodRepository(), FakeOnlineSource(), portions)
            .resolve(ParsedMeal(null, listOf(parsed("Dal tadka", unit = "bowl", grams = 200.0))), date, MealType.LUNCH).single()
        assertEquals(200.0, dal.grams, 0.0)
    }
}
