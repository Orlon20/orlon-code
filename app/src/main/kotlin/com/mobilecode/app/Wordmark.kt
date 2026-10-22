package com.mobilecode.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View

/**
 * The Orlon Code logo: a pixel wordmark built on the same block grid as OpenCode's own logo
 * (4-wide letters, 1-cell strokes, 'd' with an ascender), so the two read as one family.
 * Single source of truth for the in-app view, the web-page logo swap and the launcher icon.
 */
object Wordmark {
    const val TEXT = "orloncode"
    const val HEIGHT_CELLS = 6
    const val OPENCODE_CELL = 18.4615f   // OpenCode's logo cell size, for SVG parity

    private val GLYPHS = mapOf(
        'o' to listOf("....", "####", "#..#", "#..#", "#..#", "####"),
        'r' to listOf("....", "###.", "#..#", "#...", "#...", "#..."),
        'l' to listOf("#", "#", "#", "#", "#", "#"),
        'n' to listOf("....", "###.", "#..#", "#..#", "#..#", "#..#"),
        'c' to listOf("....", "####", "#...", "#...", "#...", "####"),
        'd' to listOf("...#", "####", "#..#", "#..#", "#..#", "####"),
        'e' to listOf("....", "####", "#..#", "####", "#...", "####"),
    )

    data class Run(val x: Int, val y: Int, val w: Int)

    fun glyph(c: Char): List<String> = GLYPHS.getValue(c)

    val widthCells: Int = TEXT.sumOf { glyph(it)[0].length } + (TEXT.length - 1)

    /** Horizontal runs of filled cells (adjacent cells merged), in grid units. */
    fun runs(): List<Run> {
        val out = ArrayList<Run>()
        var x0 = 0
        for (ch in TEXT) {
            val g = glyph(ch)
            for ((y, row) in g.withIndex()) {
                var x = 0
                while (x < row.length) {
                    if (row[x] == '#') { var e = x; while (e < row.length && row[e] == '#') e++; out += Run(x0 + x, y, e - x); x = e } else x++
                }
            }
            x0 += g[0].length + 1
        }
        return out
    }

    private fun n(v: Float) = if (v == v.toInt().toFloat()) v.toInt().toString() else "%.2f".format(java.util.Locale.US, v).trimEnd('0').trimEnd('.')

    /** SVG path data (one closed rectangle per run) at the given cell size. */
    fun pathData(cell: Float): String = runs().joinToString("") {
        "M${n(it.x * cell)} ${n(it.y * cell)}h${n(it.w * cell)}v${n(cell)}h-${n(it.w * cell)}Z"
    }

    fun viewBox(cell: Float) = "0 0 ${n(widthCells * cell)} ${n(HEIGHT_CELLS * cell)}"
}

/** Draws the wordmark crisply in [color]; sized by height. */
class WordmarkView(ctx: Context, private val heightPx: Int, var color: Int = Palette.FG, private val alphaFactor: Float = 1f) : View(ctx) {
    private val paint = Paint().apply { isAntiAlias = false; style = Paint.Style.FILL }
    private val cell = heightPx / Wordmark.HEIGHT_CELLS.toFloat()
    private val runs = Wordmark.runs()

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension((cell * Wordmark.widthCells).toInt(), heightPx)

    override fun onDraw(c: Canvas) {
        paint.color = color; paint.alpha = (255 * alphaFactor).toInt()
        for (r in runs) c.drawRect(r.x * cell, r.y * cell, (r.x + r.w) * cell, (r.y + 1) * cell, paint)
    }
}
