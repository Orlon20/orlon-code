package com.mobilecode.app

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class PreviewTest {
    private val root = File.createTempFile("pvw", "").let { it.delete(); it.mkdirs(); it.canonicalFile }

    @Test fun emptyInputOpensProjectRoot() = assertEquals("${Preview.ASSET_ORIGIN}/p/", previewUrl(""))
    @Test fun portAndLocalhostGoToTheLocalServer() {
        assertEquals("http://localhost:5173", previewUrl("localhost:5173"))
        assertEquals("http://127.0.0.1:8080/x", previewUrl("127.0.0.1:8080/x"))
        assertEquals("http://localhost:3000", previewUrl(":3000"))
        assertEquals("http://localhost:3000", previewUrl("3000"))
    }
    @Test fun httpUrlsPassThrough() = assertEquals("http://localhost:1/a", previewUrl("http://localhost:1/a"))
    @Test fun plainPathsAreProjectFiles() {
        assertEquals("${Preview.ASSET_ORIGIN}/p/index.html", previewUrl("index.html"))
        assertEquals("${Preview.ASSET_ORIGIN}/p/site/about.html", previewUrl("/site/about.html"))
    }
    @Test fun traversalOutsideProjectIsRefused() {
        assertNull(resolveInside(root, "../../etc/passwd"))
        assertNull(resolveInside(root, "a/../../x"))
    }
    @Test fun pathsInsideProjectResolve() {
        assertEquals(root, resolveInside(root, ""))
        assertEquals(File(root, "a/b.txt").canonicalFile, resolveInside(root, "a/b.txt"))
    }
    @Test fun siblingDirectoryWithSharedPrefixIsRefused() {
        val sib = File(root.path + "-evil").apply { mkdirs() }
        assertNull(resolveInside(root, "../" + sib.name))
    }
    @Test fun mimeTypesForWebFiles() {
        assertEquals("text/html", mimeFor("a.html")); assertEquals("text/javascript", mimeFor("a.js"))
        assertEquals("text/javascript", mimeFor("a.mjs")); assertEquals("text/css", mimeFor("a.css"))
        assertEquals("image/svg+xml", mimeFor("a.svg")); assertEquals("application/wasm", mimeFor("a.wasm"))
        assertEquals("application/octet-stream", mimeFor("a.zzz"))
    }
}
