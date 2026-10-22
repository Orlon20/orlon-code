package com.mobilecode.app

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProjectsTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun onlyRealApksInsideProjectOutputFoldersAreOffered() {
        val root = tmp.newFolder("rootfs-projects")
        File(root, "app/.orlon/out").mkdirs(); File(root, "app/.orlon/out/app-debug.apk").writeText("apk")
        File(root, "app/.orlon/out/app-release.aab").writeText("aab")
        File(root, ".orlon").mkdirs()
        val ok = artifactFile(root, "/root/projects", "/root/projects/app/.orlon/out/app-debug.apk")
        assertEquals(File(root, "app/.orlon/out/app-debug.apk").canonicalFile, ok)
        assertNotNull(artifactFile(root, "/root/projects", "/root/projects/app/.orlon/out/app-release.aab"))
        // a project's build script could print anything; these must all be refused
        assertNull(artifactFile(root, "/root/projects", "/root/.orlon/release.jks"))
        assertNull(artifactFile(root, "/root/projects", "/root/projects/app/.orlon/out/../../../../.orlon/release.jks"))
        assertNull(artifactFile(root, "/root/projects", "/root/projects/app/src/Main.kt"))
        assertNull(artifactFile(root, "/root/projects", "/root/projects/app/.orlon/out/missing.apk"))
        assertNull(artifactFile(root, "/root/projects", "/etc/passwd"))
    }
}
