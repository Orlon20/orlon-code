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
