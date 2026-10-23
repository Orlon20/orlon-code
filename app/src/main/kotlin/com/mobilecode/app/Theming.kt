package com.mobilecode.app

import org.json.JSONArray
import org.json.JSONObject

/** Parses CSS colors (#rgb, #rrggbb, #rrggbbaa, rgb[a](), color(srgb ...)) into Android ARGB ints. Note CSS hex8 is RRGGBBAA. */
fun cssColor(raw: String): Int? {
    val s = raw.trim().lowercase()
    fun argb(a: Int, r: Int, g: Int, b: Int) = (a shl 24) or (r shl 16) or (g shl 8) or b
    if (s.startsWith("#")) {
        val h = s.drop(1)
        if (!Regex("[0-9a-f]+").matches(h)) return null
        return when (h.length) {
            3, 4 -> {
                val d = h.map { it.digitToInt(16) * 17 }
                argb(if (h.length == 4) d[3] else 255, d[0], d[1], d[2])
            }
            6, 8 -> argb(if (h.length == 8) h.substring(6, 8).toInt(16) else 255, h.substring(0, 2).toInt(16), h.substring(2, 4).toInt(16), h.substring(4, 6).toInt(16))
            else -> null
        }
    }
    Regex("^rgba?\\(([^)]+)\\)$").find(s)?.let { m ->
        val p = m.groupValues[1].split(Regex("[\\s,/]+")).filter { it.isNotEmpty() }
        if (p.size < 3) return null
        val c = p.take(3).map { Math.round(it.toDoubleOrNull() ?: return null).toInt().coerceIn(0, 255) }
        val a = p.getOrNull(3)?.let { v -> if (v.endsWith("%")) v.dropLast(1).toDoubleOrNull()?.div(100) else v.toDoubleOrNull() } ?: 1.0
        return argb(Math.round(a.coerceIn(0.0, 1.0) * 255).toInt(), c[0], c[1], c[2])
    }
    Regex("^color\\(srgb\\s+([^)]+)\\)$").find(s)?.let { m ->
        val p = m.groupValues[1].split(Regex("[\\s/]+")).filter { it.isNotEmpty() }
        if (p.size < 3) return null
        val c = p.take(3).map { Math.round((it.toDoubleOrNull() ?: return null) * 255).toInt().coerceIn(0, 255) }
        val a = p.getOrNull(3)?.toDoubleOrNull() ?: 1.0
        return argb(Math.round(a.coerceIn(0.0, 1.0) * 255).toInt(), c[0], c[1], c[2])
    }
    return null
}

fun isLightColor(argb: Int): Boolean {
    val r = (argb shr 16 and 255) / 255.0; val g = (argb shr 8 and 255) / 255.0; val b = (argb and 255) / 255.0
    return 0.2126 * r + 0.7152 * g + 0.0722 * b > 0.55
}

/** Swatch colors for one scheme of a theme. */
data class Swatch(val bg: Int, val text: Int, val primary: Int, val accent: Int)
data class ThemeInfo(val id: String, val name: String, val light: Swatch, val dark: Swatch)

private fun swatch(o: JSONObject?): Swatch? {
    o ?: return null
    val bg = cssColor(o.optString("neutral")) ?: return null
    val ink = cssColor(o.optString("ink")) ?: return null
    val primary = cssColor(o.optString("primary")) ?: ink
    return Swatch(bg, ink, primary, cssColor(o.optString("accent")) ?: primary)
}

/** Parses the theme list scraped from OpenCode's bundle. Broken entries are skipped; the default theme sorts first. */
fun parseThemes(json: String): List<ThemeInfo> = try {
    val arr = JSONArray(json)
    (0 until arr.length()).mapNotNull { i ->
        val o = arr.optJSONObject(i) ?: return@mapNotNull null
        val id = o.optString("id").ifEmpty { return@mapNotNull null }
        ThemeInfo(id, o.optString("name").ifEmpty { id }, swatch(o.optJSONObject("light")) ?: return@mapNotNull null, swatch(o.optJSONObject("dark")) ?: return@mapNotNull null)
    }.sortedWith(compareBy({ it.id != "oc-2" }, { it.name.lowercase() }))
} catch (e: Exception) { emptyList() }

/** Live native palette. Starts as OpenCode's default dark tokens and follows the selected OpenCode theme. */
object Palette {
    const val DEFAULT_BG = 0xFF000000.toInt(); const val DEFAULT_CARD = 0xFF0D0D0D.toInt(); const val DEFAULT_LAYER = 0xFF1A1A1A.toInt()
    var BG = DEFAULT_BG; var CARD = DEFAULT_CARD; var LAYER = DEFAULT_LAYER
    var BUTTON = 0x0FFFFFFF; var BORDER = 0x1AFFFFFF
    var FG = 0xFFFAFAFA.toInt(); var MUTED = 0xFFAEAEAE.toInt(); var FAINT = 0xFF808080.toInt(); var ACCENT = 0xFFA2BCFF.toInt()
    var INVERSE_BG = 0xFFFFFFFF.toInt(); var INVERSE_FG = 0xFF161616.toInt()

    fun reset() = apply("{}", true)

    /** Applies the JSON captured from the page's --v2-* variables; missing/unparseable keys keep their default. */
    fun apply(json: String, fromDefaults: Boolean = true) {
        val o = try { JSONObject(json) } catch (e: Exception) { JSONObject() }
        fun c(k: String, d: Int) = cssColor(o.optString(k)) ?: d
        BG = c("bg", DEFAULT_BG); CARD = c("card", DEFAULT_CARD); LAYER = c("layer", DEFAULT_LAYER)
        BUTTON = c("button", 0x0FFFFFFF); BORDER = c("border", 0x1AFFFFFF)
        FG = c("fg", 0xFFFAFAFA.toInt()); MUTED = c("muted", 0xFFAEAEAE.toInt()); FAINT = c("faint", 0xFF808080.toInt()); ACCENT = c("accent", 0xFFA2BCFF.toInt())
        INVERSE_BG = c("invBg", 0xFFFFFFFF.toInt()); INVERSE_FG = c("invFg", 0xFF161616.toInt())
    }
}
