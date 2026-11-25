package com.nguyenhieu.quicknotes

import android.text.Html
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.Charset
import java.util.Locale

object LinkPreviewFetcher {

    private const val MAX_BYTES = 110 * 1024
    private const val CONNECT_TIMEOUT_MS = 5500
    private const val READ_TIMEOUT_MS = 6500
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; SM-S926B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36"

    private val YT_ID_REGEX = Regex(
        """(?:youtube\.com/(?:watch\?(?:.*&)?v=|shorts/|embed/|live/)|youtu\.be/)([A-Za-z0-9_-]{11})""",
        RegexOption.IGNORE_CASE
    )
    private val META_TAG_REGEX = Regex("""<meta\b[^>]*>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val LINK_TAG_REGEX = Regex("""<link\b[^>]*>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val TITLE_TAG_REGEX = Regex("""<title\b[^>]*>(.*?)</title>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val ATTR_REGEX = Regex("""([a-zA-Z0-9_:-]+)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'=<>`]+))""")
    private val CHARSET_REGEX = Regex("""charset\s*=\s*["']?([a-zA-Z0-9._-]+)""", RegexOption.IGNORE_CASE)

    fun extractHost(url: String): String {
        return try {
            val host = URL(url).host.orEmpty().lowercase(Locale.ROOT)
            host.removePrefix("www.").removePrefix("m.").ifBlank { url }
        } catch (_: Exception) {
            url
        }
    }

    fun googleFaviconUrl(url: String): String {
        val host = extractHost(url)
        if (host.isBlank() || host.contains("/")) return ""
        return "https://www.google.com/s2/favicons?domain=${URLEncoder.encode(host, "UTF-8")}&sz=64"
    }

    fun buildFallback(url: String): JSONObject {
        val host = extractHost(url)
        return JSONObject().apply {
            put("url", url)
            put("title", host)
            put("description", "")
            put("site", host)
            put("image", "")
            put("favicon", googleFaviconUrl(url))
            put("fetchedAt", System.currentTimeMillis())
        }
    }

    fun fetch(rawUrl: String): JSONObject {
        val url = rawUrl.trim()
        if (url.isEmpty()) return buildFallback(url)

        // 1. Fast-path YouTube via official oEmbed (fast, accurate title & thumbnail, no anti-bot block)
        val ytMatch = YT_ID_REGEX.find(url)
        if (ytMatch != null) {
            fetchYouTubeOEmbed(url, ytMatch.groupValues[1])?.let { return it }
        }

        // 2. General HTML OpenGraph / Twitter Card / <title> fetch
        return try {
            val (finalUrl, contentType, bytes) = downloadLimited(url)
            if (contentType.startsWith("image/", ignoreCase = true)) {
                val host = extractHost(finalUrl)
                val fileTitle = finalUrl.substringAfterLast('/').substringBefore('?').ifBlank { host }
                return JSONObject().apply {
                    put("url", url)
                    put("title", fileTitle)
                    put("description", "")
                    put("site", host)
                    put("image", finalUrl)
                    put("favicon", googleFaviconUrl(finalUrl))
                    put("fetchedAt", System.currentTimeMillis())
                }
            }
            if (bytes.isEmpty()) return buildFallback(url)

            val charset = detectCharset(contentType, bytes)
            val html = String(bytes, charset)
            val headEnd = html.indexOf("</head>", ignoreCase = true)
            val headHtml = if (headEnd > 0) html.substring(0, minOf(headEnd + 7, html.length)) else html

            val metaMap = LinkedHashMap<String, String>()
            for (m in META_TAG_REGEX.findAll(headHtml)) {
                val attrs = parseAttributes(m.value)
                val key = (attrs["property"] ?: attrs["name"] ?: attrs["itemprop"] ?: "").lowercase(Locale.ROOT)
                val content = attrs["content"]?.trim().orEmpty()
                if (key.isNotEmpty() && content.isNotEmpty() && !metaMap.containsKey(key)) {
                    metaMap[key] = decodeHtml(content)
                }
            }

            val rawTitleTag = TITLE_TAG_REGEX.find(headHtml)?.groupValues?.getOrNull(1)?.let { decodeHtml(it) }.orEmpty()
            val host = extractHost(finalUrl)

            val title = firstNonBlank(
                metaMap["og:title"],
                metaMap["twitter:title"],
                metaMap["title"],
                rawTitleTag,
                host
            ).take(180)

            val description = firstNonBlank(
                metaMap["og:description"],
                metaMap["twitter:description"],
                metaMap["description"]
            ).take(220)

            val site = firstNonBlank(
                metaMap["og:site_name"],
                metaMap["application-name"],
                host
            ).take(60)

            val rawImage = firstNonBlank(
                metaMap["og:image:secure_url"],
                metaMap["og:image"],
                metaMap["twitter:image:src"],
                metaMap["twitter:image"],
                metaMap["image"]
            )
            val image = resolveUrl(finalUrl, rawImage)

            var iconHref = ""
            for (m in LINK_TAG_REGEX.findAll(headHtml)) {
                val attrs = parseAttributes(m.value)
                val rel = attrs["rel"]?.lowercase(Locale.ROOT).orEmpty()
                val href = attrs["href"]?.trim().orEmpty()
                if (href.isNotEmpty() && (rel.contains("icon") || rel == "shortcut icon" || rel == "apple-touch-icon")) {
                    iconHref = resolveUrl(finalUrl, href)
                    if (iconHref.isNotEmpty()) break
                }
            }
            val favicon = iconHref.ifEmpty { googleFaviconUrl(finalUrl) }

            JSONObject().apply {
                put("url", url)
                put("title", title)
                put("description", description)
                put("site", site)
                put("image", image)
                put("favicon", favicon)
                put("fetchedAt", System.currentTimeMillis())
            }
        } catch (_: Exception) {
            buildFallback(url)
        }
    }

    private fun fetchYouTubeOEmbed(originalUrl: String, videoId: String): JSONObject? {
        var conn: HttpURLConnection? = null
        return try {
            val encoded = URLEncoder.encode(originalUrl, "UTF-8")
            val apiUrl = "https://www.youtube.com/oembed?url=$encoded&format=json"
            conn = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/json")
            }
            if (conn.responseCode != HttpURLConnection.HTTP_OK) return null
            val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val json = JSONObject(body)
            val title = json.optString("title", "").trim()
            if (title.isEmpty()) return null
            val author = json.optString("author_name", "").trim()
            val thumb = json.optString("thumbnail_url", "").trim()
                .ifEmpty { "https://i.ytimg.com/vi/$videoId/hqdefault.jpg" }
            JSONObject().apply {
                put("url", originalUrl)
                put("title", title.take(180))
                put("description", author.take(120))
                put("site", "YouTube")
                put("image", thumb)
                put("favicon", "https://www.google.com/s2/favicons?domain=youtube.com&sz=64")
                put("fetchedAt", System.currentTimeMillis())
            }
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun downloadLimited(startUrl: String): Triple<String, String, ByteArray> {
        var currentUrl = startUrl
        for (hop in 0..4) {
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    requestMethod = "GET"
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    setRequestProperty("User-Agent", USER_AGENT)
                    setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    setRequestProperty("Accept-Language", "vi-VN,vi;q=0.9,en-US;q=0.8,en;q=0.7")
                }
                val code = conn.responseCode
                if (code in 300..399) {
                    val loc = conn.getHeaderField("Location")?.trim().orEmpty()
                    if (loc.isEmpty()) return Triple(currentUrl, "", ByteArray(0))
                    currentUrl = resolveUrl(currentUrl, loc).ifEmpty { return Triple(currentUrl, "", ByteArray(0)) }
                    continue
                }
                val ct = conn.contentType.orEmpty()
                if (code !in 200..299) {
                    return Triple(currentUrl, ct, ByteArray(0))
                }
                if (ct.startsWith("image/", ignoreCase = true)) {
                    return Triple(currentUrl, ct, ByteArray(0))
                }
                val buf = ByteArray(4096)
                val out = ByteArrayOutputStream()
                conn.inputStream.use { inp ->
                    while (out.size() < MAX_BYTES) {
                        val toRead = minOf(buf.size, MAX_BYTES - out.size())
                        val n = inp.read(buf, 0, toRead)
                        if (n <= 0) break
                        out.write(buf, 0, n)
                    }
                }
                return Triple(currentUrl, ct, out.toByteArray())
            } finally {
                conn?.disconnect()
            }
        }
        return Triple(currentUrl, "", ByteArray(0))
    }

    private fun detectCharset(contentType: String, bytes: ByteArray): Charset {
        val fromHeader = CHARSET_REGEX.find(contentType)?.groupValues?.getOrNull(1)
        if (!fromHeader.isNullOrBlank()) {
            try {
                return Charset.forName(fromHeader.trim())
            } catch (_: Exception) {
            }
        }
        val sniff = String(bytes, 0, minOf(bytes.size, 2048), Charsets.ISO_8859_1)
        val fromMeta = CHARSET_REGEX.find(sniff)?.groupValues?.getOrNull(1)
        if (!fromMeta.isNullOrBlank()) {
            try {
                return Charset.forName(fromMeta.trim())
            } catch (_: Exception) {
            }
        }
        return Charsets.UTF_8
    }

    private fun parseAttributes(tagHtml: String): Map<String, String> {
        val map = LinkedHashMap<String, String>()
        for (m in ATTR_REGEX.findAll(tagHtml)) {
            val k = m.groupValues[1].lowercase(Locale.ROOT)
            val v = m.groupValues[2].ifEmpty { m.groupValues[3].ifEmpty { m.groupValues[4] } }
            map[k] = v
        }
        return map
    }

    private fun decodeHtml(raw: String): String {
        val cleaned = raw.replace(Regex("""\s+"""), " ").trim()
        if (cleaned.isEmpty()) return ""
        return try {
            Html.fromHtml(cleaned, Html.FROM_HTML_MODE_LEGACY).toString().replace(Regex("""\s+"""), " ").trim()
        } catch (_: Exception) {
            cleaned
        }
    }

    private fun resolveUrl(base: String, candidate: String): String {
        val c = candidate.trim()
        if (c.isEmpty() || c.startsWith("data:", ignoreCase = true)) return ""
        return try {
            URL(URL(base), c).toString()
        } catch (_: Exception) {
            ""
        }
    }

    private fun firstNonBlank(vararg values: String?): String {
        for (v in values) {
            val s = v?.trim().orEmpty()
            if (s.isNotEmpty()) return s
        }
        return ""
    }
}
