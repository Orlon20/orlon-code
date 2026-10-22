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
    private lateinit var store: ServerStore
    private lateinit var web: WebView
    private val refresh = object { var isRefreshing = false }
    private lateinit var errorView: android.widget.LinearLayout
    private lateinit var errorText: TextView
    private var server: Server? = null
    private var keepAwake = false
    private var fileCb: ValueCallback<Array<Uri>>? = null
    private var failed = false
    private lateinit var prefs: Prefs
    private lateinit var retryBtn: android.widget.Button
    private lateinit var altBtn: android.widget.Button
    private var starting = false
    private var pruneThenHome = false
    private lateinit var rootFrame: FrameLayout
    private lateinit var menuBtn: TextView
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

    /** Page -> app calls (only these two; the app never exposes anything else to web content). */
    private inner class Bridge {
        @JavascriptInterface fun openThemes() { runOnUiThread { showThemes() } }
        @JavascriptInterface fun themesData(json: String) { runOnUiThread { onThemesData(json) } }
    }
    private val local get() = !prefs.remote
    private val notifPerm = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }

    private val picker = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) {
        fileCb?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(it.resultCode, it.data)); fileCb = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyThemeChrome()
        store = ServerStore(this)
        prefs = Prefs(this)
        if (local) {
            if (!Sandbox.isInstalled(Sandbox.paths(this))) { startActivity(Intent(this, SetupActivity::class.java)); finish(); return }
            server = prefs.localServer()
            if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                notifPerm.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            server = store.active()
            if (server == null) { startActivity(Intent(this, ConnectActivity::class.java)); finish(); return }
        }

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
                    refresh.isRefreshing = false
                    if (pruneThenHome) {
                        pruneThenHome = false
                        v.evaluateJavascript(Branding.prepStateJs(if (local) Sandbox.WORKSPACE else null)) { v.loadUrl(homeUrl(server!!), mapOf("Authorization" to basicAuth(server!!.password))) }
                        return
                    }
                    if (!failed) { if (loadingVisible) awaitPromptThenHide() else errorView.visibility = android.view.View.GONE; v.evaluateJavascript(Branding.SWAP_JS, null); v.evaluateJavascript(Branding.logoSwapJs(), null); v.evaluateJavascript(Branding.FIT_JS, null); v.evaluateJavascript(Branding.NO_PROVIDER_BLOCK_JS, null); v.evaluateJavascript(Branding.SLASH_THEMES_JS, null); v.postDelayed({ v.evaluateJavascript(Branding.THEME_COLORS_JS) { raw -> adoptPalette(raw) } }, 700); if (local) v.evaluateJavascript(Branding.CLEAN_JS, null) }
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
            retryBtn = primaryButton("Retry") { if (local) restartLocal() else load() }
            altBtn = ghostButton(if (local) "Connect to a PC instead" else "Rescan / change server") { startActivity(Intent(this@MainActivity, ConnectActivity::class.java)) }
            addView(retryBtn, android.widget.LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(24) })
            addView(altBtn, android.widget.LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8) })
        }
        menuBtn = TextView(this).apply {
            text = "⋮"; textSize = 20f; setTextColor(Palette.FG); gravity = Gravity.CENTER; alpha = 0.7f
            setOnClickListener { showMenu(it) }
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
        rootFrame = FrameLayout(this).apply {
            addView(web, ViewGroup.LayoutParams(-1, -1))
            addView(errorView, ViewGroup.LayoutParams(-1, -1))
            addView(loadingView, ViewGroup.LayoutParams(-1, -1))
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
            override fun onAvailable(n: Network) { if (failed) runOnUiThread { load() } }
        })
        load()
    }

    private fun unreachableMsg() = if (local) "Your agent isn't responding.\nTap Retry to restart it." else "Server unreachable.\nCheck it is running, or rescan the code."
    private fun wrongPasswordMsg() = if (local) "Your agent restarted with new credentials.\nTap Retry." else "Wrong password or the session changed.\nRescan the code."

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

    private fun load() {
        if (local) { startLocal(); return }
        val s = server ?: return
        failed = false; errorView.visibility = android.view.View.GONE; showLoading("Connecting…")
        // Pre-check so 401 / unreachable get distinct messages even when the WebView hides them.
        thread {
            val res = checkServer(s)
            runOnUiThread {
                when (res) {
                    CheckResult.OK -> web.loadUrl(s.url + "/", mapOf("Authorization" to basicAuth(s.password)))
                    CheckResult.WRONG_PASSWORD -> showError(wrongPasswordMsg())
                    CheckResult.UNREACHABLE -> showError(unreachableMsg())
                }
            }
        }
    }

    private fun showError(msg: String) { failed = true; hideLoading(immediately = true); showMessage(msg, buttons = true) }

    // ---- Loading screen -------------------------------------------------------------------------

    private val MIN_LOADING_MS = 1600L

    /** Branded loading screen that covers every intermediate page load until the prompt screen is really ready. */
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
            if (ready) { awaitingReady = false; hideLoading() }
            else web.postDelayed({ awaitingReady = false; awaitPromptThenHide(attempt + 1) }, 150)
        }
    }

    private fun showMessage(msg: String, buttons: Boolean) {
        refresh.isRefreshing = false
        errorText.text = msg; errorView.visibility = android.view.View.VISIBLE
        val v = if (buttons) android.view.View.VISIBLE else android.view.View.GONE
        retryBtn.visibility = v; altBtn.visibility = v
    }

    private fun showMenu(anchor: android.view.View) {
        val items = mutableListOf<Pair<Int, String>>()
        items += 1 to (if (keepAwake) "Keep screen awake ✓" else "Keep screen awake")
        if (local) items += 8 to "Preview what was built"
        items += 9 to "Themes"
        items += 2 to "Reload"
        if (local) { items += 6 to "Restart agent"; items += 3 to "Connect to a PC instead" }
        else { items += 3 to "Switch / add server"; items += 4 to "Forget this server"; items += 7 to "Use on-device agent" }
        items += 5 to "About"
        showMenu(anchor, items) { id ->
            when (id) {
                1 -> { keepAwake = !keepAwake; if (keepAwake) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
                2 -> if (local) loadHome() else load()
                3 -> startActivity(Intent(this@MainActivity, ConnectActivity::class.java))
                4 -> { store.forget(server!!.url); startActivity(Intent(this@MainActivity, ConnectActivity::class.java)); finish() }
                5 -> startActivity(Intent(this@MainActivity, AboutActivity::class.java))
                6 -> restartLocal()
                7 -> { prefs.remote = false; recreate() }
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
        errorText.setTextColor(Palette.FG); errTitle.color = Palette.FG; errTitle.invalidate(); menuBtn.setTextColor(Palette.FG)
        retryBtn.stylePrimary(); altBtn.styleGhost()
        themeDialog?.restyle()
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent) }

    override fun onResume() {
        super.onResume()
        if (local) {
            if (!starting && !failed && ::web.isInitialized) thread { if (checkServer(prefs.localServer()) != CheckResult.OK) runOnUiThread { startLocal() } }
            return
        }
        val a = store.active()
        if (a != null && a != server) { server = a; load() } else if (failed) load()
    }
}
