package com.example.mycalories.data.remote

import android.util.Base64
import android.util.Log
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.models.messages.Base64ImageSource
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.JsonOutputFormat
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Calls Claude with the official Anthropic Java SDK. */
class AnthropicTransport(
    private val client: AnthropicClient,
    private val model: String = MODEL,
) : AiTransport {

    override suspend fun send(request: AiRequest): AiReply = withContext(Dispatchers.IO) {
        val builder = MessageCreateParams.builder()
            .model(model)
            .maxTokens(request.maxTokens)
            .system(request.system)

        request.messages.forEachIndexed { i, m ->
            val isLast = i == request.messages.lastIndex
            when {
                m.role == ChatMessage.Role.ASSISTANT -> builder.addAssistantMessage(m.text)
                isLast && request.image != null -> builder.addUserMessageOfContentBlockParams(
                    listOf(
                        ContentBlockParam.ofImage(
                            Base64ImageSource.builder()
                                .data(Base64.encodeToString(request.image.bytes, Base64.NO_WRAP))
                                .mediaType(Base64ImageSource.MediaType.of(request.image.mediaType))
                                .build(),
                        ),
                        ContentBlockParam.ofText(m.text),
                    ),
                )
                else -> builder.addUserMessage(m.text)
            }
        }

        val output = OutputConfig.builder()
            // Picking foods out of one sentence or photo is quick work: low effort keeps it fast.
            .effort(OutputConfig.Effort.LOW)
        request.schema?.let { schema ->
            val s = JsonOutputFormat.Schema.builder().apply {
                schema.forEach { (k, v) -> putAdditionalProperty(k, JsonValue.from(v)) }
            }.build()
            output.format(JsonOutputFormat.builder().schema(s).build())
        }
        builder.outputConfig(output.build())

        val message = try {
            client.messages().create(builder.build())
        } catch (e: AnthropicServiceException) {
            // e.g. 401 for a wrong key: say so instead of blaming the internet.
            Log.w(TAG, "Claude API error ${e.statusCode()}", e)
            throw FoodParserException(ApiErrors.forStatus(e.statusCode(), e.message), e)
        } catch (e: AnthropicIoException) {
            Log.w(TAG, "Couldn't reach Claude", e)
            throw FoodParserException(AppError.NETWORK, e)
        }
        val text = message.content().mapNotNull { block -> block.text().orElse(null)?.text() }.joinToString("")
        AiReply(stopReason = message.stopReason().orElse(null)?.asString(), text = text)
    }

    companion object {
        const val MODEL = "claude-opus-5-5"
        private const val TAG = "MyCalories"

        fun direct(apiKey: String): AiTransport = AnthropicTransport(AnthropicOkHttpClient.builder().apiKey(apiKey).build())
    }
}
