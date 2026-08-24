package com.ravanx.jarvis

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * 👂 WAKE SERVICE — "Jarvis" sunne wali service
 *
 * ⚠️ IMANDARI SE 3 BAATEIN:
 *
 * 1. Android me asli "always-on wake word" (jaise Google ka
 *    "Hey Google") ke liye phone ke chip ka special hardware
 *    chahiye, jo sirf Google/Samsung ko milta hai. Normal app
 *    ye nahi kar sakti.
 *
 * 2. Isliye ye service baar-baar chhota-chhota sunti hai aur
 *    check karti hai ki "jarvis" bola gaya ya nahi. Kaam karta
 *    hai, par BATTERY zyada khaata hai.
 *
 * 3. Notification hamesha dikhega — Android ka niyam hai, hata
 *    nahi sakte. Isse pata chalta hai ki mic chalu hai.
 *
 * Battery bachane ke liye Settings me band kar sakte hain.
 */
class WakeService : Service() {

    private var rec: SpeechRecognizer? = null
    private val h = Handler(Looper.getMainLooper())
    private var running = false
    private var fails = 0

    companion object {
        const val CH = "jarvis_wake"
        const val ID = 7001

        fun start(c: Context) {
            val i = Intent(c, WakeService::class.java)
            if (Build.VERSION.SDK_INT >= 26)
                c.startForegroundService(i) else c.startService(i)
        }

        fun stop(c: Context) {
            c.stopService(Intent(c, WakeService::class.java))
        }
    }

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        makeChannel()
        startForeground(ID, notif("Sun raha hoon — \"Jarvis\" boliye"))
        running = true
        h.postDelayed({ listen() }, 700)
    }

    private fun makeChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CH, "JARVIS",
                NotificationManager.IMPORTANCE_LOW).apply {
                description = "Wake word sunne ke liye"
                setShowBadge(false)
            }
            (getSystemService(NOTIFICATION_SERVICE)
                as NotificationManager).createNotificationChannel(ch)
        }
    }

    private fun notif(txt: String): Notification {
        val pi = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or
                PendingIntent.FLAG_UPDATE_CURRENT)
        val bld = if (Build.VERSION.SDK_INT >= 26)
            Notification.Builder(this, CH) else
            @Suppress("DEPRECATION") Notification.Builder(this)
        return bld.setContentTitle("JARVIS")
            .setContentText(txt)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    private fun listen() {
        if (!running) return
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            stopSelf(); return
        }
        try { rec?.destroy() } catch (e: Exception) {}
        rec = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(p: Bundle?) { fails = 0 }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(v: Float) {}
                override fun onBufferReceived(p: ByteArray?) {}
                override fun onEndOfSpeech() {}

                override fun onError(e: Int) {
                    // Chup rehne pe bhi "error" aata hai — normal hai.
                    // Par lagataar fail ho to thoda ruk kar try karo,
                    // warna battery jal jayegi.
                    fails++
                    val wait = when {
                        fails > 12 -> 8000L
                        fails > 5  -> 3000L
                        else       -> 900L
                    }
                    h.postDelayed({ listen() }, wait)
                }

                override fun onResults(r: Bundle?) {
                    val heard = r?.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION)
                        ?: arrayListOf()
                    check(heard)
                    h.postDelayed({ listen() }, 600)
                }

                override fun onPartialResults(r: Bundle?) {
                    val heard = r?.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION)
                        ?: arrayListOf()
                    if (hasWake(heard)) {
                        try { rec?.cancel() } catch (ex: Exception) {}
                        wake()
                    }
                }
                override fun onEvent(t: Int, p: Bundle?) {}
            })
        }
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        try { rec?.startListening(i) } catch (e: Exception) {
            h.postDelayed({ listen() }, 3000)
        }
    }

    /** "jarvis" ke jitne tarike log bolte/likhte hain */
    private fun hasWake(list: List<String>): Boolean {
        val w = Keys(this).wakeWord().lowercase()
        val alt = listOf(w, "jarvis", "jaarvis", "jarvees", "jaravis",
            "jarwis", "जार्विस", "जारविस", "jervis", "service")
        return list.any { s ->
            val t = s.lowercase()
            alt.any { t.contains(it) }
        }
    }

    private fun check(list: List<String>) {
        if (hasWake(list)) wake()
    }

    private fun wake() {
        // JARVIS khol do aur turant sunna shuru
        startActivity(Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                     Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("listen", true)
        })
    }

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        h.removeCallbacksAndMessages(null)
        try { rec?.destroy() } catch (e: Exception) {}
        super.onDestroy()
    }
}
