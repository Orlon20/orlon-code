package com.mobilecode.app

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.app.Activity
import android.graphics.drawable.ColorDrawable
import androidx.core.view.WindowInsetsControllerCompat
import android.widget.ArrayAdapter
import android.widget.ListPopupWindow
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat


/** The Orlon Code pixel wordmark at the given height (dp). */
fun Context.wordmark(heightDp: Int, color: Int = Palette.FG, alpha: Float = 1f) = WordmarkView(this, dp(heightDp), color, alpha)

fun Context.dp(v: Int) = (v * resources.displayMetrics.density).toInt()

fun Context.label(text: String, size: Float = 14f, color: Int = Palette.FG, mono: Boolean = false, bold: Boolean = false) = TextView(this).apply {
    this.text = text; textSize = size; setTextColor(color)
    typeface = if (mono) Typeface.MONOSPACE else if (bold) Typeface.create("sans-serif-medium", Typeface.NORMAL) else Typeface.SANS_SERIF
}

private fun Context.box(fill: Int, stroke: Int = 0, radius: Int = 10) = GradientDrawable().apply {
    setColor(fill); cornerRadius = dp(radius).toFloat(); if (stroke != 0) setStroke(dp(1), stroke)
}

fun Button.stylePrimary() {
    setTextColor(Palette.INVERSE_FG); background = GradientDrawable().apply { setColor(Palette.INVERSE_BG); cornerRadius = context.dp(10).toFloat() }
}
fun Button.styleGhost() {
    setTextColor(Palette.FG); background = GradientDrawable().apply { setColor(Palette.BUTTON); cornerRadius = context.dp(10).toFloat(); setStroke(context.dp(1), Palette.BORDER) }
}

fun Context.primaryButton(text: String, onClick: () -> Unit) = Button(this).apply {
    this.text = text; isAllCaps = false; textSize = 14f
    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); stateListAnimator = null
    stylePrimary(); setOnClickListener { onClick() }
}

fun Context.ghostButton(text: String, onClick: () -> Unit) = Button(this).apply {
    this.text = text; isAllCaps = false; textSize = 14f
    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); stateListAnimator = null
    styleGhost(); setOnClickListener { onClick() }
}

fun Context.field(hint: String, password: Boolean = false) = EditText(this).apply {
    this.hint = hint; setHintTextColor(Palette.FAINT); setTextColor(Palette.FG); typeface = Typeface.SANS_SERIF; textSize = 14f
    setSingleLine(); setPadding(dp(14), dp(12), dp(14), dp(12))
    background = box(Palette.CARD, Palette.BORDER)
    inputType = if (password) 129 else 17 // textPassword / textUri
}

fun View.applySystemInsets() {
    val l = paddingLeft; val t = paddingTop; val r = paddingRight; val b = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val i = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
        v.setPadding(l + i.left, t + i.top, r + i.right, b + i.bottom); WindowInsetsCompat.CONSUMED
    }
}

/** Loads the saved theme palette and paints window background, status/navigation bars to match. Call first in onCreate. */
fun Activity.applyThemeChrome() {
    Palette.apply(Prefs(this).paletteJson)
    window.setBackgroundDrawable(ColorDrawable(Palette.BG))
    window.statusBarColor = Palette.BG; window.navigationBarColor = Palette.BG
    WindowInsetsControllerCompat(window, window.decorView).apply {
        val light = isLightColor(Palette.BG); isAppearanceLightStatusBars = light; isAppearanceLightNavigationBars = light
    }
}

/** A themed popup menu (replaces PopupMenu so it follows the selected theme). */
fun Context.showMenu(anchor: View, items: List<Pair<Int, String>>, onPick: (Int) -> Unit) {
    val popup = ListPopupWindow(this)
    popup.anchorView = anchor; popup.isModal = true; popup.width = dp(260)
    popup.setBackgroundDrawable(GradientDrawable().apply { setColor(Palette.LAYER); cornerRadius = dp(10).toFloat(); setStroke(dp(1), Palette.BORDER) })
    popup.setAdapter(object : ArrayAdapter<String>(this, 0, items.map { it.second }) {
        override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View =
            label(items[position].second, 15f).apply { setPadding(dp(18), dp(14), dp(18), dp(14)) }
    })
    popup.setOnItemClickListener { _, _, pos, _ -> popup.dismiss(); onPick(items[pos].first) }
    popup.show()
}

/** A rounded surface with a hairline border, in the current theme. */
fun Context.cardBg() = GradientDrawable().apply { setColor(Palette.CARD); cornerRadius = dp(12).toFloat(); setStroke(dp(1), Palette.BORDER) }
