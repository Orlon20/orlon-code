package com.mobilecode.app

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ApkWatcherTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun apk(root: File, rel: String, modified: Long): File = File(root, rel).apply { parentFile.mkdirs(); writeText("apk"); setLastModified(modified) }

    @Test fun returnsTheNewestApkNewerThanTheMarker() {
        val root = tmp.newFolder()
        apk(root, "a/.orlon/out/old.apk", 1_000); val newer = apk(root, "a/.orlon/out/app-debug.apk", 5_000); apk(root, "b/.orlon/out/b.apk", 3_000)
        assertEquals(newer.canonicalFile, newestNewApk(root, 2_000)?.canonicalFile)
    }

    @Test fun nothingNewMeansNull() {
        val root = tmp.newFolder(); apk(root, "a/.orlon/out/a.apk", 1_000)
        assertNull(newestNewApk(root, 1_000)); assertNull(newestNewApk(root, 9_000))
    }

    @Test fun unsignedAndNonApkFilesAreIgnored() {
        val root = tmp.newFolder()
        apk(root, "a/.orlon/out/app-release-unsigned.apk", 9_000); apk(root, "a/.orlon/out/bundle.aab", 9_000); apk(root, "a/.orlon/out/notes.txt", 9_000)
        assertNull(newestNewApk(root, 1_000))
    }

    @Test fun onlyFilesInOrlonOutAreConsidered() {
        val root = tmp.newFolder()
        apk(root, "a/app/build/outputs/apk/debug/app-debug.apk", 9_000); apk(root, "a/.orlon/other/x.apk", 9_000); apk(root, "x.apk", 9_000)
        assertNull(newestNewApk(root, 1_000))
    }

    @Test fun findsProjectsOneFolderDeeperToo() {
        val root = tmp.newFolder(); val f = apk(root, "group/proj/.orlon/out/p.apk", 9_000)
        assertEquals(f.canonicalFile, newestNewApk(root, 1_000)?.canonicalFile)
    }

    @Test fun missingRootIsHarmless() = assertNull(newestNewApk(File(tmp.root, "nope"), 0))

    @Test fun displayNameDropsTheDebugSuffixNoise() {
        assertEquals("hello-android · debug", apkTitle(File("/x/hello-android/.orlon/out/app-debug.apk")))
        assertEquals("tipcalc · release", apkTitle(File("/x/tipcalc/.orlon/out/app-release.apk")))
        assertEquals("p · other", apkTitle(File("/x/p/.orlon/out/other.apk")))
    }
}
