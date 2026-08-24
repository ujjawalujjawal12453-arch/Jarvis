package com.ravanx.jarvis

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * 🧠 JARVIS ka dimaag
 *
 * Do hisse:
 *   1. LOCAL match  — bina internet, turant (0ms)
 *   2. AI se poochho — jab local samajh na paye
 *
 * Local pehle chalta hai kyunki "flashlight on karo" ke liye
 * AI ko poochhna bewakoofi hai — 2 second waste aur data bhi.
 */
object Brain {

    data class Cmd(
        val action: String,
        val arg: String = "",
        val say: String = "",
        val fromAI: Boolean = false
    )

    // ═══════════════════════════════════════════
    //   APP ke naam — bolne wale naam se package
    // ═══════════════════════════════════════════
    val APPS = mapOf(
        "youtube" to "com.google.android.youtube",
        "yt" to "com.google.android.youtube",
        "whatsapp" to "com.whatsapp",
        "instagram" to "com.instagram.android",
        "insta" to "com.instagram.android",
        "facebook" to "com.facebook.katana",
        "telegram" to "org.telegram.messenger",
        "chrome" to "com.android.chrome",
        "browser" to "com.android.chrome",
        "gmail" to "com.google.android.gm",
        "maps" to "com.google.android.apps.maps",
        "map" to "com.google.android.apps.maps",
        "camera" to "camera",
        "gallery" to "gallery",
        "spotify" to "com.spotify.music",
        "netflix" to "com.netflix.mediaclient",
        "amazon" to "in.amazon.mShop.android.shopping",
        "flipkart" to "com.flipkart.android",
        "paytm" to "net.one97.paytm",
        "phonepe" to "com.phonepe.app",
        "gpay" to "com.google.android.apps.nbu.paisa.user",
        "google pay" to "com.google.android.apps.nbu.paisa.user",
        "snapchat" to "com.snapchat.android",
        "twitter" to "com.twitter.android",
        "x" to "com.twitter.android",
        "linkedin" to "com.linkedin.android",
        "zomato" to "com.application.zomato",
        "swiggy" to "in.swiggy.android",
        "uber" to "com.ubercab",
        "ola" to "com.olacabs.customer",
        "settings" to "settings",
        "calculator" to "calculator",
        "clock" to "clock",
        "calendar" to "com.google.android.calendar",
        "playstore" to "com.android.vending",
        "play store" to "com.android.vending",
        "files" to "files",
        "termux" to "com.termux",
        "discord" to "com.discord",
        "pinterest" to "com.pinterest",
        "reddit" to "com.reddit.frontpage",
        "hotstar" to "in.startv.hotstar",
        "jio" to "com.jio.myjio",
        "truecaller" to "com.truecaller"
    )

    private fun norm(s: String) = s.lowercase().trim()
        .replace(Regex("[?.!,]"), " ")
        .replace(Regex("\\s+"), " ")

    /** Wake word / naam hata do — "jarvis youtube kholo" → "youtube kholo" */
    fun strip(t: String): String {
        var s = norm(t)
        for (w in listOf("hey jarvis", "ok jarvis", "arey jarvis",
                         "jarvis", "jaarvis", "jarvees", "jaravis")) {
            if (s.startsWith(w)) s = s.removePrefix(w).trim()
        }
        return s
    }

