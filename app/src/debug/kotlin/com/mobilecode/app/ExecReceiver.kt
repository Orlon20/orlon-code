package com.mobilecode.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.io.File
import kotlin.concurrent.thread

/** Debug-only: runs files/exec/cmd.sh inside the sandbox; output -> files/exec/out.txt, exit code -> files/exec/done. */
class ExecReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val p = Sandbox.paths(ctx); Sandbox.prepare(p)
        val dir = File(ctx.filesDir, "exec"); val script = File(dir, "cmd.sh")
        if (!script.exists()) return
        File(dir, "done").delete()
        script.copyTo(File(p.rootfs, "tmp/cmd.sh"), true)
        runCatching { Sandbox.writeResolvConf(ctx, p) }
        thread {
            val extra = intent.getStringExtra("binds")?.split(",")?.filter { it.isNotBlank() && it != "none" } ?: emptyList()
            val cmd = Sandbox.prootCommand(p, "/root", Sandbox.baseEnv(), listOf("/bin/sh", "/tmp/cmd.sh")).toMutableList()
            val wi = cmd.indexOf("-w")
            extra.forEachIndexed { k, b -> cmd.add(wi + 2 * k, "-b"); cmd.add(wi + 2 * k + 1, b) }
            val pb = ProcessBuilder(cmd).redirectErrorStream(true).redirectOutput(File(dir, "out.txt"))
            pb.environment().putAll(Sandbox.processEnv(p))
            val rc = try { pb.start().waitFor() } catch (e: Exception) { File(dir, "out.txt").appendText("\nexec failed: $e\n"); -1 }
            File(dir, "done").writeText(rc.toString())
        }
    }
}
