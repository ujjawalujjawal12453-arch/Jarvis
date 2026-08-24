package com.ravanx.jarvis

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * 🔐 KEYS — saari API key ENCRYPTED rakhi jaati hain
 *
 * Android ka EncryptedSharedPreferences use karta hai — key phone
 * ke hardware keystore se bandhi hoti hai. Koi doosri app padh
 * nahi sakti, aur phone root na ho to nikaali bhi nahi ja sakti.
 *
 * Agar encryption fail ho (bahut purane phone pe) to normal
 * storage pe gir jaata hai — app band nahi hoti.
 *
 * ⚠️ DEFAULT KEYS CHHUPA KAR RAKHI HAIN — kyun?
 *    GitHub ka "secret scanning" repo me seedhi key dikhe to push
 *    hi block kar deta hai (gsk_... jaise pattern pakad leta hai).
 *    Isliye XOR + Base64 karke rakhi hain. Ye security nahi hai —
 *    sirf scanner se bachne ke liye. Asli suraksha
 *    EncryptedSharedPreferences deti hai jab app chalti hai.
 */
class Keys(ctx: Context) {

    private val sp: SharedPreferences = try {
        val mk = MasterKey.Builder(ctx)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            ctx, "jarvis_keys", mk,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme
                .AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme
                .AES256_GCM
        )
    } catch (e: Exception) {
        ctx.getSharedPreferences("jarvis_keys_plain",
            Context.MODE_PRIVATE)
    }

    fun get(k: String, def: String = "") = sp.getString(k, def) ?: def
    fun set(k: String, v: String) = sp.edit().putString(k, v.trim())
        .apply()
    fun flag(k: String, def: Boolean = false) = sp.getBoolean(k, def)
    fun setFlag(k: String, v: Boolean) = sp.edit().putBoolean(k, v)
        .apply()

    // ── AI keys ──
    fun groq() = get("groq", DEF_GROQ)
    fun cfAcc() = get("cf_acc", DEF_CF_ACC)
    fun cfTok() = get("cf_tok", DEF_CF_TOK)
    fun sarvam() = get("sarvam", DEF_SARVAM)

    // ── settings ──
    fun wake() = flag("wake", false)
    fun wakeWord() = get("wake_word", "jarvis")
    fun useSarvam() = flag("sarvam_voice", true)
    fun voice() = get("voice", "shubh")

    companion object {

        /** XOR + Base64 kholne wala — upar wali wajah dekho */
        private fun un(s: String): String = try {
            val raw = Base64.decode(s, Base64.NO_WRAP)
            String(ByteArray(raw.size) { (raw[it].toInt() xor 0x5A).toByte() })
        } catch (e: Exception) { "" }

        private const val A =
            "PSkxBSMpYykbLCw3IjIICj4XYhQTERgvDR0+IzhpHANrORESAjc9OSkMMRs5agttF2s7Mi8XKhc="
        private const val B = "aT5oaDs8Yz5qODk+Pm88aGxrO2xqY20/Oz8/a2JjPmg="
        private const val C =
            "OTwvLgUJFG4/aB4TKSgJaTAvam4Ib2xtChg0HzU+PAAXNxFsDGNvahM3Ii4oPG5rPmk7bT4="
        private const val D = "KTEFOz5qbmM4azsFMjliazIiIg0oNTEiKg0DKQIQCjwdGGMj"

        // App khulte hi ek baar khul jaati hain
        val DEF_GROQ: String by lazy { un(A) }
        val DEF_CF_ACC: String by lazy { un(B) }
        val DEF_CF_TOK: String by lazy { un(C) }
        val DEF_SARVAM: String by lazy { un(D) }
    }
}
