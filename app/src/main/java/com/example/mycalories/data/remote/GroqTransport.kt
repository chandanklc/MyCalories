package com.example.mycalories.data.remote

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.Base64

/**
 * Calls Groq's OpenAI-compatible chat completions API (free tier, very fast).
 * Text goes to [model] (falling back to [fallbackModel] when busy or limited); photos go to [visionModel].
 */
class GroqTransport(
    private val client: HttpClient,
    private val apiKey: String,
    private val model: String,
    private val fallbackModel: String? = null,
    private val visionModel: String = AiConfig.DEFAULT_GROQ_VISION_MODEL,
) : AiTransport {

    override suspend fun send(request: AiRequest): AiReply {
        if (request.image != null) return sendTo(visionModel, request)
        return try {
            sendTo(model, request)
        } catch (e: FoodParserException) {
            val retry = fallbackModel != null && fallbackModel != model && e.error in RETRY_ON
            if (!retry) throw e
            Log.w(TAG, "$model gave ${e.error}, trying $fallbackModel")
            sendTo(fallbackModel!!, request)
        }
    }

    private suspend fun sendTo(model: String, request: AiRequest): AiReply {
        val strict = supportsStrictSchema(model)
        val schema = request.schema
        val system = if (schema != null && !strict) {
            request.system + "\n\nReply with only a JSON object that matches this JSON Schema:\n" + GeminiTransport.toJson(schema)
        } else request.system

        val body = buildJsonObject {
            put("model", model)
            put("messages", buildJsonArray {
                add(buildJsonObject { put("role", "system"); put("content", system) })
                request.messages.forEachIndexed { i, m ->
                    add(buildJsonObject {
                        put("role", if (m.role == ChatMessage.Role.ASSISTANT) "assistant" else "user")
                        val image = request.image
                        if (i == request.messages.lastIndex && image != null) {
                            put("content", buildJsonArray {
                                add(buildJsonObject { put("type", "text"); put("text", m.text) })
                                add(buildJsonObject {
                                    put("type", "image_url")
                                    put("image_url", buildJsonObject {
                                        put("url", "data:${image.mediaType};base64," + Base64.getEncoder().encodeToString(image.bytes))
                                    })
                                })
                            })
                        } else put("content", m.text)
                    })
                }
            })
            put("max_completion_tokens", request.maxTokens)
            // gpt-oss thinks before answering; a little is plenty for picking foods out of a sentence.
            if (model.startsWith("openai/gpt-oss")) put("reasoning_effort", "low")
            if (schema != null) put("response_format", buildJsonObject {
                if (strict) {
                    put("type", "json_schema")
                    put("json_schema", buildJsonObject {
                        put("name", "reply")
                        put("strict", true)
                        put("schema", GeminiTransport.toJson(schema))
                    })
                } else put("type", "json_object")
            })
        }

        val response = try {
            client.post(URL) {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                setBody(TextContent(body.toString(), ContentType.Application.Json))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't reach Groq", e)
            throw FoodParserException(ApiErrors.forThrowable(e), e)
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            Log.w(TAG, "Groq error ${response.status.value} from $model: $text")
            throw FoodParserException(errorFor(response.status.value, text))
        }
        return parse(text)
    }

    private fun parse(text: String): AiReply {
        val choice = try {
            Json.parseToJsonElement(text).jsonObject["choices"]?.jsonArray?.firstOrNull()?.jsonObject
        } catch (e: Exception) {
            throw FoodParserException(AppError.BAD_REPLY, e)
        } ?: return AiReply("refusal", "")
        val message = choice["message"]?.jsonObject
        val refusal = message?.get("refusal")?.takeIf { it != JsonNull }?.jsonPrimitive?.contentOrNull
        if (!refusal.isNullOrBlank()) return AiReply("refusal", "")
        val content = message?.get("content")?.takeIf { it != JsonNull }?.jsonPrimitive?.contentOrNull.orEmpty()
        val stop = when (choice["finish_reason"]?.jsonPrimitive?.contentOrNull) {
            null, "stop" -> "end_turn"
            "length" -> "max_tokens"
            else -> "refusal" // content_filter
        }
        return AiReply(stop, stripThinking(content))
    }

    companion object {
        const val URL = "https://api.groq.com/openai/v1/chat/completions"
        private const val TAG = "MyCalories"
        private val RETRY_ON = setOf(AppError.BUSY, AppError.MODEL_UNAVAILABLE, AppError.FREE_LIMIT, AppError.BAD_REPLY)
        private val THINKING = Regex("<think>.*?</think>", RegexOption.DOT_MATCHES_ALL)

        /** Models Groq can force to match a JSON Schema exactly (constrained decoding). */
        fun supportsStrictSchema(model: String): Boolean =
            listOf("openai/gpt-oss-", "qwen/qwen3", "meta-models/").any { model.startsWith(it) } &&
                !model.contains("safeguard")

        /** Some models write their reasoning in <think> tags before the answer. */
        fun stripThinking(text: String): String = text.replace(THINKING, "").trim()

        fun errorFor(status: Int, body: String): AppError = when {
            status == 401 || status == 403 || body.contains("invalid_api_key") -> AppError.BAD_KEY
            status == 429 -> AppError.FREE_LIMIT
            status == 404 || body.contains("model_not_found") || body.contains("decommissioned") ||
                body.contains("does not exist") -> AppError.MODEL_UNAVAILABLE
            status == 498 || status >= 500 -> AppError.BUSY
            else -> AppError.BAD_REPLY // 400 / 413 / 422, including JSON that didn't fit the schema
        }
    }
}
