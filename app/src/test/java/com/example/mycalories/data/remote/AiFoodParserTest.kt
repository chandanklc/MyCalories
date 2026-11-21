package com.example.mycalories.data.remote

import com.example.mycalories.domain.model.MealType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingTransport(var reply: AiReply) : AiTransport {
    val requests = mutableListOf<AiRequest>()
    override suspend fun send(request: AiRequest): AiReply {
        requests += request
        return reply
    }
}

class AiFoodParserTest {

    @Test
    fun `prompt keeps foods eaten together as separate items`() {
        val prompt = AiFoodParser.systemPrompt(emptyList())
        assertTrue("roti with dal is two foods", prompt.contains("\"2 roti with dal\" is Roti and Dal"))
        assertTrue(prompt.contains("only combine", ignoreCase = true))
        assertTrue("sides count", prompt.contains("butter, ghee, pickle, chutney, raita and salad"))
        assertTrue("no over-matching", prompt.contains("Paneer paratha is not Paratha"))
    }


    private val okJson = """{"meal":"dinner","items":[{"name":"Paneer bhurji","quantity":1,"unit":"plate","grams":180,
        "amount_guessed":false,"usda_search_term":null,
        "per_100g":{"calories":220,"protein":13,"carbs":6,"fat":16,"fiber":1,"sugar":2,"sodium_mg":350}}]}"""

    @Test
    fun `sends the spoken text, known foods and the meal schema`() = runTest {
        val transport = RecordingTransport(AiReply(stopReason = "end_turn", text = okJson))
        val parser = AiFoodParser(transport) { listOf("Roti", "Dal tadka") }

        parser.parse("  had paneer bhurji for dinner ")

        val req = transport.requests.single()
        assertEquals("had paneer bhurji for dinner", req.messages.single().text)
        assertTrue(req.system.contains("Roti"))
        assertTrue(req.system.contains("Dal tadka"))
        assertEquals(ParsedMealJson.schema, req.schema)
        assertNull(req.image)
    }

    @Test
    fun `returns the decoded meal`() = runTest {
        val parser = AiFoodParser(RecordingTransport(AiReply("end_turn", okJson))) { emptyList() }
        val meal = parser.parse("paneer bhurji")
        assertEquals(MealType.DINNER, meal.meal)
        assertEquals("Paneer bhurji", meal.items.single().name)
    }

