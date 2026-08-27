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
        splash()

        keys = Keys(this)
        voice = Voice(this)
        mem = Memory(this)

        askPerms()

        b.mic.setOnClickListener {
            if (keepOn) { stopListen(); bot("Theek hai sir, mic band.") }
            else startListen()
        }
        b.send.setOnClickListener {
            val t = b.input.text.toString().trim()
            if (t.isNotEmpty()) {
                b.input.setText("")
                busy = true
                handle(t)
            }
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
        handoff(intent)

        // ⚠️ Eyes OFF hai to JARVIS aadha hi kaam kar payega.
        //    User ki jaanch me "❌ OFF" aaya tha aur usse pata
        //    hi nahi tha ki asli dikkat yahi hai. Ab saaf bolo.
        if (!Eyes.on()) {
            b.root.postDelayed({
                bot("⚠️ Sir, meri aankhein band hain.\n\n" +
                    "Abhi main sirf app khol sakta hoon. " +
                    "Group dhoondhna, message likhna, send " +
                    "dabana — ye sab NAHI kar sakta.\n\n" +
                    "Upar 👁 button dabaiye → list me JARVIS " +
                    "→ ON kar dijiye.\n\n" +
                    "Uske baad boliye:\n" +
                    "\"WhatsApp me School Friends group me " +
                    "hi bhej do\"")
            }, 2200)
        }

        // Eyes / Bubble chalu hai ya nahi
        updateEyes()
        updateBub()

        b.bubble.setOnClickListener {
            if (Bubble.on()) {
                Bubble.stop(this)
                bot("Bubble band kar diya sir.")
            } else if (!Bubble.allowed(this)) {
                bot("Sir, home screen pe bubble dikhane ke liye ek " +
                    "permission chahiye.\n\n" +
                    "Settings khol raha hoon — JARVIS ko " +
                    "\"Display over other apps\" me ON kar dijiye.")
                Bubble.askPerm(this)
            } else {
                Bubble.start(this)
                bot("Bubble chalu! 🫧\n\n" +
                    "• Ek TAP = mic khulega, boliye\n" +
                    "• Ungli se kheench kar kahin bhi rakho\n" +
                    "• LAMBA dabao = ye app khule\n\n" +
                    "Home screen pe jaake dekhiye — circle " +
                    "wahan milega. Kisi bhi app me kaam karega.")
            }
            updateBub()
        }

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

    // ═══════════════ SPLASH ═══════════════

    /**
     * App khulte hi ek chhota sa intro — 3 ring phailti hain,
     * beech me orb dhadakta hai, phir naam aata hai aur sab
     * gayab. Kul 1.4 second.
     */
    private fun splash() {
        val root = android.widget.FrameLayout(this)
        root.setBackgroundColor(0xFF070B14.toInt())

        fun ring(size: Int, color: String, delay: Long) {
            val v = android.widget.TextView(this)
            v.background =
                android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setStroke(dp(2),
                        android.graphics.Color.parseColor(color))
                    setColor(android.graphics.Color.TRANSPARENT)
                }
            v.alpha = 0f
            v.scaleX = 0.2f; v.scaleY = 0.2f
            root.addView(v, android.widget.FrameLayout.LayoutParams(
                dp(size), dp(size), android.view.Gravity.CENTER))
            v.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setStartDelay(delay).setDuration(560)
                .withEndAction {
                    v.animate().alpha(0f).scaleX(1.5f).scaleY(1.5f)
                        .setDuration(420).start()
                }.start()
        }
        ring(190, "#7C3AED", 0)
        ring(140, "#00E6FF", 110)
        ring(96, "#EC4899", 220)

        val orb = android.widget.TextView(this).apply {
            text = "⚡"; textSize = 34f
            gravity = android.view.Gravity.CENTER
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable
                    .Orientation.TL_BR,
                intArrayOf(0xFF7C3AED.toInt(), 0xFFEC4899.toInt()))
                .apply {
                    shape = android.graphics.drawable
                        .GradientDrawable.OVAL
                }
            alpha = 0f; scaleX = 0.4f; scaleY = 0.4f
        }
        root.addView(orb, android.widget.FrameLayout.LayoutParams(
            dp(74), dp(74), android.view.Gravity.CENTER))
        orb.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(420).start()

        val name = android.widget.TextView(this).apply {
            text = "J A R V I S"
            textSize = 19f
            setTextColor(0xFFF8FAFC.toInt())
            gravity = android.view.Gravity.CENTER
            letterSpacing = 0.4f
            alpha = 0f
            translationY = dp(18).toFloat()
        }
        root.addView(name, android.widget.FrameLayout.LayoutParams(
            android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
            android.widget.FrameLayout.LayoutParams.WRAP_CONTENT,
            android.view.Gravity.CENTER).apply { topMargin = dp(120) })
        name.animate().alpha(1f).translationY(0f)
            .setStartDelay(320).setDuration(420).start()

        addContentView(root,
            android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT))

        root.postDelayed({
            root.animate().alpha(0f).setDuration(340)
                .withEndAction {
                    (root.parent as? android.view.ViewGroup)
                        ?.removeView(root)
                }.start()
        }, 1400)
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

    // ═══════════════ SUNNA — CONTINUOUS ═══════════════
    //
    // ⚠️ 3 badi dikkatein thi, teeno yahan theek ki hain:
    //
    // 1. MIC BAND-CHALU HOTA THA — har jawab ke baad mic band ho
    //    jaata tha. Ab `keepOn` chalu rehta hai: JARVIS bol kar
    //    khatam karte hi mic apne aap wapas on ho jaata hai.
    //
    // 2. "Sunai nahi diya, phir se boliye" — ye NO_MATCH pe aata
    //    tha, jo chup rehne pe bhi aata hai. Ab chup rehna normal
    //    maana jaata hai, koi message nahi — bas mic chalta rehta.
    //
    // 3. LATE — pehle poora bolne ka intezaar hota tha. Ab partial
    //    result se hi kaam shuru ho jaata hai (jab bolna ruk jaye).

    private var keepOn = false          // continuous mode chalu?
    private var lastPartial = ""
    private var busy = false            // ek waqt me ek hi kaam
    private val ui = android.os.Handler(android.os.Looper.getMainLooper())
    private var silence: Runnable? = null

    private fun micOk(): Boolean {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            askPerms(); return false
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            bot("Sir, is phone me Google ka voice service nahi hai. " +
                "Play Store se \"Google\" app install kar lijiye.")
            return false
        }
        return true
    }

    private fun startListen() {
        if (!micOk()) return
        keepOn = true
        listen()
    }

    /** Asli mic — baar-baar isi ko call karte hain */
    private fun listen() {
        if (!keepOn || busy) return
        voice.stop()
        listening = true
        lastPartial = ""
        b.mic.text = "⏹"
        b.mic.setBackgroundResource(R.drawable.btn_mic_on)
        b.status.text = "🎤 Sun raha hoon…"
        b.status.visibility = View.VISIBLE

        try { rec?.destroy() } catch (e: Exception) {}
        rec = SpeechRecognizer.createSpeechRecognizer(this)
        rec?.setRecognitionListener(object : RecognitionListener {

            override fun onReadyForSpeech(p: Bundle?) {
                b.status.text = "🎤 Boliye…"
            }

            override fun onBeginningOfSpeech() {
                b.status.text = "🎤 Sun raha hoon…"
                cancelSilence()
            }

            /** Awaaz ka level — bar dikhane ke liye */
            override fun onRmsChanged(v: Float) {
                if (v > 1f) {
                    val n = (v / 2f).toInt().coerceIn(1, 8)
                    b.wave.text = "▁▂▃▄▅▆▇█".substring(0, n)
                    b.wave.visibility = View.VISIBLE
                }
            }

            override fun onBufferReceived(p: ByteArray?) {}

            override fun onEndOfSpeech() {
                b.wave.visibility = View.GONE
                b.status.text = "⚡ Samajh raha hoon…"
            }

            override fun onError(e: Int) {
                b.wave.visibility = View.GONE
                listening = false

                // ⚠️ Chup rehna = error NAHI hai. Bas phir se suno.
                val chup = e == SpeechRecognizer.ERROR_NO_MATCH ||
                           e == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                if (chup) {
                    // partial me kuch tha? to wahi use karo
                    val p = lastPartial.trim()
                    if (p.length > 1) { fire(p); return }
                    if (keepOn) { again(250); return }
                    idle(); return
                }

                if (e == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                    again(500); return
                }
                if (e == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                    keepOn = false; idle(); askPerms(); return
                }
                if (e == SpeechRecognizer.ERROR_NETWORK ||
                    e == SpeechRecognizer.ERROR_NETWORK_TIMEOUT) {
                    b.status.text = "📶 Network slow…"
                    again(900); return
                }
                // baaki sab — chup-chaap dobara
                if (keepOn) again(600) else idle()
            }

            override fun onResults(r: Bundle?) {
                b.wave.visibility = View.GONE
                listening = false
                val t = r?.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.trim()
                    ?: lastPartial.trim()
                if (t.isNotBlank()) fire(t)
                else if (keepOn) again(200) else idle()
            }

            /**
             * REAL-TIME — bolte-bolte hi text dikhta hai.
             * Aur agar 900ms tak kuch naya na aaye to samajh lo
             * baat khatam — turant kaam shuru. Isse 2-3 second
             * bach jaate hain.
             */
            override fun onPartialResults(r: Bundle?) {
                val t = r?.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.trim() ?: return
                if (t.isBlank() || t == lastPartial) return
                lastPartial = t
                b.status.text = "🎤 $t"
                cancelSilence()
                silence = Runnable {
                    if (listening && lastPartial.isNotBlank()) {
                        try { rec?.stopListening() } catch (e: Exception) {}
                    }
                }
                ui.postDelayed(silence!!, 900)
            }

            override fun onEvent(t: Int, p: Bundle?) {}
        })

        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            // Jaldi jawab ke liye — Android ko batao ki thoda sa
            // rukna kaafi hai
            putExtra(RecognizerIntent
                .EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 900L)
            putExtra(RecognizerIntent
                .EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                700L)
            putExtra(RecognizerIntent
                .EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 700L)
        }
        try { rec?.startListening(i) } catch (e: Exception) { again(700) }
    }

    private fun cancelSilence() {
        silence?.let { ui.removeCallbacks(it) }
        silence = null
    }

    /** Thodi der baad phir se suno */
    private fun again(ms: Long) {
        cancelSilence()
        listening = false
        ui.postDelayed({ if (keepOn && !busy) listen() }, ms)
    }

    private fun idle() {
        cancelSilence()
        listening = false
        keepOn = false
        b.mic.text = "🎤"
        b.mic.setBackgroundResource(R.drawable.btn_mic)
        b.status.visibility = View.GONE
        b.wave.visibility = View.GONE
    }

    private fun stopListen() {
        keepOn = false
        cancelSilence()
        try { rec?.cancel() } catch (e: Exception) {}
        try { rec?.destroy() } catch (e: Exception) {}
        rec = null
        idle()
    }

    /** Kaam shuru — ek hi baar, chahe partial se aaye ya final se */
    private fun fire(text: String) {
        if (busy) return
        busy = true
        cancelSilence()
        listening = false
        try { rec?.cancel() } catch (e: Exception) {}
        handle(text)
    }

    // ═══════════════ SAMAJHNA + KARNA ═══════════════

    private fun handle(text: String) {
        me(text)
        b.status.text = "⚡ Kaam kar raha hoon…"
        b.status.visibility = View.VISIBLE

        // ═══ 0. AGENT — screen ke andar ka kaam ═══
        //
        // "WhatsApp kholo, School Friends group me hi bhejo"
        // jaisa kaam ek JSON se nahi hota. Uske liye screen
        // dekh-dekh kar kadam uthane padte hain. Agent wahi
        // karta hai.
        if (Agent.needed(text) && online()) {
            if (!Eyes.on()) {
                done("Sir, iske liye meri aankhein chahiye 👁\n\n" +
                    "Upar 👁 button dabaiye aur Accessibility ON " +
                    "kar dijiye. Uske bina main sirf app khol " +
                    "sakta hoon — andar group dhoondhna, message " +
                    "likhna, send dabana nahi kar sakta.")
                return
            }
            bot("🤖 Theek hai sir, main khud karta hoon…")
            thinking(true)
            Agent.run(this, text, object : Agent.Watch {
                override fun onStep(n: Int, total: Int, what: String) {
                    runOnUiThread {
                        b.status.text = "🤖 $n · $what"
                        b.status.visibility = View.VISIBLE
                    }
                }
                override fun onDone(msg: String) {
                    runOnUiThread {
                        thinking(false)
                        bot(msg)
                        voice.say(msg) { runOnUiThread { after() } }
                    }
                }
            })
            return
        }

        // 1. LOCAL — 0 ms, bina internet.
        //    ⚠️ Multi-command (do kaam ek saath) ho to local
        //       khud null deta hai — AI hi sambhalta hai.
        val loc = Brain.local(text)
        if (loc != null) { runAll(listOf(loc)); return }

        if (!online()) {
            done("Sir, internet nahi hai. Bina internet main seedhe " +
                 "kaam kar sakta hoon — \"torch on\", \"YouTube " +
                 "kholo\", \"volume badhao\".")
            return
        }

        // 2. AI — background thread, UI atkegi nahi
        b.status.text = "🧠 Soch raha hoon…"
        thinking(true)
        Thread {
            val cmds = try { Brain.aiMulti(this, text) } catch (e: Exception) {
                emptyList() }
            runOnUiThread {
                thinking(false)
                if (cmds.isNotEmpty()) runAll(cmds)
                else {
                    // ⚠️ Pehle yahan sirf "samajh nahi aaya" tha.
                    //    User ko kabhi pata nahi chalta tha ki
                    //    asli me API fail hui hai. Ab saaf batao.
                    val e = Brain.lastError
                    done(if (e.isBlank())
                        "Samajh nahi aaya sir. Thoda saaf boliye?"
                    else "Sir, AI se baat nahi ho payi — $e\n\n" +
                        "⚙ Settings → 🩺 AI Jaanch dabao, " +
                        "poori detail mil jayegi.")
                }
            }
        }.start()
    }

    /**
     * ⚡ EK SE ZYADA KAAM — ek ke baad ek.
     *
     * User ki shikayat thi: "jab ek saath kuch bola jata hai to
     * kuch nahi kar pata". Wajah — pehle sirf PEHLA command
     * chalta tha, baaki gir jaate the. Ab poori list chalti hai.
     *
     * Har kaam ke beech 350ms ka gap — warna Android ke pass
     * ek Activity khulne se pehle doosri aa jaati hai aur
     * dono me se koi nahi chalti.
     */
    private fun runAll(cmds: List<Brain.Cmd>) {
        if (cmds.isEmpty()) { after(); return }

        if (cmds.size > 1) {
            val list = cmds.mapIndexed { i, c ->
                "${i + 1}. ${Actions.label(c.action)}" }
                .joinToString("\n")
            bot("Theek hai sir — ${cmds.size} kaam kar raha hoon:\n" +
                list)
        }

        val replies = mutableListOf<String>()

        fun step(i: Int) {
            if (i >= cmds.size) {
                // sab ho gaya — ab ek saath bol do
                val all = replies.filter { it.isNotBlank() }
                val say = when {
                    all.isEmpty() -> "Ho gaya sir."
                    cmds.size == 1 -> all.first()
                    else -> all.joinToString(". ")
                }
                voice.say(say) { runOnUiThread { after() } }
                return
            }
            val c = cmds[i]
            val reply = try { Actions.run(this, c) }
                catch (e: Exception) { "Ye kaam nahi ho paya sir." }
            bot(reply, c)
            replies.add(reply)
            // agla kaam thodi der baad
            b.root.postDelayed({ step(i + 1) },
                if (cmds.size == 1) 0L else 350L)
        }
        step(0)
    }

    private fun doIt(c: Brain.Cmd) = runAll(listOf(c))

    private fun done(msg: String) {
        bot(msg)
        voice.say(msg) { runOnUiThread { after() } }
    }

    /** Jawab khatam — ab phir se suno */
    private fun after() {
        busy = false
        if (keepOn) {
            b.status.text = "🎤 Boliye…"
            again(120)          // turant wapas mic
        } else {
            b.status.visibility = View.GONE
        }
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

    private fun updateBub() {
        b.bubble.alpha = if (Bubble.on()) 1f else 0.35f
    }

    private fun updateEyes() {
        val on = Eyes.on()
        b.eyes.text = if (on) "👁" else "👁"
        b.eyes.alpha = if (on) 1f else 0.35f
    }

    override fun onResume() {
        super.onResume()
        updateEyes()
        updateBub()
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
        setIntent(i)
        handoff(i)
        if (i?.getBooleanExtra("listen", false) == true) startListen()
    }

    /** Bubble ne jo kaam nahi kar paya, wo yahan poora hota hai */
    private fun handoff(i: Intent?) {
        val a = i?.getStringExtra("cmd_action") ?: return
        if (a.isBlank()) return
        i.removeExtra("cmd_action")
        val c = Brain.Cmd(a, i.getStringExtra("cmd_arg") ?: "",
            i.getStringExtra("cmd_say") ?: "")
        busy = true
        ui.postDelayed({ doIt(c) }, 350)
    }

    override fun onDestroy() {
        super.onDestroy()
        voice.shutdown()
        rec?.destroy()
    }

    // ═══════════════ 🧠 SOCHNE KA ANIMATION ═══════════════
    //
    // User bola: "usse puchte hain to bahut time lagta hai".
    // Waqt to 0.2s hi hai, par bina kuch dikhe wo lamba lagta
    // hai. Ab 3 dot naachte hain — pata chalta hai kaam ho raha.

    private var dots: android.widget.LinearLayout? = null

    private fun thinking(on: Boolean) {
        if (!on) {
            dots?.let { d ->
                (d.parent as? android.view.ViewGroup)?.removeView(d)
            }
            dots = null
            return
        }
        if (dots != null) return

        val row = android.widget.LinearLayout(this)
        row.orientation = android.widget.LinearLayout.HORIZONTAL
        row.setPadding(dp(16), dp(10), dp(16), dp(10))
        val lp = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.setMargins(dp(4), dp(6), dp(4), dp(6))
        row.layoutParams = lp
        row.background = android.graphics.drawable.GradientDrawable()
            .apply {
                shape = android.graphics.drawable.GradientDrawable
                    .RECTANGLE
                cornerRadius = dp(18).toFloat()
                setColor(0xFF111827.toInt())
            }

        for (i in 0..2) {
            val d = android.widget.TextView(this)
            d.background = android.graphics.drawable.GradientDrawable()
                .apply {
                    shape = android.graphics.drawable
                        .GradientDrawable.OVAL
                    setColor(0xFF00E6FF.toInt())
                }
            val p = android.widget.LinearLayout.LayoutParams(
                dp(8), dp(8))
            p.setMargins(dp(3), 0, dp(3), 0)
            row.addView(d, p)
            d.alpha = 0.3f
            d.animate().alpha(1f).setDuration(400)
                .setStartDelay(i * 160L)
                .withEndAction(object : Runnable {
                    override fun run() {
                        if (dots == null) return
                        d.animate().alpha(0.3f).setDuration(400)
                            .withEndAction {
                                if (dots != null)
                                    d.animate().alpha(1f)
                                        .setDuration(400)
                                        .withEndAction(this).start()
                            }.start()
                    }
                }).start()
        }
        dots = row
        b.chat.addView(row)
        b.scroll.post {
            b.scroll.fullScroll(View.FOCUS_DOWN) }
    }
}
