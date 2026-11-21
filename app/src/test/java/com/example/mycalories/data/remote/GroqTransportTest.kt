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
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GroqTransportTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private val urls = mutableListOf<String>()
    private val bodies = mutableListOf<JsonObject>()
    private var auth: String? = null

    private fun okReply(text: String, finish: String = "stop") =
        """{"choices":[{"index":0,"message":{"role":"assistant","content":${Json.encodeToString(kotlinx.serialization.serializer<String>(), text)}},"finish_reason":"$finish"}]}"""

    /** Replies with each (status, body) in turn; the last one repeats. */
    private fun transport(
        vararg replies: Pair<HttpStatusCode, String> = arrayOf(HttpStatusCode.OK to okReply("{\"meal\":null,\"items\":[]}")),
        model: String = "openai/gpt-oss-20b",
        fallback: String? = "openai/gpt-oss-120b",
        vision: String = "qwen/qwen3.8-27b",
    ): GroqTransport {
        var n = 0
        return GroqTransport(
            HttpClient(MockEngine { req ->
                urls += req.url.toString()
                auth = req.headers[HttpHeaders.Authorization]
                bodies += Json.parseToJsonElement((req.body as TextContent).text).jsonObject
                val (status, body) = replies[minOf(n++, replies.lastIndex)]
                respond(body, status, jsonHeaders)
            }),
            apiKey = "gsk_KEY", model = model, fallbackModel = fallback, visionModel = vision,
        )
    }

    private val schema = mapOf("type" to "object", "properties" to mapOf("meal" to mapOf("type" to listOf("string", "null"))),
        "required" to listOf("meal"), "additionalProperties" to false)

    private val request = AiRequest(
        system = "You turn food into data.",
        messages = listOf(ChatMessage.user("two roti")),
        schema = schema,
        maxTokens = 4000,
    )

    @Test
    fun `calls Groq chat completions with the key as a bearer token`() = runTest {
        transport().send(request)
        assertEquals("https://api.groq.com/openai/v1/chat/completions", urls.single())
        assertEquals("Bearer gsk_KEY", auth)
        assertTrue("key never goes in the URL", !urls.single().contains("gsk_KEY"))
    }

    @Test
    fun `sends the system prompt, the message, the token limit and low reasoning`() = runTest {
        transport().send(request)
        val b = bodies.single()
        assertEquals("openai/gpt-oss-20b", b["model"]!!.jsonPrimitive.content)
        val messages = b["messages"]!!.jsonArray
        assertEquals("system", messages[0].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("You turn food into data.", messages[0].jsonObject["content"]!!.jsonPrimitive.content)
        assertEquals("user", messages[1].jsonObject["role"]!!.jsonPrimitive.content)
        assertEquals("two roti", messages[1].jsonObject["content"]!!.jsonPrimitive.content)
        assertEquals(4000, b["max_completion_tokens"]!!.jsonPrimitive.int)
        assertEquals("low", b["reasoning_effort"]!!.jsonPrimitive.content)
    }

    @Test
    fun `a schema asks for strict json in exactly that shape`() = runTest {
        transport().send(request)
        val format = bodies.single()["response_format"]!!.jsonObject
        assertEquals("json_schema", format["type"]!!.jsonPrimitive.content)
        val js = format["json_schema"]!!.jsonObject
        assertTrue(js["strict"]!!.jsonPrimitive.boolean)
        assertEquals(GeminiTransport.toJson(schema), js["schema"])
    }

    @Test
    fun `a model without strict schemas gets json mode and the schema in the prompt`() = runTest {
        transport(model = "llama-3.3-70b-versatile", fallback = null).send(request)
        val b = bodies.single()
        assertEquals("json_object", b["response_format"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        val system = b["messages"]!!.jsonArray[0].jsonObject["content"]!!.jsonPrimitive.content
        assertTrue(system.contains("JSON") && system.contains("\"meal\""))
        assertNull("only gpt-oss takes reasoning_effort", b["reasoning_effort"])
    }

    @Test
    fun `chat turns keep their roles and use no json mode`() = runTest {
        transport(HttpStatusCode.OK to okReply("Eat more dal.")).send(
            AiRequest("Be brief.", listOf(ChatMessage.user("hi"), ChatMessage.assistant("hello"), ChatMessage.user("protein?")), maxTokens = 2000),
        )
        val b = bodies.single()
        assertEquals(listOf("system", "user", "assistant", "user"), b["messages"]!!.jsonArray.map { it.jsonObject["role"]!!.jsonPrimitive.content })
        assertNull(b["response_format"])
    }

    @Test
    fun `a photo goes to the vision model as a data url with the last message`() = runTest {
        transport().send(request.copy(image = ImageInput(byteArrayOf(1, 2, 3), "image/jpeg")))
        val b = bodies.single()
        assertEquals("qwen/qwen3.8-27b", b["model"]!!.jsonPrimitive.content)
        val content = b["messages"]!!.jsonArray.last().jsonObject["content"]!!.jsonArray
        assertEquals("two roti", content[0].jsonObject["text"]!!.jsonPrimitive.content)
        assertEquals("data:image/jpeg;base64,AQID",
            content[1].jsonObject["image_url"]!!.jsonObject["url"]!!.jsonPrimitive.content)
        assertNull("reasoning_effort is only for gpt-oss", b["reasoning_effort"])
    }

    @Test
    fun `reads the reply text and maps finish reasons`() = runTest {
        assertEquals(AiReply("end_turn", "{\"meal\":null}"), transport(HttpStatusCode.OK to okReply("{\"meal\":null}")).send(request))
        assertEquals("max_tokens", transport(HttpStatusCode.OK to okReply("{", "length")).send(request).stopReason)
        assertEquals("refusal", transport(HttpStatusCode.OK to okReply("", "content_filter")).send(request).stopReason)
    }

    @Test
    fun `thinking tags some models add are removed`() = runTest {
        val reply = transport(HttpStatusCode.OK to okReply("<think>hmm, roti</think>\n{\"meal\":null}")).send(request)
        assertEquals("{\"meal\":null}", reply.text)
    }

    @Test
    fun `errors become clear messages`() {
        assertEquals(AppError.BAD_KEY, GroqTransport.errorFor(401, """{"error":{"message":"Invalid API Key","type":"invalid_request_error","code":"invalid_api_key"}}"""))
        assertEquals(AppError.BAD_KEY, GroqTransport.errorFor(403, ""))
        assertEquals(AppError.FREE_LIMIT, GroqTransport.errorFor(429, """{"error":{"message":"Rate limit reached","type":"tokens"}}"""))
        assertEquals(AppError.BUSY, GroqTransport.errorFor(498, ""))
        assertEquals(AppError.BUSY, GroqTransport.errorFor(503, ""))
        assertEquals(AppError.MODEL_UNAVAILABLE, GroqTransport.errorFor(404, ""))
        assertEquals(AppError.MODEL_UNAVAILABLE, GroqTransport.errorFor(400,
            """{"error":{"message":"The model `x` has been decommissioned","code":"model_decommissioned"}}"""))
        assertEquals(AppError.BAD_REPLY, GroqTransport.errorFor(400,
            """{"error":{"message":"Generated JSON does not match the expected schema.","code":"json_validate_failed"}}"""))
    }

    @Test
    fun `a busy or limited model falls back to the bigger one`() = runTest {
        val reply = transport(HttpStatusCode.TooManyRequests to "{}", HttpStatusCode.OK to okReply("{\"meal\":\"lunch\"}")).send(request)
        assertEquals("{\"meal\":\"lunch\"}", reply.text)
        assertEquals(listOf("openai/gpt-oss-20b", "openai/gpt-oss-120b"), bodies.map { it["model"]!!.jsonPrimitive.content })
    }

    @Test
    fun `json that does not fit the schema is retried on the bigger model`() = runTest {
        transport(HttpStatusCode.BadRequest to """{"error":{"code":"json_validate_failed"}}""",
            HttpStatusCode.OK to okReply("{\"meal\":null}")).send(request)
        assertEquals(2, bodies.size)
    }

    @Test
    fun `a bad key does not retry`() = runTest {
        val r = runCatching { transport(HttpStatusCode.Unauthorized to "{}").send(request) }
        assertEquals(AppError.BAD_KEY, (r.exceptionOrNull() as FoodParserException).error)
        assertEquals(1, bodies.size)
    }

    @Test
    fun `works end to end with the food parser`() = runTest {
        val json = """{"meal":"lunch","items":[{"name":"Roti","quantity":2,"unit":"piece","grams":80,"amount_guessed":false,"usda_search_term":null,
            "per_100g":{"calories":297,"protein":9.6,"carbs":46,"fat":7.5,"fiber":4.9,"sugar":1.5,"sodium_mg":400,"iron_mg":2,"calcium_mg":30,"vitamin_b12_ug":0,"vitamin_d_ug":0,"vitamin_c_mg":0,"potassium_mg":200}}]}"""
        val meal = AiFoodParser(transport(HttpStatusCode.OK to okReply(json))) { emptyList() }.parse("two roti for lunch")
        assertEquals("Roti", meal.items.single().name)
    }
}