    @Test
    fun `blank text fails without calling Claude`() = runTest {
        val transport = RecordingTransport(AiReply("end_turn", okJson))
        val result = runCatching { AiFoodParser(transport) { emptyList() }.parse("   ") }
        assertTrue(result.exceptionOrNull() is FoodParserException)
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun `a refusal becomes a friendly error`() = runTest {
        val parser = AiFoodParser(RecordingTransport(AiReply("refusal", ""))) { emptyList() }
        assertEquals(AppError.REFUSED, (runCatching { parser.parse("rice") }.exceptionOrNull() as FoodParserException).error)
    }

    @Test
    fun `a cut off reply becomes a friendly error`() = runTest {
        val parser = AiFoodParser(RecordingTransport(AiReply("max_tokens", "{\"meal\":"))) { emptyList() }
        assertEquals(AppError.TOO_LONG, (runCatching { parser.parse("rice") }.exceptionOrNull() as FoodParserException).error)
    }

    @Test
    fun `transport failures are wrapped`() = runTest {
        val failing = object : AiTransport {
            override suspend fun send(request: AiRequest): AiReply = throw java.io.IOException("offline")
        }
        val e = runCatching { AiFoodParser(failing) { emptyList() }.parse("rice") }.exceptionOrNull()
        assertEquals(AppError.NETWORK, (e as FoodParserException).error)
    }

    @Test
    fun `missing api key gives a clear message`() = runTest {
        val error = runCatching { MissingKeyTransport.send(AiRequest("s", listOf(ChatMessage.user("u")))) }.exceptionOrNull()
        assertEquals(AppError.NOT_CONFIGURED, (error as FoodParserException).error)
    }

    // ---- photo ----

    @Test
    fun `photo is sent as an image with the meal schema`() = runTest {
        val transport = RecordingTransport(AiReply("end_turn", okJson))
        val bytes = byteArrayOf(1, 2, 3)
        val meal = AiFoodParser(transport) { listOf("Roti") }.parseImage(ImageInput(bytes, "image/jpeg"))
        val req = transport.requests.single()
        assertArrayEquals(bytes, req.image!!.bytes)
        assertEquals("image/jpeg", req.image!!.mediaType)
        assertEquals(ParsedMealJson.schema, req.schema)
        assertTrue(req.system.contains("photo"))
        assertEquals("Paneer bhurji", meal.items.single().name)
    }

    // ---- recipe ----

    private val recipeJson = """{"name":"Mom's dal","servings":4,"serving_unit":"bowl","ingredients":[
        {"name":"Toor dal, dry","quantity":1,"unit":"cup","grams":200,"amount_guessed":false,"usda_search_term":"pigeon peas, raw",
         "per_100g":{"calories":343,"protein":22,"carbs":63,"fat":1.5,"fiber":15,"sugar":0,"sodium_mg":17}},
        {"name":"Ghee","quantity":2,"unit":"tbsp","grams":26,"amount_guessed":false,"usda_search_term":null,
         "per_100g":{"calories":900,"protein":0,"carbs":0,"fat":99.5,"fiber":0,"sugar":0,"sodium_mg":2}}]}"""

    @Test
    fun `recipe is parsed with name, servings and ingredients`() = runTest {
        val transport = RecordingTransport(AiReply("end_turn", recipeJson))
        val r = AiFoodParser(transport) { emptyList() }.parseRecipe("mom's dal: one cup toor dal, two tbsp ghee, makes 4 bowls")
        assertEquals("Mom's dal", r.name)
        assertEquals(4, r.servings)
        assertEquals("bowl", r.servingUnit)
        assertEquals(listOf("Toor dal, dry", "Ghee"), r.ingredients.map { it.name })
        assertEquals(26.0, r.ingredients[1].grams, 0.0)
        assertEquals(RecipeJson.schema, transport.requests.single().schema)
    }

    @Test
    fun `recipe with zero servings becomes one`() = runTest {
        val json = """{"name":"x","servings":0,"serving_unit":"","ingredients":[]}"""
        val r = AiFoodParser(RecordingTransport(AiReply("end_turn", json))) { emptyList() }.parseRecipe("x")
        assertEquals(1, r.servings)
        assertEquals("serving", r.servingUnit)
    }
}

class AiAssistantTest {

    @Test
    fun `sends the day's context and the conversation`() = runTest {
        val transport = RecordingTransport(AiReply("end_turn", "Try paneer bhurji."))
        val history = listOf(ChatMessage.user("What should I eat?"), ChatMessage.assistant("How hungry are you?"), ChatMessage.user("Very"))
        val answer = AiAssistant(transport).ask("You have 951 kcal left.", history)
        assertEquals("Try paneer bhurji.", answer)
        val req = transport.requests.single()
        assertTrue(req.system.contains("You have 951 kcal left."))
        assertEquals(history, req.messages)
        assertNull(req.schema)
    }

    @Test
    fun `refusal gives a friendly answer error`() = runTest {
        val r = runCatching { AiAssistant(RecordingTransport(AiReply("refusal", ""))).ask("ctx", listOf(ChatMessage.user("hi"))) }
        assertTrue(r.exceptionOrNull() is FoodParserException)
    }
}

class AiConfigTest {

    private val gemini = "AIzaSy" + "x".repeat(33)
    private val claude = "sk-ant-api03-" + "a".repeat(80)

    @Test
    fun `gemini is used when its key is set`() {
        assertEquals(AiConfig.Gemini(gemini, "gemini-3.8-flash"), AiConfig.choose(gemini, "gemini-3.8-flash", claude))
    }

    @Test
    fun `newer AQ style google keys are accepted`() {
        val aq = "AQ." + "Ab9_x-".repeat(8)
        assertEquals(AiConfig.Gemini(aq, AiConfig.DEFAULT_GEMINI_MODEL), AiConfig.choose(aq, "", ""))
    }

