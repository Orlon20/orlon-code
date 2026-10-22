package com.mobilecode.app

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class SandboxTest {
    private val p = Sandbox.Paths("/native", "/files")

    @Test fun commandStartsWithProotAndRootfs() {
        val c = Sandbox.prootCommand(p, "/root/projects", mapOf("A" to "1"), listOf("/bin/sh", "-c", "echo hi"))
        assertEquals("/native/libproot.so", c.first())
        assertEquals("/files/rootfs", c[c.indexOf("-r") + 1])
        assertEquals("/root/projects", c[c.indexOf("-w") + 1])
    }

    @Test fun envComesAfterEnvDashIAndArgsLast() {
        val c = Sandbox.prootCommand(p, "/root", mapOf("A" to "1", "B" to "x y"), listOf("/bin/sh", "-c", "echo hi"))
        val i = c.indexOf("-i")
        assertEquals("/usr/bin/env", c[i - 1])
        assertEquals(listOf("A=1", "B=x y"), c.subList(i + 1, i + 3))
        assertEquals(listOf("/bin/sh", "-c", "echo hi"), c.takeLast(3))
    }

    @Test fun killOnExitAndLink2SymlinkSet() {
        val c = Sandbox.prootCommand(p, "/root", emptyMap(), listOf("true"))
        assertTrue("--kill-on-exit" in c); assertTrue("--link2symlink" in c)
    }

    @Test fun processEnvPointsAtLoaderAndLibs() {
        val e = Sandbox.processEnv(p)
        assertEquals("/native/libproot-loader.so", e["PROOT_LOADER"])
        assertEquals("/files/lib:/native", e["LD_LIBRARY_PATH"])
    }

    @Test fun markerIsVersioned() = assertEquals("/files/installed-${Sandbox.OPENCODE_VERSION}", p.marker.path)

    @Test fun sha256HexMatchesKnownValue() {
        val f = File.createTempFile("sbx", ".txt").apply { writeText("abc"); deleteOnExit() }
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Sandbox.sha256Hex(f))
    }

    @Test fun pinnedChecksumsLookLikeSha256() {
        for (h in listOf(Sandbox.ALPINE_SHA256, Sandbox.OPENCODE_SHA256)) assertTrue(Regex("[0-9a-f]{64}").matches(h))
    }
}
