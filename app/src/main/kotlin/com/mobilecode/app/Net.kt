package com.mobilecode.app

import android.util.Base64
import java.net.HttpURLConnection
import java.net.URL

data class Server(val url: String, val password: String)

const val AUTH_USER = "opencode"

fun basicAuth(password: String) =
    "Basic " + Base64.encodeToString("$AUTH_USER:$password".toByteArray(), Base64.NO_WRAP)

enum class CheckResult { OK, WRONG_PASSWORD, UNREACHABLE }

/** Blocking; call off the main thread. */
fun checkServer(server: Server): CheckResult = try {
    val c = URL(server.url + "/").openConnection() as HttpURLConnection
    c.connectTimeout = 8000; c.readTimeout = 8000; c.instanceFollowRedirects = false
    c.setRequestProperty("Authorization", basicAuth(server.password))
    val code = c.responseCode; c.disconnect()
    when { code == 401 || code == 403 -> CheckResult.WRONG_PASSWORD; code in 200..399 -> CheckResult.OK; else -> CheckResult.UNREACHABLE }
} catch (e: Exception) { CheckResult.UNREACHABLE }

/** True if OpenCode's `/session/status` JSON says any session is working (or retrying). Anything unreadable counts as idle. */
fun isAgentBusy(json: String): Boolean = try {
    val o = org.json.JSONObject(json)
    o.keys().asSequence().any { o.optJSONObject(it)?.optString("type") in setOf("busy", "retry") }
} catch (e: Exception) { false }

/** Asks the local server whether the agent is working. Blocking; false on any error. */
fun fetchAgentBusy(server: Server): Boolean = try {
    val c = URL(server.url + "/session/status").openConnection() as HttpURLConnection
    c.connectTimeout = 2000; c.readTimeout = 2000; c.setRequestProperty("Authorization", basicAuth(server.password))
    val body = c.inputStream.bufferedReader().readText(); c.disconnect(); isAgentBusy(body)
} catch (e: Exception) { false }
