package com.ravanx.jarvis

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.ravanx.jarvis.databinding.ActivitySettingsBinding

/**
 * ⚙️ SETTINGS — API keys aur awaaz
 * Saari key ENCRYPTED storage me jaati hai (Keys.kt).
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var b: ActivitySettingsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(b.root)
        val k = Keys(this)

        b.groq.setText(k.groq())
        b.cfAcc.setText(k.cfAcc())
        b.cfTok.setText(k.cfTok())
        b.sarvam.setText(k.sarvam())
        b.wakeWord.setText(k.wakeWord())
        b.sarvamVoice.isChecked = k.useSarvam()

        b.save.setOnClickListener {
            k.set("groq", b.groq.text.toString())
            k.set("cf_acc", b.cfAcc.text.toString())
            k.set("cf_tok", b.cfTok.text.toString())
            k.set("sarvam", b.sarvam.text.toString())
            k.set("wake_word",
                b.wakeWord.text.toString().ifBlank { "jarvis" })
            k.setFlag("sarvam_voice", b.sarvamVoice.isChecked)
            Toast.makeText(this, "Save ho gaya sir ✅",
                Toast.LENGTH_SHORT).show()
            finish()
        }
        b.back.setOnClickListener { finish() }

        // ═══ 🩺 AI JAANCH ═══
        //
        // User ko shak tha "API use hi nahi ho rahi". Ab wo
        // khud dekh sakta hai — har model ko sach me poochha
        // jaata hai aur natija samne aata hai.
        b.diag.setOnClickListener {
            // pehle jo type kiya hai wo save karo, warna purani
            // key se test hoga
            k.set("groq", b.groq.text.toString().trim())
            k.set("cf_acc", b.cfAcc.text.toString().trim())
            k.set("cf_tok", b.cfTok.text.toString().trim())

            val dlg = androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("🩺 Jaanch chal rahi hai…")
                .setMessage("Har AI model ko sach me poochh raha "
                    + "hoon.\nThoda ruko sir…")
                .setCancelable(false)
                .show()

            Thread {
                val out = try { Brain.diagnose(this) }
                    catch (e: Exception) {
                        "Jaanch me dikkat: " + e.message }
                runOnUiThread {
                    dlg.dismiss()
                    val tv = android.widget.TextView(this)
                    tv.text = out
                    tv.setTextIsSelectable(true)
                    tv.textSize = 12f
                    tv.typeface = android.graphics.Typeface.MONOSPACE
                    tv.setTextColor(0xFFE2E8F0.toInt())
                    val pad = (16 * resources.displayMetrics.density)
                        .toInt()
                    tv.setPadding(pad, pad, pad, pad)
                    val sc = android.widget.ScrollView(this)
                    sc.addView(tv)

                    androidx.appcompat.app.AlertDialog.Builder(this)
                        .setView(sc)
                        .setPositiveButton("Theek hai", null)
                        .setNeutralButton("Copy karo") { _, _ ->
                            val cm = getSystemService(
                                CLIPBOARD_SERVICE)
                                as android.content.ClipboardManager
                            cm.setPrimaryClip(
                                android.content.ClipData.newPlainText(
                                    "jarvis", out))
                            Toast.makeText(this,
                                "Copy ho gaya — mujhe bhej dijiye",
                                Toast.LENGTH_SHORT).show()
                        }
                        .show()
                }
            }.start()
        }
    }
}
