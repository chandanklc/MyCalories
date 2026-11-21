package com.example.mycalories.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiTransportTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private var url = ""
    private var key: String? = null
    private var body: JsonObject? = null

    private fun transport(status: HttpStatusCode = HttpStatusCode.OK, reply: String = okReply("{\"meal\":null,\"items\":[]}")) =
        GeminiTransport(
            HttpClient(MockEngine { req ->
                url = req.url.toString()
                key = req.headers["x-goog-api-key"]
                body = Json.parseToJsonElement((req.body as TextContent).text).jsonObject
                respond(reply, status, jsonHeaders)
            }),
            apiKey = "AIzaKEY", model = "gemini-3.8-flash",
        )

    private fun okReply(text: String, finish: String = "STOP") =
        """{"candidates":[{"content":{"role":"model","parts":[{"text":${Json.encodeToString(kotlinx.serialization.serializer<String>(), text)}}]},"finishReason":"$finish"}]}"""

    private val request = AiRequest(
        system = "You turn food into data.",
        messages = listOf(ChatMessage.user("two roti")),
        schema = mapOf("type" to "object", "properties" to mapOf("meal" to mapOf("type" to listOf("string", "null")))),
        maxTokens = 4000,
    )

    @Test
    fun `calls generateContent for the model with the key in a header`() = runTest {
        transport().send(request)
        assertEquals("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.8-flash:generateContent", url)
        assertEquals("AIzaKEY", key)
        assertFalse("key never goes in the URL", url.contains("AIzaKEY"))
    }

    @Test
    fun `sends system text, messages, schema and token limit`() = runTest {
        transport().send(request)
        val b = body!!
        assertEquals("You turn food into data.", b["systemInstruction"]!!.jsonObject["parts"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content)
        val content = b["contents"]!!.jsonArray.single().jsonObject
        assertEquals("user", content["role"]!!.jsonPrimitive.content)
        assertEquals("two roti", content["parts"]!!.jsonArray.single().jsonObject["text"]!!.jsonPrimitive.content)
        val config = b["generationConfig"]!!.jsonObject
        assertEquals("application/json", config["responseMimeType"]!!.jsonPrimitive.content)
        assertEquals("object", config["responseJsonSchema"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals(4000, config["maxOutputTokens"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun `chat turns use the model role and no json mode`() = runTest {
        transport(reply = okReply("Try paneer.")).send(
            AiRequest("ctx", listOf(ChatMessage.user("hi"), ChatMessage.assistant("hello"), ChatMessage.user("dinner?"))),
        )
        val roles = body!!["contents"]!!.jsonArray.map { it.jsonObject["role"]!!.jsonPrimitive.content }
        assertEquals(listOf("user", "model", "user"), roles)
        assertFalse(body!!["generationConfig"]!!.jsonObject.containsKey("responseMimeType"))
    }

    @Test
    fun `a photo goes with the last message as inline data`() = runTest {
        transport().send(request.copy(image = ImageInput(byteArrayOf(1, 2, 3), "image/jpeg")))
        val parts = body!!["contents"]!!.jsonArray.last().jsonObject["parts"]!!.jsonArray
        val inline = parts[0].jsonObject["inlineData"]!!.jsonObject
        assertEquals("image/jpeg", inline["mimeType"]!!.jsonPrimitive.content)
        assertEquals("AQID", inline["data"]!!.jsonPrimitive.content)
        assertEquals("two roti", parts[1].jsonObject["text"]!!.jsonPrimitive.content)
    }

    @Test
    fun `reads the reply text and a normal stop`() = runTest {
        val reply = transport(reply = okReply("{\"meal\":\"lunch\",\"items\":[]}")).send(request)
        assertEquals("end_turn", reply.stopReason)
        assertEquals("{\"meal\":\"lunch\",\"items\":[]}", reply.text)
    }

    @Test
    fun `finish reasons map to the app's stop reasons`() = runTest {
        assertEquals("max_tokens", transport(reply = okReply("{", "MAX_TOKENS")).send(request).stopReason)
        assertEquals("refusal", transport(reply = okReply("", "SAFETY")).send(request).stopReason)
        assertEquals("refusal", transport(reply = okReply("", "PROHIBITED_CONTENT")).send(request).stopReason)
    }

    @Test
    fun `a blocked prompt is a refusal`() = runTest {
        val reply = transport(reply = """{"promptFeedback":{"blockReason":"SAFETY"}}""").send(request)
        assertEquals("refusal", reply.stopReason)
    }

    private suspend fun errorFor(status: HttpStatusCode, reply: String): AppError? =
        (runCatching { transport(status, reply).send(request) }.exceptionOrNull() as? FoodParserException)?.error

    @Test
    fun `errors become clear messages`() = runTest {
        assertEquals(AppError.BAD_KEY, errorFor(HttpStatusCode.BadRequest,
            """{"error":{"code":400,"message":"API key not valid. Please pass a valid API key.","status":"INVALID_ARGUMENT","details":[{"reason":"API_KEY_INVALID"}]}}"""))
        assertEquals(AppError.BAD_KEY, errorFor(HttpStatusCode.Forbidden, """{"error":{"code":403,"status":"PERMISSION_DENIED"}}"""))
        assertEquals(AppError.FREE_LIMIT, errorFor(HttpStatusCode.TooManyRequests, """{"error":{"code":429,"status":"RESOURCE_EXHAUSTED"}}"""))
        assertEquals(AppError.BUSY, errorFor(HttpStatusCode.ServiceUnavailable, """{"error":{"code":503}}"""))
        assertEquals(AppError.BAD_REPLY, errorFor(HttpStatusCode.BadRequest, """{"error":{"code":400,"message":"Invalid JSON payload"}}"""))
        assertEquals(AppError.MODEL_UNAVAILABLE, errorFor(HttpStatusCode.NotFound, """{"error":{"code":404,"message":"models/x is not found"}}"""))
    }

    @Test
    fun `no internet is a network error`() = runTest {
        val t = GeminiTransport(HttpClient(MockEngine { throw java.net.UnknownHostException("offline") }), "AIzaKEY", "m")
        val e = runCatching { t.send(request) }.exceptionOrNull() as FoodParserException
        assertEquals(AppError.NETWORK, e.error)
    }

    @Test
    fun `works end to end with the food parser`() = runTest {
        val json = """{"meal":"lunch","items":[{"name":"Roti","quantity":2,"unit":"piece","grams":80,"amount_guessed":false,
            "usda_search_term":null,"per_100g":{"calories":297,"protein":9.8,"carbs":55.8,"fat":3.7,"fiber":9.7,"sugar":1.6,"sodium_mg":409}}]}"""
        val meal = AiFoodParser(transport(reply = okReply(json))) { emptyList() }.parse("two roti for lunch")
        assertEquals("Roti", meal.items.single().name)
        assertTrue(body!!["generationConfig"]!!.jsonObject.containsKey("responseJsonSchema"))
    }

    @Test
    fun `a busy model falls back to the lighter one`() = runTest {
        val urls = mutableListOf<String>()
        val t = GeminiTransport(
            HttpClient(MockEngine { req ->
                urls += req.url.toString()
                if (req.url.toString().contains("gemini-3.8-flash")) respond("""{"error":{"code":503}}""", HttpStatusCode.ServiceUnavailable, jsonHeaders)
                else respond(okReply("{\"meal\":null,\"items\":[]}"), HttpStatusCode.OK, jsonHeaders)
            }),
            apiKey = "AQ.KEY", model = "gemini-3.8-flash", fallbackModel = "gemini-3.5-flash-lite",
        )
        assertEquals("end_turn", t.send(request).stopReason)
        assertEquals(2, urls.size)
        assertTrue(urls[1].contains("gemini-3.5-flash-lite"))
    }

    @Test
    fun `a bad key does not retry`() = runTest {
        var calls = 0
        val t = GeminiTransport(
            HttpClient(MockEngine { calls++; respond("""{"error":{"code":400,"message":"API key not valid"}}""", HttpStatusCode.BadRequest, jsonHeaders) }),
            apiKey = "AQ.KEY", model = "gemini-3.8-flash", fallbackModel = "gemini-3.5-flash-lite",
        )
        val e = runCatching { t.send(request) }.exceptionOrNull() as FoodParserException
        assertEquals(AppError.BAD_KEY, e.error)
        assertEquals(1, calls)
    }
}
