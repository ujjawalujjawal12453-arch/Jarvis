package com.ravanx.jarvis

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.ravanx.jarvis.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private lateinit var voice: Voice
    private lateinit var keys: Keys
    private lateinit var mem: Memory
    private val log = mutableListOf<Memory.Msg>()
    private var rec: SpeechRecognizer? = null
    private var listening = false

    private val PERMS = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.CALL_PHONE,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.CAMERA
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        keys = Keys(this)
        voice = Voice(this)
        mem = Memory(this)

        askPerms()

        b.mic.setOnClickListener { if (listening) stopListen()
                                   else startListen() }
        b.send.setOnClickListener {
            val t = b.input.text.toString().trim()
            if (t.isNotEmpty()) { b.input.setText(""); handle(t) }
        }
        b.settings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        b.wakeSwitch.isChecked = keys.wake()
        b.wakeSwitch.setOnCheckedChangeListener { _, on ->
            keys.setFlag("wake", on)
            if (on) {
                WakeService.start(this)
                bot("Ab main hamesha sunta rahunga sir. " +
                    "\"Jarvis\" bol kar bulaiye.")
            } else {
                WakeService.stop(this)
                bot("Theek hai, ab sirf button dabane pe sunuga.")
            }
        }

        // Purani baat-cheet wapas laao
        val old = mem.load()
        if (old.isNotEmpty()) {
            log.addAll(old.takeLast(40))
            log.forEach { bubble(it.text, it.me,
                if (it.action.isBlank()) null
                else Brain.Cmd(it.action, "", "", it.ai)) }
        } else {
            bot("Namaste sir! Main JARVIS hoon.\n\n" +
                "Mic dabaiye ya likh kar bataiye — kya karna hai?")
        }
        if (keys.wake()) WakeService.start(this)

        // Eyes chalu hai ya nahi — status dikhao
        updateEyes()

        b.eyes.setOnClickListener {
            if (Eyes.on()) {
                bot("Eyes chalu hai sir — main screen dekh sakta hoon, " +
                    "button daba sakta hoon, aur message bhej sakta " +
                    "hoon.\n\nBand karna ho to Settings → " +
                    "Accessibility → JARVIS")
                Eyes.openSettings(this)
            } else {
                bot("Sir, ye JARVIS ki sabse badi taakat hai.\n\n" +
                    "Isse main:\n" +
                    "• Screen padh sakta hoon\n" +
                    "• Button khud daba sakta hoon\n" +
                    "• WhatsApp message SACH ME bhej sakta hoon\n" +
                    "• Notification padh sakta hoon\n\n" +
                    "Settings khol raha hoon — list me JARVIS " +
                    "dhoondh kar ON kar dijiye.")
                Eyes.openSettings(this)
            }
        }
    }

    // ═══════════════ PERMISSION ═══════════════

    private fun askPerms() {
        val need = PERMS.filter {
            ContextCompat.checkSelfPermission(this, it) !=
                PackageManager.PERMISSION_GRANTED
        }.toMutableList()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this,
                Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED) {
            need.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (need.isNotEmpty())
            ActivityCompat.requestPermissions(this,
                need.toTypedArray(), 1)
    }

    // ═══════════════ SUNNA ═══════════════

    private fun startListen() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            askPerms(); return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            bot("Sir, is phone me Google ka voice service nahi hai. " +
                "Play Store se \"Google\" app install kar lijiye.")
            return
        }
        voice.stop()
        listening = true
        b.mic.text = "⏹"
        b.status.text = "Sun raha hoon…"
        b.status.visibility = View.VISIBLE

        rec?.destroy()
        rec = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(p: Bundle?) {}
                override fun onBeginningOfSpeech() {
                    b.status.text = "Bol rahe hain…"
                }
                override fun onRmsChanged(v: Float) {}
                override fun onBufferReceived(p: ByteArray?) {}
                override fun onEndOfSpeech() {
                    b.status.text = "Samajh raha hoon…"
                }
                override fun onError(e: Int) {
                    stopListen()
                    if (e == SpeechRecognizer.ERROR_NO_MATCH ||
                        e == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
                        b.status.visibility = View.GONE
                    else bot("Sunai nahi diya sir, phir se boliye")
                }
                override fun onResults(r: Bundle?) {
                    stopListen()
                    val t = r?.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull() ?: return
                    handle(t)
                }
                override fun onPartialResults(r: Bundle?) {
                    r?.getStringArrayList(
                        SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()?.let { b.status.text = it }
                }
                override fun onEvent(t: Int, p: Bundle?) {}
            })
        }
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            // Hinglish ke liye hi-IN sabse achha kaam karta hai
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
        rec?.startListening(i)
    }

    private fun stopListen() {
        listening = false
        b.mic.text = "🎤"
        try { rec?.stopListening() } catch (e: Exception) {}
    }

    // ═══════════════ SAMAJHNA + KARNA ═══════════════

    private fun handle(text: String) {
        me(text)
        b.status.visibility = View.GONE

        // 1. LOCAL — turant, bina internet
        val loc = Brain.local(text)
        if (loc != null) { doIt(loc); return }

        // Internet hai ya nahi
        if (!online()) {
            bot("Sir, internet nahi hai. Bina internet main sirf " +
                "seedhe kaam kar sakta hoon — jaise \"torch on\", " +
                "\"YouTube kholo\", \"volume badhao\".")
            voice.say("Internet nahi hai sir")
            return
        }

        // 2. AI se poochho (background thread me)
        b.status.text = "Soch raha hoon…"
        b.status.visibility = View.VISIBLE
        Thread {
            val cmd = Brain.ai(this, text)
            runOnUiThread {
                b.status.visibility = View.GONE
                if (cmd != null) doIt(cmd)
                else bot("Samajh nahi aaya sir. Thoda saaf boliye?")
            }
        }.start()
    }

    private fun doIt(c: Brain.Cmd) {
        val reply = Actions.run(this, c)
        bot(reply, c)
        voice.say(reply)
    }

    // ═══════════════ CHAT UI ═══════════════

    private fun me(t: String) {
        bubble(t, true, null)
        log.add(Memory.Msg(true, t)); mem.save(log)
    }

    private fun bot(t: String, c: Brain.Cmd? = null) {
        bubble(t, false, c)
        log.add(Memory.Msg(false, t, c?.action ?: "",
            c?.fromAI ?: false))
        mem.save(log)
    }

    private fun updateEyes() {
        val on = Eyes.on()
        b.eyes.text = if (on) "👁" else "👁"
        b.eyes.alpha = if (on) 1f else 0.35f
    }

    override fun onResume() {
        super.onResume()
        updateEyes()
    }

    private fun bubble(text: String, mine: Boolean, c: Brain.Cmd?) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.topMargin = dp(8)
            lp.marginStart = if (mine) dp(48) else 0
            lp.marginEnd = if (mine) 0 else dp(48)
            layoutParams = lp
            setBackgroundResource(
                if (mine) R.drawable.bub_me else R.drawable.bub_bot)
            setPadding(dp(14), dp(11), dp(14), dp(11))
        }
        // kaunsa action hua — chhota tag
        if (c != null && c.action != "chat") {
            val tag = TextView(this)
            tag.setText("\u26a1 " + c.action.uppercase()
                .replace("_", " ") +
                (if (c.fromAI) "  \u00b7 AI" else "  \u00b7 instant"))
            tag.textSize = 9.5f
            tag.setTextColor(0xFF64748B.toInt())
            tag.letterSpacing = 0.08f
            row.addView(tag)
        }
        val body = TextView(this)
        body.setText(text)
        body.textSize = 15f
        body.setTextColor(0xFFF8FAFC.toInt())
        body.setLineSpacing(dp(3).toFloat(), 1f)
        row.addView(body)
        b.chat.addView(row)
        b.scroll.post {
            b.scroll.fullScroll(ScrollView.FOCUS_DOWN)
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun online(): Boolean = try {
        val cm = getSystemService(CONNECTIVITY_SERVICE)
            as android.net.ConnectivityManager
        if (Build.VERSION.SDK_INT >= 23) {
            val n = cm.activeNetwork
            val c = cm.getNetworkCapabilities(n)
            c != null && c.hasCapability(
                android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            @Suppress("DEPRECATION")
            cm.activeNetworkInfo?.isConnected == true
        }
    } catch (e: Exception) { true }

    override fun onNewIntent(i: Intent?) {
        super.onNewIntent(i)
        // Wake service se aaya — turant sunna shuru
        if (i?.getBooleanExtra("listen", false) == true) startListen()
    }

    override fun onDestroy() {
        super.onDestroy()
        voice.shutdown()
        rec?.destroy()
    }
}
