package com.ravanx.jarvis

import android.Manifest
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

/**
 * 🎙️ VOICE ACTIVITY — bubble ka asli dimaag
 *
 * ⚠️ SABSE BADA SABAK:
 *    Android 11+ me SpeechRecognizer ek background Service se
 *    THEEK SE NAHI CHALTA. Mic khulta hai par kuch sunai nahi
 *    deta — bilkul wahi jo aapke saath ho raha tha.
 *
 *    Isliye ab bubble tap karne par YE Activity khulti hai —
 *    poori transparent, sirf ek chhota card dikhta hai. Activity
 *    ke andar mic 100% kaam karta hai.
 *
 *    User ko lagta hai bubble hi sun raha hai. Kaam khatam,
 *    ye khud band ho jaati hai.
 */
class VoiceActivity : AppCompatActivity() {

    private lateinit var voice: Voice
    private var rec: SpeechRecognizer? = null
    private val h = Handler(Looper.getMainLooper())

    private var ring: TextView? = null
    private var icon: TextView? = null
    private var line: TextView? = null
    private var wave: TextView? = null
    private var card: LinearLayout? = null

    private var spin: ValueAnimator? = null
    private var partial = ""
    private var busy = false
    private var keepOn = true
    private var silence: Runnable? = null

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Lock screen pe bhi khule
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.addFlags(
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
        window.setBackgroundDrawable(
            android.graphics.drawable.ColorDrawable(0x99000000.toInt()))

        setContentView(ui())

        voice = Voice(this)

        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            startActivity(Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            finish(); return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            line?.text = "Google voice service chahiye sir"
            h.postDelayed({ finish() }, 2200); return
        }

        // Bubble se command bhi aa sakti hai (bina bole)
        val a = intent?.getStringExtra("cmd_action")
        if (!a.isNullOrBlank()) {
            busy = true
            think(true)
            h.postDelayed({
                doIt(Brain.Cmd(a,
                    intent.getStringExtra("cmd_arg") ?: "",
                    intent.getStringExtra("cmd_say") ?: ""))
            }, 200)
        } else {
            h.postDelayed({ listen() }, 120)
        }
    }

    // ═══════════════ UI ═══════════════

