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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class UsdaNutritionSourceTest {

    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    private fun client(body: String, status: HttpStatusCode = HttpStatusCode.OK, onUrl: (String) -> Unit = {}) =
        HttpClient(MockEngine { request ->
            onUrl(request.url.toString())
            if (status == HttpStatusCode.OK) respond(body, status, json) else respondError(status)
        })

    private val riceBody = """
        {"foods":[{"description":"Rice, white, cooked","foodNutrients":[
          {"nutrientId":1008,"value":130.0,"unitName":"KCAL"},
          {"nutrientId":1003,"value":2.69,"unitName":"G"},
          {"nutrientId":1004,"value":0.28,"unitName":"G"},
          {"nutrientId":1005,"value":28.2,"unitName":"G"},
          {"nutrientId":1079,"value":0.4,"unitName":"G"},
          {"nutrientId":2000,"value":0.05,"unitName":"G"},
          {"nutrientId":1093,"value":1.0,"unitName":"MG"},
          {"nutrientId":1162,"value":0.0,"unitName":"MG"},
          {"nutrientId":1089,"value":1.2,"unitName":"MG"},
          {"nutrientId":1087,"value":10.0,"unitName":"MG"},
          {"nutrientId":1178,"value":0.0,"unitName":"UG"},
          {"nutrientId":1114,"value":0.0,"unitName":"UG"},
          {"nutrientId":1092,"value":35.0,"unitName":"MG"}
        ]}]}
    """.trimIndent()

    @Test
    fun `maps USDA nutrient ids to nutrients per 100 g`() = runTest {
        val n = UsdaNutritionSource(client(riceBody), apiKey = "KEY").lookup("rice, white, cooked")
        assertNotNull(n)
        assertEquals(130.0, n!!.calories, 0.001)
        assertEquals(2.69, n.protein, 0.001)
        assertEquals(28.2, n.carbs, 0.001)
        assertEquals(0.28, n.fat, 0.001)
        assertEquals(0.4, n.fiber, 0.001)
        assertEquals(0.05, n.sugar, 0.001)
        assertEquals(1.0, n.sodiumMg, 0.001)
        assertEquals(1.2, n.ironMg, 0.001)
        assertEquals(10.0, n.calciumMg, 0.001)
        assertEquals(35.0, n.potassiumMg, 0.001)
        assertEquals(0.0, n.vitaminCMg, 0.001)
    }

    @Test
    fun `sends the query and api key`() = runTest {
        var url = ""
        UsdaNutritionSource(client(riceBody) { url = it }, apiKey = "KEY").lookup("banana raw")
        assert(url.startsWith("https://api.nal.usda.gov/fdc/v1/foods/search")) { url }
        assert(url.contains("query=banana")) { url }
        assert(url.contains("api_key=KEY")) { url }
        assert(url.contains("pageSize=1")) { url }
    }

    @Test
    fun `uses Atwater energy when 1008 is missing`() = runTest {
        val body = """{"foods":[{"description":"Egg","foodNutrients":[
            {"nutrientId":2047,"value":148.0,"unitName":"KCAL"},{"nutrientId":1003,"value":12.4,"unitName":"G"}]}]}"""
        assertEquals(148.0, UsdaNutritionSource(client(body), "K").lookup("egg")!!.calories, 0.0)
    }

    @Test
    fun `works out energy from macros when no energy value`() = runTest {
        val body = """{"foods":[{"description":"X","foodNutrients":[
            {"nutrientId":1003,"value":10,"unitName":"G"},{"nutrientId":1005,"value":20,"unitName":"G"},
            {"nutrientId":1004,"value":5,"unitName":"G"}]}]}"""
        assertEquals(165.0, UsdaNutritionSource(client(body), "K").lookup("x")!!.calories, 0.0)
    }

    @Test
    fun `energy in kilojoules is ignored`() = runTest {
        val body = """{"foods":[{"description":"X","foodNutrients":[
            {"nutrientId":1008,"value":544,"unitName":"kJ"},{"nutrientId":1003,"value":10,"unitName":"G"}]}]}"""
        assertEquals(40.0, UsdaNutritionSource(client(body), "K").lookup("x")!!.calories, 0.0)
    }

    @Test
    fun `no results returns null`() = runTest {
        assertNull(UsdaNutritionSource(client("""{"foods":[]}"""), "K").lookup("zzz"))
    }

    @Test
    fun `server error returns null`() = runTest {
        assertNull(UsdaNutritionSource(client("", HttpStatusCode.TooManyRequests), "K").lookup("rice"))
    }

    @Test
    fun `blank search term returns null without a request`() = runTest {
        var called = false
        assertNull(UsdaNutritionSource(client(riceBody) { called = true }, "K").lookup("  "))
        assertEquals(false, called)
    }
}
