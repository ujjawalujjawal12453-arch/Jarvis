package com.ravanx.jarvis

import android.Manifest
import android.app.Activity
import android.app.SearchManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 🎬 ACTIONS — asli kaam yahan hota hai
 *
 * Har function saaf jawab deta hai (Hinglish me) jo JARVIS bolega.
 */
object Actions {

    private var torchOn = false

    fun run(act: Activity, c: Brain.Cmd): String {
        return try {
            when (c.action) {
                "open_app"   -> openApp(act, c.arg)
                "yt_search"  -> ytSearch(act, c.arg)
                "google"     -> google(act, c.arg)
                "call"       -> call(act, c.arg)
                "sms"        -> sms(act, c.arg)
                "whatsapp"   -> whatsapp(act, c.arg)
                "torch_on"   -> torch(act, true)
                "torch_off"  -> torch(act, false)
                "lock"       -> lock(act)
                "no_unlock"  -> c.say
                "wifi"       -> panel(act, Settings.ACTION_WIFI_SETTINGS,
                                    "WiFi settings khol di")
                "bluetooth"  -> panel(act,
                                    Settings.ACTION_BLUETOOTH_SETTINGS,
                                    "Bluetooth settings khol di")
                "data"       -> panel(act,
                                    Settings.ACTION_DATA_ROAMING_SETTINGS,
                                    "Data settings khol di")
                "airplane"   -> panel(act,
                                    Settings.ACTION_AIRPLANE_MODE_SETTINGS,
                                    "Airplane settings")
                "vol_up"     -> vol(act, 1)
                "vol_down"   -> vol(act, -1)
                "vol_max"    -> volMax(act)
                "mute"       -> mute(act)
                "bright_up"  -> bright(act, true)
                "bright_down"-> bright(act, false)
                "screenshot" -> shot(act)
                "battery"    -> battery(act)
                "time"       -> "Abhi " + SimpleDateFormat(
                                    "h:mm a", Locale.ENGLISH)
                                    .format(java.util.Date()) + " hue hain"
                "date"       -> "Aaj " + SimpleDateFormat(
                                    "d MMMM yyyy, EEEE", Locale.ENGLISH)
                                    .format(java.util.Date()) + " hai"
                "alarm"      -> alarm(act, c.arg)
                "alarm_app"  -> panel(act, AlarmClock.ACTION_SHOW_ALARMS,
                                    "Alarm khol diya")
                "timer"      -> timer(act, c.arg)
                "chat"       -> c.say.ifBlank { "Ji sir?" }

                // ═══ EYES wale (Accessibility) ═══
                "back"       -> eye(act) { it.back(); "Wapas" }
                "home"       -> eye(act) { it.home(); "Home" }
                "recents"    -> eye(act) { it.recents(); "Recent apps" }
                "notif_panel"-> eye(act) { it.notifPanel()
                                    "Notifications khol diye" }
                "read_notif" -> readNotif(act)
                "read_screen"-> readScreen(act)
                "scroll"     -> eye(act) {
                                    it.scroll(c.arg != "up")
                                    "Scroll kiya" }
                "tap"        -> tap(act, c.arg)
                "type"       -> eye(act) {
                                    if (it.type(c.arg)) "Likh diya"
                                    else "Text box nahi mila" }
                "close_app"  -> eye(act) { it.back(); it.home()
                                    "Band kar diya" }

                // ═══ MEMORY ═══
                "remember"   -> remember(act, c.arg)
                "recall"     -> recall(act, c.arg)
                "all_facts"  -> allFacts(act)

                // ═══ AUR ═══
                "calc"       -> calc(c.arg)
                "copy"       -> copy(act, c.arg)
                "copy_screen"-> copyScreen(act)
                "share"      -> share(act, c.arg)
                "dnd"        -> panel(act,
                                    Settings.ACTION_SOUND_SETTINGS,
                                    "Sound settings khol di")
                "ringer"     -> ringer(act, c.arg)
                "rotate"     -> rotate(act)
                "uninstall"  -> uninstall(act, c.arg)

                else         -> c.say.ifBlank {
                                    "Ye kaam abhi nahi kar sakta sir" }
            }
        } catch (e: Exception) {
            "Dikkat aa gayi sir: ${e.message?.take(60)}"
        }
    }

