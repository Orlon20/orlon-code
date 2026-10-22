package com.mobilecode.app

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject

class ServerStore(context: Context) {
    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context, "mobilecode_servers",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun all(): List<Server> {
        val arr = JSONArray(prefs.getString("servers", "[]"))
        return (0 until arr.length()).map { arr.getJSONObject(it).let { o -> Server(o.getString("url"), o.getString("password")) } }
    }

    private fun write(list: List<Server>) {
        val arr = JSONArray(); list.forEach { arr.put(JSONObject().put("url", it.url).put("password", it.password)) }
        prefs.edit().putString("servers", arr.toString()).apply()
    }

    fun save(server: Server) {
        write(all().filter { it.url != server.url } + server)
        setActive(server.url)
    }

    fun active(): Server? = all().firstOrNull { it.url == prefs.getString("active", null) }

    fun setActive(url: String) { prefs.edit().putString("active", url).apply() }

    fun forget(url: String) {
        write(all().filter { it.url != url })
        if (prefs.getString("active", null) == url) prefs.edit().remove("active").apply()
    }
}
