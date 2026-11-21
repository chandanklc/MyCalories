package com.example.mycalories.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One turn of a conversation with the AI. */
data class ChatMessage(val role: Role, val text: String) {
    enum class Role { USER, ASSISTANT }

    companion object {
        fun user(text: String) = ChatMessage(Role.USER, text)
        fun assistant(text: String) = ChatMessage(Role.ASSISTANT, text)
    }
}

class ImageInput(val bytes: ByteArray, val mediaType: String)

/** Everything one AI call needs. A [schema] asks for JSON in exactly that shape. */
data class AiRequest(
    val system: String,
    val messages: List<ChatMessage>,
    val image: ImageInput? = null,
    val schema: Map<String, Any>? = null,
    val maxTokens: Long = 4000,
)

/** Raw reply from the AI: why it stopped ("end_turn", "max_tokens" or "refusal"), and its text. */
data class AiReply(val stopReason: String?, val text: String)

/** Sends one request to an AI service (Gemini or Claude). Kept separate so callers can be tested without the network. */
interface AiTransport {
    suspend fun send(request: AiRequest): AiReply
}

/** Used when nothing is configured, so the app explains what to do instead of crashing. */
object MissingKeyTransport : AiTransport {
    override suspend fun send(request: AiRequest): AiReply =
        throw FoodParserException(AppError.NOT_CONFIGURED)
}

/** Sends a request and turns network problems and refusals into messages the user can read. */
internal suspend fun AiTransport.call(request: AiRequest): String {
    val reply = try {
        send(request)
    } catch (e: FoodParserException) {
        throw e
    } catch (e: Exception) {
        throw FoodParserException(ApiErrors.forThrowable(e), e)
    }
    when (reply.stopReason) {
        "refusal" -> throw FoodParserException(AppError.REFUSED)
        "max_tokens" -> throw FoodParserException(AppError.TOO_LONG)
    }
    return reply.text
}

/** [FoodParser] backed by an AI service. */
class AiFoodParser(
    private val transport: AiTransport,
    private val knownFoodNames: suspend () -> List<String>,
) : FoodParser {

    override suspend fun parse(text: String): ParsedMeal {
        val spoken = text.trim()
        if (spoken.isEmpty()) throw FoodParserException(AppError.NO_SPEECH)
        val reply = transport.call(AiRequest(systemPrompt(knownFoodNames()), listOf(ChatMessage.user(spoken)), schema = ParsedMealJson.schema))
        return ParsedMealJson.decode(reply)
    }

    override suspend fun parseImage(image: ImageInput): ParsedMeal {
        val system = systemPrompt(knownFoodNames()) + "\n" + PHOTO_NOTE
        val reply = transport.call(
            AiRequest(system, listOf(ChatMessage.user("What food is in this photo, and how much?")), image = image, schema = ParsedMealJson.schema),
        )
        return ParsedMealJson.decode(reply)
    }

    override suspend fun parseRecipe(text: String): ParsedRecipe {
        val spoken = text.trim()
        if (spoken.isEmpty()) throw FoodParserException(AppError.NO_SPEECH)
        val reply = transport.call(AiRequest(RECIPE_PROMPT, listOf(ChatMessage.user(spoken)), schema = RecipeJson.schema))
        return RecipeJson.decode(reply)
    }

    companion object {
        private const val PHOTO_NOTE =
            "This time the food is in a photo instead of words. Identify each food on the plate and estimate its amount " +
                "from what you can see, using the plate and cutlery for scale. Set amount_guessed to true for any amount you are unsure of. " +
                "meal is null unless the photo makes it obvious."

        private val RECIPE_PROMPT = """
            You turn a spoken home recipe into ingredients for a calorie tracker. The cook is usually in India.
            Return the recipe name, how many servings it makes (servings) and what a serving is called (serving_unit: bowl, katori, plate, piece…).
            For each ingredient return the raw amount used for the whole recipe: quantity, unit, grams, and nutrients per 100 g of the ingredient
            as used (dry dal, raw vegetables, oil or ghee). Set usda_search_term for plain ingredients, otherwise null.
            Units: energy in kcal; protein, carbs, fat, fiber and sugar in g; sodium, iron, calcium, vitamin C and potassium in mg; vitamin B12 and vitamin D in µg.
            If they don't say how many servings, use 4.
        """.trimIndent()

        fun systemPrompt(knownFoods: List<String>): String = buildString {
            appendLine("You turn what a person says about food they ate into data for a calorie tracker.")
            appendLine("The person is usually in India and may mix English and Hindi words.")
            appendLine()
            appendLine("For each food they mention, return:")
            appendLine("- name: a short common English name, e.g. \"Roti\", \"Dal tadka\", \"Boiled egg\". If a known food below matches, use its exact name.")
            appendLine("- quantity and unit as they said them (piece, bowl, katori, cup, glass, plate, slice, tbsp, tsp, g, ml).")
            appendLine("- grams: your best estimate of the total weight eaten, using typical Indian home portions (1 roti ≈ 40 g, 1 katori ≈ 150 g, 1 bowl ≈ 200 g, 1 cup cooked rice ≈ 158 g, 1 glass ≈ 250 ml).")
            appendLine("- amount_guessed: true only if they gave no amount and you assumed one serving.")
            appendLine("- usda_search_term: for a plain single-ingredient food (fruit, egg, milk, plain rice, nuts) a USDA FoodData Central search phrase such as \"rice, white, cooked\"; null for mixed dishes, recipes and Indian preparations.")
            appendLine("- per_100g: your best estimate of nutrients per 100 g as the food is usually prepared (energy in kcal; protein, carbs, fat, fiber and sugar in g; sodium, iron, calcium, vitamin C and potassium in mg; vitamin B12 and vitamin D in µg).")
            appendLine("If ingredients of one dish are listed, return the dish as one food unless they clearly ate them separately.")
            appendLine()
            appendLine("meal: breakfast, lunch, snack or dinner only if they said which meal or clearly implied it (\"this morning\", \"at night\"); otherwise null.")
            appendLine("If there is no food, return an empty items list.")
            if (knownFoods.isNotEmpty()) {
                appendLine()
                appendLine("Known foods: " + knownFoods.joinToString(", "))
            }
        }
    }
}

