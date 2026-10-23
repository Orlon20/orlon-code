package com.mobilecode.app

import android.content.Context
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Backup and restore of everything that cannot be recreated: the projects and the on-phone signing key.
 * One zip with two top-level folders; build output and dependency caches are left out.
 */
object Backup {
    /** Top-level zip folder -> location inside the sandbox root filesystem. */
    val ROOTS = mapOf("projects" to "root/projects", "orlon" to "root/.orlon")
    private val SKIP_DIRS = setOf("build", ".gradle", "node_modules", "out")

    fun shouldSkip(relPath: String): Boolean {
        val parts = relPath.split('/')
        return parts.dropLast(1).any { it in SKIP_DIRS } || relPath.endsWith(".apk")
    }

    fun roots(ctx: Context): Map<String, File> = ROOTS.mapValues { File(Sandbox.paths(ctx).rootfs, it.value) }

    /** Writes the zip; returns the number of files stored. Symlinks are skipped. */
    fun export(roots: Map<String, File>, out: OutputStream): Int {
        var count = 0
        ZipOutputStream(out.buffered()).use { zip ->
            for ((name, dir) in roots) {
                if (!dir.isDirectory) continue
                dir.walkTopDown().onEnter { d -> d == dir || (d.name !in SKIP_DIRS && !java.nio.file.Files.isSymbolicLink(d.toPath())) }.forEach { f ->
                    if (!f.isFile || java.nio.file.Files.isSymbolicLink(f.toPath())) return@forEach
                    val rel = f.relativeTo(dir).path.replace(File.separatorChar, '/')
                    if (shouldSkip(rel)) return@forEach
                    zip.putNextEntry(ZipEntry("$name/$rel").apply { time = f.lastModified() })
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry(); count++
                }
            }
        }
        return count
    }

    /** Restores a zip made by [export]. Entries outside the known folders or escaping them are ignored. Returns the number of files written. */
    fun restore(roots: Map<String, File>, input: InputStream): Int {
        var count = 0
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                if (e.isDirectory) continue
                val top = e.name.substringBefore('/')
                val base = roots[top] ?: continue
                val target = File(base, e.name.substringAfter('/'))
                if (!target.canonicalPath.startsWith(base.canonicalPath + File.separator)) continue
                target.parentFile?.mkdirs()
                target.outputStream().use { zip.copyTo(it) }
                if (e.time > 0) target.setLastModified(e.time)
                count++
            }
        }
        return count
    }
}
