package com.mobilecode.app

import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlin.concurrent.thread

class ConnectActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private lateinit var store: ServerStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = ServerStore(this)
        val url = field("Server address (https://…)")
        val pw = field("Password", password = true)
        status = label("", 13f, Palette.ACCENT, mono = true)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(24))
            setBackgroundColor(Palette.BG); applySystemInsets()
            addView(label("Mobilecode", 30f, mono = true))
            addView(label("Prompt your coding agent from anywhere.", 14f, Palette.MUTED).apply { setPadding(0, dp(4), 0, dp(28)) })
            addView(primaryButton("Scan to connect") { scan() }, LinearLayout.LayoutParams(-1, dp(52)))
            addView(label("or enter manually", 12f, Palette.MUTED, mono = true).apply { setPadding(0, dp(28), 0, dp(8)) })
            addView(url, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
            addView(pw, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
            addView(ghostButton("Connect") {
                when (val n = normalizeUrl(url.text.toString())) {
                    is PairResult.Ok -> connect(Server(n.server.url, pw.text.toString()))
                    is PairResult.Invalid -> status.text = n.reason
                }
            }, LinearLayout.LayoutParams(-1, dp(48)))
            addView(status.apply { setPadding(0, dp(16), 0, 0) })
            addView(label("Powered by OpenCode (MIT)", 11f, Palette.MUTED, mono = true).apply { setPadding(0, dp(32), 0, 0) })
        }
        setContentView(col)
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); handleIntent(intent) }

    private fun handleIntent(i: Intent?) {
        val data = i?.dataString ?: return
        handlePairing(data)
    }

    private fun scan() {
        GmsBarcodeScanning.getClient(this).startScan()
            .addOnSuccessListener { handlePairing(it.rawValue ?: "") }
            .addOnFailureListener { status.text = "Scan unavailable: ${it.message}. Enter the address manually." }
    }

    private fun handlePairing(raw: String) {
        when (val r = parsePairing(raw)) {
            is PairResult.Ok -> connect(r.server)
            is PairResult.Invalid -> status.text = r.reason
        }
    }

    private fun connect(server: Server) {
        status.text = "Connecting…"
        thread {
            val res = checkServer(server)
            runOnUiThread {
                when (res) {
                    CheckResult.OK -> {
                        store.save(server)
                        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
                        finish()
                    }
                    CheckResult.WRONG_PASSWORD -> status.text = "Wrong password."
                    CheckResult.UNREACHABLE -> status.text = "Server unreachable. Check it is running and the address is right."
                }
            }
        }
    }
}
