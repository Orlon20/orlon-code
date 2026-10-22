package com.mobilecode.app

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.webkit.WebViewAssetLoader
import java.io.ByteArrayInputStream
import java.io.File
import java.net.URLEncoder

/** Lets the user review what the agent built: project files as a website, or any local dev-server port. */
class PreviewActivity : AppCompatActivity() {
    private lateinit var web: WebView
    private lateinit var bar: EditText

    private fun projectRoot() = File(Sandbox.paths(this).rootfs + Sandbox.WORKSPACE)

    private inner class ProjectFiles : WebViewAssetLoader.PathHandler {
        override fun handle(path: String): WebResourceResponse? {
            val f = resolveInside(projectRoot(), path) ?: return text(403, "Forbidden", "Outside the project folder")
            return when {
                f.isFile -> WebResourceResponse(mimeFor(f.name), null, f.inputStream())
                f.isDirectory -> File(f, "index.html").takeIf { it.isFile }
                    ?.let { WebResourceResponse("text/html", null, it.inputStream()) } ?: listing(f, path)
                else -> text(404, "Not Found", "Nothing at /$path")
            }
        }

        private fun text(code: Int, reason: String, body: String) =
            WebResourceResponse("text/plain", "utf-8", code, reason, emptyMap(), ByteArrayInputStream(body.toByteArray()))

        private fun listing(dir: File, path: String): WebResourceResponse {
            val base = "/p/" + path.trim('/').let { if (it.isEmpty()) "" else "$it/" }
            val rows = (dir.listFiles() ?: emptyArray()).filter { !it.name.startsWith(".") }
                .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                .joinToString("") {
                    val href = base + URLEncoder.encode(it.name, "UTF-8").replace("+", "%20") + if (it.isDirectory) "/" else ""
                    "<li><a href=\"$href\">${android.text.TextUtils.htmlEncode(it.name)}${if (it.isDirectory) "/" else ""}</a></li>"
                }
            val html = "<!doctype html><meta name=viewport content='width=device-width,initial-scale=1'>" +
                "<body style='background:#0a0a0a;color:#ededed;font:16px monospace;padding:16px'>" +
                "<h3>${android.text.TextUtils.htmlEncode(Sandbox.WORKSPACE + "/" + path.trim('/'))}</h3>" +
                (if (rows.isEmpty()) "<p>Nothing here yet. Ask the agent to build something.</p>" else "<ul style='line-height:2'>$rows</ul>") +
                "<style>a{color:#fab283}</style>"
            return WebResourceResponse("text/html", "utf-8", ByteArrayInputStream(html.toByteArray()))
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyThemeChrome()
        val loader = WebViewAssetLoader.Builder().setDomain("appassets.androidplatform.net").addPathHandler("/p/", ProjectFiles()).build()
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) WebView.setWebContentsDebuggingEnabled(true)
        web = WebView(this).apply {
            setBackgroundColor(0xFFFFFFFF.toInt())
            settings.javaScriptEnabled = true; settings.domStorageEnabled = true
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            settings.useWideViewPort = true; settings.loadWithOverviewMode = true
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(v: WebView, r: WebResourceRequest) = loader.shouldInterceptRequest(r.url)
                override fun onPageFinished(v: WebView, url: String?) { if (!bar.hasFocus()) bar.setText(display(url)) }
            }
        }
        bar = EditText(this).apply {
            hint = "file or port (e.g. 5173)"; setHintTextColor(Palette.FAINT); setTextColor(Palette.FG)
            typeface = android.graphics.Typeface.SANS_SERIF; setSingleLine(); textSize = 14f
            imeOptions = EditorInfo.IME_ACTION_GO; background = android.graphics.drawable.GradientDrawable().apply { setColor(Palette.CARD); cornerRadius = dp(8).toFloat(); setStroke(dp(1), Palette.BORDER) }; setPadding(dp(12), 0, dp(12), 0)
            setOnEditorActionListener { _, _, _ -> go(text.toString()); clearFocus(); true }
        }
        fun btn(t: String, f: () -> Unit) = TextView(this).apply {
            text = t; textSize = 20f; setTextColor(Palette.FG); gravity = android.view.Gravity.CENTER; setOnClickListener { f() }
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(Palette.BG); applySystemInsets()
            addView(LinearLayout(this@PreviewActivity).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL
                addView(btn("←") { finish() }, LinearLayout.LayoutParams(dp(48), dp(48)))
                addView(bar, LinearLayout.LayoutParams(0, dp(40), 1f))
                addView(btn("⟳") { web.reload() }, LinearLayout.LayoutParams(dp(48), dp(48)))
            })
            addView(web, LinearLayout.LayoutParams(-1, 0, 1f))
        })
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if (web.canGoBack()) web.goBack() else finish() }
        })
        go(intent.getStringExtra("target") ?: "")
    }

    private fun go(input: String) = web.loadUrl(previewUrl(input))

    private fun display(url: String?): String {
        val p = "${Preview.ASSET_ORIGIN}/p/"
        return when { url == null -> ""; url.startsWith(p) -> Uri.decode(url.removePrefix(p)); else -> url }
    }
}
