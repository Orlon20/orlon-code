package com.mobilecode.app

import android.content.Context
import android.net.ConnectivityManager
import java.io.File
import java.security.MessageDigest

/** Layout and command construction for the on-device Linux sandbox (proot + Alpine + OpenCode). */
object Sandbox {
    const val OPENCODE_VERSION = "1.19.3"
    const val ALPINE_URL = "https://dl-cdn.alpinelinux.org/alpine/v3.24/releases/aarch64/alpine-minirootfs-3.24.3-aarch64.tar.gz"
    const val ALPINE_SHA256 = "4d3f80afaebd447c5591a63a73bde380a149fe8b2d85488cafabb2ff97551f50"
    const val OPENCODE_URL = "https://registry.npmjs.org/opencode-linux-arm64-musl/-/opencode-linux-arm64-musl-$OPENCODE_VERSION.tgz"
    const val OPENCODE_SHA256 = "aaa3a7254cc1b5e639129f88c0adaa6814ea507d66f8062669b9956bc16f2741"
    const val WORKSPACE = "/root/projects"
    const val MIN_FREE_BYTES = 900L * 1024 * 1024

    class Paths(val nativeDir: String, val filesDir: String) {
        val rootfs get() = "$filesDir/rootfs"
        val marker get() = File(filesDir, "installed-$OPENCODE_VERSION")
        val proot get() = "$nativeDir/libproot.so"
    }

    fun paths(ctx: Context) = Paths(ctx.applicationInfo.nativeLibraryDir, ctx.filesDir.absolutePath)

    fun isInstalled(p: Paths) = p.marker.exists()

    /** Host-side environment proot itself needs (not the environment inside the sandbox). */
    fun processEnv(p: Paths) = mapOf(
        "LD_LIBRARY_PATH" to "${p.filesDir}/lib:${p.nativeDir}",
        "PROOT_LOADER" to "${p.nativeDir}/libproot-loader.so",
        "PROOT_TMP_DIR" to "${p.filesDir}/tmp",
    )

    fun baseEnv(extra: Map<String, String> = emptyMap()) = mapOf(
        "HOME" to "/root", "PATH" to "/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
        "TERM" to "xterm", "LANG" to "C.UTF-8", "TMPDIR" to "/tmp", "SHELL" to "/bin/bash",
    ) + extra

    fun prootCommand(p: Paths, cwd: String, env: Map<String, String>, args: List<String>): List<String> =
        listOf(
            p.proot, "--link2symlink", "-0", "--kill-on-exit", "-r", p.rootfs,
            "-b", "/dev", "-b", "/proc", "-b", "/sys", "-w", cwd, "/usr/bin/env", "-i",
        ) + env.map { "${it.key}=${it.value}" } + args

    fun processBuilder(p: Paths, cwd: String, env: Map<String, String>, args: List<String>): ProcessBuilder =
        ProcessBuilder(prootCommand(p, cwd, env, args)).redirectErrorStream(true).also {
            it.environment().putAll(processEnv(p))
        }

    /** proot needs libtalloc.so.2, but APKs can only carry lib*.so, so link it into a dir on the loader path. */
    fun prepare(p: Paths) {
        File(p.filesDir, "lib").mkdirs(); File(p.filesDir, "tmp").mkdirs()
        val link = File(p.filesDir, "lib/libtalloc.so.2")
        if (!link.exists()) android.system.Os.symlink("${p.nativeDir}/libtalloc.so", link.absolutePath)
    }

    /** Use the phone's current DNS servers inside the sandbox (falls back to Cloudflare). */
    fun writeResolvConf(ctx: Context, p: Paths) {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val dns = cm.getLinkProperties(cm.activeNetwork)?.dnsServers?.mapNotNull { it.hostAddress }.orEmpty()
        val lines = (dns.ifEmpty { listOf("1.1.1.1") }).joinToString("\n") { "nameserver $it" }
        File(p.rootfs, "etc").mkdirs()
        File(p.rootfs, "etc/resolv.conf").writeText(lines + "\n")
    }

    fun sha256Hex(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { s -> val b = ByteArray(1 shl 16); while (true) { val n = s.read(b); if (n < 0) break; md.update(b, 0, n) } }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
