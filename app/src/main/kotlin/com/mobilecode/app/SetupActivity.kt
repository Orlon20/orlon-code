package com.mobilecode.app

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlin.concurrent.thread

/** First launch: downloads and prepares the on-device Linux environment and OpenCode. */
class SetupActivity : AppCompatActivity() {
    private lateinit var step: TextView
    private lateinit var logv: TextView
    private lateinit var bar: ProgressBar
    private lateinit var go: android.widget.Button
    private lateinit var other: android.widget.Button
    private var running = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyThemeChrome()
        step = label("A one-time download of about 100 MB (about 600 MB installed). Wi-Fi recommended.", 14f, Palette.MUTED)
        bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; progressTintList = android.content.res.ColorStateList.valueOf(Palette.ACCENT); visibility = android.view.View.INVISIBLE
        }
        logv = label("", 11f, Palette.FAINT, mono = true)
        go = primaryButton("Set up Orlon Code") { start() }
        other = ghostButton("Connect to a PC instead") { Prefs(this).remote = true; startActivity(Intent(this, ConnectActivity::class.java)); finish() }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(24)); setBackgroundColor(Palette.BG); applySystemInsets()
            addView(wordmark(22))
            addView(label("Vibecode on your phone. No PC needed.", 14f, Palette.MUTED).apply { setPadding(0, dp(4), 0, dp(24)) })
            addView(step); addView(bar, LinearLayout.LayoutParams(-1, dp(24)).apply { topMargin = dp(16) })
            addView(go, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(16) })
            addView(other, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8) })
            addView(ScrollView(this@SetupActivity).apply { addView(logv) }, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(16) })
        })
    }

    private fun start() {
        if (running) return
        running = true; go.isEnabled = false; other.isEnabled = false; go.alpha = 0.4f; other.alpha = 0.4f; bar.visibility = android.view.View.VISIBLE
        thread {
            try {
                Installer(this, { s, pct -> runOnUiThread { step.text = s; bar.progress = pct } }, { l ->
                    runOnUiThread { logv.text = (logv.text.toString() + l + "\n").takeLast(3000) }
                }).run()
                runOnUiThread { startActivity(Intent(this, MainActivity::class.java)); finish() }
            } catch (e: Exception) {
                runOnUiThread {
                    step.text = "Setup failed: ${e.message}"; go.text = "Retry"; go.isEnabled = true; other.isEnabled = true; go.alpha = 1f; other.alpha = 1f; running = false
                }
            }
        }
    }
}
