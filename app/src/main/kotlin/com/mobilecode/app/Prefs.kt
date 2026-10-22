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

    fun localServer() = Server("http://127.0.0.1:$localPort", localPassword)
}
