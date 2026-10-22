package com.mobilecode.app

import org.junit.Assert.*
import org.junit.Test

class BuildLogTest {
    private fun log(name: String) = javaClass.getResourceAsStream("/buildlogs/$name.log")!!.bufferedReader().readLines()
    private fun report(name: String) = parseBuildLog(log(name))

    @Test fun kotlinErrorsHaveFileLineColumnAndMessage() {
        val r = report("fx-kotlin")
        assertEquals(false, r.success)
        val errs = r.issues.filter { it.severity == Severity.ERROR }
        assertEquals(2, errs.size)
        assertEquals("/root/projects/hello-android/app/src/main/kotlin/dev/orlon/hello/MainActivity.kt", errs[0].file)
        assertEquals(12, errs[0].line); assertEquals(90, errs[0].column)
        assertEquals("Initializer type mismatch: expected 'Int', actual 'String'.", errs[0].message)
        assertEquals("Unresolved reference 'undefinedThing'.", errs[1].message); assertEquals(98, errs[1].column)
    }

    @Test fun javaErrorsAreDedupedAndKeepTheSymbolDetail() {
        val errs = report("fx-java").issues.filter { it.severity == Severity.ERROR }
        assertEquals(2, errs.size)   // Gradle prints each javac error twice (live + summary)
        assertEquals("/root/projects/hello-android/app/src/main/java/dev/orlon/hello/Bad.java", errs[0].file)
        assertEquals(3, errs[0].line); assertNull(errs[0].column)
        assertEquals("incompatible types: String cannot be converted to int", errs[0].message)
        assertEquals("cannot find symbol (method undefinedCall())", errs[1].message)
    }

    @Test fun resourceErrorsMapTheVirtualPathToAResFile() {
        val errs = report("fx-resource").issues.filter { it.severity == Severity.ERROR }
        assertEquals(1, errs.size)
        assertEquals("res/layout/broken.xml", errs[0].file); assertEquals(5, errs[0].line)
        assertTrue(errs[0].message.startsWith("resource color/does_not_exist"))
    }

    @Test fun gradleFailureSummaryUsesTheWhatWentWrongBlock() {
        val f = report("fx-kotlin").failure!!
        assertTrue(f, f.startsWith("Execution failed for task ':app:compileDebugKotlin'."))
        assertTrue(f, f.contains("Compilation error. See log for more details"))
    }

    @Test fun noiseLinesAreNotIssues() {
        for (n in listOf("fx-kotlin", "fx-java", "fx-resource"))
            assertTrue(n, report(n).issues.none { it.message.contains("tzdata") || it.message.contains("failed linking") || it.message.contains("--stacktrace") })
    }

    @Test fun successReportListsApksAndBundles() {
        val r = report("success")
        assertEquals(true, r.success); assertTrue(r.issues.isEmpty()); assertNull(r.failure)
        assertEquals(listOf("/root/projects/hello-android/.orlon/out/app-debug.apk", "/root/projects/hello-android/.orlon/out/app-release.apk"), r.apks.map { it.path })
        assertEquals(listOf(true, false), r.apks.map { it.signed })
        assertEquals(listOf("/root/projects/hello-android/.orlon/out/app-release.aab"), r.bundles)
    }

    @Test fun manifestErrorTakesTheMessageFromTheNextLine() {
        val e = report("manifest").issues.single { it.severity == Severity.ERROR }
        assertEquals("/root/projects/x/app/src/main/AndroidManifest.xml", e.file); assertEquals(12, e.line)
        assertTrue(e.message, e.message.startsWith("android:exported needs to be explicitly specified"))
    }

    @Test fun inProgressLogHasNoResultYet() = assertNull(parseBuildLog(listOf("> Task :app:compileDebugKotlin")).success)
    @Test fun emptyLogIsHarmless() { val r = parseBuildLog(emptyList()); assertNull(r.success); assertTrue(r.issues.isEmpty()) }

    @Test fun hintsExplainCommonFailures() {
        assertTrue(buildHint(log("network"))!!.contains("internet", ignoreCase = true))
        assertTrue(buildHint(log("oom"))!!.contains("memory", ignoreCase = true))
        assertTrue(buildHint(log("license"))!!.contains("licen", ignoreCase = true))
        assertNull(buildHint(log("fx-kotlin")))
    }

    @Test fun displayLocationIsRelativeToTheProject() {
        val i = BuildIssue(Severity.ERROR, "/root/projects/hello-android/app/src/Main.kt", 12, 3, "x")
        assertEquals("app/src/Main.kt:12:3", i.location("/root/projects/hello-android"))
        assertEquals("res/layout/a.xml:5", BuildIssue(Severity.ERROR, "res/layout/a.xml", 5, null, "x").location("/root/projects/hello-android"))
        assertEquals("", BuildIssue(Severity.ERROR, null, null, null, "x").location("/p"))
    }

    @Test fun agentPromptListsErrorsAndTheBuildCommand() {
        val p = fixPrompt("/root/projects/hello-android", "assembleDebug", report("fx-kotlin"))
        assertTrue(p.contains("orlon-build -C /root/projects/hello-android assembleDebug"))
        assertTrue(p.contains("app/src/main/kotlin/dev/orlon/hello/MainActivity.kt:12:90"))
        assertTrue(p.contains("Unresolved reference 'undefinedThing'"))
    }
}