    private fun ui(): View {
        val root = FrameLayout(this)
        root.setOnClickListener { bye() }   // bahar tap = band

        card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(26), dp(24), dp(26), dp(24))
            background = GradientDrawable().apply {
                cornerRadius = dp(26).toFloat()
                setColor(Color.parseColor("#F00B1020"))
                setStroke(dp(1), Color.parseColor("#3394A3B8"))
            }
            isClickable = true              // andar tap = kuch nahi
            alpha = 0f
            scaleX = 0.82f; scaleY = 0.82f
        }

        // ── orb ──
        val orb = FrameLayout(this)
        ring = TextView(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setStroke(dp(3), Color.parseColor("#00E6FF"))
                setColor(Color.TRANSPARENT)
            }
        }
        orb.addView(ring, FrameLayout.LayoutParams(dp(84), dp(84)))
        icon = TextView(this).apply {
            text = "🎤"; textSize = 30f
            gravity = Gravity.CENTER
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#7C3AED"),
                           Color.parseColor("#EC4899"))).apply {
                shape = GradientDrawable.OVAL
            }
        }
        orb.addView(icon, FrameLayout.LayoutParams(
            dp(66), dp(66), Gravity.CENTER))
        card!!.addView(orb, LinearLayout.LayoutParams(dp(84), dp(84)))

        wave = TextView(this).apply {
            textSize = 15f
            setTextColor(Color.parseColor("#00E6FF"))
            letterSpacing = 0.1f
            gravity = Gravity.CENTER
            visibility = View.INVISIBLE
            text = "▁▁▁▁▁"
        }
        card!!.addView(wave, lp(dp(12)))

        line = TextView(this).apply {
            text = "Boliye…"
            textSize = 15f
            setTextColor(Color.parseColor("#F8FAFC"))
            gravity = Gravity.CENTER
            maxLines = 4
        }
        card!!.addView(line, lp(dp(10)))

        root.addView(card, FrameLayout.LayoutParams(
            dp(290),
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER))

        card!!.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(260).start()
        return root
    }

    private fun lp(top: Int) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT).apply {
        topMargin = top
    }

    private fun rotate(color: String, ms: Long) {
        spin?.cancel()
        ring?.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setStroke(dp(3), Color.parseColor(color))
            setColor(Color.TRANSPARENT)
        }
        spin = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = ms
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { ring?.rotation = it.animatedValue as Float }
            start()
        }
    }

    private fun think(on: Boolean) {
        if (on) {
            icon?.text = "🧠"
            line?.text = "Soch raha hoon…"
            rotate("#F59E0B", 620)
        }
    }

    // ═══════════════ SUNNA ═══════════════

    private fun listen() {
        if (!keepOn || busy) return
        voice.stop()
        partial = ""
        icon?.text = "🎤"
        line?.text = "Boliye…"
        rotate("#EF4444", 1100)

        try { rec?.destroy() } catch (e: Exception) {}
        rec = SpeechRecognizer.createSpeechRecognizer(this)
        rec?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) {}
            override fun onBeginningOfSpeech() { cancelSil() }

            override fun onRmsChanged(v: Float) {
                if (v > 1f) {
                    val n = (v / 2f).toInt().coerceIn(1, 5)
                    wave?.text = "▁▂▃▄▅▆▇█".substring(0, n)
                    wave?.visibility = View.VISIBLE
                }
            }

            override fun onBufferReceived(p: ByteArray?) {}
            override fun onEndOfSpeech() {
                wave?.visibility = View.INVISIBLE
            }

            override fun onError(e: Int) {
                wave?.visibility = View.INVISIBLE
                val chup = e == SpeechRecognizer.ERROR_NO_MATCH ||
                           e == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                if (partial.trim().length > 1) { fire(partial.trim());return }
                if (chup) { again(180); return }
                if (e == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                    again(450); return
                }
                again(600)
            }

            override fun onResults(r: Bundle?) {
                wave?.visibility = View.INVISIBLE
                val t = r?.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.trim() ?: partial.trim()
                if (t.isNotBlank()) fire(t) else again(180)
            }

            override fun onPartialResults(r: Bundle?) {
                val t = r?.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.trim() ?: return
                if (t.isBlank() || t == partial) return
                partial = t
                line?.text = t
                cancelSil()
                silence = Runnable {
                    try { rec?.stopListening() } catch (e: Exception) {}
                }
                h.postDelayed(silence!!, 800)
            }

            override fun onEvent(t: Int, p: Bundle?) {}
        })

        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent
                .EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 800L)
            putExtra(RecognizerIntent
                .EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 600L)
        }
        try { rec?.startListening(i) } catch (e: Exception) { again(600) }
    }

    private fun cancelSil() {
        silence?.let { h.removeCallbacks(it) }
        silence = null
    }

    private fun again(ms: Long) {
        cancelSil()
        h.postDelayed({ if (keepOn && !busy) listen() }, ms)
    }

    // ═══════════════ KAAM ═══════════════

    private fun fire(text: String) {
        if (busy) return
        busy = true
        cancelSil()
        try { rec?.cancel() } catch (e: Exception) {}
        line?.text = text
        think(true)

        // Agent — screen ke andar ka kaam
        if (Agent.needed(text) && Eyes.on()) {
            line?.text = "🤖 Karta hoon…"
            Agent.run(this, text, object : Agent.Watch {
                override fun onStep(n: Int, total: Int, what: String) {
                    runOnUiThread { line?.text = "🤖 $n · $what" }
                }
                override fun onDone(msg: String) {
                    runOnUiThread { say(msg) }
                }
            })
            return
        }

        val loc = Brain.local(text)
        if (loc != null) { runAll(listOf(loc)); return }

        Thread {
            val cs = try { Brain.aiMulti(this, text) }
                catch (e: Exception) { emptyList<Brain.Cmd>() }
            runOnUiThread {
                if (cs.isNotEmpty()) runAll(cs)
                else {
                    val e = Brain.lastError
                    say(if (e.isBlank()) "Samajh nahi aaya sir"
                        else "AI fail — $e")
                }
            }
        }.start()
    }

    /** Ek se zyada kaam — ek ke baad ek, 350ms ke gap se */
    private fun runAll(cmds: List<Brain.Cmd>) {
        if (cmds.isEmpty()) { say("Samajh nahi aaya sir"); return }
        val out = mutableListOf<String>()

        fun step(i: Int) {
            if (i >= cmds.size) {
                val all = out.filter { it.isNotBlank() }
                say(if (all.isEmpty()) "Ho gaya sir"
                    else all.joinToString(". "))
                return
            }
            val c = cmds[i]
            val r = try { Actions.run(this, c) }
                catch (e: Exception) { "" }
            out.add(r)
            if (cmds.size > 1) line?.text =
                "${i + 1}/${cmds.size}  ${Actions.label(c.action)}"
            h.postDelayed({ step(i + 1) },
                if (cmds.size == 1) 0L else 350L)
        }
        step(0)
    }

    private fun doIt(c: Brain.Cmd) = runAll(listOf(c))

    private fun say(msg: String) {
        icon?.text = "⚡"
        line?.text = msg
        rotate("#22C55E", 1600)
        Memory(this).let {
            val l = it.load()
            l.add(Memory.Msg(false, msg))
            it.save(l)
        }
        voice.say(msg) {
            runOnUiThread {
                busy = false
                // Chhota jawab = shayad aur bologe. Sun lete hain.
                if (keepOn) { again(150) }
                // 12 second koi baat nahi = band
                h.removeCallbacksAndMessages("bye")
                h.postDelayed({ if (!busy) bye() }, 12_000)
            }
        }
    }

    private fun bye() {
        keepOn = false
        cancelSil()
        spin?.cancel()
        try { rec?.cancel(); rec?.destroy() } catch (e: Exception) {}
        rec = null
        card?.animate()?.alpha(0f)?.scaleX(0.85f)?.scaleY(0.85f)
            ?.setDuration(180)?.withEndAction { finish() }?.start()
            ?: finish()
    }

    override fun onPause() {
        super.onPause()
        if (!isFinishing) bye()
    }

    override fun onDestroy() {
        keepOn = false
        cancelSil()
        spin?.cancel()
        try { rec?.destroy() } catch (e: Exception) {}
        voice.shutdown()
        super.onDestroy()
    }
}
