package com.mobilecode.app

import org.junit.Assert.*
import org.junit.Test

class ThemingTest {
    @Test fun hexColors() {
        assertEquals(0xFF080808.toInt(), cssColor("#080808"))
        assertEquals(0xFFAABBCC.toInt(), cssColor("#abc"))
        assertEquals(0xFF080808.toInt(), cssColor("#080808ff"))
    }
    @Test fun hex8IsRrggbbaaNotAarrggbb() = assertEquals(0x0FFFFFFF, cssColor("#ffffff0f"))
    @Test fun rgbAndRgba() {
        assertEquals(0xFF102030.toInt(), cssColor("rgb(16, 32, 48)"))
        assertEquals(0x80FF0000.toInt(), cssColor("rgba(255, 0, 0, 0.5)"))
        assertEquals(0xFF102030.toInt(), cssColor("rgb(16 32 48)"))
    }
    @Test fun colorSrgbFunction() = assertEquals(0xFF808080.toInt(), cssColor("color(srgb 0.5 0.5 0.5)"))
    @Test fun garbageIsNull() { assertNull(cssColor("")); assertNull(cssColor("var(--x)")); assertNull(cssColor("#12")) }
    @Test fun luminanceSeparatesLightFromDark() {
        assertTrue(isLightColor(0xFFF7F7F7.toInt())); assertFalse(isLightColor(0xFF080808.toInt()))
    }

    @Test fun paletteAppliesAndKeepsDefaultsForMissingKeys() {
        Palette.reset()
        Palette.apply("""{"bg":"#ffffff","fg":"#111111","card":""}""")
        assertEquals(0xFFFFFFFF.toInt(), Palette.BG); assertEquals(0xFF111111.toInt(), Palette.FG)
        assertEquals(Palette.DEFAULT_CARD, Palette.CARD)
        Palette.reset(); assertEquals(0xFF080808.toInt(), Palette.BG)
    }

    @Test fun parseThemesReadsSwatches() {
        val j = """[{"id":"dracula","name":"Dracula","light":{"neutral":"#f8f8f2","ink":"#1f1f2f","primary":"#7c6bf5","accent":"#d16090"},"dark":{"neutral":"#1d1e28","ink":"#f8f8f2","primary":"#bd93f9","accent":"#ff79c6"}}]"""
        val t = parseThemes(j).single()
        assertEquals("dracula", t.id); assertEquals("Dracula", t.name)
        assertEquals(0xFF1D1E28.toInt(), t.dark.bg); assertEquals(0xFFBD93F9.toInt(), t.dark.primary)
        assertEquals(0xFFF8F8F2.toInt(), t.light.bg)
    }
    @Test fun parseThemesSkipsBrokenEntriesAndSortsDefaultFirst() {
        val j = """[{"id":"zen","name":"Zenburn","light":{},"dark":{}},{"id":"oc-2","name":"OC-2","light":{"neutral":"#fff","ink":"#000"},"dark":{"neutral":"#000","ink":"#fff"}},{"nope":1}]"""
        val ids = parseThemes(j).map { it.id }
        assertEquals("oc-2", ids.first()); assertFalse("nope" in ids)
    }
    @Test fun parseThemesOfGarbageIsEmpty() { assertTrue(parseThemes("not json").isEmpty()); assertTrue(parseThemes("").isEmpty()) }
}