    // ═══════════════════════════════════════════
    //   1. LOCAL — bina internet, turant
    // ═══════════════════════════════════════════
    fun local(raw: String): Cmd? {
        val t = strip(raw)
        if (t.isBlank()) return null

        fun has(vararg w: String) = w.any { t.contains(it) }
        val on = has(" on", "chalu", "khol", "jala", "start", "enable")
        val off = has(" off", "band", "bujha", "stop", "disable", "bandh")

        // ── SEARCH kisi app me ──
        // "youtube pe arijit singh search karo"
        Regex("(?:youtube|yt)\\s*(?:pe|par|me|main|mein)?\\s*(.+?)" +
              "\\s*(?:search|dhundo|dhoondo|chalao|baja|play)")
            .find(t)?.let {
                return Cmd("yt_search", it.groupValues[1].trim(),
                    "YouTube pe dhoondh raha hoon")
            }
        Regex("(?:youtube|yt)\\s*(?:pe|par|me)?\\s*" +
              "(?:search|dhundo|chalao|play)\\s+(.+)")
            .find(t)?.let {
                return Cmd("yt_search", it.groupValues[1].trim(),
                    "YouTube pe dhoondh raha hoon")
            }
        Regex("(?:google|net|internet)\\s*(?:pe|par|me)?\\s*" +
              "(.+?)\\s*(?:search|dhundo|dhoondo)").find(t)?.let {
            return Cmd("google", it.groupValues[1].trim(),
                "Google kar raha hoon")
        }
        Regex("(?:search|dhundo|dhoondo)\\s+(.+?)\\s*" +
              "(?:on|pe|par)?\\s*(?:google)?$").find(t)?.let {
            val q = it.groupValues[1].trim()
            if (q.length > 1) return Cmd("google", q, "Dhoondh raha hoon")
        }

        // ── APP kholo ──
        if (has("khol", "open", "chalu karo", "launch", "start karo")) {
            for ((name, pkg) in APPS) {
                if (t.contains(name)) {
                    // "flashlight on karo" APP nahi hai
                    if (name == "camera" && has("flash")) break
                    return Cmd("open_app", pkg, "$name khol raha hoon")
                }
            }
        }

        // ── FLASHLIGHT / TORCH ──
        if (has("flash", "torch", "batti", "light")) {
            if (off) return Cmd("torch_off", "", "Torch band")
            if (on || has("torch", "flash")) return Cmd("torch_on", "",
                "Torch chalu")
        }

        // ── PHONE LOCK ──
        if (has("lock") && !has("unlock")) {
            return Cmd("lock", "", "Phone lock kar raha hoon")
        }
        if (has("unlock")) {
            return Cmd("no_unlock", "",
                "Sir, phone unlock karna Android ki security ke " +
                "khilaf hai — koi bhi app ye nahi kar sakta. " +
                "Aapko khud fingerprint lagana padega.")
        }

        // ── WIFI / BLUETOOTH / DATA ──
        if (has("wifi", "wi-fi")) return Cmd("wifi",
            if (off) "off" else "on", "WiFi settings khol raha hoon")
        if (has("bluetooth", "blue tooth")) return Cmd("bluetooth",
            if (off) "off" else "on", "Bluetooth settings")
        if (has("mobile data", "data on", "data off", "internet on",
                "internet off"))
            return Cmd("data", if (off) "off" else "on", "Data settings")
        if (has("airplane", "flight mode", "hawai"))
            return Cmd("airplane", "", "Airplane mode settings")

        // ── VOLUME ──
        if (has("volume", "awaaz", "sound", "aawaz")) {
            if (has("badha", "up", "zyada", "tez", "increase"))
                return Cmd("vol_up", "", "Volume badha raha hoon")
            if (has("kam", "down", "ghata", "decrease", "chota"))
                return Cmd("vol_down", "", "Volume kam")
            if (off || has("mute", "silent", "chup"))
                return Cmd("mute", "", "Silent kar diya")
            if (has("full", "max", "poora"))
                return Cmd("vol_max", "", "Full volume")
        }
        if (has("silent", "mute")) return Cmd("mute", "", "Silent mode")

        // ── BRIGHTNESS ──
        if (has("bright", "roshni", "screen light")) {
            if (has("badha", "up", "zyada", "tez"))
                return Cmd("bright_up", "", "Brightness badha di")
            if (has("kam", "down", "ghata"))
                return Cmd("bright_down", "", "Brightness kam ki")
        }

        // ── CALL ──
        Regex("(?:call|phone|ring|lagao|milao)\\s+(?:karo\\s+)?(.+?)" +
              "\\s*(?:ko|se)?\\s*(?:call|phone|lagao|milao)?$")
            .find(t)?.let {
                val who = it.groupValues[1].trim()
                    .removeSuffix("ko").trim()
                if (who.length > 1 && !who.contains("karo"))
                    return Cmd("call", who, "$who ko call laga raha hoon")
            }
        Regex("(.+?)\\s+ko\\s+(?:call|phone|ring)").find(t)?.let {
            return Cmd("call", it.groupValues[1].trim(),
                "Call laga raha hoon")
        }

        // ── SMS ──
        Regex("(.+?)\\s+ko\\s+(?:message|msg|sms)\\s*(?:bhejo|karo)?" +
              "\\s*(.*)").find(t)?.let {
            return Cmd("sms", it.groupValues[1].trim() + "|" +
                it.groupValues[2].trim(), "Message ready kar raha hoon")
        }

        // ── WHATSAPP message ──
        Regex("whatsapp\\s*(?:pe|par|se)?\\s*(.+?)\\s+ko\\s*(.*)")
            .find(t)?.let {
                return Cmd("whatsapp", it.groupValues[1].trim() + "|" +
                    it.groupValues[2].trim(), "WhatsApp khol raha hoon")
            }

        // ── SCREENSHOT ──
        if (has("screenshot", "screen shot", "screen capture"))
            return Cmd("screenshot", "", "Screenshot le raha hoon")

        // ── BATTERY ──
        if (has("battery", "charge", "power"))
            return Cmd("battery", "", "")

        // ── ALARM / TIMER ──
        Regex("(\\d{1,2})\\s*(?:baje|bje|:|\\s)?\\s*(\\d{0,2})\\s*" +
              "(?:ka)?\\s*alarm").find(t)?.let {
            val h = it.groupValues[1].toIntOrNull() ?: 7
            val m = it.groupValues[2].toIntOrNull() ?: 0
            return Cmd("alarm", "$h:$m", "Alarm laga raha hoon")
        }
        if (has("alarm")) return Cmd("alarm_app", "", "Clock khol raha hoon")
        Regex("(\\d+)\\s*(?:minute|min|mint)\\s*(?:ka)?\\s*timer")
            .find(t)?.let {
                return Cmd("timer", it.groupValues[1],
                    "Timer laga raha hoon")
            }

        // ── TIME / DATE ──
        if (has("time kya", "kitne baje", "samay kya", "what time"))
            return Cmd("time", "", "")
        if (has("date kya", "aaj ki date", "aaj kaunsi", "what date"))
            return Cmd("date", "", "")

        // ── MUSIC ──
        Regex("(.+?)\\s*(?:gaana|song|music)\\s*(?:bajao|chalao|play)")
            .find(t)?.let {
                return Cmd("yt_search", it.groupValues[1].trim() + " song",
                    "Gaana chala raha hoon")
            }
        if (has("gaana bajao", "music chalao", "song play",
                "gana chalao"))
            return Cmd("open_app", "com.spotify.music", "Music chalu")

        // ── SCREEN CONTROL (Eyes se) ──
        if (has("back", "peeche", "wapas jao", "pichla"))
            return Cmd("back", "", "")
        if (has("home", "ghar", "home jao", "home screen"))
            return Cmd("home", "", "")
        if (has("recent", "recents", "sare app", "app list"))
            return Cmd("recents", "", "")
        if (has("notification", "notif") &&
            has("khol", "dikha", "kholo", "padho", "sunao")) {
            return if (has("padho", "sunao", "kya", "batao"))
                Cmd("read_notif", "", "") else Cmd("notif_panel", "", "")
        }
        if (has("scroll", "neeche karo", "upar karo", "sarkao")) {
            return Cmd("scroll",
                if (has("upar", "up")) "up" else "down", "")
        }
        if (has("screen pe kya", "screen padho", "kya likha",
                "ye padho", "isko padho", "read screen"))
            return Cmd("read_screen", "", "")

        // "X pe click karo" / "X dabao"
        Regex("(.+?)\\s*(?:pe|par|ko)?\\s*(?:click|tap|dabao|daba do|press)")
            .find(t)?.let {
                val w = it.groupValues[1].trim()
                if (w.length in 2..30 && !w.contains("screen"))
                    return Cmd("tap", w, "")
            }
        Regex("(?:click|tap|dabao|press)\\s+(?:karo\\s+)?(?:on\\s+)?(.+)")
            .find(t)?.let {
                val w = it.groupValues[1].trim()
                    .removeSuffix("pe").removeSuffix("par").trim()
                if (w.length in 2..30) return Cmd("tap", w, "")
            }

        // ── YAAD RAKHO ──
        Regex("(?:yaad rakho|yaad rakhna|remember|note karo)\\s+(?:ki\\s+)?(.+)")
            .find(t)?.let {
                return Cmd("remember", it.groupValues[1].trim(), "")
            }
        Regex("mera\\s+(.+?)\\s+(?:hai|he)\\s*(.*)").find(t)?.let {
            val k = it.groupValues[1].trim()
            val v = it.groupValues[2].trim()
            if (v.isNotBlank() && k.length < 25)
                return Cmd("remember", "$k = $v", "")
        }
        Regex("(?:mera|meri|mere)\\s+(.+?)\\s+kya\\s+(?:hai|he)")
            .find(t)?.let {
                return Cmd("recall", it.groupValues[1].trim(), "")
            }
        if (has("kya yaad hai", "sab yaad", "kya kya pata"))
            return Cmd("all_facts", "", "")

        // ── APP BAND KARO ──
        if (has("band karo", "close karo", "app band") &&
            !has("torch", "flash", "wifi", "bluetooth", "volume",
                 "data", "screen")) {
            for ((name, _) in APPS) if (t.contains(name))
                return Cmd("close_app", name, "")
            return Cmd("back", "", "")
        }

        // ── COPY / PASTE ──
        if (has("copy karo", "copy kar do")) {
            val what = t.substringAfter("copy").trim()
                .removePrefix("karo").removePrefix("kar do").trim()
            if (what.isNotBlank()) return Cmd("copy", what, "")
            return Cmd("copy_screen", "", "")
        }

        // ── SHARE ──
        Regex("(?:share|bhejo)\\s+(?:karo\\s+)?(.+)").find(t)?.let {
            val w = it.groupValues[1].trim()
            if (w.length > 2 && !w.contains(" ko "))
                return Cmd("share", w, "")
        }

        // ── SPEAK / TARJUMA ──
        Regex("(?:translate|tarjuma)\\s+(?:karo\\s+)?(.+)")
            .find(t)?.let { return Cmd("ai_ask", t, "") }

        // ── CALCULATOR ──
        Regex("^(\\d+(?:\\.\\d+)?)\\s*([+\\-*/x×÷])\\s*(\\d+(?:\\.\\d+)?)")
            .find(t)?.let {
                return Cmd("calc", t, "")
            }
        if (has("plus", "minus", "multiply", "divide", "jod", "ghata",
                "guna", "bhag") && Regex("\\d").containsMatchIn(t))
            return Cmd("calc", t, "")

        // ── DND / RINGER ──
        if (has("dnd", "disturb", "pareshan mat"))
            return Cmd("dnd", if (off) "off" else "on", "")
        if (has("ringtone", "ring mode", "normal mode", "vibrate"))
            return Cmd("ringer",
                if (has("vibrate")) "vibrate"
                else if (has("silent")) "silent" else "normal", "")

        // ── ROTATE / SCREEN ──
        if (has("rotate", "ghumao", "landscape", "portrait"))
            return Cmd("rotate", "", "")

        // ── UNINSTALL / APP INFO ──
        Regex("(.+?)\\s*(?:app)?\\s*(?:uninstall|hatao|delete)")
            .find(t)?.let {
                return Cmd("uninstall", it.groupValues[1].trim(), "")
            }

        // ── CHAT (baat-cheet) ──
        if (has("hello", "hi jarvis", "hey", "namaste", "namaskar"))
            return Cmd("chat", "", "Namaste sir! Batayiye kya karna hai?")
        if (has("kaise ho", "kaisa hai", "how are you"))
            return Cmd("chat", "", "Main bilkul theek hoon sir. Aap batao?")
        if (has("kaun ho", "tum kaun", "who are you"))
            return Cmd("chat", "",
                "Main JARVIS hoon sir — aapka apna assistant. " +
                "RAVAN X HOSTING TEAM ne banaya hai.")
        if (has("thank", "shukriya", "dhanyawad"))
            return Cmd("chat", "", "Koi baat nahi sir.")
        if (has("bye", "alvida", "so ja", "band ho ja"))
            return Cmd("chat", "", "Theek hai sir, main yahin hoon.")

        return null   // local samajh nahi paya — AI se poochho
    }

