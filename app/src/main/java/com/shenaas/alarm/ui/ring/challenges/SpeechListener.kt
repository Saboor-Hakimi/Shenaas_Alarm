package com.shenaas.alarm.ui.ring.challenges

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

/**
 * Keeps a [SpeechRecognizer] listening for as long as it's started, restarting it after every
 * utterance or timeout. Prefers the on-device recognizer (Pixels ship one) for speed and offline use.
 * Must be used from the main thread.
 */
class SpeechListener(
    private val context: Context,
    private val onHeard: (List<String>) -> Unit,
    private val onLevel: (Float) -> Unit,
    private val onFatal: () -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var onDevice = SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
    private var active = false
    private var hardErrors = 0

    /** Words the recognizer should expect. Biasing makes sleepy or accented speech land on them far more often. */
    var bias: List<String> = emptyList()

    private fun intent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
        .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
        .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2_500L)
        .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2_500L)
        .putExtra(RecognizerIntent.EXTRA_MASK_OFFENSIVE_WORDS, false)
        .apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && bias.isNotEmpty()) {
                putStringArrayListExtra(RecognizerIntent.EXTRA_BIASING_STRINGS, ArrayList(bias.flatMap { it.split(' ') }.distinct()))
            }
        }

    fun start() {
        if (active) return
        active = true
        create()
        listen()
    }

    fun stop() {
        active = false
        handler.removeCallbacksAndMessages(null)
        recognizer?.runCatching { cancel(); destroy() }
        recognizer = null
    }

    private fun create() {
        recognizer?.runCatching { destroy() }
        recognizer = (if (onDevice) SpeechRecognizer.createOnDeviceSpeechRecognizer(context) else SpeechRecognizer.createSpeechRecognizer(context))
            .apply { setRecognitionListener(listener) }
    }

    private fun listen(delayMs: Long = 0) {
        handler.postDelayed({
            if (!active) return@postDelayed
            try {
                recognizer?.startListening(intent())
            } catch (e: Exception) {
                Log.w(TAG, "startListening failed", e)
                fatal()
            }
        }, delayMs)
    }

    private fun fatal() {
        if (!active) return
        stop()
        onFatal()
    }

    private fun deliver(bundle: Bundle?) {
        val texts = bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.filter { it.isNotBlank() }.orEmpty()
        if (texts.isNotEmpty()) onHeard(texts)
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onPartialResults(partialResults: Bundle?) = deliver(partialResults)

        override fun onResults(results: Bundle?) {
            deliver(results)
            hardErrors = 0
            listen()
        }

        override fun onError(error: Int) {
            if (!active) return
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> listen()
                SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                    if (onDevice) {
                        // No on-device model for English: fall back to the regular recognizer.
                        onDevice = false
                        create()
                        listen()
                    } else {
                        fatal()
                    }
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> fatal()
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                    recognizer?.cancel()
                    listen(300)
                }
                else -> {
                    Log.w(TAG, "Recognizer error $error")
                    if (++hardErrors > 6) fatal() else listen(200)
                }
            }
        }
    }

    private companion object {
        const val TAG = "SpeechListener"
    }
}
