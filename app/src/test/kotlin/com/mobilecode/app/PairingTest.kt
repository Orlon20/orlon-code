package com.mobilecode.app

import java.net.URLEncoder
import org.junit.Assert.*
import org.junit.Test

class PairingTest {
    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
    private fun ok(r: PairResult) = (r as PairResult.Ok).server
    private fun invalid(r: PairResult) = assertTrue(r.toString(), r is PairResult.Invalid)

    @Test fun deepLinkRoundTripSpecialCharPassword() {
        val pw = "p&s%w+d é"
        val s = ok(parsePairing("mobilecode://connect?url=${enc("https://a.trycloudflare.com")}&password=${enc(pw)}"))
        assertEquals(pw, s.password)
        assertEquals("https://a.trycloudflare.com", s.url)
    }
    @Test fun httpPublicRejected() = invalid(normalizeUrl("http://example.com"))
    @Test fun httpPrivateAllowed() {
        assertEquals("http://192.168.1.5:4096", ok(normalizeUrl("http://192.168.1.5:4096")).url)
        assertEquals("http://100.101.1.1:4096", ok(normalizeUrl("http://100.101.1.1:4096")).url)
        assertEquals("http://localhost:4096", ok(normalizeUrl("http://localhost:4096")).url)
    }
    @Test fun noSchemeDefaultsHttpsPublicHttpPrivate() {
        assertEquals("https://example.com", ok(normalizeUrl("example.com")).url)
        assertEquals("http://192.168.1.5:4096", ok(normalizeUrl("192.168.1.5:4096")).url)
    }
    @Test fun trailingSlashStripped() = assertEquals("https://example.com", ok(normalizeUrl("https://example.com/")).url)
    @Test fun rejectsJavascriptFileAndForeignScheme() {
        invalid(parsePairing("javascript:alert(1)"))
        invalid(parsePairing("file:///etc/passwd"))
        invalid(parsePairing("https://evil.com/?url=x&password=y"))
        invalid(normalizeUrl("javascript:alert(1)"))
        invalid(normalizeUrl("file:///etc/passwd"))
    }
    @Test fun missingPasswordInvalid() =
        invalid(parsePairing("mobilecode://connect?url=${enc("https://a.com")}"))
    @Test fun privateHostRanges() {
        assertTrue(isPrivateHost("10.0.0.1")); assertTrue(isPrivateHost("172.16.0.1"))
        assertFalse(isPrivateHost("172.32.0.1")); assertFalse(isPrivateHost("100.128.0.1"))
        assertTrue(isPrivateHost("100.64.0.1")); assertTrue(isPrivateHost("pc.local"))
        assertFalse(isPrivateHost("8.8.8.8"))
    }
}
