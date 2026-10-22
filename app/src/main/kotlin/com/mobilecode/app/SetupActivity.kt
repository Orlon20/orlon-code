package com.mobilecode.app

import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlin.concurrent.thread

/** A thin rounded progress bar in the current theme. */
private class ThinProgress(ctx: android.content.Context) : View(ctx) {
    var progress = 0
        set(v) { field = v.coerceIn(0, 100); invalidate() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(c: Canvas) {
        val r = height / 2f; val box = RectF(0f, 0f, width.toFloat(), height.toFloat())
        paint.color = (Palette.FG and 0x00FFFFFF) or 0x22000000
        c.drawRoundRect(box, r, r, paint)
        paint.color = Palette.FG
        if (progress > 0) c.drawRoundRect(RectF(0f, 0f, width * progress / 100f, height.toFloat()), r, r, paint)
    }
}

/** First launch: downloads and prepares the on-device Linux environment and OpenCode. One centered column, nothing else. */
class SetupActivity : AppCompatActivity() {
    private lateinit var step: TextView
    private lateinit var percent: TextView
    private lateinit var bar: ThinProgress
    private lateinit var go: Button
    private lateinit var detailsToggle: TextView
    private lateinit var details: TextView
    private lateinit var detailsBox: ScrollView
    private lateinit var progressGroup: LinearLayout
    private var running = false
    private val logText = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyThemeChrome()

        val tagline = label("Vibecode on your phone.", 14f, Palette.MUTED).apply { gravity = Gravity.CENTER }

        go = primaryButton("Get Started") { start() }

        step = label("", 14f).apply { gravity = Gravity.CENTER }
        percent = label("0%", 28f, bold = true).apply { gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(14)) }
        bar = ThinProgress(this)
        detailsToggle = label("Show details", 13f, Palette.MUTED).apply {
            gravity = Gravity.CENTER; setPadding(dp(16), dp(14), dp(16), dp(6)); visibility = View.GONE
            setOnClickListener { val show = detailsBox.visibility != View.VISIBLE; detailsBox.visibility = if (show) View.VISIBLE else View.GONE; text = if (show) "Hide details" else "Show details" }
        }
        details = label("", 11f, Palette.MUTED, mono = true)
        detailsBox = ScrollView(this).apply {
            visibility = View.GONE; background = cardBg(); setPadding(dp(12), dp(10), dp(12), dp(10)); addView(details)
        }
        progressGroup = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; visibility = View.GONE
            addView(percent, LinearLayout.LayoutParams(-1, -2))
            addView(bar, LinearLayout.LayoutParams(-1, dp(6)))
            addView(step.apply { setPadding(0, dp(16), 0, 0) }, LinearLayout.LayoutParams(-1, -2))
        }

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            addView(wordmark(30), LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = dp(24) })
            addView(tagline, LinearLayout.LayoutParams(-1, -2))
            addView(go, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(36) })
            addView(progressGroup, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
            addView(detailsToggle, LinearLayout.LayoutParams(-2, -2))
            addView(detailsBox, LinearLayout.LayoutParams(-1, dp(150)))
        }
        val footer = label("Powered by OpenCode", 11f, Palette.FAINT).apply { gravity = Gravity.CENTER; setPadding(0, dp(12), 0, dp(16)) }
        setContentView(FrameLayout(this).apply {
            setBackgroundColor(Palette.BG); setPadding(dp(28), 0, dp(28), 0); applySystemInsets()
            addView(column, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER))
            addView(footer, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        })
    }

    private fun setProgress(text: String, pct: Int) { step.text = text; percent.text = "$pct%"; bar.progress = pct }

    private fun start() {
        if (running) return
        running = true; window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        go.visibility = View.GONE
        detailsToggle.visibility = View.GONE; detailsBox.visibility = View.GONE
        step.setTextColor(Palette.FG); progressGroup.visibility = View.VISIBLE; setProgress("Starting…", 0); logText.clear(); details.text = ""
        thread {
            try {
                Installer(this, { s, pct -> runOnUiThread { setProgress(s, pct) } }, { l ->
                    runOnUiThread { logText.append(l).append('\n'); if (logText.length > 6000) logText.delete(0, logText.length - 4000); details.text = logText }
                }).run()
                runOnUiThread { startActivity(Intent(this, MainActivity::class.java)); finish() }
            } catch (e: Exception) {
                runOnUiThread {
                    running = false; window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    step.text = e.message ?: "Setup failed."; step.setTextColor(0xFFFF6B6B.toInt())
                    go.text = "Try again"; go.visibility = View.VISIBLE
                    detailsToggle.visibility = View.VISIBLE; detailsToggle.text = "Show details"
                }
            }
        }
    }
}