    // ═══════════════════════════════════════════
    //   2. AI — jab local fail ho
    // ═══════════════════════════════════════════
    private const val SYS = """Tum JARVIS ho — ek Android phone assistant.
User Hinglish me baat karta hai. Uski baat samajh kar SIRF ek JSON do.

JSON format:
{"action":"...","arg":"...","say":"..."}

action ye ho sakta hai:
open_app   arg = app ka naam (youtube/whatsapp/instagram/chrome...)
yt_search  arg = YouTube pe kya search karna hai
google     arg = Google pe kya search karna hai
call       arg = kis ko call karna hai (naam)
sms        arg = "naam|message"
whatsapp   arg = "naam|message"
torch_on   torch_off   lock
wifi       arg = on/off
bluetooth  arg = on/off
vol_up  vol_down  vol_max  mute
bright_up  bright_down
screenshot  battery  time  date
alarm      arg = "7:30"
timer      arg = minute
back  home  recents  notif_panel  read_notif  read_screen
scroll     arg = up/down
tap        arg = button ka naam jo screen pe dikh raha hai
type       arg = jo text likhna hai
remember   arg = "cheez = value"   (user ki baat yaad rakho)
recall     arg = kya yaad karna hai
close_app  arg = app ka naam
dnd        arg = on/off
rotate  copy_screen
calc       arg = poora sawaal (jaise "25 * 4")
chat       arg = ""  (jab koi kaam nahi, sirf baat)

say = jo JARVIS bolega — chhota, Hinglish, seedha.
Agar samajh na aaye to action "chat" do aur say me poochho.
SIRF JSON do, aur kuch nahi."""

