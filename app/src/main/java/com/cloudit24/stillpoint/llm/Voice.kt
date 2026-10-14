package com.cloudit24.stillpoint.llm

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * Ears and mouth for the assistant, from Android itself: speech recognition (on the phone where the phone
 * can) and text-to-speech. Nothing of ours leaves the phone; what Android's recogniser does is Android's.
 */
class Voice(private val context: Context) {
    var listening by mutableStateOf(false)
    var heard by mutableStateOf("")       // partial text while you speak
    var error by mutableStateOf("")

    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pending: String? = null

    private var onDevice = false
    private var avoidOnDevice = false

    fun listen(onResult: (String) -> Unit) = listen(onResult, preferOffline = true)

    private fun listen(onResult: (String) -> Unit, preferOffline: Boolean) {
        stopSpeaking()
        val r = recognizer ?: run {
            // The phone's own recogniser keeps speech on the phone; otherwise the default (Google's) one.
            onDevice = !avoidOnDevice && android.os.Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
            when {
                onDevice -> SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                SpeechRecognizer.isRecognitionAvailable(context) -> SpeechRecognizer.createSpeechRecognizer(context)
                else -> { error = "No speech recognition on this phone. Install Google's Speech Services."; return }
            }.also { recognizer = it }
        }
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { listening = true; heard = ""; error = "" }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { listening = false }
            override fun onError(code: Int) {
                listening = false
                // No offline language pack: try again, letting the recogniser go online.
                if (preferOffline && !onDevice && code in listOf(12, 13)) { listen(onResult, preferOffline = false); return }
                // The phone's own recogniser refused (error 10, "too many requests", is its usual way): use the default one instead.
                if (onDevice && code in listOf(10, 12, 13) && SpeechRecognizer.isRecognitionAvailable(context)) {
                    runCatching { recognizer?.destroy() }; recognizer = null; onDevice = false; avoidOnDevice = true
                    listen(onResult, preferOffline = false); return
                }
                error = when (code) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Didn't catch that."
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is needed."
                    SpeechRecognizer.ERROR_AUDIO -> "The microphone is busy."
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech needs a connection right now."
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "The recogniser is busy. Try again."
                    10 -> "The recogniser refused. Check Google's Speech Services is on in the phone's Settings."
                    12, 13 -> "No speech pack for your language. Add one in the phone's Settings, Languages."
                    else -> "Couldn't listen (error $code)."
                }
            }
            override fun onResults(results: Bundle?) {
                listening = false
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                heard = text
                if (text.isNotBlank()) onResult(text)
            }
            override fun onPartialResults(partialResults: Bundle?) {
                heard = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, preferOffline)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        runCatching { r.startListening(intent) }.onFailure { error = "Couldn't start listening."; listening = false }
    }

    private var onDone: (() -> Unit)? = null

    /** Says [text]; [then] runs when it has finished (on the main thread). */
    fun speak(text: String, then: (() -> Unit)? = null) {
        if (text.isBlank()) { then?.invoke(); return }
        onDone = then
        if (tts == null) {
            pending = text
            tts = TextToSpeech(context) { status ->
                ttsReady = status == TextToSpeech.SUCCESS
                if (ttsReady) {
                    tts?.language = Locale.getDefault()
                    tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                        override fun onStart(id: String?) {}
                        override fun onError(id: String?) { finish() }
                        override fun onDone(id: String?) { finish() }
                        private fun finish() { android.os.Handler(android.os.Looper.getMainLooper()).post { onDone?.invoke(); onDone = null } }
                    })
                    pending?.let { say(it) }
                    pending = null
                }
            }
            return
        }
        if (ttsReady) say(text) else pending = text
    }

    private fun say(text: String) { tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "stillpoint") }

    fun stopSpeaking() { tts?.stop() }

    fun release() {
        runCatching { recognizer?.destroy() }; recognizer = null
        runCatching { tts?.shutdown() }; tts = null; ttsReady = false
        listening = false
    }
}
