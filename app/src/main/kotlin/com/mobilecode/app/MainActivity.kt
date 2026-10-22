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
    private lateinit var errTitle: TextView
    private var themeDialog: ThemeDialog? = null
    private var showThemesWhenReady = false

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
                        v.evaluateJavascript(Branding.PRUNE_DRAFTS_JS) { v.loadUrl(homeUrl(server!!), mapOf("Authorization" to basicAuth(server!!.password))) }
                        return
                    }
                    if (!failed) { errorView.visibility = android.view.View.GONE; v.evaluateJavascript(Branding.SWAP_JS, null); v.evaluateJavascript(Branding.FIT_JS, null); v.evaluateJavascript(Branding.NO_PROVIDER_BLOCK_JS, null); v.evaluateJavascript(Branding.SLASH_THEMES_JS, null); v.postDelayed({ v.evaluateJavascript(Branding.THEME_COLORS_JS) { raw -> adoptPalette(raw) } }, 700); if (local) v.evaluateJavascript(Branding.CLEAN_JS, null) }
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
                    startActivity(Intent(Intent.ACTION_VIEW, r.url)); return true
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
            errTitle = label("Mobilecode", 24f, bold = true).apply { setPadding(0, 0, 0, dp(16)) }
            addView(errTitle)
            addView(errorText)
            retryBtn = primaryButton("Retry") { if (local) restartLocal() else load() }
            altBtn = ghostButton(if (local) "Connect to a PC instead" else "Rescan / change server") { startActivity(Intent(this@MainActivity, ConnectActivity::class.java)) }
            addView(retryBtn, android.widget.LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(24) })
            addView(altBtn, android.widget.LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8) })
        }
        menuBtn = TextView(this).apply {
            text = "⋮"; textSize = 20f; setTextColor(Palette.FG); gravity = Gravity.CENTER; alpha = 0.55f
            setOnClickListener { showMenu(it) }
        }
        rootFrame = FrameLayout(this).apply {
            setBackgroundColor(Palette.BG); applySystemInsets()
            addView(web, ViewGroup.LayoutParams(-1, -1))
            addView(errorView, ViewGroup.LayoutParams(-1, -1))
            addView(menuBtn, FrameLayout.LayoutParams(dp(36), dp(36), Gravity.TOP or Gravity.END))
        }
        setContentView(rootFrame)
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
        showMessage("Starting your agent…", buttons = false)
        LocalServerService.start(this)
        val s = prefs.localServer()
        thread {
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
                else showError("Your agent didn't start.\nTap Retry, or see server.log in the app files.")
            }
        }
    }

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
        failed = false; errorView.visibility = android.view.View.GONE
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

    private fun showError(msg: String) { failed = true; showMessage(msg, buttons = true) }

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
        rootFrame.setBackgroundColor(Palette.BG); web.setBackgroundColor(Palette.BG); errorView.setBackgroundColor(Palette.BG)
        errorText.setTextColor(Palette.FG); errTitle.setTextColor(Palette.FG); menuBtn.setTextColor(Palette.FG)
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
