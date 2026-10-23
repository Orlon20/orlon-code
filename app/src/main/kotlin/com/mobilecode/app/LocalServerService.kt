package com.mobilecode.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import java.io.File
import kotlin.concurrent.thread

/** Keeps OpenCode's server running inside the sandbox, even while the app is in the background. */
class LocalServerService : Service() {
    private var proc: Process? = null
    private var watcher: Thread? = null

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopServer(); stopSelf(); return START_NOT_STICKY }
        prefetchNow = prefetchNow || intent?.action == ACTION_PREFETCH
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("server", "Agent running", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(this, 0, Intent(this, LocalServerService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        val n: Notification = NotificationCompat.Builder(this, "server")
            .setSmallIcon(android.R.drawable.stat_sys_download_done).setContentTitle("Orlon Code is running")
            .setContentText("Your coding agent stays available in the background.")
            .setContentIntent(open).addAction(0, "Stop", stop).setOngoing(true).build()
        // When the system restarts this service in the background, Android may refuse to promote it; stop cleanly instead of crashing.
        try { ServiceCompat.startForeground(this, 1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) }
        catch (e: Exception) { stopSelf(); return START_NOT_STICKY }
        if (proc?.isAlive != true) startServer()
        return START_STICKY
    }

    private fun startServer() {
        val p = Sandbox.paths(this)
        if (!Sandbox.isInstalled(p)) { stopSelf(); return }
        Sandbox.prepare(p)
        runCatching { SandboxScripts.install(this) }
        runCatching { Sandbox.writeResolvConf(this, p) }
        File(p.rootfs, "root/projects").mkdirs()
        val prefs = Prefs(this)
        val env = Sandbox.baseEnv(mapOf("OPENCODE_SERVER_PASSWORD" to prefs.localPassword, "OPENCODE_DISABLE_AUTOUPDATE" to "true"))
        val args = listOf("/usr/local/bin/opencode", "web", "--hostname", "127.0.0.1", "--port", prefs.localPort.toString())
        proc = Sandbox.processBuilder(p, Sandbox.WORKSPACE, env, args)
            .redirectOutput(File(filesDir, "server.log")).start()
        running = true
        startApkWatcher()
        startToolPrefetch()
    }

    /** Tells the user the moment a build produces an APK (a notification when the app is not on screen; the app shows its own banner otherwise). */
    private fun startApkWatcher() {
        if (watcher?.isAlive == true) return
        val prefs = Prefs(this)
        if (prefs.lastApkSeen == 0L) prefs.lastApkSeen = System.currentTimeMillis()
        val root = File(Sandbox.paths(this).rootfs + Sandbox.WORKSPACE)
        watcher = thread(name = "apk-watch", isDaemon = true) {
            while (true) {
                try { Thread.sleep(3000) } catch (e: InterruptedException) { break }
                showBuildProgress(root)
                if (MainActivity.visible) continue
                val f = newestNewApk(root, prefs.lastApkSeen) ?: continue
                val size = f.length(); val mod = f.lastModified()
                try { Thread.sleep(700) } catch (e: InterruptedException) { break }
                if (f.length() != size || f.lastModified() != mod) continue   // still being written
                prefs.lastApkSeen = mod
                notifyApk(f, root)
            }
        }
    }

    private var buildShown: String? = null

    /** An ongoing notification while a build runs, so a long first build is visible (and the agent stays alive) when the app is in the background. */
    private fun showBuildProgress(root: File) {
        val running = findRunningBuild(root)
        if (running == buildShown) return
        buildShown = running
        val nm = getSystemService(NotificationManager::class.java)
        if (running == null) { nm.cancel(3); return }
        nm.createNotificationChannel(NotificationChannel("progress", "Build progress", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        nm.notify(3, NotificationCompat.Builder(this, "progress").setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Building $running").setContentText("Orlon Code is building your app on this phone.")
            .setProgress(0, 0, true).setOngoing(true).setOnlyAlertOnce(true).setContentIntent(open).build())
    }

    /** Downloads the Android build tools by themselves on an unmetered network (once), so the first build is not a long wait. */
    private fun startToolPrefetch() {
        if (prefetching) return
        prefetching = true
        thread(name = "tool-prefetch", isDaemon = true) {
            try {
                val prefs = Prefs(this)
                Thread.sleep(25_000)
                while (running) {
                    if (prefs.prefetch && (prefetchNow || isUnmetered())) break
                    if (!prefs.prefetch && !prefetchNow) { Thread.sleep(60_000); continue }
                    Thread.sleep(60_000)
                }
                if (!running || toolchainReady()) return@thread
                prefetchNow = false
                installTools()
            } catch (e: Exception) { /* best effort */ } finally { prefetching = false }
        }
    }

    private fun isUnmetered(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    private fun sandboxRun(script: String): Process {
        val p = Sandbox.paths(this)
        return Sandbox.processBuilder(p, "/root", Sandbox.baseEnv(), listOf("/bin/sh", "-c", script)).start()
    }

    private fun toolchainReady(): Boolean = try { val pr = sandboxRun("/usr/local/bin/orlon-toolchain status >/dev/null 2>&1"); pr.inputStream.readBytes(); pr.waitFor() == 0 } catch (e: Exception) { false }

    private fun installTools() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("progress", "Build progress", NotificationManager.IMPORTANCE_LOW))
        fun note(text: String, ongoing: Boolean) = nm.notify(4, NotificationCompat.Builder(this, "progress").setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Preparing build tools").setContentText(text).setOngoing(ongoing).setOnlyAlertOnce(true).setAutoCancel(!ongoing).build())
        note("Starting…", true)
        val pr = sandboxRun("/usr/local/bin/orlon-toolchain install 2>&1")
        pr.inputStream.bufferedReader().forEachLine { l -> if (l.startsWith("ORLON_STEP ")) note(l.removePrefix("ORLON_STEP "), true) }
        val ok = pr.waitFor() == 0
        if (ok) { nm.cancel(4); note("Ready: your first build will start right away.", false) } else note("Could not finish; it will retry on your next build.", false)
    }

    private fun notifyApk(f: File, root: File) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("builds", "App ready to install", NotificationManager.IMPORTANCE_HIGH))
        val path = Sandbox.WORKSPACE + "/" + f.relativeTo(root).path
        val tap = PendingIntent.getActivity(this, f.hashCode(), Intent(this, MainActivity::class.java).putExtra("install_path", path).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        nm.notify(2, NotificationCompat.Builder(this, "builds").setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Your app is ready").setContentText("${apkTitle(f)} — tap to install").setContentIntent(tap).setAutoCancel(true).build())
    }

    private fun stopServer() { watcher?.interrupt(); watcher = null; running = false; proc?.destroy(); proc = null; runCatching { Runtime.getRuntime().exec(arrayOf("pkill", "-f", "libproot.so")) } }

    override fun onDestroy() { stopServer(); super.onDestroy() }

    companion object {
        const val ACTION_STOP = "com.mobilecode.app.STOP"
        const val ACTION_PREFETCH = "com.mobilecode.app.PREFETCH"
        @Volatile var running = false
        @Volatile private var prefetching = false
        @Volatile var prefetchNow = false
        fun prepareTools(ctx: Context) = ContextCompat.startForegroundService(ctx, Intent(ctx, LocalServerService::class.java).setAction(ACTION_PREFETCH))
        fun start(ctx: Context) = ContextCompat.startForegroundService(ctx, Intent(ctx, LocalServerService::class.java))
        fun stop(ctx: Context) { ctx.startService(Intent(ctx, LocalServerService::class.java).setAction(ACTION_STOP)) }
    }
}
