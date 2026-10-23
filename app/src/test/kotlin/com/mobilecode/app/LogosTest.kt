package com.mobilecode.app

import org.junit.Assert.*
import org.junit.Test

class LogosTest {
    @Test fun parsesViewBoxAndInnerMarkup() {
        val (vb, html) = Logos.parse("<svg width=\"24\" viewBox=\"0 0 40 40\" xmlns=\"x\">\n<path d=\"M0 0\"/>\n</svg>")!!
        assertEquals("0 0 40 40", vb); assertEquals("<path d=\"M0 0\"/>", html)
    }

    @Test fun dropsScriptsAndRejectsNonSvg() {
        assertEquals("<g/>", Logos.parse("<svg><script>alert(1)</script><g/></svg>")!!.second)
        assertNull(Logos.parse("not an svg"))
        assertEquals("0 0 24 24", Logos.parse("<svg><g/></svg>")!!.first)
    }
}
