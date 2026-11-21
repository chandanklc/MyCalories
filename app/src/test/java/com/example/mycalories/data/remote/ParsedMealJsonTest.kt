package com.example.mycalories.data.remote

import com.example.mycalories.domain.model.MealType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParsedMealJsonTest {

    private val lunchJson = """
        {"meal":"lunch","items":[
          {"name":"Roti","quantity":2,"unit":"piece","grams":80,"amount_guessed":false,"usda_search_term":null,
           "per_100g":{"calories":300,"protein":7.5,"carbs":45,"fat":9.3,"fiber":6.8,"sugar":1,"sodium_mg":400,
             "iron_mg":3.6,"calcium_mg":34,"vitamin_b12_ug":0,"vitamin_d_ug":0,"vitamin_c_mg":0,"potassium_mg":300}},
          {"name":"White rice, cooked","quantity":1,"unit":"cup","grams":158,"amount_guessed":false,
           "usda_search_term":"rice, white, cooked",
           "per_100g":{"calories":130,"protein":2.7,"carbs":28,"fat":0.3,"fiber":0.4,"sugar":0,"sodium_mg":1}},
          {"name":"Cucumber salad","quantity":1,"unit":"bowl","grams":150,"amount_guessed":true,"usda_search_term":null,
           "per_100g":{"calories":23,"protein":1,"carbs":4.5,"fat":0.5,"fiber":1,"sugar":2,"sodium_mg":150}}
        ]}
    """.trimIndent()

    @Test
    fun `decodes meal and items`() {
        val parsed = ParsedMealJson.decode(lunchJson)
        assertEquals(MealType.LUNCH, parsed.meal)
        assertEquals(3, parsed.items.size)
        val roti = parsed.items[0]
        assertEquals("Roti", roti.name)
        assertEquals(2.0, roti.quantity, 0.0)
        assertEquals("piece", roti.unit)
        assertEquals(80.0, roti.grams, 0.0)
        assertNull(roti.searchTerm)
        assertEquals(300.0, roti.estimatePer100g.calories, 0.0)
        assertEquals(400.0, roti.estimatePer100g.sodiumMg, 0.0)
        assertFalse(roti.amountGuessed)
        assertEquals(3.6, roti.estimatePer100g.ironMg, 0.0)
        assertEquals(34.0, roti.estimatePer100g.calciumMg, 0.0)
        assertEquals(300.0, roti.estimatePer100g.potassiumMg, 0.0)
    }

    @Test
    fun `missing micronutrients default to zero`() {
        val parsed = ParsedMealJson.decode(lunchJson)
        assertEquals(0.0, parsed.items[1].estimatePer100g.ironMg, 0.0)
    }

    @Test
    fun `schema asks for every nutrient`() {
        @Suppress("UNCHECKED_CAST")
        val item = ((ParsedMealJson.schema["properties"] as Map<String, Any>)["items"] as Map<String, Any>)["items"] as Map<String, Any>
        @Suppress("UNCHECKED_CAST")
        val nutrients = (item["properties"] as Map<String, Any>)["per_100g"] as Map<String, Any>
        assertEquals(
            listOf("calories", "protein", "carbs", "fat", "fiber", "sugar", "sodium_mg", "iron_mg", "calcium_mg",
                "vitamin_b12_ug", "vitamin_d_ug", "vitamin_c_mg", "potassium_mg"),
            nutrients["required"],
        )
    }

    @Test
    fun `keeps usda search term and amount guessed flag`() {
        val parsed = ParsedMealJson.decode(lunchJson)
        assertEquals("rice, white, cooked", parsed.items[1].searchTerm)
        assertTrue(parsed.items[2].amountGuessed)
    }

    @Test
    fun `null meal means the user did not say`() {
        val parsed = ParsedMealJson.decode("""{"meal":null,"items":[]}""")
        assertNull(parsed.meal)
        assertTrue(parsed.items.isEmpty())
    }

    @Test
    fun `unknown meal text becomes null`() {
        assertNull(ParsedMealJson.decode("""{"meal":"brunch","items":[]}""").meal)
    }

    @Test
    fun `items with a blank name are dropped`() {
        val json = """{"meal":null,"items":[{"name":"  ","quantity":1,"unit":"g","grams":10,"amount_guessed":false,
            "usda_search_term":null,"per_100g":{"calories":1,"protein":0,"carbs":0,"fat":0,"fiber":0,"sugar":0,"sodium_mg":0}}]}"""
        assertTrue(ParsedMealJson.decode(json).items.isEmpty())
    }

    @Test
    fun `missing grams are worked out from the unit`() {
        val json = """{"meal":null,"items":[{"name":"Dal","quantity":2,"unit":"bowl","grams":0,"amount_guessed":false,
            "usda_search_term":"  ","per_100g":{"calories":90,"protein":4,"carbs":12,"fat":2,"fiber":3,"sugar":1,"sodium_mg":200}}]}"""
        val dal = ParsedMealJson.decode(json).items.single()
        assertEquals(400.0, dal.grams, 0.0)
        assertNull("blank search term becomes null", dal.searchTerm)
    }

    @Test
    fun `negative nutrient numbers are clamped to zero`() {
        val json = """{"meal":null,"items":[{"name":"Odd","quantity":1,"unit":"g","grams":10,"amount_guessed":false,
            "usda_search_term":null,"per_100g":{"calories":-5,"protein":0,"carbs":0,"fat":0,"fiber":0,"sugar":0,"sodium_mg":0}}]}"""
        assertEquals(0.0, ParsedMealJson.decode(json).items.single().estimatePer100g.calories, 0.0)
    }

    @Test(expected = FoodParserException::class)
    fun `invalid json throws a parser exception`() {
        ParsedMealJson.decode("not json")
    }
}
