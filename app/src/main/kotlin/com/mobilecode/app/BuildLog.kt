package com.mobilecode.app

enum class Severity { ERROR, WARNING }

/** One compiler / resource / manifest problem found in a Gradle build log. */
data class BuildIssue(val severity: Severity, val file: String?, val line: Int?, val column: Int?, val message: String) {
    /** "app/src/Main.kt:12:3", relative to [projectDir] when the file lives inside it. */
    fun location(projectDir: String): String {
        val f = file ?: return ""
        val rel = f.removePrefix(projectDir.trimEnd('/') + "/")
        return buildString {
            append(rel)
            if (line != null) { append(':').append(line); if (column != null) append(':').append(column) }
        }
    }
}

data class ApkOutput(val path: String, val signed: Boolean)

data class BuildReport(
    /** true/false once the build finished; null while it is still running. */
    val success: Boolean?,
    val issues: List<BuildIssue>,
    /** Gradle's own one-paragraph explanation ("What went wrong"), if any. */
    val failure: String?,
    val apks: List<ApkOutput>,
    val bundles: List<String>,
)

private val KOTLIN = Regex("""^([ew]): file://(/.+?):(\d+):(\d+)\s+(.*)$""")
private val KOTLIN_PLAIN = Regex("""^e: (?!file://)(.+)$""")
private val JAVAC = Regex("""^\s*(/\S.*?\.(?:java|kt)):(\d+): (error|warning): (.*)$""")
private val JAVAC_SYMBOL = Regex("""^\s*symbol:\s+(.*)$""")
private val AAPT_VIRTUAL = Regex("""^\s*[\w.\-]+:/([^:\s]+?):(\d+): error: (.*)$""")
private val AAPT_REAL = Regex("""^\s*(/.+?):(\d+): AAPT: error: (.*)$""")
private val MANIFEST = Regex("""^(/.+?\.xml):(\d+):(\d+)(?:-\d+:\d+)?\s+(Error|Warning):?\s*(.*)$""")
private val APK = Regex("""^ORLON_APK (\S.*) \((signed|unsigned)\)$""")
private val BUNDLE = Regex("""^ORLON_BUNDLE (\S.*)$""")
private val RESULT = Regex("""^ORLON_RESULT (success|failed)$""")

/** Reads a (possibly still growing) `orlon-build` log. Pure; safe to call on every refresh. */
fun parseBuildLog(lines: List<String>): BuildReport {
    val issues = ArrayList<BuildIssue>()
    val seen = HashSet<String>()
    val apks = ArrayList<ApkOutput>(); val bundles = ArrayList<String>()
    var result: Boolean? = null
    var lastNew = -1   // index of the most recently added issue, or -1 when the last one was a duplicate

    fun add(i: BuildIssue) {
        val key = "${i.severity}|${i.file}|${i.line}|${i.message}"
        if (seen.add(key)) { issues += i; lastNew = issues.size - 1 } else lastNew = -1
    }

    var idx = 0
    var failure: String? = null
    while (idx < lines.size) {
        val l = lines[idx]
        val sev = { s: String -> if (s == "error" || s == "e" || s.equals("Error", true)) Severity.ERROR else Severity.WARNING }
        RESULT.find(l)?.let { result = it.groupValues[1] == "success" }
        APK.find(l)?.let { apks += ApkOutput(it.groupValues[1], it.groupValues[2] == "signed") }
        BUNDLE.find(l)?.let { bundles += it.groupValues[1] }
        when {
            KOTLIN.matches(l) -> KOTLIN.find(l)!!.let { m -> add(BuildIssue(sev(m.groupValues[1]), m.groupValues[2], m.groupValues[3].toInt(), m.groupValues[4].toInt(), m.groupValues[5].trim())) }
            KOTLIN_PLAIN.matches(l) -> add(BuildIssue(Severity.ERROR, null, null, null, KOTLIN_PLAIN.find(l)!!.groupValues[1].trim()))
            JAVAC.matches(l) -> JAVAC.find(l)!!.let { m -> add(BuildIssue(sev(m.groupValues[3]), m.groupValues[1], m.groupValues[2].toInt(), null, m.groupValues[4].trim())) }
            AAPT_VIRTUAL.matches(l) -> AAPT_VIRTUAL.find(l)!!.let { m -> add(BuildIssue(Severity.ERROR, "res/" + m.groupValues[1], m.groupValues[2].toInt(), null, m.groupValues[3].trim())) }
            AAPT_REAL.matches(l) -> AAPT_REAL.find(l)!!.let { m -> add(BuildIssue(Severity.ERROR, m.groupValues[1], m.groupValues[2].toInt(), null, m.groupValues[3].trim())) }
            MANIFEST.matches(l) -> MANIFEST.find(l)!!.let { m ->
                var msg = m.groupValues[5].trim()
                if (msg.isEmpty()) { var j = idx + 1; while (j < lines.size && lines[j].isBlank()) j++; if (j < lines.size) msg = lines[j].trim() }
                add(BuildIssue(sev(m.groupValues[4]), m.groupValues[1], m.groupValues[2].toInt(), m.groupValues[3].toInt(), msg))
            }
            JAVAC_SYMBOL.matches(l) && lastNew >= 0 -> {
                val sym = JAVAC_SYMBOL.find(l)!!.groupValues[1].trim(); val cur = issues[lastNew]
                if (!cur.message.contains("(")) issues[lastNew] = cur.copy(message = "${cur.message} ($sym)")
            }
            l.trim() == "* What went wrong:" && failure == null -> {
                val body = ArrayList<String>(); var j = idx + 1
                while (j < lines.size && !lines[j].startsWith("* Try:") && !(lines[j].isBlank() && body.isNotEmpty())) {
                    val t = lines[j].trim().removePrefix(">").trim()
                    if (t.isNotEmpty() && !t.contains("tzdata")) body += t
                    j++
                }
                failure = body.joinToString("\n").ifEmpty { null }
            }
        }
        idx++
    }
    if (result == null) {
        if (lines.any { it.startsWith("BUILD SUCCESSFUL") }) result = true
        else if (lines.any { it.startsWith("BUILD FAILED") }) result = false
    }
    return BuildReport(result, issues, failure, apks, bundles)
}

