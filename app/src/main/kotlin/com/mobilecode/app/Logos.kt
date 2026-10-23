package com.mobilecode.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Brand logos shipped with the app (assets/logos, from models.dev), so the model icons also show offline and on the first launch.
 * They are handed to the page's logo cache before it looks for them.
 */
object Logos {
    /** viewBox and inner markup of a simple SVG, or null if it is not one. Scripts are dropped. */
    fun parse(svg: String): Pair<String, String>? {
        val open = Regex("<svg\\b[^>]*>", RegexOption.IGNORE_CASE).find(svg) ?: return null
        val end = svg.lastIndexOf("</svg>")
        if (end < open.range.last) return null
        val viewBox = Regex("viewBox=\"([^\"]+)\"").find(open.value)?.groupValues?.get(1) ?: "0 0 24 24"
        val inner = svg.substring(open.range.last + 1, end).replace(Regex("<script\\b.*?</script>", RegexOption.DOT_MATCHES_ALL), "")
        return viewBox to inner.trim()
    }

    /** JavaScript that seeds the page's logo cache (only keys that are missing) and lists the available logo ids. */
    fun seedJs(ctx: Context): String {
        val ids = JSONArray(); val data = JSONObject()
        for (f in ctx.assets.list("logos").orEmpty().filter { it.endsWith(".svg") }) {
            val id = f.removeSuffix(".svg")
            val (vb, html) = parse(ctx.assets.open("logos/$f").bufferedReader().readText()) ?: continue
            ids.put(id); data.put(id, JSONObject().put("vb", vb).put("html", html))
        }
        return "(function(){try{window.__mcLogoIds=$ids;var d=$data;Object.keys(d).forEach(function(k){if(!localStorage.getItem('mc-logo-'+k))localStorage.setItem('mc-logo-'+k,JSON.stringify(d[k]))})}catch(e){}})();"
    }
}
