package com.ravanx.jarvis

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import org.json.JSONObject
import java.io.File
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * 🔊 VOICE — JARVIS ki awaaz
 *
 * Do rasta:
 *   1. SARVAM — asli Indian awaaz, Hinglish bahut achhi bolta hai
 *   2. Android ka apna TTS — backup, offline bhi chalta hai
 *
 * ⚠️ Sarvam ko internet chahiye aur ~1 second lagta hai. Isliye
 *    agar wo fail ho to turant Android wale pe gir jaata hai —
 *    JARVIS kabhi chup nahi rehta.
 */
class Voice(private val ctx: Context) {

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var player: MediaPlayer? = null
    private val keys = Keys(ctx)

    init {
        tts = TextToSpeech(ctx) { st ->
            if (st == TextToSpeech.SUCCESS) {
                // Hindi try karo, na mile to English
                val r = tts?.setLanguage(Locale("hi", "IN"))
                if (r == TextToSpeech.LANG_MISSING_DATA ||
                    r == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.language = Locale.US
                }
                tts?.setSpeechRate(1.02f)
                tts?.setPitch(0.92f)      // thoda bhaari — JARVIS jaisa
                ttsReady = true
            }
        }
    }

    /** Bolo. Sarvam pehle, phir Android. */
    fun say(text: String, onDone: (() -> Unit)? = null) {
        if (text.isBlank()) { onDone?.invoke(); return }
        stop()
        if (keys.useSarvam() && keys.sarvam().isNotBlank()) {
            Thread {
                val f = sarvamTTS(text)
                if (f != null) playFile(f, onDone)
                else android(text, onDone)
            }.start()
        } else android(text, onDone)
    }

    private fun android(text: String, onDone: (() -> Unit)?) {
        if (!ttsReady) { onDone?.invoke(); return }
        tts?.setOnUtteranceProgressListener(
            object : android.speech.tts.UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) { onDone?.invoke() }
                @Deprecated("old api")
                override fun onError(id: String?) { onDone?.invoke() }
            })
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jv")
    }

    /**
     * Sarvam se awaaz banwao.
     * ⚠️ Sarvam base64 WAV deta hai — usko file me likhna padta hai.
     */
    private fun sarvamTTS(text: String): File? = try {
        val body = JSONObject().apply {
            put("text", text.take(480))
            put("target_language_code", "hi-IN")
            put("speaker", keys.voice())
            put("model", "bulbul:v3")
            put("speech_sample_rate", 22050)
        }
        val c = (URL("https://api.sarvam.ai/text-to-speech")
            .openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 8000
            readTimeout = 20000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("api-subscription-key", keys.sarvam())
        }
        OutputStreamWriter(c.outputStream).use { it.write(body.toString()) }
        val out = c.inputStream.bufferedReader().use { it.readText() }
        val b64 = JSONObject(out).getJSONArray("audios").getString(0)
        val bytes = android.util.Base64.decode(b64,
            android.util.Base64.DEFAULT)
        File(ctx.cacheDir, "jv_say.wav").apply { writeBytes(bytes) }
    } catch (e: Exception) { null }

    private fun playFile(f: File, onDone: (() -> Unit)?) {
        try {
            player = MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(
                        AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build())
                setDataSource(f.absolutePath)
                setOnCompletionListener {
                    it.release(); player = null; onDone?.invoke()
                }
                setOnErrorListener { _, _, _ ->
                    onDone?.invoke(); true
                }
                prepare()
                start()
            }
        } catch (e: Exception) { onDone?.invoke() }
    }

    fun stop() {
        try { tts?.stop() } catch (e: Exception) {}
        try { player?.release() } catch (e: Exception) {}
        player = null
    }

    fun shutdown() {
        stop()
        try { tts?.shutdown() } catch (e: Exception) {}
        tts = null
    }
}
