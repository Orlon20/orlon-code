package com.mobilecode.app

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupTest {
    private fun tmp(): File = kotlin.io.path.createTempDirectory("bk").toFile()

    @Test fun skipsBuildOutputAndApks() {
        assertTrue(Backup.shouldSkip("App/build/outputs/x.txt"))
        assertTrue(Backup.shouldSkip("App/.gradle/a"))
        assertTrue(Backup.shouldSkip("App/.orlon/out/app.apk"))
        assertTrue(Backup.shouldSkip("App/release.apk"))
        assertFalse(Backup.shouldSkip("App/src/main/Main.kt"))
        assertFalse(Backup.shouldSkip("App/.orlon/release.jks"))
    }

    @Test fun roundTripKeepsFilesAndLeavesOutBuilds() {
        val src = tmp(); val key = tmp()
        File(src, "App/src").mkdirs(); File(src, "App/src/Main.kt").writeText("fun main() {}")
        File(src, "App/build").mkdirs(); File(src, "App/build/junk.bin").writeText("x")
        File(key, "release.jks").writeBytes(byteArrayOf(1, 2, 3))
        val zip = ByteArrayOutputStream()
        assertEquals(2, Backup.export(mapOf("projects" to src, "orlon" to key), zip))

        val dst = tmp(); val dkey = tmp()
        assertEquals(2, Backup.restore(mapOf("projects" to dst, "orlon" to dkey), ByteArrayInputStream(zip.toByteArray())))
        assertEquals("fun main() {}", File(dst, "App/src/Main.kt").readText())
        assertTrue(File(dkey, "release.jks").readBytes().contentEquals(byteArrayOf(1, 2, 3)))
        assertFalse(File(dst, "App/build").exists())
    }

    @Test fun restoreIgnoresPathEscapesAndUnknownFolders() {
        val bytes = ByteArrayOutputStream().also { o ->
            ZipOutputStream(o).use { z ->
                for (n in listOf("projects/../evil.txt", "other/x.txt", "projects/ok.txt")) { z.putNextEntry(ZipEntry(n)); z.write(1); z.closeEntry() }
            }
        }.toByteArray()
        val dst = tmp()
        assertEquals(1, Backup.restore(mapOf("projects" to File(dst, "p")), ByteArrayInputStream(bytes)))
        assertTrue(File(dst, "p/ok.txt").exists()); assertFalse(File(dst, "evil.txt").exists())
    }
}
