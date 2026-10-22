package com.mobilecode.app

import java.io.File

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
