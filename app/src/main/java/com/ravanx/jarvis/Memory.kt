package com.ravanx.jarvis

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 🧠 MEMORY — JARVIS ki yaaddasht
 *
 * Do hisse:
 *   1. CHAT history  — poori baat-cheet save (app band ho to bhi)
 *   2. FACTS         — "mera naam Ujjawal hai" jaisi baatein
 *
 * Sab phone ke andar hi rehta hai — kahin bheja nahi jaata.
 */
class Memory(private val ctx: Context) {

    data class Msg(val me: Boolean, val text: String,
                   val action: String = "", val ai: Boolean = false,
                   val t: Long = System.currentTimeMillis())

    private val chatFile = File(ctx.filesDir, "chat.json")
    private val factFile = File(ctx.filesDir, "facts.json")

    // ═══════════════ CHAT ═══════════════

    fun load(): MutableList<Msg> {
        if (!chatFile.exists()) return mutableListOf()
        return try {
            val a = JSONArray(chatFile.readText())
            val out = mutableListOf<Msg>()
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                out.add(Msg(o.getBoolean("me"), o.getString("t"),
                    o.optString("a", ""), o.optBoolean("ai", false),
                    o.optLong("ts", 0)))
            }
            out
        } catch (e: Exception) { mutableListOf() }
    }

    fun save(list: List<Msg>) {
        try {
            // Aakhri 200 hi rakho — file badi na ho jaye
            val keep = list.takeLast(200)
            val a = JSONArray()
            keep.forEach {
                a.put(JSONObject().apply {
                    put("me", it.me); put("t", it.text)
                    put("a", it.action); put("ai", it.ai)
                    put("ts", it.t)
                })
            }
            chatFile.writeText(a.toString())
        } catch (e: Exception) {}
    }

    fun clearChat() { try { chatFile.delete() } catch (e: Exception) {} }

    /** AI ko bhejne ke liye — aakhri kuch baatein */
    fun context(n: Int = 6): String {
        val l = load().takeLast(n)
        if (l.isEmpty()) return ""
        return l.joinToString("\n") {
            (if (it.me) "User: " else "JARVIS: ") + it.text.take(160)
        }
    }

    // ═══════════════ FACTS ═══════════════

    private fun facts(): JSONObject = try {
        if (factFile.exists()) JSONObject(factFile.readText())
        else JSONObject()
    } catch (e: Exception) { JSONObject() }

    fun remember(key: String, value: String) {
        try {
            val f = facts()
            f.put(key.lowercase().trim(), value.trim())
            factFile.writeText(f.toString())
        } catch (e: Exception) {}
    }

    fun recall(key: String): String? {
        val f = facts()
        val k = key.lowercase().trim()
        if (f.has(k)) return f.getString(k)
        // Aadha match — "naam" se "mera naam" bhi mil jaye
        f.keys().forEach {
            if (it.contains(k) || k.contains(it))
                return f.getString(it)
        }
        return null
    }

    fun allFacts(): Map<String, String> {
        val f = facts()
        val m = mutableMapOf<String, String>()
        f.keys().forEach { m[it] = f.getString(it) }
        return m
    }

    fun forget(key: String) {
        try {
            val f = facts()
            f.remove(key.lowercase().trim())
            factFile.writeText(f.toString())
        } catch (e: Exception) {}
    }

    /** AI ko batao user ke baare me kya pata hai */
    fun factLine(): String {
        val f = allFacts()
        if (f.isEmpty()) return ""
        return "User ke baare me jo pata hai: " +
            f.entries.joinToString(", ") { "${it.key} = ${it.value}" }
    }
}
