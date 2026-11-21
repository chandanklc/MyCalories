package com.example.mycalories.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.example.mycalories.data.remote.AppError
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

sealed interface VoiceEvent {
    data object Ready : VoiceEvent
    data class Partial(val text: String) : VoiceEvent
    data class Final(val text: String) : VoiceEvent
    data class Error(val error: AppError) : VoiceEvent
}

/** Speech to text. Abstracted so view models can be tested without a microphone. */
interface VoiceInput {
    fun listen(): Flow<VoiceEvent>
    fun stop()
}

/** Android's built-in speech recognizer. Indian English, which also copes with mixed Hindi words. */
class AndroidVoiceInput(private val context: Context, private val language: () -> String = { "en-IN" }) : VoiceInput {

    private var recognizer: SpeechRecognizer? = null

    override fun listen(): Flow<VoiceEvent> = callbackFlow {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            trySend(VoiceEvent.Error(AppError.SPEECH_UNAVAILABLE))
            close()
            return@callbackFlow
        }
        val sr = SpeechRecognizer.createSpeechRecognizer(context)
        recognizer = sr
        sr.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { trySend(VoiceEvent.Ready) }
            override fun onPartialResults(partialResults: Bundle?) {
                firstResult(partialResults)?.let { trySend(VoiceEvent.Partial(it)) }
            }
            override fun onResults(results: Bundle?) {
                trySend(VoiceEvent.Final(firstResult(results).orEmpty()))
                close()
            }
            override fun onError(error: Int) {
                trySend(VoiceEvent.Error(errorFor(error)))
                close()
            }
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        sr.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, language())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
            },
        )
        awaitClose {
            sr.destroy()
            if (recognizer === sr) recognizer = null
        }
    }

    override fun stop() {
        recognizer?.stopListening()
    }

    private fun firstResult(bundle: Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }

    private fun errorFor(code: Int): AppError = when (code) {
        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> AppError.NO_SPEECH
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> AppError.MIC_PERMISSION
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> AppError.NETWORK
        else -> AppError.GENERIC
    }
}
