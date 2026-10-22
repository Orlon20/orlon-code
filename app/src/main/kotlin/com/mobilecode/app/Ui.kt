package com.mobilecode.app

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/** OpenCode's own design tokens (--v2-*), so native screens match the web UI. */
object Palette {
    const val BG = 0xFF080808.toInt()        // background-bg-deep
    const val CARD = 0xFF161616.toInt()      // background-bg-base
    const val LAYER = 0xFF242424.toInt()     // background-bg-layer-01
    const val BUTTON = 0x0FFFFFFF            // background-bg-button-neutral (white @ 6%)
    const val BORDER = 0x1AFFFFFF            // border-border-base
    const val FG = 0xFFFAFAFA.toInt()        // text-text-base
    const val MUTED = 0xFFAEAEAE.toInt()     // text-text-muted
    const val FAINT = 0xFF808080.toInt()     // text-text-faint
    const val ACCENT = 0xFFA2BCFF.toInt()    // text-text-accent
    const val INVERSE_BG = 0xFFFFFFFF.toInt()
    const val INVERSE_FG = 0xFF161616.toInt()
}

fun Context.dp(v: Int) = (v * resources.displayMetrics.density).toInt()

fun Context.label(text: String, size: Float = 14f, color: Int = Palette.FG, mono: Boolean = false, bold: Boolean = false) = TextView(this).apply {
    this.text = text; textSize = size; setTextColor(color)
    typeface = if (mono) Typeface.MONOSPACE else if (bold) Typeface.create("sans-serif-medium", Typeface.NORMAL) else Typeface.SANS_SERIF
}

private fun Context.box(fill: Int, stroke: Int = 0, radius: Int = 10) = GradientDrawable().apply {
    setColor(fill); cornerRadius = dp(radius).toFloat(); if (stroke != 0) setStroke(dp(1), stroke)
}

fun Context.primaryButton(text: String, onClick: () -> Unit) = Button(this).apply {
    this.text = text; isAllCaps = false; setTextColor(Palette.INVERSE_FG); textSize = 14f
    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); stateListAnimator = null
    background = box(Palette.INVERSE_BG); setOnClickListener { onClick() }
}

fun Context.ghostButton(text: String, onClick: () -> Unit) = Button(this).apply {
    this.text = text; isAllCaps = false; setTextColor(Palette.FG); textSize = 14f
    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); stateListAnimator = null
    background = box(Palette.BUTTON, Palette.BORDER); setOnClickListener { onClick() }
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
