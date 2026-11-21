package com.example.mycalories.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenFoodFactsSourceTest {

    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private var lastUrl = ""
    private var lastAgent: String? = null

    private fun client(body: String, status: HttpStatusCode = HttpStatusCode.OK) = HttpClient(MockEngine { req ->
        lastUrl = req.url.toString()
        lastAgent = req.headers[HttpHeaders.UserAgent]
        if (status == HttpStatusCode.OK) respond(body, status, json) else respondError(status)
    })

    private val product = """{"status":1,"product":{"product_name":"Whole wheat crackers","brands":"Acme",
        "serving_size":"30 g","serving_quantity":30,
        "nutriments":{"energy-kcal_100g":450,"proteins_100g":9,"carbohydrates_100g":68,"fat_100g":15,
          "fiber_100g":6,"sugars_100g":8,"sodium_100g":0.6,"iron_100g":0.0035,"calcium_100g":0.05}}}"""

    @Test
    fun `reads name, serving and nutrients per 100 g`() = runTest {
        val p = OpenFoodFactsSource(client(product)).lookup("8901234567890")!!
        assertEquals("Whole wheat crackers", p.name)
        assertEquals("Acme", p.brand)
        assertEquals(30.0, p.servingGrams!!, 0.0)
        assertEquals(450.0, p.per100g.calories, 0.0)
        assertEquals(9.0, p.per100g.protein, 0.0)
        assertEquals(600.0, p.per100g.sodiumMg, 0.001)   // grams -> mg
        assertEquals(3.5, p.per100g.ironMg, 0.001)
        assertEquals(50.0, p.per100g.calciumMg, 0.001)
    }

    @Test
    fun `asks for the code with a generic app user agent`() = runTest {
        OpenFoodFactsSource(client(product)).lookup("8901234567890")
        assertTrue(lastUrl, lastUrl.startsWith("https://world.openfoodfacts.org/api/v2/product/8901234567890"))
        assertEquals("MyCalories Android app", lastAgent)
    }

    @Test
    fun `kilojoules are converted when kcal is missing`() = runTest {
        val body = """{"status":1,"product":{"product_name":"X","nutriments":{"energy_100g":1046}}}"""
        assertEquals(250.0, OpenFoodFactsSource(client(body)).lookup("1")!!.per100g.calories, 0.5)
    }

    @Test
    fun `unknown product is null`() = runTest {
        assertNull(OpenFoodFactsSource(client("""{"status":0}""")).lookup("1"))
    }

    @Test
    fun `server error is null`() = runTest {
        assertNull(OpenFoodFactsSource(client("", HttpStatusCode.ServiceUnavailable)).lookup("1"))
    }

    @Test
    fun `codes that are not digits are refused without a request`() = runTest {
        assertNull(OpenFoodFactsSource(client(product)).lookup("abc/../x"))
        assertEquals("", lastUrl)
    }
}
