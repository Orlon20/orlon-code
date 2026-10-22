package com.mobilecode.app

import android.os.Bundle
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity

class AboutActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyThemeChrome()
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(24), dp(24), dp(24))
            setBackgroundColor(Palette.BG); applySystemInsets()
            addView(wordmark(20))
            addView(label("v1.0", 12f, Palette.FAINT))
            addView(label("\nA mobile client for your own OpenCode server.\n\nPowered by OpenCode (https://opencode.ai), MIT License, © the OpenCode authors. Orlon Code is an independent app and is not affiliated with OpenCode.", 14f))
        })
    }
}
