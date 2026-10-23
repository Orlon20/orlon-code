package com.mobilecode.app

import java.io.File

/** The newest signed APK (in some project's .orlon/out folder) modified after [afterMs], or null. Cheap enough to poll. */
fun newestNewApk(projectsRoot: File, afterMs: Long): File? {
    val best = ArrayList<File>()
    fun scan(dir: File, depth: Int) {
        val out = File(dir, ".orlon/out")
        out.listFiles { f -> f.isFile && f.name.endsWith(".apk") && !f.name.contains("unsigned") && f.lastModified() > afterMs }?.let { best += it }
        if (depth < 2) dir.listFiles { f -> f.isDirectory && !f.name.startsWith(".") && f.name != "build" && f.name != "node_modules" }?.forEach { scan(it, depth + 1) }
    }
    if (!projectsRoot.isDirectory) return null
    projectsRoot.listFiles { f -> f.isDirectory && !f.name.startsWith(".") }?.forEach { scan(it, 1) }
    return best.maxByOrNull { it.lastModified() }
}

/** "hello-android · debug" for /…/hello-android/.orlon/out/app-debug.apk */
fun apkTitle(apk: File): String {
    val project = apk.parentFile?.parentFile?.parentFile?.name ?: apk.name
    val kind = when { apk.name.contains("release") -> "release"; apk.name.contains("debug") -> "debug"; else -> "other" }
    return "$project · $kind"
}

/**
 * The project name of a build that is running right now (its `.orlon/job.pid` exists, no `job.done`, and the process is alive), or null.
 * [alive] decides whether a pid is still running; the default looks in /proc.
 */
fun findRunningBuild(projectsRoot: File, alive: (Int) -> Boolean = { File("/proc/$it").exists() }): String? {
    fun check(dir: File): String? {
        val pidFile = File(dir, ".orlon/job.pid")
        if (!pidFile.isFile || File(dir, ".orlon/job.done").exists()) return null
        val pid = pidFile.readText().trim().toIntOrNull() ?: return null
        return if (alive(pid)) dir.name else null
    }
    if (!projectsRoot.isDirectory) return null
    val top = projectsRoot.listFiles { f -> f.isDirectory && !f.name.startsWith(".") } ?: return null
    for (d in top) {
        check(d)?.let { return it }
        d.listFiles { f -> f.isDirectory && !f.name.startsWith(".") && f.name != "build" && f.name != "node_modules" }?.forEach { c -> check(c)?.let { return it } }
    }
    return null
}
