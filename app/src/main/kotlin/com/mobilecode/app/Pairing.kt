package com.mobilecode.app

import java.net.URI
import java.net.URLDecoder

data class Server(val url: String, val password: String)

sealed class PairResult {
    data class Ok(val server: Server) : PairResult()
    data class Invalid(val reason: String) : PairResult()
}

fun isPrivateHost(host: String): Boolean {
    val h = host.lowercase().trim('[', ']')
    if (h == "localhost" || h == "::1" || h.endsWith(".local") || h.endsWith(".localhost")) return true
    val p = h.split(".").map { it.toIntOrNull() ?: return false }
    if (p.size != 4 || p.any { it !in 0..255 }) return false
    return p[0] == 10 || p[0] == 127 ||
        (p[0] == 172 && p[1] in 16..31) ||
        (p[0] == 192 && p[1] == 168) ||
        (p[0] == 100 && p[1] in 64..127)
}

private fun invalid(r: String) = PairResult.Invalid(r)

/** Normalizes a server URL; the returned Ok carries an empty password. */
fun normalizeUrl(raw: String): PairResult {
    var s = raw.trim()
    if (s.isEmpty()) return invalid("Empty address")
    val scheme = Regex("^([a-zA-Z][a-zA-Z0-9+.-]*):").find(s)?.groupValues?.get(1)?.lowercase()
    val hasAuthority = s.contains("://")
    if (scheme != null && !(hasAuthority && (scheme == "http" || scheme == "https")) && !Regex("^[^/]+:\\d+").containsMatchIn(s)) {
        return invalid("Unsupported address")
    }
    if (!hasAuthority) {
        val host = s.substringBefore("/").substringBeforeLast(":").trim('[', ']')
        s = (if (isPrivateHost(host)) "http://" else "https://") + s
    }
    val uri = try { URI(s) } catch (e: Exception) { return invalid("Malformed address") }
    val host = uri.host ?: return invalid("Malformed address")
    if (uri.scheme == "http" && !isPrivateHost(host)) {
        return invalid("Public servers must use https")
    }
    val port = if (uri.port == -1) "" else ":${uri.port}"
    val path = (uri.rawPath ?: "").trimEnd('/')
    return PairResult.Ok(Server("${uri.scheme}://$host$port$path", ""))
}

fun parsePairing(input: String): PairResult {
    val s = input.trim()
    if (!s.startsWith("mobilecode://connect?")) return invalid("Not an Orlon Code pairing code")
    val params = s.substringAfter("?").split("&").mapNotNull {
        val i = it.indexOf('=')
        if (i < 0) null else it.substring(0, i) to URLDecoder.decode(it.substring(i + 1), "UTF-8")
    }.toMap()
    val url = params["url"] ?: return invalid("Missing server address")
    val password = params["password"]
    if (password.isNullOrEmpty()) return invalid("Missing password")
    return when (val n = normalizeUrl(url)) {
        is PairResult.Ok -> PairResult.Ok(Server(n.server.url, password))
        is PairResult.Invalid -> n
    }
}
