package com.mobilecode.app

import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File
import kotlin.concurrent.thread

/** Build Android projects on this phone: set up the toolchain, pick or add a project, build, read errors, install the APK. */
class BuildActivity : AppCompatActivity(), BuildRunner.Listener {
    private lateinit var prefs: Prefs
    private lateinit var tcStatus: TextView
    private lateinit var tcBar: ProgressBar
    private lateinit var tcButton: Button
    private lateinit var tcNote: TextView
    private lateinit var projectBtn: Button
    private lateinit var debugBtn: Button
    private lateinit var releaseBtn: Button
    private lateinit var moreBtn: Button
    private lateinit var cancelBtn: Button
    private lateinit var resultCard: LinearLayout
    private lateinit var logView: TextView
    private lateinit var logScroll: ScrollView

    private var toolchainReady = false
    private var projects: List<AndroidProject> = emptyList()
    private val log = SpannableStringBuilder()
    private var lastRender = 0L

    private val projectsHost get() = File(Sandbox.paths(this).rootfs + Sandbox.WORKSPACE)

    private val zipPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) importZip(uri) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyThemeChrome()
        prefs = Prefs(this)
        buildUi()
        refreshProjects()
        BuildRunner.addListener(this)
        replayLog()
        updateButtons()
        refreshToolchain()
        intent?.getStringExtra("install_path")?.let { path -> artifactFile(projectsHost, Sandbox.WORKSPACE, path)?.let { installApk(it) } }
    }

    override fun onDestroy() { BuildRunner.removeListener(this); super.onDestroy() }

    // ---- UI ---------------------------------------------------------------------------------------

    private fun buildUi() {
        tcStatus = label("Checking build tools…", 14f, bold = true)
        tcBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; progressTintList = android.content.res.ColorStateList.valueOf(Palette.ACCENT); visibility = View.GONE
        }
        tcNote = label("Installs JDK 17 and the Android SDK (about 750 MB). By continuing you accept the Android SDK License.", 12f, Palette.FAINT)
        tcButton = primaryButton("Set up build tools") { setupToolchain() }.apply { visibility = View.GONE }
        val tcCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; background = cardBg(); setPadding(dp(14), dp(12), dp(14), dp(12))
            addView(tcStatus); addView(tcBar, LinearLayout.LayoutParams(-1, dp(14)).apply { topMargin = dp(8) })
            addView(tcNote.apply { setPadding(0, dp(6), 0, 0) }); addView(tcButton, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(10) })
        }

        projectBtn = ghostButton("Choose a project") { showProjectMenu() }
        debugBtn = primaryButton("Build debug APK") { build("assembleDebug") }
        releaseBtn = ghostButton("Release APK") { build("assembleRelease") }
        moreBtn = ghostButton("⋯") { showMoreMenu() }
        cancelBtn = ghostButton("Cancel build") { BuildRunner.cancel() }.apply { visibility = View.GONE }
        val actions = LinearLayout(this).apply {
            addView(debugBtn, LinearLayout.LayoutParams(0, dp(46), 1.5f).apply { marginEnd = dp(8) })
            addView(releaseBtn, LinearLayout.LayoutParams(0, dp(46), 1f).apply { marginEnd = dp(8) })
            addView(moreBtn, LinearLayout.LayoutParams(dp(52), dp(46)))
        }

        resultCard = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = cardBg(); setPadding(dp(14), dp(12), dp(14), dp(12)); visibility = View.GONE }

        logView = label("", 11f, Palette.MUTED, mono = true).apply { setTextIsSelectable(true); setPadding(dp(10), dp(8), dp(10), dp(8)); background = cardBg() }

        val head = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(label("Android builds", 20f, bold = true), LinearLayout.LayoutParams(0, -2, 1f))
            addView(label("✕", 20f, Palette.MUTED).apply { setPadding(dp(12), dp(8), dp(4), dp(8)); setOnClickListener { finish() } })
        }
        // Fixed controls on top; results and the log flow together in one scrolling area below.
        logScroll = ScrollView(this).apply {
            addView(LinearLayout(this@BuildActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(resultCard, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
                addView(label("Log", 12f, Palette.FAINT).apply { setPadding(0, 0, 0, dp(4)) })
                addView(logView, LinearLayout.LayoutParams(-1, -2))
            })
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(8)); setBackgroundColor(Palette.BG); applySystemInsets()
            addView(head)
            addView(tcCard, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
            addView(projectBtn, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(12) })
            addView(actions, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
            addView(cancelBtn, LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(8) })
            addView(logScroll, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(12) })
        })
    }

    private fun updateButtons() {
        val running = BuildRunner.running
        val idle = !running
        val ok = idle && toolchainReady && selected() != null
        debugBtn.isEnabled = ok; releaseBtn.isEnabled = ok; moreBtn.isEnabled = idle; projectBtn.isEnabled = idle
        for (b in listOf(debugBtn, releaseBtn)) b.alpha = if (ok) 1f else 0.4f
        projectBtn.alpha = if (idle) 1f else 0.4f
        cancelBtn.visibility = if (running) View.VISIBLE else View.GONE
        tcButton.isEnabled = idle; tcButton.alpha = if (idle) 1f else 0.4f
        tcButton.visibility = if (toolchainReady) View.GONE else View.VISIBLE
        tcNote.visibility = if (toolchainReady) View.GONE else View.VISIBLE
        tcStatus.text = when {
            running && BuildRunner.label == "Set up build tools" -> tcStatus.text
            toolchainReady -> "Build tools ready ✓  (JDK 17 · Android SDK 36)"
            else -> "Build tools not installed"
        }
        projectBtn.text = selected()?.let { "Project: ${it.name}  ▾" } ?: (if (projects.isEmpty()) "Add a project  ▾" else "Choose a project  ▾")
    }

    // ---- Projects ---------------------------------------------------------------------------------

    private fun selected(): AndroidProject? = projects.firstOrNull { it.sandboxPath == prefs.buildProject }

    private fun refreshProjects() {
        projects = detectProjects(projectsHost, Sandbox.WORKSPACE)
        if (selected() == null) prefs.buildProject = projects.firstOrNull { it.isAndroid }?.sandboxPath ?: projects.firstOrNull()?.sandboxPath ?: ""
    }

    private fun showProjectMenu() {
        refreshProjects(); updateButtons()
        val items = ArrayList<Pair<Int, String>>()
        projects.forEachIndexed { i, p -> items += i to ((if (p.sandboxPath == prefs.buildProject) "✓ " else "") + p.name + if (p.isAndroid) "" else "  (not Android)") }
        items += 1000 to "＋ Clone from Git…"
        items += 1001 to "＋ Import a ZIP…"
        showMenu(projectBtn, items) { id ->
            when {
                id == 1000 -> askClone()
                id == 1001 -> zipPicker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
                else -> { prefs.buildProject = projects[id].sandboxPath; updateButtons() }
            }
        }
    }

    private fun askClone() {
        val url = field("https://github.com/owner/repo")
        val box = LinearLayout(this).apply { setPadding(dp(20), dp(8), dp(20), 0); addView(url, LinearLayout.LayoutParams(-1, -2)) }
        AlertDialog.Builder(this).setTitle("Clone from Git").setMessage("Public https repositories only.").setView(box)
            .setPositiveButton("Clone") { _, _ ->
                val u = url.text.toString().trim()
                val name = repoNameFromUrl(u)
                if (name == null) toast("That doesn't look like a plain https:// repository URL.")
                else startRun("Clone", listOf("/usr/local/bin/orlon-project", "clone", u, uniqueName(name)))
            }.setNegativeButton("Cancel", null).show()
    }

    private fun uniqueName(base: String): String {
        var n = base; var i = 2
        while (File(projectsHost, n).exists()) n = "$base-${i++}"
        return n
    }

    private fun importZip(uri: Uri) {
        val display = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } ?: "imported"
        val name = uniqueName(safeProjectName(display.removeSuffix(".zip").removeSuffix(".ZIP")) ?: "imported")
        thread {
            val dest = File(Sandbox.paths(this).rootfs, "root/work/import.zip").apply { parentFile?.mkdirs() }
            try { contentResolver.openInputStream(uri)?.use { i -> dest.outputStream().use { o -> i.copyTo(o) } } }
            catch (e: Exception) { runOnUiThread { toast("Couldn't read that file.") }; return@thread }
            runOnUiThread { startRun("Import", listOf("/usr/local/bin/orlon-project", "import-zip", "/root/work/import.zip", name)) }
        }
    }

    private fun showMoreMenu() {
        showMenu(moreBtn, listOf(1 to "Clean project", 2 to "Refresh projects", 3 to "Update build tools", 4 to "Clear build cache (frees storage)")) { id ->
            when (id) {
                1 -> build("clean")
                2 -> { refreshProjects(); updateButtons() }
                3 -> setupToolchain()
                4 -> AlertDialog.Builder(this).setTitle("Clear build cache?")
                    .setMessage("Removes downloaded Gradle files (usually 1–2 GB). The next build downloads them again.")
                    .setPositiveButton("Clear") { _, _ -> startRun("Clear cache", listOf("/usr/local/bin/orlon-toolchain", "clean-cache")) }
                    .setNegativeButton("Cancel", null).show()
            }
        }
    }

    // ---- Running things ---------------------------------------------------------------------------

    private fun build(task: String) {
        val p = selected() ?: run { toast("Add or choose a project first."); return }
        if (!toolchainReady) { toast("Set up the build tools first."); return }
        if (!Regex("[A-Za-z0-9:_.-]+").matches(task)) return
        startRun("Build", listOf("/usr/local/bin/orlon-build", "-C", p.sandboxPath, task), p.sandboxPath, task)
    }

    private fun setupToolchain() {
        tcBar.progress = 0; tcBar.visibility = View.VISIBLE; tcStatus.text = "Setting up build tools…"
        startRun("Set up build tools", listOf("/usr/local/bin/orlon-toolchain", "install"))
    }

    private fun startRun(label: String, args: List<String>, project: String = "", task: String = "") {
        log.clear(); logView.text = ""; resultCard.visibility = View.GONE
        if (!BuildRunner.start(this, label, args, project, task)) { toast("Something is already running."); return }
        updateButtons()
    }

    private fun refreshToolchain() {
        thread {
            val ready = BuildRunner.toolchainReady(applicationContext)
            runOnUiThread { toolchainReady = ready; tcBar.visibility = View.GONE; updateButtons() }
        }
    }

    // ---- Output -----------------------------------------------------------------------------------

    override fun onLines(newLines: List<String>) { appendLog(newLines); renderProgress(newLines); if (System.currentTimeMillis() - lastRender > 1200) { lastRender = System.currentTimeMillis(); renderResult(false) } }

    override fun onState(running: Boolean, exitCode: Int?) {
        updateButtons()
        if (!running) {
            if (BuildRunner.label == "Set up build tools") { tcBar.visibility = View.GONE; refreshToolchain() }
            if (BuildRunner.label == "Clone" || BuildRunner.label == "Import") {
                BuildRunner.snapshot().lastOrNull { it.startsWith("ORLON_PROJECT ") }?.let { prefs.buildProject = it.removePrefix("ORLON_PROJECT ").trim() }
                refreshProjects(); updateButtons()
            }
            renderResult(true)
        }
    }

    private fun replayLog() {
        val lines = BuildRunner.snapshot()
        if (lines.isEmpty()) return
        appendLog(lines.takeLast(1500)); renderResult(!BuildRunner.running)
    }

    private fun appendLog(lines: List<String>) {
        val nearBottom = logScroll.getChildAt(0).bottom - (logScroll.height + logScroll.scrollY) < dp(160)
        for (l in lines) {
            val start = log.length
            log.append(l).append('\n')
            val color = when {
                l.startsWith("e: ") || l.contains(": error:") || l.startsWith("ORLON_ERROR") || l.startsWith("BUILD FAILED") || l.startsWith("ORLON_RESULT failed") -> 0xFFFF6B6B.toInt()
                l.startsWith("BUILD SUCCESSFUL") || l.startsWith("ORLON_RESULT success") || l.startsWith("ORLON_APK") -> 0xFF5FD38D.toInt()
                l.startsWith("ORLON_STEP") -> Palette.ACCENT
                l.startsWith("> Task") -> Palette.FAINT
                else -> 0
            }
            if (color != 0) log.setSpan(ForegroundColorSpan(color), start, log.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        if (log.length > 250_000) log.delete(0, log.length - 150_000)
        logView.text = log
        if (nearBottom) logScroll.post { logScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun renderProgress(lines: List<String>) {
        if (BuildRunner.label != "Set up build tools") return
        for (l in lines) Regex("^ORLON_STEP (\\d+)/(\\d+) (.*)$").find(l)?.let {
            val i = it.groupValues[1].toInt(); val n = it.groupValues[2].toInt()
            tcBar.progress = (i - 1) * 100 / n; tcStatus.text = "Step $i of $n: ${it.groupValues[3]}"
        }
    }

    private fun renderResult(finished: Boolean) {
        if (BuildRunner.label != "Build") { if (finished && BuildRunner.label != "Set up build tools") showGenericResult(); return }
        val r = parseBuildLog(BuildRunner.snapshot())
        val project = BuildRunner.project; val task = BuildRunner.task
        resultCard.removeAllViews()
        val errors = r.issues.filter { it.severity == Severity.ERROR }
        when {
            !finished && r.success == null -> {
                if (errors.isEmpty()) { resultCard.visibility = View.GONE; return }
                resultCard.visibility = View.VISIBLE
                resultCard.addView(label("Building…  ${errors.size} error${if (errors.size == 1) "" else "s"} so far", 14f, 0xFFFF6B6B.toInt(), bold = true))
            }
            r.success == true -> {
                resultCard.visibility = View.VISIBLE
                resultCard.addView(label("✓ Build succeeded", 16f, 0xFF5FD38D.toInt(), bold = true))
                val files = r.apks.mapNotNull { a -> artifactFile(projectsHost, Sandbox.WORKSPACE, a.path)?.let { it to a } }
                if (files.isEmpty()) resultCard.addView(label(if (task == "clean") "Project cleaned." else "No APK was produced by this task. Try \"Build debug APK\".", 13f, Palette.MUTED).apply { setPadding(0, dp(6), 0, 0) })
                for ((f, a) in files) addApkRow(f, a)
            }
            else -> {
                resultCard.visibility = View.VISIBLE
                resultCard.addView(label("✗ Build failed" + if (errors.isNotEmpty()) " — ${errors.size} error${if (errors.size == 1) "" else "s"}" else "", 16f, 0xFFFF6B6B.toInt(), bold = true))
                buildHint(BuildRunner.snapshot())?.let { resultCard.addView(label(it, 13f, Palette.ACCENT).apply { setPadding(0, dp(6), 0, 0) }) }
                if (errors.isEmpty()) r.failure?.let { resultCard.addView(label(it.lines().take(3).joinToString("\n"), 12f, Palette.MUTED).apply { setPadding(0, dp(6), 0, 0) }) }
                for (e in errors.take(8)) resultCard.addView(issueRow(e, project))
                if (errors.size > 8) resultCard.addView(label("+ ${errors.size - 8} more in the log", 12f, Palette.FAINT).apply { setPadding(0, dp(6), 0, 0) })
                if (project.isNotEmpty()) resultCard.addView(primaryButton("Ask the agent to fix this") { askAgent(fixPrompt(project, task, r)) },
                    LinearLayout.LayoutParams(-1, dp(46)).apply { topMargin = dp(12) })
            }
        }
    }

    private fun showGenericResult() {
        resultCard.removeAllViews(); resultCard.visibility = View.VISIBLE
        val ok = BuildRunner.lastExit == 0
        resultCard.addView(label((if (ok) "✓ " else "✗ ") + BuildRunner.label + if (ok) " finished" else " failed", 15f, if (ok) 0xFF5FD38D.toInt() else 0xFFFF6B6B.toInt(), bold = true))
        if (!ok) BuildRunner.snapshot().lastOrNull { it.startsWith("ORLON_ERROR") }?.let { resultCard.addView(label(it.removePrefix("ORLON_ERROR").trim(), 13f, Palette.MUTED).apply { setPadding(0, dp(6), 0, 0) }) }
    }

    private fun issueRow(e: BuildIssue, project: String) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL; setPadding(0, dp(8), 0, 0)
        val loc = e.location(project)
        if (loc.isNotEmpty()) addView(label(loc, 12f, Palette.ACCENT, mono = true))
        addView(label(e.message, 13f))
    }

    private fun addApkRow(f: File, a: ApkOutput) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(10), 0, 0) }
        row.addView(label(f.name, 14f, bold = true))
        row.addView(label("${f.length() / 1024} KB · ${if (a.signed) "signed" else "NOT signed (can't be installed)"}", 12f, Palette.MUTED))
        val buttons = LinearLayout(this).apply { setPadding(0, dp(8), 0, 0) }
        if (f.name.endsWith(".apk") && a.signed) buttons.addView(primaryButton("Install") { installApk(f) }, LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(8) })
        buttons.addView(ghostButton("Share") { shareFile(f) }, LinearLayout.LayoutParams(0, dp(42), 1f))
        row.addView(buttons); resultCard.addView(row)
    }

    // ---- Hand-offs --------------------------------------------------------------------------------

    private fun installApk(f: File) = ApkInstall.install(this, f)

    private fun shareFile(f: File) {
        val uri = FileProvider.getUriForFile(this, "$packageName.files", f)
        val send = Intent(Intent.ACTION_SEND).setType(if (f.name.endsWith(".apk")) "application/vnd.android.package-archive" else "application/octet-stream")
            .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(send, "Share ${f.name}"))
    }

    /** Opens the agent with the build errors typed into the prompt, ready to send. */
    private fun askAgent(prompt: String) {
        startActivity(Intent(this, MainActivity::class.java).putExtra("draft", prompt).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        finish()
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_LONG).show()
}
