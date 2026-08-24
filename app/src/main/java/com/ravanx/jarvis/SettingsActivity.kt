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
    }
}
