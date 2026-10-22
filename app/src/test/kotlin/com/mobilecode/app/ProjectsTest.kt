package com.mobilecode.app

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProjectsTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun mk(vararg paths: String): File { val root = tmp.newFolder(); paths.forEach { p -> File(root, p).apply { parentFile.mkdirs(); writeText("x") } }; return root }

    @Test fun findsGradleProjectsByWrapperOrSettings() {
        val root = mk("a/gradlew", "b/settings.gradle.kts", "c/readme.md", ".hidden/gradlew")
        assertEquals(listOf("a", "b"), detectProjects(root, "/root/projects").map { it.name })
    }

    @Test fun reportsSandboxPathsAndWrapperFlag() {
        val p = detectProjects(mk("a/gradlew", "a/settings.gradle"), "/root/projects").single()
        assertEquals("/root/projects/a", p.sandboxPath); assertTrue(p.hasWrapper)
        assertFalse(detectProjects(mk("b/settings.gradle"), "/root/projects").single().hasWrapper)
    }

    @Test fun marksAndroidProjectsByTheirBuildFiles() {
        val root = tmp.newFolder("p2")
        File(root, "droid/app").mkdirs(); File(root, "droid/settings.gradle").writeText("")
        File(root, "droid/app/build.gradle.kts").writeText("plugins { id(\"com.android.application\") }")
        File(root, "lib").mkdirs(); File(root, "lib/settings.gradle").writeText(""); File(root, "lib/build.gradle").writeText("apply plugin: 'java'")
        val byName = detectProjects(root, "/root/projects").associateBy { it.name }
        assertTrue(byName.getValue("droid").isAndroid); assertFalse(byName.getValue("lib").isAndroid)
    }

    @Test fun looksOneLevelDeeperButSkipsBuildOutput() {
        val root = mk("group/inner/gradlew", "x/build/gradlew", "x/node_modules/gradlew")
        assertEquals(listOf("group/inner"), detectProjects(root, "/root/projects").map { it.name })
    }

    @Test fun projectNamesAreSanitised() {
        assertEquals("my-app", safeProjectName("my app"))
        assertEquals("a-b", safeProjectName("a/b")); assertEquals("x.y_z-1", safeProjectName("x.y_z-1"))
        assertNull(safeProjectName("")); assertNull(safeProjectName("..")); assertNull(safeProjectName("///"))
        assertEquals("hidden", safeProjectName(".hidden"))
        assertTrue(safeProjectName("a".repeat(200))!!.length <= 64)
    }

    @Test fun cloneUrlsMustBePlainHttps() {
        assertTrue(isCloneUrlAllowed("https://github.com/lzhiyong/android-sdk-tools"))
        assertTrue(isCloneUrlAllowed("https://gitlab.com/a/b.git"))
        for (bad in listOf("http://github.com/a/b", "git@github.com:a/b.git", "ssh://git@x/y", "file:///etc", "ext::sh -c id", "--upload-pack=x",
            "https://x.com/a b", "https://x.com/a;rm", "https://x.com/a'b", "https://user:tok@github.com/a/b", "https://", "https:///a", "", "https://x.com/\$(id)"))
            assertFalse(bad, isCloneUrlAllowed(bad))
    }

    @Test fun repoNameComesFromTheLastPathSegment() {
        assertEquals("b", repoNameFromUrl("https://github.com/a/b.git"))
        assertEquals("b", repoNameFromUrl("https://github.com/a/b/"))
        assertEquals("android-sdk-tools", repoNameFromUrl("https://github.com/lzhiyong/android-sdk-tools"))
        assertNull(repoNameFromUrl("https://github.com"))
    }

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
