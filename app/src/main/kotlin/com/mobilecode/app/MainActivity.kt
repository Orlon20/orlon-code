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
import android.widget.FrameLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    private lateinit var store: ServerStore
    private lateinit var web: WebView
    private lateinit var refresh: SwipeRefreshLayout
    private lateinit var errorView: android.widget.LinearLayout
    private lateinit var errorText: TextView
    private var server: Server? = null
    private var keepAwake = false
    private var fileCb: ValueCallback<Array<Uri>>? = null
    private var failed = false

    private val picker = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) {
        fileCb?.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(it.resultCode, it.data)); fileCb = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = ServerStore(this)
        server = store.active()
        if (server == null) { startActivity(Intent(this, ConnectActivity::class.java)); finish(); return }

        web = WebView(this).apply {
            setBackgroundColor(Palette.BG)
            settings.javaScriptEnabled = true; settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            webViewClient = object : WebViewClient() {
                override fun onReceivedHttpAuthRequest(v: WebView, h: HttpAuthHandler, host: String?, realm: String?) {
                    h.proceed(AUTH_USER, server!!.password)
                }
                override fun onPageFinished(v: WebView, url: String?) {
                    refresh.isRefreshing = false
                    if (!failed) { errorView.visibility = android.view.View.GONE; v.evaluateJavascript(Branding.SWAP_JS, null) }
                }
                override fun onReceivedError(v: WebView, r: WebResourceRequest, e: WebResourceError) {
                    if (r.isForMainFrame) showError("Server unreachable.\nCheck it is running, or rescan the code.")
                }
                override fun onReceivedHttpError(v: WebView, r: WebResourceRequest, e: WebResourceResponse) {
                    if (r.isForMainFrame && e.statusCode == 401) showError("Wrong password or the session changed.\nRescan the code.")
                }
                override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                    val host = Uri.parse(server!!.url).host
                    if (r.url.host == host) return false
                    startActivity(Intent(Intent.ACTION_VIEW, r.url)); return true
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onShowFileChooser(w: WebView, cb: ValueCallback<Array<Uri>>, p: FileChooserParams): Boolean {
                    fileCb?.onReceiveValue(null); fileCb = cb
                    return try { picker.launch(p.createIntent()); true } catch (e: Exception) { fileCb = null; false }
                }
            }
        }
        refresh = SwipeRefreshLayout(this).apply {
            setColorSchemeColors(Palette.ACCENT); setProgressBackgroundColorSchemeColor(Palette.CARD)
            addView(web); setOnRefreshListener { load() }
            // Only allow pull-to-refresh at the very top of the page.
            setOnChildScrollUpCallback { _, _ -> web.scrollY > 0 }
        }
        errorText = label("", 15f, Palette.FG, mono = true).apply { gravity = Gravity.CENTER }
        errorView = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL; gravity = Gravity.CENTER
            setBackgroundColor(Palette.BG); visibility = android.view.View.GONE; setPadding(dp(24), 0, dp(24), 0)
            addView(label("Mobilecode", 24f, mono = true).apply { setPadding(0, 0, 0, dp(16)) })
            addView(errorText)
            addView(primaryButton("Retry") { load() }, android.widget.LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(24) })
            addView(ghostButton("Rescan / change server") { startActivity(Intent(this@MainActivity, ConnectActivity::class.java)) },
                android.widget.LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(8) })
        }
        val menu = TextView(this).apply {
            text = "⋮"; textSize = 20f; setTextColor(Palette.FG); gravity = Gravity.CENTER; alpha = 0.55f
            setOnClickListener { showMenu(it) }
        }
        setContentView(FrameLayout(this).apply {
            setBackgroundColor(Palette.BG); applySystemInsets()
            addView(refresh, ViewGroup.LayoutParams(-1, -1))
            addView(errorView, ViewGroup.LayoutParams(-1, -1))
            addView(menu, FrameLayout.LayoutParams(dp(36), dp(36), Gravity.TOP or Gravity.END))
        })
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if (web.canGoBack()) web.goBack() else { isEnabled = false; onBackPressedDispatcher.onBackPressed() } }
        })
        (getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager).registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(n: Network) { if (failed) runOnUiThread { load() } }
        })
        load()
    }

    private fun load() {
        val s = server ?: return
        failed = false; errorView.visibility = android.view.View.GONE
        // Pre-check so 401 / unreachable get distinct messages even when the WebView hides them.
        thread {
            val res = checkServer(s)
            runOnUiThread {
                when (res) {
                    CheckResult.OK -> web.loadUrl(s.url + "/", mapOf("Authorization" to basicAuth(s.password)))
                    CheckResult.WRONG_PASSWORD -> showError("Wrong password or the session changed.\nRescan the code.")
                    CheckResult.UNREACHABLE -> showError("Server unreachable.\nCheck it is running, or rescan the code.")
                }
            }
        }
    }

    private fun showError(msg: String) {
        failed = true; refresh.isRefreshing = false
        errorText.text = msg; errorView.visibility = android.view.View.VISIBLE
    }

    private fun showMenu(anchor: android.view.View) {
        PopupMenu(this, anchor).apply {
            menu.add(0, 1, 0, if (keepAwake) "Keep screen awake ✓" else "Keep screen awake")
            menu.add(0, 2, 1, "Reload")
            menu.add(0, 3, 2, "Switch / add server")
            menu.add(0, 4, 3, "Forget this server")
            menu.add(0, 5, 4, "About")
            setOnMenuItemClickListener {
                when (it.itemId) {
                    1 -> { keepAwake = !keepAwake; if (keepAwake) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
                    2 -> load()
                    3 -> startActivity(Intent(this@MainActivity, ConnectActivity::class.java))
                    4 -> { store.forget(server!!.url); startActivity(Intent(this@MainActivity, ConnectActivity::class.java)); finish() }
                    5 -> startActivity(Intent(this@MainActivity, AboutActivity::class.java))
                }; true
            }
            show()
        }
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent) }

    override fun onResume() {
        super.onResume()
        val a = store.active()
        if (a != null && a != server) { server = a; load() } else if (failed) load()
    }
}
