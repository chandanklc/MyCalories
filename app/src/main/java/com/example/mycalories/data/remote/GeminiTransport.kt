package com.example.mycalories.data.remote

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.Base64

/** Calls Google's Gemini API (generateContent), asking for JSON in the request's schema when it has one. */
class GeminiTransport(
    private val client: HttpClient,
    private val apiKey: String,
    private val model: String,
    private val fallbackModel: String? = null,
) : AiTransport {

    /** Tries the main model; if it's busy or unavailable, tries the fallback model once. */
    override suspend fun send(request: AiRequest): AiReply = try {
        sendTo(model, request)
    } catch (e: FoodParserException) {
        val retry = fallbackModel != null && fallbackModel != model &&
            (e.error == AppError.BUSY || e.error == AppError.MODEL_UNAVAILABLE || e.error == AppError.FREE_LIMIT)
        if (!retry) throw e
        Log.w(TAG, "$model is ${e.error}, trying $fallbackModel")
        sendTo(fallbackModel!!, request)
    }

    private suspend fun sendTo(model: String, request: AiRequest): AiReply {
        val body = buildJsonObject {
            put("systemInstruction", buildJsonObject { put("parts", buildJsonArray { add(textPart(request.system)) }) })
            put("contents", buildJsonArray {
                request.messages.forEachIndexed { i, m ->
                    add(buildJsonObject {
                        put("role", if (m.role == ChatMessage.Role.ASSISTANT) "model" else "user")
                        put("parts", buildJsonArray {
                            val image = request.image
                            if (i == request.messages.lastIndex && image != null) add(buildJsonObject {
                                put("inlineData", buildJsonObject {
                                    put("mimeType", image.mediaType)
                                    put("data", Base64.getEncoder().encodeToString(image.bytes))
                                })
                            })
                            add(textPart(m.text))
                        })
                    })
                }
            })
            put("generationConfig", buildJsonObject {
                put("maxOutputTokens", request.maxTokens)
                request.schema?.let {
                    put("responseMimeType", "application/json")
                    put("responseJsonSchema", toJson(it))
                }
            })
        }

        val response = try {
            client.post("$BASE/$model:generateContent") {
                header("x-goog-api-key", apiKey)
                contentType(ContentType.Application.Json)
                setBody(io.ktor.http.content.TextContent(body.toString(), ContentType.Application.Json))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't reach Gemini", e)
            throw FoodParserException(ApiErrors.forThrowable(e), e)
        }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            Log.w(TAG, "Gemini error ${response.status.value}: $text")
            throw FoodParserException(errorFor(response.status.value, text))
        }
        return parse(text)
    }

    private fun textPart(text: String) = buildJsonObject { put("text", text) }

    private fun parse(text: String): AiReply {
        val root = try { Json.parseToJsonElement(text).jsonObject } catch (e: Exception) {
            throw FoodParserException(AppError.BAD_REPLY, e)
        }
        if (root["promptFeedback"]?.jsonObject?.get("blockReason") != null) return AiReply("refusal", "")
        val candidate = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject ?: return AiReply("refusal", "")
        val reply = candidate["content"]?.jsonObject?.get("parts")?.jsonArray
            ?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }?.joinToString("").orEmpty()
        val stop = when (candidate["finishReason"]?.jsonPrimitive?.contentOrNull) {
            null, "STOP", "FINISH_REASON_UNSPECIFIED" -> "end_turn"
            "MAX_TOKENS" -> "max_tokens"
            else -> "refusal" // SAFETY, PROHIBITED_CONTENT, RECITATION, BLOCKLIST, SPII, OTHER…
        }
        return AiReply(stop, reply)
    }

    companion object {
        const val BASE = "https://generativelanguage.googleapis.com/v1beta/models"
        private const val TAG = "MyCalories"

        fun errorFor(status: Int, body: String): AppError = when {
            body.contains("API_KEY_INVALID") || body.contains("API key not valid", ignoreCase = true) -> AppError.BAD_KEY
            status == 401 || status == 403 -> AppError.BAD_KEY
            status == 429 -> AppError.FREE_LIMIT
            status == 404 -> AppError.MODEL_UNAVAILABLE
            status >= 500 -> AppError.BUSY
            else -> AppError.BAD_REPLY
        }

        /** Plain Kotlin maps and lists (the schemas) as JSON. */
        fun toJson(value: Any?): JsonElement = when (value) {
            null -> JsonNull
            is JsonElement -> value
            is String -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            is Map<*, *> -> JsonObject(value.entries.associate { (k, v) -> k.toString() to toJson(v) })
            is Iterable<*> -> JsonArray(value.map { toJson(it) })
            else -> JsonPrimitive(value.toString())
        }
    }
}
