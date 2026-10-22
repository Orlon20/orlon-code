package com.mobilecode.app

import android.content.Context
import java.net.ServerSocket
import java.security.SecureRandom

/** Non-secret-ish app state. The local server password is private to this app's sandbox. */
class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("mobilecode", Context.MODE_PRIVATE)

    var remote: Boolean
        get() = sp.getBoolean("remote", false)
        set(v) { sp.edit().putBoolean("remote", v).apply() }

    val localPassword: String get() = sp.getString("lpw", null) ?: ByteArray(16).also { SecureRandom().nextBytes(it) }
        .joinToString("") { "%02x".format(it) }.also { sp.edit().putString("lpw", it).apply() }

    val localPort: Int get() = sp.getInt("lport", 0).takeIf { it > 0 }
        ?: ServerSocket(0).use { it.localPort }.also { sp.edit().putInt("lport", it).apply() }

    /** Selected OpenCode theme id ("oc-2" is OpenCode's default) and color scheme ("system" | "light" | "dark"). */
    var themeId: String
        get() = sp.getString("theme", "oc-2") ?: "oc-2"
        set(v) { sp.edit().putString("theme", v).apply() }
    var scheme: String
        get() = sp.getString("scheme", "system") ?: "system"
        set(v) { sp.edit().putString("scheme", v).apply() }
    /** Colors captured from the page's --v2-* variables, so native screens match the chosen theme. */
    var paletteJson: String
        get() = sp.getString("palette", "{}") ?: "{}"
        set(v) { sp.edit().putString("palette", v).apply() }
    /** Theme list scraped from OpenCode's bundle (cached per OpenCode version). */
    var themesJson: String
        get() = if (sp.getString("themesVer", "") == Sandbox.OPENCODE_VERSION) sp.getString("themes", "") ?: "" else ""
        set(v) { sp.edit().putString("themes", v).putString("themesVer", Sandbox.OPENCODE_VERSION).apply() }

    fun localServer() = Server("http://127.0.0.1:$localPort", localPassword)
}
