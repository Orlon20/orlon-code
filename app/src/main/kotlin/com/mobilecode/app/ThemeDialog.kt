package com.mobilecode.app

import android.app.Dialog
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Full-screen theme picker: appearance (system/light/dark) plus every OpenCode theme with a live swatch. */
class ThemeDialog(
    private val ctx: Context, private val themes: List<ThemeInfo>, private var themeId: String, private var scheme: String,
    private val onPick: (themeId: String, scheme: String) -> Unit,
) : Dialog(ctx, android.R.style.Theme_DeviceDefault_NoActionBar) {

    private val list = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
    private val appearance = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }

    private fun darkNow() = when (scheme) { "dark" -> true; "light" -> false
        else -> (ctx.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES }

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Palette.BG))
            statusBarColor = Palette.BG; navigationBarColor = Palette.BG
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        val head = LinearLayout(ctx).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(ctx.label("Themes", 20f, bold = true), LinearLayout.LayoutParams(0, -2, 1f))
            addView(ctx.label("✕", 20f, Palette.MUTED).apply { setPadding(ctx.dp(12), ctx.dp(8), ctx.dp(4), ctx.dp(8)); setOnClickListener { dismiss() } })
        }
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL; setPadding(ctx.dp(20), ctx.dp(16), ctx.dp(20), ctx.dp(8)); applySystemInsets()
            addView(head)
            addView(ctx.label("Appearance", 12f, Palette.FAINT).apply { setPadding(0, ctx.dp(16), 0, ctx.dp(8)) })
            addView(appearance)
            addView(ctx.label("Theme", 12f, Palette.FAINT).apply { setPadding(0, ctx.dp(20), 0, ctx.dp(8)) })
            addView(ScrollView(ctx).apply { addView(list) }, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        setContentView(root); render()
    }

    private fun chip(text: String, selected: Boolean, onClick: () -> Unit) = TextView(ctx).apply {
        this.text = text; textSize = 14f; gravity = Gravity.CENTER; setPadding(0, ctx.dp(11), 0, ctx.dp(11))
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setTextColor(if (selected) Palette.INVERSE_FG else Palette.FG)
        background = GradientDrawable().apply {
            cornerRadius = ctx.dp(8).toFloat()
            if (selected) setColor(Palette.INVERSE_BG) else { setColor(Palette.BUTTON); setStroke(ctx.dp(1), Palette.BORDER) }
        }
        setOnClickListener { onClick() }
    }

    private fun render() {
        appearance.removeAllViews()
        listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEach { (k, name) ->
            appearance.addView(chip(name, scheme == k) { scheme = k; onPick(themeId, scheme); render() },
                LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = ctx.dp(8) })
        }
        list.removeAllViews()
        val dark = darkNow()
        for (t in themes) {
            val sw = if (dark) t.dark else t.light
            val selected = t.id == themeId
            val swatch = TextView(ctx).apply {
                text = "Aa"; textSize = 13f; gravity = Gravity.CENTER; setTextColor(sw.text)
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                background = GradientDrawable().apply { setColor(sw.bg); cornerRadius = ctx.dp(8).toFloat(); setStroke(ctx.dp(2), sw.primary) }
            }
            val dots = LinearLayout(ctx).apply {
                for (c in listOf(sw.primary, sw.accent)) addView(android.view.View(ctx).apply {
                    background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(c) }
                }, LinearLayout.LayoutParams(ctx.dp(10), ctx.dp(10)).apply { marginEnd = ctx.dp(4); topMargin = ctx.dp(4) })
            }
            val names = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                addView(ctx.label(t.name, 15f, bold = true)); addView(dots)
            }
            val row = LinearLayout(ctx).apply {
                gravity = Gravity.CENTER_VERTICAL; setPadding(ctx.dp(12), ctx.dp(10), ctx.dp(12), ctx.dp(10))
                background = GradientDrawable().apply {
                    cornerRadius = ctx.dp(10).toFloat()
                    if (selected) { setColor(Palette.LAYER); setStroke(ctx.dp(1), Palette.BORDER) } else setColor(0)
                }
                addView(swatch, LinearLayout.LayoutParams(ctx.dp(52), ctx.dp(40)).apply { marginEnd = ctx.dp(14) })
                addView(names, LinearLayout.LayoutParams(0, -2, 1f))
                if (selected) addView(ctx.label("✓", 18f, Palette.ACCENT))
                setOnClickListener { themeId = t.id; onPick(themeId, scheme); render() }
            }
            list.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = ctx.dp(4) })
        }
    }

    /** Re-style after the theme changed (called once the page reports its new colors). */
    fun restyle() {
        window?.apply { setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Palette.BG)); statusBarColor = Palette.BG; navigationBarColor = Palette.BG }
        (list.parent as? ScrollView)?.let { }
        render()
    }
}