    fun ai(ctx: Context, text: String): Cmd? {
        val keys = Keys(ctx)
        val mem = Memory(ctx)
        var t = strip(text)

        // JARVIS ko yaad dilao: pichhli baat + user ke facts
        val extra = StringBuilder()
        mem.factLine().takeIf { it.isNotBlank() }
            ?.let { extra.append(it).append("\n") }
        mem.context(4).takeIf { it.isNotBlank() }
            ?.let { extra.append("Pichhli baat:\n").append(it)
                .append("\n") }
        // Screen pe kya hai — agar Eyes chalu hai
        if (Eyes.on()) {
            Eyes.live?.readScreen()?.take(600)
                ?.takeIf { it.isNotBlank() }
                ?.let { extra.append("Screen pe abhi ye dikh raha:\n")
                    .append(it).append("\n") }
        }
        if (extra.isNotEmpty())
            t = extra.toString() + "\n---\nUser bola: " + t

        // Groq sabse tez — pehle wahi
        keys.groq().takeIf { it.isNotBlank() }?.let { k ->
            askOpenAI(
                "https://api.groq.com/openai/v1/chat/completions",
                k, "qwen/qwen3.6-27b", t,
                mapOf("reasoning_effort" to "none")
            )?.let { return it }
        }
        // Cloudflare backup
        val acc = keys.cfAcc()
        val tok = keys.cfTok()
        if (acc.isNotBlank() && tok.isNotBlank()) {
            askCF(acc, tok, t)?.let { return it }
        }
        return null
    }

