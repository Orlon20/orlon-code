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

object Palette { const val BG = 0xFF0A0A0A.toInt(); const val FG = 0xFFEDEDED.toInt(); const val MUTED = 0xFF8F8F8F.toInt(); const val CARD = 0xFF1A1A1A.toInt(); const val ACCENT = 0xFFFAB283.toInt() }

fun Context.dp(v: Int) = (v * resources.displayMetrics.density).toInt()

fun Context.label(text: String, size: Float = 14f, color: Int = Palette.FG, mono: Boolean = false) = TextView(this).apply {
    this.text = text; textSize = size; setTextColor(color)
    if (mono) typeface = Typeface.MONOSPACE
}

fun Context.primaryButton(text: String, onClick: () -> Unit) = Button(this).apply {
    this.text = text; isAllCaps = false; setTextColor(Palette.BG); typeface = Typeface.MONOSPACE
    background = GradientDrawable().apply { setColor(Palette.ACCENT); cornerRadius = dp(8).toFloat() }
    setOnClickListener { onClick() }
}

fun Context.ghostButton(text: String, onClick: () -> Unit) = Button(this).apply {
    this.text = text; isAllCaps = false; setTextColor(Palette.FG); typeface = Typeface.MONOSPACE
    background = GradientDrawable().apply { setColor(Palette.CARD); cornerRadius = dp(8).toFloat() }
    setOnClickListener { onClick() }
}

fun Context.field(hint: String, password: Boolean = false) = EditText(this).apply {
    this.hint = hint; setHintTextColor(Palette.MUTED); setTextColor(Palette.FG); typeface = Typeface.MONOSPACE
    setSingleLine(); setPadding(dp(14), dp(12), dp(14), dp(12))
    background = GradientDrawable().apply { setColor(Palette.CARD); cornerRadius = dp(8).toFloat() }
    inputType = if (password) 129 else 17 // textPassword / textUri
}

fun View.applySystemInsets() = ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
    val i = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
    v.setPadding(i.left, i.top, i.right, i.bottom); WindowInsetsCompat.CONSUMED
}
