package com.mobilecode.app

import org.junit.Assert.*
import org.junit.Test

class WordmarkTest {
    @Test fun spellsOrlonCode() = assertEquals("orloncode", Wordmark.TEXT)
    @Test fun widthIsGlyphsPlusOneCellGaps() = assertEquals(41, Wordmark.widthCells)
    @Test fun heightIsSixRows() = assertEquals(6, Wordmark.HEIGHT_CELLS)
    @Test fun everyGlyphRowHasTheGlyphsWidth() {
        for (c in Wordmark.TEXT.toSet()) {
            val g = Wordmark.glyph(c)
            assertEquals("rows for $c", 6, g.size)
            assertTrue("widths for $c", g.all { it.length == g[0].length })
        }
    }
    @Test fun oIsAHollowBlock() {
        val g = Wordmark.glyph('o')
        assertEquals("####", g[1]); assertEquals("#..#", g[2]); assertEquals("####", g[5])
    }
    @Test fun dHasAnAscender() { val g = Wordmark.glyph('d'); assertEquals("...#", g[0]); assertEquals("####", g[1]) }
    @Test fun lIsASingleTallStem() = assertTrue(Wordmark.glyph('l').all { it == "#" })
    @Test fun runsStayInsideTheGrid() {
        for (r in Wordmark.runs()) { assertTrue(r.x >= 0 && r.x + r.w <= Wordmark.widthCells); assertTrue(r.y in 0 until Wordmark.HEIGHT_CELLS); assertTrue(r.w > 0) }
    }
    @Test fun adjacentCellsAreMergedIntoOneRun() {
        val firstRow = Wordmark.runs().filter { it.y == 1 && it.x < 4 }
        assertEquals(1, firstRow.size); assertEquals(4, firstRow[0].w) // the top of the first 'o'
    }
    @Test fun noRunsOverlapAndTextHasNoSpaceGlyphs() {
        val seen = HashSet<Pair<Int, Int>>()
        for (r in Wordmark.runs()) for (x in r.x until r.x + r.w) assertTrue("dup at $x,${r.y}", seen.add(x to r.y))
    }
    @Test fun pathDataIsClosedRectsInCellUnits() {
        val d = Wordmark.pathData(10f)
        assertTrue(d.startsWith("M")); assertEquals(Wordmark.runs().size, d.count { it == 'Z' })
        assertTrue(d.contains("M0 10h40"))   // top of the first 'o': x=0, y=1 row, width 4 cells
    }
    @Test fun viewBoxMatchesGridAtCellSize() = assertEquals("0 0 410 60", Wordmark.viewBox(10f))
}