    private fun askOpenAI(
        url: String, key: String, model: String, text: String,
        extra: Map<String, Any> = emptyMap()
    ): Cmd? = try {
        val body = JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system"); put("content", SYS)
                })
                put(JSONObject().apply {
                    put("role", "user"); put("content", text)
                })
            })
            put("max_tokens", 300)
            put("temperature", 0.2)
            extra.forEach { (k, v) -> put(k, v) }
        }
        val out = post(url, body.toString(),
            mapOf("Authorization" to "Bearer $key"))
        val msg = JSONObject(out).getJSONArray("choices")
            .getJSONObject(0).getJSONObject("message")
        parse(msg.optString("content", ""))
    } catch (e: Exception) { null }

    private fun askCF(acc: String, tok: String, text: String): Cmd? = try {
        val url = "https://api.cloudflare.com/client/v4/accounts/" +
            "$acc/ai/run/@cf/meta/llama-3.3-70b-instruct-fp8-fast"
        val body = JSONObject().apply {
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system"); put("content", SYS)
                })
                put(JSONObject().apply {
                    put("role", "user"); put("content", text)
                })
            })
            put("max_tokens", 300)
        }
        val out = post(url, body.toString(),
            mapOf("Authorization" to "Bearer $tok"))
        val r = JSONObject(out).getJSONObject("result")
        var txt = r.optString("response", "")
        if (txt.isBlank()) {
            txt = r.optJSONArray("choices")?.optJSONObject(0)
                ?.optJSONObject("message")?.optString("content", "") ?: ""
        }
        parse(txt)
    } catch (e: Exception) { null }

    /** AI ke jawab se JSON nikalo — wo aksar bakwas ke saath deta hai */
    private fun parse(raw: String): Cmd? {
        if (raw.isBlank()) return null
        val a = raw.indexOf('{')
        val b = raw.lastIndexOf('}')
        if (a < 0 || b <= a) return null
        return try {
            val j = JSONObject(raw.substring(a, b + 1))
            val act = j.optString("action", "").trim()
            if (act.isBlank()) return null
            Cmd(act, j.optString("arg", "").trim(),
                j.optString("say", "").trim(), true)
        } catch (e: Exception) { null }
    }

    private fun post(
        url: String, body: String, headers: Map<String, String>
    ): String {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 12000
            readTimeout = 25000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        OutputStreamWriter(c.outputStream).use { it.write(body) }
        return c.inputStream.bufferedReader().use { it.readText() }
    }
}
