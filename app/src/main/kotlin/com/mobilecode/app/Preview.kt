package com.mobilecode.app

import java.io.File

/** Pure helpers behind the Preview screen. */
object Preview {
    const val ASSET_ORIGIN = "https://appassets.androidplatform.net"
}

/** Turns what the user typed into a URL: a local dev-server port, an http(s) URL, or a file in the project. */
fun previewUrl(input: String): String {
    val s = input.trim()
    if (s.isEmpty()) return "${Preview.ASSET_ORIGIN}/p/"
    if (s.startsWith("http://") || s.startsWith("https://")) return s
    if (Regex("^\\d{2,5}$").matches(s)) return "http://localhost:$s"
    if (s.startsWith(":") && Regex("^:\\d{2,5}(/.*)?$").matches(s)) return "http://localhost$s"
    if (Regex("^(localhost|127\\.0\\.0\\.1)(:\\d+)?(/.*)?$").matches(s)) return "http://$s"
    return "${Preview.ASSET_ORIGIN}/p/" + s.trimStart('/')
}

/** Resolves [rel] under [root], or null if it would escape it (".." tricks, sibling dirs sharing a prefix). */
fun resolveInside(root: File, rel: String): File? {
    val base = root.canonicalFile
    val f = File(base, rel).canonicalFile
    return if (f == base || f.path.startsWith(base.path + File.separator)) f else null
}

fun mimeFor(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
    "html", "htm" -> "text/html"; "js", "mjs" -> "text/javascript"; "css" -> "text/css"; "json" -> "application/json"
    "svg" -> "image/svg+xml"; "wasm" -> "application/wasm"; "png" -> "image/png"; "jpg", "jpeg" -> "image/jpeg"
    "gif" -> "image/gif"; "webp" -> "image/webp"; "ico" -> "image/x-icon"; "txt", "md", "log" -> "text/plain"
    "woff2" -> "font/woff2"; "woff" -> "font/woff"; "mp3" -> "audio/mpeg"; "mp4" -> "video/mp4"
    else -> "application/octet-stream"
}