    @Test
    fun `blank gemini model falls back to the default`() {
        assertEquals(AiConfig.Gemini(gemini, AiConfig.DEFAULT_GEMINI_MODEL), AiConfig.choose(gemini, " ", ""))
    }

    @Test
    fun `claude key is used when there is no gemini key`() {
        assertEquals(AiConfig.ClaudeDirect(claude), AiConfig.choose("", "", claude))
    }

    private val groq = "gsk_" + "Ab12".repeat(13)

    @Test
    fun `groq is used first when its key is set`() {
        assertEquals(
            AiConfig.Groq(groq, AiConfig.DEFAULT_GROQ_MODEL, AiConfig.DEFAULT_GROQ_VISION_MODEL),
            AiConfig.choose(gemini, "", claude, groqKey = groq),
        )
    }

    @Test
    fun `groq models can be changed`() {
        assertEquals(AiConfig.Groq(groq, "openai/gpt-oss-120b", "meta-models/muse-spark-open"),
            AiConfig.choose("", "", "", groqKey = groq, groqModel = "openai/gpt-oss-120b", groqVisionModel = " meta-models/muse-spark-open "))
    }

    @Test
    fun `without a groq key gemini is still used`() {
        assertEquals(AiConfig.Gemini(gemini, AiConfig.DEFAULT_GEMINI_MODEL), AiConfig.choose(gemini, "", "", groqKey = ""))
    }

    @Test
    fun `groq placeholders count as not set`() {
        assertEquals(AiConfig.None, AiConfig.choose("", "", "", groqKey = "gsk_..."))
        assertEquals(AiConfig.None, AiConfig.choose("", "", "", groqKey = "gsk_short"))
        assertEquals(AiConfig.None, AiConfig.choose("", "", "", groqKey = "your-groq-key"))
    }

    @Test
    fun `placeholders count as not set`() {
        assertEquals(AiConfig.None, AiConfig.choose("AIza...", "", "sk-ant-..."))
        assertEquals(AiConfig.None, AiConfig.choose("your-key-here", "", ""))
        assertEquals(AiConfig.None, AiConfig.choose("AQ...", "", ""))
        assertEquals(AiConfig.None, AiConfig.choose("has a space in it and is quite long 12345", "", ""))
        assertEquals(AiConfig.None, AiConfig.choose("", "", ""))
    }
}

class ApiErrorsTest {
    @Test
    fun `http status codes become clear errors`() {
        assertEquals(AppError.BAD_KEY, ApiErrors.forStatus(401))
        assertEquals(AppError.BAD_KEY, ApiErrors.forStatus(403))
        assertEquals(AppError.BUSY, ApiErrors.forStatus(429))
        assertEquals(AppError.BUSY, ApiErrors.forStatus(529))
        assertEquals(AppError.BUSY, ApiErrors.forStatus(503))
        assertEquals(AppError.BAD_REPLY, ApiErrors.forStatus(400))
        assertEquals(AppError.GENERIC, ApiErrors.forStatus(418))
    }

    @Test
    fun `a low credit balance is named as such`() {
        val msg = "{\"type\":\"error\",\"error\":{\"type\":\"invalid_request_error\",\"message\":\"Your credit balance is too low to access the Anthropic API.\"}}"
        assertEquals(AppError.NO_CREDIT, ApiErrors.forStatus(400, msg))
        assertEquals(AppError.BAD_REPLY, ApiErrors.forStatus(400, "{\"message\":\"messages: field required\"}"))
    }

    @Test
    fun `network problems stay network errors`() {
        assertEquals(AppError.NETWORK, ApiErrors.forThrowable(java.net.UnknownHostException("x")))
        assertEquals("a slow reply means busy, not offline", AppError.BUSY, ApiErrors.forThrowable(java.net.SocketTimeoutException("x")))
        assertEquals(AppError.GENERIC, ApiErrors.forThrowable(IllegalStateException("x")))
    }
}
