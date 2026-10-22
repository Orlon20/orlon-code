package com.mobilecode.app

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** One-time, resumable setup. Blocking: run off the main thread. Throws on failure. */
class Installer(private val ctx: Context, private val report: (step: String, percent: Int) -> Unit, private val log: (String) -> Unit) {
    private val p = Sandbox.paths(ctx)
    private val dl = File(p.filesDir, "dl")

    fun run() {
        if (Sandbox.isInstalled(p)) return
        if (File(p.filesDir).usableSpace < Sandbox.MIN_FREE_BYTES) error("Not enough free storage (need about 900 MB).")
        Sandbox.prepare(p); dl.mkdirs()

        val alpine = download("Downloading Linux environment", Sandbox.ALPINE_URL, Sandbox.ALPINE_SHA256, File(dl, "alpine.tgz"), 0, 6)
        report("Unpacking Linux environment", 6)
        File(p.rootfs).deleteRecursively(); File(p.rootfs).mkdirs()
        exec(listOf("/system/bin/tar", "xzf", alpine.absolutePath, "-C", p.rootfs), "unpack alpine")
        alpine.delete()

        val oc = download("Downloading OpenCode ${Sandbox.OPENCODE_VERSION}", Sandbox.OPENCODE_URL, Sandbox.OPENCODE_SHA256, File(dl, "opencode.tgz"), 6, 60)
        report("Unpacking OpenCode", 60)
        val stage = File(dl, "oc").apply { deleteRecursively(); mkdirs() }
        exec(listOf("/system/bin/tar", "xzf", oc.absolutePath, "-C", stage.absolutePath, "package/bin/opencode"), "unpack opencode")
        val dest = File(p.rootfs, "usr/local/bin").apply { mkdirs() }.resolve("opencode")
        if (!File(stage, "package/bin/opencode").renameTo(dest)) error("Could not install OpenCode")
        dest.setExecutable(true, false)
        oc.delete(); stage.deleteRecursively()

        report("Installing tools", 66)
        Sandbox.writeResolvConf(ctx, p)
        File(p.rootfs, "root/projects").mkdirs()
        val rc = runSandbox(
            "apk add --no-cache libstdc++ libgcc git bash ripgrep ca-certificates && " +
                "git config --global --add safe.directory '*' && git config --global init.defaultBranch main && " +
                "git config --global user.name Mobilecode && git config --global user.email mobilecode@localhost",
        )
        if (rc != 0) error("Installing tools failed (exit $rc). Check your connection and retry.")
        val v = runSandboxCapture("/usr/local/bin/opencode --version").trim()
        if (v != Sandbox.OPENCODE_VERSION) error("OpenCode failed its self-check (got '$v').")
        report("Done", 100)
        Sandbox.paths(ctx).marker.writeText(v)
        dl.deleteRecursively()
    }

    private fun runSandbox(script: String): Int {
        val pb = Sandbox.processBuilder(p, "/root", Sandbox.baseEnv(), listOf("/bin/sh", "-c", script))
        val proc = pb.start(); proc.inputStream.bufferedReader().forEachLine { log(it) }
        return proc.waitFor()
    }

    private fun runSandboxCapture(script: String): String {
        val proc = Sandbox.processBuilder(p, "/root", Sandbox.baseEnv(), listOf("/bin/sh", "-c", script)).start()
        val out = proc.inputStream.bufferedReader().readText(); proc.waitFor(); return out.lines().lastOrNull { it.isNotBlank() } ?: ""
    }

    private fun exec(cmd: List<String>, what: String) {
        val proc = ProcessBuilder(cmd).redirectErrorStream(true).start()
        val out = proc.inputStream.bufferedReader().readText()
        if (proc.waitFor() != 0) error("Failed to $what: ${out.take(200)}")
    }

    private fun download(step: String, url: String, sha256: String, out: File, from: Int, to: Int): File {
        if (out.exists() && Sandbox.sha256Hex(out) == sha256) return out
        out.delete()
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 20000; c.readTimeout = 30000
        if (c.responseCode != 200) error("Download failed (HTTP ${c.responseCode}) for $step")
        val total = c.contentLengthLong.coerceAtLeast(1)
        val md = MessageDigest.getInstance("SHA-256")
        var done = 0L; var lastPct = -1
        c.inputStream.use { ins -> out.outputStream().use { os ->
            val b = ByteArray(1 shl 16)
            while (true) {
                val n = ins.read(b); if (n < 0) break
                os.write(b, 0, n); md.update(b, 0, n); done += n
                val pct = from + ((to - from) * done / total).toInt()
                if (pct != lastPct) { lastPct = pct; report("$step (${done / 1_000_000} / ${total / 1_000_000} MB)", pct) }
            }
        } }
        val got = md.digest().joinToString("") { "%02x".format(it) }
        if (got != sha256) { out.delete(); error("Downloaded file failed verification. Retry; if it persists the source changed.") }
        return out
    }
}