    // ═══════════════ APP ═══════════════

    private fun openApp(a: Activity, name: String): String {
        // special wale
        when (name) {
            "camera" -> {
                a.startActivity(Intent(
                    MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return "Camera khol diya"
            }
            "settings" -> {
                a.startActivity(Intent(Settings.ACTION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return "Settings khol di"
            }
            "gallery" -> {
                a.startActivity(Intent(Intent.ACTION_VIEW)
                    .setType("image/*")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return "Gallery khol di"
            }
            "clock" -> {
                a.startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return "Clock khol diya"
            }
            "calculator" -> {
                val i = Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_APP_CALCULATOR)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                return try { a.startActivity(i); "Calculator khol diya" }
                catch (e: Exception) { "Calculator nahi mila" }
            }
            "files" -> {
                a.startActivity(Intent(Intent.ACTION_GET_CONTENT)
                    .setType("*/*")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return "Files khol diye"
            }
        }

        val pkg = if (name.contains(".")) name
                  else Brain.APPS[name.lowercase()] ?: name
        val i = a.packageManager.getLaunchIntentForPackage(pkg)
        if (i != null) {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            a.startActivity(i)
            return "Khol diya sir"
        }
        // App nahi hai — Play Store pe le jao
        return try {
            a.startActivity(Intent(Intent.ACTION_VIEW,
                Uri.parse("market://details?id=$pkg"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            "Ye app phone me nahi hai — Play Store khol diya"
        } catch (e: Exception) {
            "Ye app aapke phone me nahi hai sir"
        }
    }

    // ═══════════════ SEARCH ═══════════════

    private fun ytSearch(a: Activity, q: String): String {
        if (q.isBlank()) return openApp(a, "youtube")
        val enc = Uri.encode(q)
        // Pehle YouTube app try karo
        try {
            val i = Intent(Intent.ACTION_SEARCH).apply {
                setPackage("com.google.android.youtube")
                putExtra("query", q)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            a.startActivity(i)
            return "YouTube pe '$q' dhoondh raha hoon"
        } catch (e: Exception) { }
        // App nahi — browser me
        a.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(
            "https://www.youtube.com/results?search_query=$enc"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return "YouTube pe '$q' dhoondh raha hoon"
    }

    private fun google(a: Activity, q: String): String {
        if (q.isBlank()) return "Kya search karun sir?"
        try {
            val i = Intent(Intent.ACTION_WEB_SEARCH).apply {
                putExtra(SearchManager.QUERY, q)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            a.startActivity(i)
            return "'$q' search kar raha hoon"
        } catch (e: Exception) { }
        a.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(
            "https://www.google.com/search?q=${Uri.encode(q)}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return "'$q' search kar raha hoon"
    }

    // ═══════════════ CALL / SMS ═══════════════

    /** Contact ke naam se number dhoondho */
    private fun findNumber(a: Activity, name: String): String? {
        if (ContextCompat.checkSelfPermission(a,
                Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) return null
        val uri = Uri.withAppendedPath(
            ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI,
            Uri.encode(name))
        a.contentResolver.query(uri, arrayOf(
            ContactsContract.CommonDataKinds.Phone.NUMBER),
            null, null, null)?.use {
            if (it.moveToFirst()) return it.getString(0)
        }
        return null
    }

    private fun call(a: Activity, who: String): String {
        if (who.isBlank()) return "Kis ko call karna hai sir?"
        // Number hi bola ho to
        val digits = who.filter { it.isDigit() || it == '+' }
        val num = if (digits.length >= 10) digits
                  else findNumber(a, who)
        if (num == null) return "'$who' naam ka contact nahi mila sir"

        val perm = ContextCompat.checkSelfPermission(a,
            Manifest.permission.CALL_PHONE) ==
            PackageManager.PERMISSION_GRANTED
        val act = if (perm) Intent.ACTION_CALL else Intent.ACTION_DIAL
        a.startActivity(Intent(act, Uri.parse("tel:$num"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return if (perm) "$who ko call laga raha hoon"
               else "$who ka number laga diya — dial dabaiye"
    }

    private fun sms(a: Activity, arg: String): String {
        val p = arg.split("|")
        val who = p.getOrNull(0)?.trim() ?: ""
        val msg = p.getOrNull(1)?.trim() ?: ""
        val digits = who.filter { it.isDigit() || it == '+' }
        val num = if (digits.length >= 10) digits else findNumber(a, who)
        if (num == null) return "'$who' ka contact nahi mila sir"
        a.startActivity(Intent(Intent.ACTION_SENDTO,
            Uri.parse("smsto:$num")).apply {
            putExtra("sms_body", msg)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        return "Message taiyaar hai — send dabaiye"
    }

    private fun whatsapp(a: Activity, arg: String): String {
        val p = arg.split("|")
        val who = p.getOrNull(0)?.trim() ?: ""
        val msg = p.getOrNull(1)?.trim() ?: ""
        val digits = who.filter { it.isDigit() }
        val num = if (digits.length >= 10) digits
                  else findNumber(a, who)?.filter { it.isDigit() }
        if (num == null) return "'$who' ka number nahi mila sir"
        val n = if (num.length == 10) "91$num" else num
        return try {
            a.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(
                "https://wa.me/$n?text=${Uri.encode(msg)}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            // ✅ Eyes chalu hai to SEND bhi khud daba dega
            val e = Eyes.live
            if (e != null && msg.isNotBlank()) {
                e.sendWhatsApp { /* JARVIS baad me bolega */ }
                "WhatsApp khol diya — send bhi daba raha hoon"
            } else if (msg.isNotBlank()) {
                "WhatsApp khol diya — send dabaiye " +
                "(Eyes on karo to main khud daba dunga)"
            } else "WhatsApp khol diya"
        } catch (e: Exception) {
            "WhatsApp nahi khul paya sir"
        }
    }

    // ═══════════════ TORCH ═══════════════

    private fun torch(a: Activity, on: Boolean): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
            return "Aapka Android purana hai sir"
        val cm = a.getSystemService(Context.CAMERA_SERVICE)
            as CameraManager
        val id = cm.cameraIdList.firstOrNull { cid ->
            cm.getCameraCharacteristics(cid).get(
                android.hardware.camera2.CameraCharacteristics
                    .FLASH_INFO_AVAILABLE) == true
        } ?: return "Is phone me flash nahi hai"
        cm.setTorchMode(id, on)
        torchOn = on
        return if (on) "Torch chalu" else "Torch band"
    }

    // ═══════════════ LOCK ═══════════════

    private fun lock(a: Activity): String {
        // Pehle Eyes try karo — usme permission dobara nahi maangni
        val e = Eyes.live
        if (e != null && Build.VERSION.SDK_INT >= 28 && e.lockScreen())
            return "Lock kar diya"
        val dpm = a.getSystemService(Context.DEVICE_POLICY_SERVICE)
            as DevicePolicyManager
        val admin = ComponentName(a, LockAdmin::class.java)
        if (!dpm.isAdminActive(admin)) {
            a.startActivity(Intent(
                DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "Phone lock karne ke liye JARVIS ko ye " +
                    "permission chahiye. Ek baar hi deni hai.")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            return "Ek baar permission de dijiye sir, phir hamesha " +
                   "lock kar paunga"
        }
        dpm.lockNow()
        return "Lock kar diya"
    }

    // ═══════════════ VOLUME / BRIGHTNESS ═══════════════

    private fun am(a: Activity) =
        a.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private fun vol(a: Activity, dir: Int): String {
        am(a).adjustStreamVolume(AudioManager.STREAM_MUSIC,
            if (dir > 0) AudioManager.ADJUST_RAISE
            else AudioManager.ADJUST_LOWER,
            AudioManager.FLAG_SHOW_UI)
        return if (dir > 0) "Volume badha diya" else "Volume kam kar diya"
    }

    private fun volMax(a: Activity): String {
        val m = am(a)
        m.setStreamVolume(AudioManager.STREAM_MUSIC,
            m.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
            AudioManager.FLAG_SHOW_UI)
        return "Full volume kar diya"
    }

    private fun mute(a: Activity): String {
        am(a).setStreamVolume(AudioManager.STREAM_MUSIC, 0,
            AudioManager.FLAG_SHOW_UI)
        return "Silent kar diya"
    }

    private fun bright(a: Activity, up: Boolean): String {
        if (!Settings.System.canWrite(a)) {
            a.startActivity(Intent(
                Settings.ACTION_MANAGE_WRITE_SETTINGS,
                Uri.parse("package:${a.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return "Brightness badalne ki permission de dijiye sir"
        }
        val cur = Settings.System.getInt(a.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS, 128)
        val next = (cur + if (up) 50 else -50).coerceIn(10, 255)
        Settings.System.putInt(a.contentResolver,
            Settings.System.SCREEN_BRIGHTNESS, next)
        return if (up) "Brightness badha di" else "Brightness kam ki"
    }

    // ═══════════════ AUR ═══════════════

    private fun panel(a: Activity, action: String, ok: String): String {
        a.startActivity(Intent(action)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return ok
    }

    @Suppress("UNUSED_PARAMETER")
    private fun shot(a: Activity): String {
        // ✅ Ab Eyes (Accessibility) se ho jata hai — Android 9+
        val e = Eyes.live
        if (e != null && Build.VERSION.SDK_INT >= 28) {
            return if (e.screenshot()) "Screenshot le liya"
                   else "Nahi ho paya — Power + Volume Down dabaiye"
        }
        return "Sir, screenshot ke liye JARVIS ko Accessibility " +
               "permission chahiye (Settings me). Ya Power + " +
               "Volume Down ek saath dabaiye."
    }

    private fun battery(a: Activity): String {
        val bm = a.getSystemService(Context.BATTERY_SERVICE)
            as BatteryManager
        val p = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val ch = bm.isCharging
        return "Battery $p% hai sir" + if (ch) ", charge ho rahi hai"
               else ""
    }

    private fun alarm(a: Activity, hm: String): String {
        val p = hm.split(":")
        var h = p.getOrNull(0)?.toIntOrNull() ?: 7
        val m = p.getOrNull(1)?.toIntOrNull() ?: 0
        // "7 baje" bola to subah 7 maano agar abhi raat hai
        val now = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (h in 1..11 && now >= 12) { /* subah ka hi rakho */ }
        a.startActivity(Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, h)
            putExtra(AlarmClock.EXTRA_MINUTES, m)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        return "Alarm laga diya — $h:${m.toString().padStart(2, '0')}"
    }

    // ═══════════════ EYES (Accessibility) ═══════════════

    /**
     * Eyes chalu hai to kaam karo, warna user ko batao.
     * ⚠️ Ye permission user ko KHUD deni padti hai — Android
     *    ise programmatically on nahi karne deta.
     */
    private fun eye(a: Activity, f: (Eyes) -> String): String {
        val e = Eyes.live ?: return needEyes(a)
        return try { f(e) } catch (ex: Exception) {
            "Nahi ho paya sir"
        }
    }

    private fun needEyes(a: Activity): String {
        Eyes.openSettings(a)
        return "Sir, iske liye ek permission chahiye. Settings khol " +
               "di hai — list me JARVIS dhoondh kar ON kar dijiye. " +
               "Ek baar hi karna hai."
    }

    private fun tap(a: Activity, what: String): String {
        val e = Eyes.live ?: return needEyes(a)
        return if (e.tapText(what)) "'$what' daba diya"
               else "Screen pe '$what' nahi mila sir"
    }

    private fun readScreen(a: Activity): String {
        val e = Eyes.live ?: return needEyes(a)
        val t = e.readScreen()
        return if (t.isBlank()) "Screen pe kuch padhne layak nahi hai"
               else t.take(700)
    }

    private fun readNotif(a: Activity): String {
        val e = Eyes.live ?: return needEyes(a)
        val n = e.lastNotifs(5)
        return if (n.isEmpty()) "Koi naya notification nahi hai sir"
               else "Aapke notifications:\n" + n.joinToString("\n")
    }

    // ═══════════════ MEMORY ═══════════════

    private fun remember(a: Activity, raw: String): String {
        val m = Memory(a)
        val p = raw.split("=", limit = 2)
        return if (p.size == 2) {
            m.remember(p[0].trim(), p[1].trim())
            "Yaad rakh liya — ${p[0].trim()} hai ${p[1].trim()}"
        } else {
            m.remember("note_" + System.currentTimeMillis()
                .toString().takeLast(4), raw)
            "Yaad rakh liya sir"
        }
    }

    private fun recall(a: Activity, what: String): String {
        val v = Memory(a).recall(what)
        return v?.let { "Aapka $what $it hai sir" }
            ?: "Ye mujhe nahi pata sir — bataiye to yaad rakh lunga"
    }

    private fun allFacts(a: Activity): String {
        val f = Memory(a).allFacts()
        if (f.isEmpty()) return "Abhi kuch yaad nahi hai sir"
        return "Ye sab yaad hai:\n" +
            f.entries.joinToString("\n") { "• ${it.key} — ${it.value}" }
    }

    // ═══════════════ CALCULATOR ═══════════════

    /** Chhota sa calculator — "25 * 4" jaise sawaal */
    private fun calc(raw: String): String {
        var t = raw.lowercase()
            .replace("plus", "+").replace("jod", "+")
            .replace("minus", "-").replace("ghata", "-")
            .replace("multiply", "*").replace("guna", "*")
            .replace("into", "*").replace("x", "*").replace("×", "*")
            .replace("divide", "/").replace("bhag", "/")
            .replace("÷", "/").replace("by", "/")
        val m = Regex("(-?\\d+(?:\\.\\d+)?)\\s*([+\\-*/])\\s*" +
                      "(-?\\d+(?:\\.\\d+)?)").find(t)
            ?: return "Sawaal samajh nahi aaya sir"
        val x = m.groupValues[1].toDouble()
        val y = m.groupValues[3].toDouble()
        val r = when (m.groupValues[2]) {
            "+" -> x + y
            "-" -> x - y
            "*" -> x * y
            "/" -> if (y == 0.0) return "Zero se bhag nahi hota sir"
                   else x / y
            else -> return "Samajh nahi aaya"
        }
        val out = if (r == r.toLong().toDouble()) r.toLong().toString()
                  else String.format(Locale.US, "%.2f", r)
        return "Jawab hai $out"
    }

    // ═══════════════ CLIPBOARD / SHARE ═══════════════

    private fun copy(a: Activity, text: String): String {
        val cm = a.getSystemService(Context.CLIPBOARD_SERVICE)
            as android.content.ClipboardManager
        cm.setPrimaryClip(
            android.content.ClipData.newPlainText("jarvis", text))
        return "Copy kar liya"
    }

    private fun copyScreen(a: Activity): String {
        val e = Eyes.live ?: return needEyes(a)
        val t = e.readScreen()
        if (t.isBlank()) return "Screen pe kuch nahi mila"
        return copy(a, t)
    }

    private fun share(a: Activity, text: String): String {
        a.startActivity(Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return "Share menu khol diya"
    }

    // ═══════════════ RINGER / ROTATE ═══════════════

    private fun ringer(a: Activity, mode: String): String {
        val m = am(a)
        return try {
            m.ringerMode = when (mode) {
                "silent" -> AudioManager.RINGER_MODE_SILENT
                "vibrate" -> AudioManager.RINGER_MODE_VIBRATE
                else -> AudioManager.RINGER_MODE_NORMAL
            }
            "Phone $mode mode me"
        } catch (e: Exception) {
            panel(a, Settings.ACTION_SOUND_SETTINGS,
                "Sound settings khol di — DND permission chahiye")
        }
    }

    private fun rotate(a: Activity): String {
        if (!Settings.System.canWrite(a)) {
            a.startActivity(Intent(
                Settings.ACTION_MANAGE_WRITE_SETTINGS,
                Uri.parse("package:${a.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return "Permission de dijiye sir"
        }
        val cur = Settings.System.getInt(a.contentResolver,
            Settings.System.ACCELEROMETER_ROTATION, 0)
        Settings.System.putInt(a.contentResolver,
            Settings.System.ACCELEROMETER_ROTATION, 1 - cur)
        return if (cur == 0) "Auto-rotate chalu" else "Auto-rotate band"
    }

    private fun uninstall(a: Activity, name: String): String {
        val pkg = Brain.APPS[name.lowercase()]
            ?: return "'$name' app pehchan nahi paya sir"
        return try {
            a.startActivity(Intent(Intent.ACTION_DELETE,
                Uri.parse("package:$pkg"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            "Uninstall screen khol di"
        } catch (e: Exception) { "Nahi ho paya sir" }
    }

    private fun timer(a: Activity, min: String): String {
        val m = min.toIntOrNull() ?: 5
        a.startActivity(Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, m * 60)
            putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        return "$m minute ka timer laga diya"
    }
}
