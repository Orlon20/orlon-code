package com.mobilecode.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import kotlin.concurrent.thread

/**
 * Runs one sandbox command at a time (a build, a toolchain install, a clone) and keeps its output so the Build
 * screen can reattach after the user leaves and comes back.
 */
object BuildRunner {
    interface Listener { fun onLines(newLines: List<String>); fun onState(running: Boolean, exitCode: Int?) }

    private const val MAX_LINES = 20_000
    private val main = Handler(Looper.getMainLooper())
    private val buffer = ArrayList<String>()
    private val listeners = ArrayList<Listener>()
    @Volatile private var process: Process? = null

    @Volatile var running = false; private set
    @Volatile var label = ""; private set
    @Volatile var lastExit: Int? = null; private set
    /** Sandbox path of the project the current/last command belongs to, and the command's first argument (the task). */
    @Volatile var project = ""; private set
    @Volatile var task = ""; private set

    @Synchronized fun snapshot(): List<String> = ArrayList(buffer)
    @Synchronized fun addListener(l: Listener) { listeners += l }
    @Synchronized fun removeListener(l: Listener) { listeners -= l }

    /** @return false if something is already running. */
    @Synchronized fun start(ctx: Context, label: String, args: List<String>, project: String = "", task: String = ""): Boolean {
        if (running) return false
        running = true; lastExit = null; this.label = label; this.project = project; this.task = task
        buffer.clear()
        val app = ctx.applicationContext
        LocalServerService.start(app)   // foreground service keeps the app process alive while a long build runs
        thread(name = "orlon-run") {
            val wl = (app.getSystemService(Context.POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "orlon:build").apply { acquire(2 * 60 * 60 * 1000L) }
            var code = -1
            try {
                SandboxScripts.install(app)
                val p = Sandbox.paths(app); Sandbox.prepare(p); runCatching { Sandbox.writeResolvConf(app, p) }
                val proc = Sandbox.processBuilder(p, "/root", Sandbox.baseEnv(mapOf("ORLON_UI" to "1")), args).start()
                process = proc
                notifyState()
                val batch = ArrayList<String>(); var last = System.currentTimeMillis()
                proc.inputStream.bufferedReader().forEachLine { line ->
                    batch += line.replace(Regex("\\u001B\\[[0-9;?]*[A-Za-z]"), "")
                    val now = System.currentTimeMillis()
                    if (batch.size >= 40 || now - last > 250) { flush(batch); last = now }
                }
                flush(batch)
                code = proc.waitFor()
            } catch (e: Exception) {
                flush(mutableListOf("ORLON_ERROR ${e.message}")); code = -1
            } finally {
                process = null; runCatching { wl.release() }
                synchronized(this) { running = false; lastExit = code }
                notifyState()
            }
        }
        return true
    }

    /** Stops the running command. proot's --kill-on-exit takes its children (Gradle) down with it; the agent server is untouched. */
    fun cancel() { process?.destroy() }

    /** Publishes and empties [lines]. */
    private fun flush(lines: MutableList<String>) {
        if (lines.isEmpty()) return
        val copy = ArrayList(lines)
        lines.clear()
        val ls: List<Listener>
        synchronized(this) {
            buffer += copy
            while (buffer.size > MAX_LINES) buffer.removeAt(0)
            ls = ArrayList(listeners)
        }
        main.post { ls.forEach { it.onLines(copy) } }
    }

    private fun notifyState() {
        val ls = synchronized(this) { ArrayList(listeners) }
        val r = running; val c = lastExit
        main.post { ls.forEach { it.onState(r, c) } }
    }

    /** Quick, separate check of the Android toolchain (does not touch the single "run" slot). Blocking. */
    fun toolchainReady(ctx: Context): Boolean = try {
        SandboxScripts.install(ctx)
        val p = Sandbox.paths(ctx); Sandbox.prepare(p)
        val proc = Sandbox.processBuilder(p, "/root", Sandbox.baseEnv(), listOf("/usr/local/bin/orlon-toolchain", "status")).start()
        val out = proc.inputStream.bufferedReader().readText(); proc.waitFor()
        out.contains("ORLON_TOOLCHAIN ready")
    } catch (e: Exception) { false }
}
