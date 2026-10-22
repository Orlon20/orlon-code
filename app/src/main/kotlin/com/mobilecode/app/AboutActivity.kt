package com.mobilecode.app

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity

class AboutActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyThemeChrome()
        val version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: ""
        val notices = runCatching { assets.open("legal/NOTICES.txt").bufferedReader().readText() }.getOrDefault("")
        val licenseText = label(notices, 12f, Palette.MUTED).apply { setPadding(dp(14), dp(12), dp(14), dp(12)); background = cardBg(); visibility = View.GONE }
        val toggle = label("Open-source licenses", 15f, bold = true).apply {
            setPadding(dp(14), dp(14), dp(14), dp(14)); background = cardBg()
            setOnClickListener { licenseText.visibility = if (licenseText.visibility == View.VISIBLE) View.GONE else View.VISIBLE }
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
            addView(wordmark(22), LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(24) })
            addView(label("Version $version", 13f, Palette.FAINT).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
            addView(label("Run the OpenCode coding agent on your phone, and build Android apps with it, without a computer.", 14f, Palette.MUTED)
                .apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(20) })
            addView(label("Powered by OpenCode. Orlon Code is an independent, open-source project (MIT) and is not affiliated with OpenCode.", 12f, Palette.FAINT)
                .apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
            addView(toggle, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(28) })
            addView(licenseText, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(Palette.BG); setPadding(dp(24), dp(16), dp(24), dp(24)); applySystemInsets(); addView(content)
        })
    }
}
