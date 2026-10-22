package com.mobilecode.app

import java.io.File

/** A Gradle project found under the sandbox's projects folder. */
data class AndroidProject(val name: String, val sandboxPath: String, val hasWrapper: Boolean, val isAndroid: Boolean)

private val SKIP_DIRS = setOf("build", "node_modules", "out", "gradle")

private fun isGradleRoot(d: File) = File(d, "gradlew").isFile || File(d, "settings.gradle").isFile || File(d, "settings.gradle.kts").isFile

private fun looksAndroid(d: File): Boolean {
    val candidates = listOf("build.gradle", "build.gradle.kts", "app/build.gradle", "app/build.gradle.kts")
    return candidates.any { f -> File(d, f).takeIf { it.isFile && it.length() < 200_000 }?.readText()?.contains("com.android.") == true }
}

/** Finds Gradle projects under [root] (up to two folders deep). [sandboxRoot] is how the sandbox sees [root]. */
fun detectProjects(root: File, sandboxRoot: String): List<AndroidProject> {
    val out = ArrayList<AndroidProject>()
    fun visit(dir: File, rel: String, depth: Int) {
        val children = dir.listFiles { f -> f.isDirectory && !f.name.startsWith(".") && f.name !in SKIP_DIRS }?.sortedBy { it.name } ?: return
        for (c in children) {
            val r = if (rel.isEmpty()) c.name else "$rel/${c.name}"
            if (isGradleRoot(c)) out += AndroidProject(r, "${sandboxRoot.trimEnd('/')}/$r", File(c, "gradlew").isFile, looksAndroid(c))
            else if (depth < 2) visit(c, r, depth + 1)
        }
    }
    visit(root, "", 1)
    return out.sortedBy { it.name }
}

/** Turns arbitrary text into a safe single folder name, or null if nothing usable is left. */
fun safeProjectName(raw: String): String? {
    val s = raw.trim().replace(Regex("[^A-Za-z0-9._-]+"), "-").trim('-', '.').take(64).trim('-', '.')
    return s.ifEmpty { null }
}

/** Only plain https:// repository URLs: no credentials, no shell characters, no git transport tricks. */
fun isCloneUrlAllowed(url: String): Boolean {
    if (!url.startsWith("https://") || url.length > 300) return false
    val rest = url.removePrefix("https://")
    if (rest.contains('@') || !Regex("^[A-Za-z0-9.-]+(:\\d+)?(/[A-Za-z0-9._~%+/-]*)?$").matches(rest)) return false
    return rest.substringBefore('/').contains('.') && rest.contains('/') && rest.substringAfter('/').isNotBlank()
}

fun repoNameFromUrl(url: String): String? {
    if (!isCloneUrlAllowed(url)) return null
    val last = url.removePrefix("https://").substringAfter('/').trimEnd('/').substringAfterLast('/').removeSuffix(".git")
    return safeProjectName(last)
}

/**
 * Resolves an artifact path printed by `orlon-build` to a real file, but only if it is an .apk/.aab sitting in
 * some project's `.orlon/out` folder. Build scripts can print anything, so nothing else is ever offered or shared.
 */
fun artifactFile(projectsHostRoot: File, sandboxRoot: String, sandboxPath: String): File? {
    val prefix = sandboxRoot.trimEnd('/') + "/"
    if (!sandboxPath.startsWith(prefix) || !(sandboxPath.endsWith(".apk") || sandboxPath.endsWith(".aab"))) return null
    val f = resolveInside(projectsHostRoot, sandboxPath.removePrefix(prefix)) ?: return null
    val outDir = f.parentFile ?: return null
    if (outDir.name != "out" || outDir.parentFile?.name != ".orlon" || !f.isFile) return null
    return f
}
