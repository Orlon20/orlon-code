package com.mobilecode.app

import android.annotation.SuppressLint
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.*
import android.webkit.JavascriptInterface
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    private lateinit var web: WebView
    private lateinit var errorView: android.widget.LinearLayout
    private lateinit var errorText: TextView
    private var server: Server? = null
    private var fileCb: ValueCallback<Array<Uri>>? = null
    private var failed = false
    private lateinit var prefs: Prefs
    private lateinit var retryBtn: android.widget.Button
    private var starting = false
    private var pruneThenHome = false
    private lateinit var rootFrame: FrameLayout
    private lateinit var menuBtn: android.widget.ImageView
    private lateinit var brandLabel: WordmarkView
    private lateinit var topBar: android.widget.LinearLayout
    private lateinit var rootColumn: android.widget.LinearLayout
    private lateinit var errTitle: WordmarkView
    private var themeDialog: ThemeDialog? = null
    private var showThemesWhenReady = false
    private lateinit var loadingView: FrameLayout
    private lateinit var loadingMark: WordmarkView
    private lateinit var loadingCaption: TextView
    private var loadingAnim: android.animation.ObjectAnimator? = null
    private var loadingVisible = false
    private var loadingShownAt = 0L
    private var awaitingReady = false
    /** Text handed over from another screen to type into the prompt box (the user still presses send). */
    private var pendingDraft: String? = null
    private lateinit var apkBanner: android.widget.LinearLayout
    private lateinit var apkBannerText: TextView
    private lateinit var apkBannerBtn: android.widget.Button
    private var bannerApk: java.io.File? = null
    private val apkPoll = object : Runnable { override fun run() { checkNewApk(); checkBusy(); web.postDelayed(this, 2500) } }

    /** The screen stays on exactly while the agent is working, so long builds are not interrupted by the screen timing out. */
    private fun checkBusy() {
        thread {
            val busy = fetchAgentBusy(prefs.localServer())
            runOnUiThread { if (busy) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        }
    }

    /** Page -> app calls (only these two; the app never exposes anything else to web content). */
    private inner class Bridge {
        @JavascriptInterface fun openThemes() { runOnUiThread { showThemes() } }
        @JavascriptInterface fun themesData(json: String) { runOnUiThread { onThemesData(json) } }
        /** Tapped an APK path in the chat. Only a real .apk in a project's .orlon/out folder is ever installed, whatever the page asks for. */
        @JavascriptInterface fun installApk(path: String) {
            runOnUiThread {
                val f = artifactFile(projectsHostRoot(), Sandbox.WORKSPACE, path)
                if (f != null && f.name.endsWith(".apk")) ApkInstall.install(this@MainActivity, f)
                else android.widget.Toast.makeText(this@MainActivity, "That APK isn't available any more. Ask the agent to build it again.", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }
    private val notifPerm = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }

    private val picker = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) {
        fileCb?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(it.resultCode, it.data)); fileCb = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyThemeChrome()
        pendingDraft = intent?.getStringExtra("draft")
        window.decorView.post { handleInstallIntent(intent) }
        prefs = Prefs(this)
        if (!Sandbox.isInstalled(Sandbox.paths(this))) { startActivity(Intent(this, SetupActivity::class.java)); finish(); return }
        server = prefs.localServer()
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
            notifPerm.launch(android.Manifest.permission.POST_NOTIFICATIONS)

        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) WebView.setWebContentsDebuggingEnabled(true)
        web = WebView(this).apply {
            setBackgroundColor(Palette.BG)
            settings.javaScriptEnabled = true; settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.useWideViewPort = true; settings.loadWithOverviewMode = true
            webViewClient = object : WebViewClient() {
                override fun onReceivedHttpAuthRequest(v: WebView, h: HttpAuthHandler, host: String?, realm: String?) {
                    h.proceed(AUTH_USER, server!!.password)
                }
                override fun onPageFinished(v: WebView, url: String?) {
                    if (pruneThenHome) {
                        pruneThenHome = false
                        v.evaluateJavascript(Branding.prepStateJs(Sandbox.WORKSPACE)) { v.loadUrl(homeUrl(server!!), mapOf("Authorization" to basicAuth(server!!.password))) }
                        return
                    }
                    if (!failed) { if (loadingVisible) awaitPromptThenHide() else errorView.visibility = android.view.View.GONE; v.evaluateJavascript(Branding.SWAP_JS, null); v.evaluateJavascript(Branding.logoSwapJs(), null); v.evaluateJavascript(Branding.FIT_JS, null); v.evaluateJavascript(Branding.NO_PROVIDER_BLOCK_JS, null); v.evaluateJavascript(Branding.SLASH_THEMES_JS, null); v.evaluateJavascript(Branding.APK_LINKS_JS, null); v.postDelayed({ v.evaluateJavascript(Branding.THEME_COLORS_JS) { raw -> adoptPalette(raw) } }, 700); v.evaluateJavascript(Branding.CLEAN_JS, null) }
                }
                override fun onReceivedError(v: WebView, r: WebResourceRequest, e: WebResourceError) {
                    if (r.isForMainFrame) showError(unreachableMsg())
                }
                override fun onReceivedHttpError(v: WebView, r: WebResourceRequest, e: WebResourceResponse) {
                    if (r.isForMainFrame && e.statusCode == 401) showError(wrongPasswordMsg())
                }
                override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                    val host = Uri.parse(server!!.url).host
                    if (r.url.host == host) return false
                    runCatching { startActivity(Intent(Intent.ACTION_VIEW, r.url)) }; return true
                }
            }
            addJavascriptInterface(Bridge(), "MobilecodeBridge")
            webChromeClient = object : WebChromeClient() {
                override fun onShowFileChooser(w: WebView, cb: ValueCallback<Array<Uri>>, p: FileChooserParams): Boolean {
                    fileCb?.onReceiveValue(null); fileCb = cb
                    return try { picker.launch(p.createIntent()); true } catch (e: Exception) { fileCb = null; false }
                }
            }
        }
        errorText = label("", 15f, Palette.FG).apply { gravity = Gravity.CENTER }
        errorView = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setBackgroundColor(Palette.BG); visibility = android.view.View.GONE; setPadding(dp(24), 0, dp(24), 0)
            errTitle = wordmark(18).apply { setPadding(0, 0, 0, dp(16)) }
            addView(errTitle)
            addView(errorText)
            retryBtn = primaryButton("Retry") { restartLocal() }
            addView(retryBtn, android.widget.LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(24) })
        }
        menuBtn = android.widget.ImageView(this).apply {
            setImageResource(R.drawable.ic_settings); setColorFilter(Palette.FG); alpha = 0.75f
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE; setPadding(dp(9), dp(9), dp(9), dp(9))
            contentDescription = "Settings"; setOnClickListener { showMenu(it) }
        }
        brandLabel = wordmark(11, Palette.FG, 0.7f)
        // A real bar above the web page (not an overlay), so the menu can never sit on top of OpenCode's tab bar or dialogs.
        topBar = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), 0, dp(2), 0)
            addView(brandLabel, android.widget.LinearLayout.LayoutParams(-2, -2))
            addView(android.view.View(this@MainActivity), android.widget.LinearLayout.LayoutParams(0, 1, 1f))
            addView(menuBtn, android.widget.LinearLayout.LayoutParams(dp(44), dp(36)))
        }
        loadingMark = wordmark(26)
        loadingCaption = label("Starting your agent…", 12f, Palette.FAINT).apply { gravity = Gravity.CENTER; setPadding(0, dp(18), 0, 0) }
        loadingView = FrameLayout(this).apply {
            setBackgroundColor(Palette.BG); visibility = android.view.View.GONE; isClickable = true
            addView(android.widget.LinearLayout(this@MainActivity).apply {
                orientation = android.widget.LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL
                addView(loadingMark); addView(loadingCaption)
            }, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        }
        apkBannerText = label("", 14f, bold = true).apply { maxLines = 2 }
        apkBannerBtn = primaryButton("Install") { bannerApk?.let { ApkInstall.install(this, it) }; hideApkBanner() }
        apkBanner = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; background = cardBg(); elevation = dp(6).toFloat()
            setPadding(dp(14), dp(10), dp(8), dp(10)); visibility = android.view.View.GONE
            addView(apkBannerText, android.widget.LinearLayout.LayoutParams(0, -2, 1f))
            addView(apkBannerBtn, android.widget.LinearLayout.LayoutParams(dp(92), dp(40)).apply { marginStart = dp(8) })
            addView(label("✕", 18f, Palette.MUTED).apply { setPadding(dp(12), dp(6), dp(8), dp(6)); setOnClickListener { hideApkBanner() } })
        }
        rootFrame = FrameLayout(this).apply {
            addView(web, ViewGroup.LayoutParams(-1, -1))
            addView(errorView, ViewGroup.LayoutParams(-1, -1))
            addView(loadingView, ViewGroup.LayoutParams(-1, -1))
            addView(apkBanner, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply { setMargins(dp(12), 0, dp(12), dp(12)) })
        }
        rootColumn = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL; setBackgroundColor(Palette.BG); applySystemInsets()
            addView(topBar, android.widget.LinearLayout.LayoutParams(-1, dp(36)))
            addView(rootFrame, android.widget.LinearLayout.LayoutParams(-1, 0, 1f))
        }
        setContentView(rootColumn)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if (web.canGoBack()) web.goBack() else { isEnabled = false; onBackPressedDispatcher.onBackPressed() } }
        })
        (getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager).registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(n: Network) { if (failed) runOnUiThread { startLocal() } }
        })
        startLocal()
    }

    private fun unreachableMsg() = "Your agent isn't responding.\nTap Retry to restart it."
    private fun wrongPasswordMsg() = "Your agent restarted with new credentials.\nTap Retry."

    /** Start the on-device server (if needed), wait for it to answer, then load the UI. */
    private fun startLocal() {
        if (starting) return
        starting = true; failed = false
        showLoading("Starting your agent…")
        thread {
            if (!LocalServerService.running && checkServer(prefs.localServer()) != CheckResult.OK) prefs.rotatePortIfBusy()
            val s = prefs.localServer()
            LocalServerService.start(this)
            var res = CheckResult.UNREACHABLE
            val deadline = System.currentTimeMillis() + 120_000
            while (System.currentTimeMillis() < deadline) {
                res = checkServer(s)
                if (res == CheckResult.OK) break
                Thread.sleep(1000)
            }
            runOnUiThread {
                starting = false
                server = s
                if (res == CheckResult.OK) { failed = false; errorView.visibility = android.view.View.GONE; loadHome() }
                else showError("Your agent didn't start.\n" + serverLogTail() + "\nTap Retry.")
            }
        }
    }

    /** Last meaningful line(s) of the agent's log, so a failed start explains itself. */
    private fun serverLogTail(): String = runCatching {
        java.io.File(filesDir, "server.log").readLines().map { it.replace(Regex("\u001B\\[[0-9;]*m"), "").trim() }
            .filter { it.isNotEmpty() && !it.startsWith("█") && !it.startsWith("▀") }.takeLast(2).joinToString("\n")
    }.getOrDefault("")

    /** Load a tiny same-origin page first so stale draft tabs can be cleared, then open the prompt screen. */
    private fun loadHome() {
        val s = server ?: return
        pruneThenHome = true
        web.loadUrl(s.url + "/site.webmanifest", mapOf("Authorization" to basicAuth(s.password)))
    }

    /** On-device mode opens straight onto the prompt box of the default project. */
    private fun homeUrl(s: Server): String {
        val dir = android.util.Base64.encodeToString(Sandbox.WORKSPACE.toByteArray(), android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING or android.util.Base64.URL_SAFE)
        return "${s.url}/$dir/session"
    }

    private fun restartLocal() {
        LocalServerService.stop(this)
        thread { Thread.sleep(1500); runOnUiThread { startLocal() } }
    }

    private fun showError(msg: String) { failed = true; hideLoading(immediately = true); showMessage(msg, buttons = true) }

    // ---- Loading screen -------------------------------------------------------------------------

    private val MIN_LOADING_MS = 1600L

    /** Branded loading screen that covers every intermediate page load until the prompt screen is really ready. */
    companion object { @Volatile var visible = false }

    private fun showLoading(caption: String) {
        loadingCaption.text = caption
        if (loadingVisible) return
        loadingVisible = true; awaitingReady = false; loadingShownAt = System.currentTimeMillis()
        errorView.visibility = android.view.View.GONE
        loadingView.animate().cancel(); loadingView.alpha = 1f; loadingView.visibility = android.view.View.VISIBLE
        loadingAnim?.cancel()
        loadingAnim = android.animation.ObjectAnimator.ofFloat(loadingMark, android.view.View.ALPHA, 0.35f, 1f).apply {
            duration = 900; repeatMode = android.animation.ValueAnimator.REVERSE; repeatCount = android.animation.ValueAnimator.INFINITE; start()
        }
    }

    private fun hideLoading(immediately: Boolean = false) {
        if (!loadingVisible) return
        val wait = if (immediately) 0L else (MIN_LOADING_MS - (System.currentTimeMillis() - loadingShownAt)).coerceAtLeast(0)
        loadingView.postDelayed({
            // Two steps so the logo never shows over the page underneath: logo first, then the cover.
            fun finish() { loadingView.visibility = android.view.View.GONE; loadingAnim?.cancel(); loadingMark.alpha = 1f; loadingCaption.alpha = 1f; loadingVisible = false }
            if (immediately) { finish(); return@postDelayed }
            loadingAnim?.cancel()
            loadingMark.animate().alpha(0f).setDuration(120).start()
            loadingCaption.animate().alpha(0f).setDuration(120).withEndAction {
                loadingView.animate().alpha(0f).setDuration(220).withEndAction { finish() }.start()
            }.start()
        }, wait)
    }

    /** The prompt box exists in the page: the screen is really ready, so lift the loading cover. */
    private fun awaitPromptThenHide(attempt: Int = 0) {
        if (!loadingVisible || (attempt == 0 && awaitingReady)) return
        awaitingReady = true
        // Ready = prompt box exists AND its model label has resolved (otherwise "Select model" flashes before the real name).
        val js = "(function(){var f=document.querySelector('form[class*=\"prompt-input\"]');if(!f)return 'no';" +
            "var labels=[].slice.call(f.querySelectorAll('button')).map(function(b){return b.innerText.trim()}).filter(function(t){return t.length>2});" +
            "if(!labels.length)return 'no';return labels.some(function(t){return /Select model/i.test(t)})?'model':'ok'})()"
        web.evaluateJavascript(js) { r ->
            val ready = r == "\"ok\"" || (r == "\"model\"" && attempt > 40) || attempt > 66
            if (ready) { awaitingReady = false; injectDraft(); hideLoading() }
            else web.postDelayed({ awaitingReady = false; awaitPromptThenHide(attempt + 1) }, 150)
        }
    }

    private fun showMessage(msg: String, buttons: Boolean) {
        errorText.text = msg; errorView.visibility = android.view.View.VISIBLE
        val v = if (buttons) android.view.View.VISIBLE else android.view.View.GONE
        retryBtn.visibility = v
    }

    private fun showMenu(anchor: android.view.View) {
        val items = listOf(
            8 to "Preview what was built",
            9 to "Themes",
            2 to "Reload",
            6 to "Restart agent",
            5 to "About",
        )
        showMenu(anchor, items) { id ->
            when (id) {
                2 -> loadHome()
                5 -> startActivity(Intent(this@MainActivity, AboutActivity::class.java))
                6 -> restartLocal()
                8 -> startActivity(Intent(this@MainActivity, PreviewActivity::class.java))
                9 -> showThemes()
            }
        }
    }

    // ---- Themes -------------------------------------------------------------------------------

    private fun showThemes() {
        val themes = parseThemes(prefs.themesJson)
        if (themes.isEmpty()) {
            showThemesWhenReady = true
            web.evaluateJavascript(Branding.THEMES_SCAN_JS, null)
            return
        }
        if (themeDialog?.isShowing == true) return
        themeDialog = ThemeDialog(this, themes, prefs.themeId, prefs.scheme) { id, scheme -> applyTheme(id, scheme) }
            .also { it.setOnDismissListener { themeDialog = null }; it.show() }
    }

    private fun onThemesData(json: String) {
        if (parseThemes(json).isEmpty()) {
            showThemesWhenReady = false
            android.widget.Toast.makeText(this, "Couldn't load themes. Try again once the agent is ready.", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        prefs.themesJson = json
        if (showThemesWhenReady) { showThemesWhenReady = false; showThemes() }
    }

    /** Switches OpenCode's own theme (it reads these keys at startup) and reloads; native screens follow via adoptPalette. */
    private fun applyTheme(id: String, scheme: String) {
        prefs.themeId = id; prefs.scheme = scheme
        val q = { v: String -> "'" + v.replace("\\", "\\\\").replace("'", "\\'") + "'" }
        web.evaluateJavascript(
            "localStorage.setItem('opencode-theme-id',${q(id)});localStorage.setItem('opencode-color-scheme',${q(scheme)});" +
                "localStorage.removeItem('opencode-theme-css-light');localStorage.removeItem('opencode-theme-css-dark');location.reload();", null)
    }

    /** Takes the colors the page reports for the active theme and restyles the native chrome to match. */
    private fun adoptPalette(raw: String?) {
        val json = try { org.json.JSONTokener(raw ?: return).nextValue() as? String ?: return } catch (e: Exception) { return }
        if (json == "{}" || json == prefs.paletteJson) return
        prefs.paletteJson = json
        applyThemeChrome()
        loadingView.setBackgroundColor(Palette.BG); loadingMark.color = Palette.FG; loadingCaption.setTextColor(Palette.FAINT); loadingMark.invalidate()
        rootColumn.setBackgroundColor(Palette.BG); rootFrame.setBackgroundColor(Palette.BG); brandLabel.color = Palette.FG; brandLabel.invalidate(); web.setBackgroundColor(Palette.BG); errorView.setBackgroundColor(Palette.BG)
        errorText.setTextColor(Palette.FG); errTitle.color = Palette.FG; errTitle.invalidate(); menuBtn.setColorFilter(Palette.FG)
        retryBtn.stylePrimary()
        themeDialog?.restyle()
    }

    /** From the "Your app is ready" notification: go straight to Android's install prompt. */
    private fun handleInstallIntent(i: Intent?) {
        val path = i?.getStringExtra("install_path") ?: return
        i.removeExtra("install_path")
        val f = artifactFile(projectsHostRoot(), Sandbox.WORKSPACE, path)
        if (f != null && f.name.endsWith(".apk")) ApkInstall.install(this, f)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleInstallIntent(intent)
        intent.getStringExtra("draft")?.let { pendingDraft = it; if (::web.isInitialized && !starting) { showLoading("Opening the agent…"); loadHome() } }
    }

    /** Types [pendingDraft] into the prompt box (once). The user still has to press send. */
    private fun injectDraft() {
        val text = pendingDraft ?: return
        pendingDraft = null
        val js = "(function(t){var f='form[class*=\"prompt-input\"]';var ed=document.querySelector(f+' [contenteditable=\"true\"], '+f+' textarea');if(!ed)return false;ed.focus();" +
            "if(ed.value!==undefined){var s=Object.getOwnPropertyDescriptor(Object.getPrototypeOf(ed),'value').set;s.call(ed,t);ed.dispatchEvent(new Event('input',{bubbles:true}));}" +
            "else{document.execCommand('selectAll');document.execCommand('insertText',false,t);}return true})(" + org.json.JSONObject.quote(text) + ")"
        web.evaluateJavascript(js, null)
    }

    override fun onStart() { super.onStart(); visible = true }
    override fun onStop() { visible = false; super.onStop() }

    /** A build just produced an APK: offer to install it right here (the service posts a notification when we're not on screen). */
    private fun checkNewApk() {
        thread {
            val root = projectsHostRoot()
            if (prefs.lastApkSeen == 0L) { prefs.lastApkSeen = System.currentTimeMillis(); return@thread }
            val f = newestNewApk(root, prefs.lastApkSeen) ?: return@thread
            val size = f.length(); Thread.sleep(700)
            if (f.length() != size) return@thread
            prefs.lastApkSeen = f.lastModified()
            runOnUiThread { showApkBanner(f) }
        }
    }

    private fun projectsHostRoot() = java.io.File(Sandbox.paths(this).rootfs + Sandbox.WORKSPACE)

    private fun showApkBanner(f: java.io.File) {
        bannerApk = f; apkBannerText.text = "✓ Your app is ready\n${apkTitle(f)}"
        apkBanner.alpha = 0f; apkBanner.visibility = android.view.View.VISIBLE; apkBanner.animate().alpha(1f).setDuration(200).start()
        apkBanner.removeCallbacks(hideBanner); apkBanner.postDelayed(hideBanner, 30_000)
    }
    private val hideBanner = Runnable { hideApkBanner() }
    private fun hideApkBanner() { apkBanner.animate().alpha(0f).setDuration(150).withEndAction { apkBanner.visibility = android.view.View.GONE }.start() }

    override fun onPause() { if (::web.isInitialized) web.removeCallbacks(apkPoll); super.onPause() }

    override fun onResume() {
        super.onResume()
        if (!::web.isInitialized) return
        web.removeCallbacks(apkPoll); web.postDelayed(apkPoll, 1500)
        // If the agent was stopped while we were away, bring it back.
        if (!starting && !failed) thread { if (checkServer(prefs.localServer()) != CheckResult.OK) runOnUiThread { startLocal() } }
    }
}
