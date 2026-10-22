package com.mobilecode.app

import android.util.Base64
import java.net.HttpURLConnection
import java.net.URL

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