/** A plain-language suggestion for failures that are about the phone/environment rather than the code. */
fun buildHint(lines: List<String>): String? {
    val t = lines.joinToString("\n")
    return when {
        Regex("OutOfMemoryError|GC overhead limit|Java heap space|Metaspace").containsMatchIn(t) ->
            "The build ran out of memory. Close other apps and build again; very large projects may not fit on this phone."
        Regex("licen[cs]es? .*not been accepted|licen[cs]es have not been accepted").containsMatchIn(t) ->
            "An Android SDK licence has not been accepted. Run \"Set up build tools\" again to accept it."
        Regex("Name or service not known|UnknownHostException|Could not GET|Could not resolve .*|Connection (reset|refused|timed out)|Read timed out").containsMatchIn(t)
            && Regex("Could not (resolve|GET)|UnknownHost|Name or service").containsMatchIn(t) ->
            "No internet, or a dependency server can't be reached. Check your connection and build again (the first build downloads the project's libraries)."
        t.contains("SDK location not found") -> "The Android SDK was not found. Run \"Set up build tools\" again."
        t.contains("Unsupported class file major version") -> "A Java version mismatch. This phone builds with JDK 17; the project or a plugin needs a different one."
        t.contains("org.gradle.wrapper.GradleWrapperMain") -> "The project's Gradle wrapper files are incomplete (gradle/wrapper/gradle-wrapper.jar is missing). Ask the agent to restore them."
        t.contains("No space left on device") -> "The phone is out of storage. Free some space (Menu > Build Android app > Clear build cache) and try again."
        else -> null
    }
}

/** A ready-to-send prompt that hands the build errors to the coding agent. */
fun fixPrompt(projectDir: String, task: String, report: BuildReport): String = buildString {
    appendLine("The Android build failed. Fix the errors below, then rebuild until it succeeds.")
    appendLine("Build command: orlon-build -C $projectDir $task")
    appendLine()
    val errors = report.issues.filter { it.severity == Severity.ERROR }.take(20)
    if (errors.isNotEmpty()) {
        appendLine("Errors:")
        errors.forEachIndexed { i, e ->
            val loc = e.location(projectDir)
            appendLine("${i + 1}. ${if (loc.isNotEmpty()) "$loc — " else ""}${e.message}")
        }
    } else if (report.failure != null) {
        appendLine("Gradle said:"); appendLine(report.failure)
    }
}.trimEnd()