object RecipeJson {
    @Serializable
    private data class RecipeDto(
        val name: String = "",
        val servings: Int = 1,
        @SerialName("serving_unit") val servingUnit: String = "serving",
        val ingredients: List<ParsedMealJson.ItemDto> = emptyList(),
    )

    fun decode(text: String): ParsedRecipe {
        val dto = try {
            ParsedMealJson.json.decodeFromString(RecipeDto.serializer(), text)
        } catch (e: Exception) {
            throw FoodParserException(AppError.BAD_REPLY, e)
        }
        return ParsedRecipe(
            name = dto.name.trim().ifBlank { "My recipe" },
            servings = dto.servings.coerceAtLeast(1),
            servingUnit = dto.servingUnit.trim().ifBlank { "serving" },
            ingredients = dto.ingredients.filter { it.name.isNotBlank() }.map { with(ParsedMealJson) { it.toParsed() } },
        )
    }

    val schema: Map<String, Any> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "name" to mapOf("type" to "string"),
            "servings" to mapOf("type" to "integer"),
            "serving_unit" to mapOf("type" to "string"),
            "ingredients" to mapOf("type" to "array", "items" to ParsedMealJson.itemSchema),
        ),
        "required" to listOf("name", "servings", "serving_unit", "ingredients"),
        "additionalProperties" to false,
    )
}

/** Answers questions about the user's own eating, using a summary of their log as context. */
class AiAssistant(private val transport: AiTransport) {

    suspend fun ask(context: String, conversation: List<ChatMessage>): String =
        transport.call(AiRequest(SYSTEM + "\n\n" + context, conversation, maxTokens = 2000)).trim()

    companion object {
        private val SYSTEM = """
            You are the helper inside a calorie-tracking app used mostly in India. Answer questions about the user's food using
            the summary of their log below. Be brief and practical: short sentences, plain words, at most a few bullet points.
            Suggest everyday Indian home foods with portions and their calories and protein. You are not a doctor: for medical
            conditions, medicines or supplements, suggest they ask a doctor or dietitian.
        """.trimIndent()
    }
}

/** Which AI service the app uses: Gemini (free tier), Claude through your own server, Claude with a key, or none. */
sealed interface AiConfig {
    data class Gemini(val apiKey: String, val model: String) : AiConfig
    data class ClaudeServer(val url: String, val appToken: String) : AiConfig
    data class ClaudeDirect(val apiKey: String) : AiConfig
    data object None : AiConfig

    companion object {
        /** Fast, with the most free requests a day; plenty for picking foods out of a sentence or photo. */
        const val DEFAULT_GEMINI_MODEL = "gemini-3.5-flash-lite"
        /** Tried when the main model is busy or slow. */
        const val FALLBACK_GEMINI_MODEL = "gemini-3.8-flash"

        fun choose(geminiKey: String, geminiModel: String, anthropicKey: String, proxyUrl: String, proxyToken: String): AiConfig = when {
            looksLikeGeminiKey(geminiKey) -> Gemini(geminiKey.trim(), geminiModel.trim().ifBlank { DEFAULT_GEMINI_MODEL })
            proxyUrl.isNotBlank() -> ClaudeServer(proxyUrl.trim(), proxyToken.trim())
            looksLikeClaudeKey(anthropicKey) -> ClaudeDirect(anthropicKey.trim())
            else -> None
        }

        /**
         * Google issues keys in more than one format ("AIza…", newer "AQ.…"), so check the shape rather than
         * the prefix: long, no spaces, and not a copied placeholder like "AIza...".
         */
        fun looksLikeGeminiKey(key: String): Boolean {
            val k = key.trim()
            return k.length >= 30 && !k.contains("...") && k.all { it.isLetterOrDigit() || it in "-_." }
        }

        /** Real Anthropic keys start with "sk-ant-" and are long; this catches placeholders like "sk-ant-...". */
        fun looksLikeClaudeKey(key: String): Boolean {
            val k = key.trim()
            return k.startsWith("sk-ant-") && k.length >= 40 && !k.contains("...")
        }
    }
}

/** Turns failures from the Claude API into errors the user can act on. */
object ApiErrors {
    fun forStatus(code: Int, body: String? = null): AppError = when {
        body?.contains("credit balance", ignoreCase = true) == true -> AppError.NO_CREDIT
        else -> forCode(code)
    }

    private fun forCode(code: Int): AppError = when (code) {
        401, 403 -> AppError.BAD_KEY
        429, 500, 502, 503, 504, 529 -> AppError.BUSY
        400, 413, 422 -> AppError.BAD_REPLY
        else -> AppError.GENERIC
    }

    fun forThrowable(e: Throwable): AppError = when {
        e is FoodParserException -> e.error
        // Connected but no answer in time: the service is busy, the phone is online.
        generateSequence(e) { it.cause }.any { it is java.net.SocketTimeoutException || it.javaClass.simpleName.contains("Timeout") } -> AppError.BUSY
        generateSequence(e) { it.cause }.any { it is java.io.IOException } -> AppError.NETWORK
        else -> AppError.GENERIC
    }
}
