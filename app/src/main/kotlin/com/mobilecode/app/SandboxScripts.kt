package com.mobilecode.app

import android.content.Context
import java.io.File

/** Puts the orlon-* helper scripts (and the agent's instructions) into the sandbox. Idempotent and cheap. */
object SandboxScripts {
    private val EXECUTABLES = listOf("orlon-env", "orlon-toolchain", "orlon-build", "orlon-keystore", "orlon-project", "orlon-new-android")
    private const val AGENTS_MARKER = "<!-- managed by Orlon Code -->"

    fun install(ctx: Context) {
        val p = Sandbox.paths(ctx)
        val bin = File(p.rootfs, "usr/local/bin").apply { mkdirs() }
        for (name in EXECUTABLES + "orlon.init.gradle") writeIfChanged(ctx, "sandbox/$name", File(bin, name), name in EXECUTABLES)
        copyTree(ctx, "sandbox/android-template", File(p.rootfs, "usr/local/share/orlon/android-template"))
        // Global agent rules; leave the file alone if the user took it over (marker removed).
        val agents = File(p.rootfs, "root/.config/opencode/AGENTS.md").apply { parentFile?.mkdirs() }
        val text = AGENTS_MARKER + "\n" + ctx.assets.open("sandbox/AGENTS.md").bufferedReader().readText()
        if (!agents.exists() || agents.readText().startsWith(AGENTS_MARKER)) { if (!agents.exists() || agents.readText() != text) agents.writeText(text) }
    }

    /** Copies an assets directory recursively (only files that differ are rewritten). */
    private fun copyTree(ctx: Context, asset: String, dest: File) {
        val children = ctx.assets.list(asset) ?: return
        if (children.isEmpty()) { writeIfChanged(ctx, asset, dest, asset.endsWith("gradlew")); return }
        dest.mkdirs()
        for (c in children) copyTree(ctx, "$asset/$c", File(dest, c))
    }

    private fun writeIfChanged(ctx: Context, asset: String, dest: File, executable: Boolean) {
        val bytes = ctx.assets.open(asset).use { it.readBytes() }
        if (!dest.exists() || !dest.readBytes().contentEquals(bytes)) dest.writeBytes(bytes)
        if (executable) dest.setExecutable(true, false)
    }
}
