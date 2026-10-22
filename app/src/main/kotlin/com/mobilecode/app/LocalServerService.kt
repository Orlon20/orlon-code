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

    private fun notifyApk(f: File, root: File) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("builds", "App ready to install", NotificationManager.IMPORTANCE_HIGH))
        val path = Sandbox.WORKSPACE + "/" + f.relativeTo(root).path
        val tap = PendingIntent.getActivity(this, f.hashCode(), Intent(this, BuildActivity::class.java).putExtra("install_path", path), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        nm.notify(2, NotificationCompat.Builder(this, "builds").setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Your app is ready").setContentText("${apkTitle(f)} — tap to install").setContentIntent(tap).setAutoCancel(true).build())
    }

    private fun stopServer() { watcher?.interrupt(); watcher = null; running = false; proc?.destroy(); proc = null; runCatching { Runtime.getRuntime().exec(arrayOf("pkill", "-f", "libproot.so")) } }

    override fun onDestroy() { stopServer(); super.onDestroy() }

    companion object {
        const val ACTION_STOP = "com.mobilecode.app.STOP"
        @Volatile var running = false
        fun start(ctx: Context) = ContextCompat.startForegroundService(ctx, Intent(ctx, LocalServerService::class.java))
        fun stop(ctx: Context) { ctx.startService(Intent(ctx, LocalServerService::class.java).setAction(ACTION_STOP)) }
    }
}
