package com.nguyenhieu.quicknotes

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.text.Html
import android.util.LruCache
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.Charset
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

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

    // =========================================================================
    // WIDGET NOTION EMBED BITMAP CACHE (COMPACT, ROUNDED, OOM/BINDER-SAFE)
    // =========================================================================
    private const val MAX_IMAGE_BYTES = 750 * 1024
    private const val THUMB_W = 148
    private const val THUMB_H = 120
    private const val FAVICON_SIZE = 28

    private val bitmapCache = object : LruCache<String, Bitmap>(48) {}
    private val attemptedImages = ConcurrentHashMap.newKeySet<String>()

    private fun thumbCacheDir(context: Context): File {
        val dir = File(context.applicationContext.cacheDir, "widget_thumbs")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun cacheKeyFor(url: String, isFavicon: Boolean): String {
        val prefix = if (isFavicon) "fav_" else "img_"
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(url.toByteArray(Charsets.UTF_8))
            val hex = digest.take(10).joinToString("") { "%02x".format(it) }
            "$prefix$hex"
        } catch (_: Exception) {
            "$prefix${url.hashCode().toUInt().toString(16)}"
        }
    }

    fun getCachedWidgetBitmap(context: Context, imageUrl: String, isFavicon: Boolean): Bitmap? {
        val url = imageUrl.trim()
        if (url.isEmpty() || !url.startsWith("http", ignoreCase = true)) return null
        val key = cacheKeyFor(url, isFavicon)
        bitmapCache.get(key)?.let { if (!it.isRecycled) return it }
        val f = File(thumbCacheDir(context), "$key.png")
        if (!f.exists() || !f.isFile || f.length() <= 0L) return null
        return try {
            val bmp = BitmapFactory.decodeFile(f.absolutePath)
            if (bmp != null) {
                bitmapCache.put(key, bmp)
            }
            bmp
        } catch (_: Exception) {
            null
        }
    }

    fun hasWidgetBitmapOnDisk(context: Context, imageUrl: String, isFavicon: Boolean): Boolean {
        val url = imageUrl.trim()
        if (url.isEmpty() || !url.startsWith("http", ignoreCase = true)) return false
        val key = cacheKeyFor(url, isFavicon)
        if (bitmapCache.get(key) != null) return true
        val f = File(thumbCacheDir(context), "$key.png")
        return f.exists() && f.isFile && f.length() > 0L
    }

    fun fetchAndCacheWidgetBitmap(context: Context, imageUrl: String, isFavicon: Boolean): Boolean {
        val url = imageUrl.trim()
        if (url.isEmpty() || !url.startsWith("http", ignoreCase = true)) return false
        if (hasWidgetBitmapOnDisk(context, url, isFavicon)) return false
        val key = cacheKeyFor(url, isFavicon)
        if (!attemptedImages.add(key)) return false

        val bytes = downloadImageBytes(url) ?: return false
        val decoded = decodeSampledBitmap(bytes, if (isFavicon) FAVICON_SIZE else THUMB_W, if (isFavicon) FAVICON_SIZE else THUMB_H)
            ?: return false

        val styled = try {
            if (isFavicon) {
                cropAndRoundBitmap(decoded, FAVICON_SIZE, FAVICON_SIZE, 6f, roundAllCorners = true)
            } else {
                cropAndRoundBitmap(decoded, THUMB_W, THUMB_H, 16f, roundAllCorners = false)
            }
        } catch (_: Exception) {
            null
        } finally {
            if (!decoded.isRecycled) decoded.recycle()
        } ?: return false

        return try {
            val outFile = File(thumbCacheDir(context), "$key.png")
            FileOutputStream(outFile).use { fos ->
                styled.compress(Bitmap.CompressFormat.PNG, 92, fos)
            }
            bitmapCache.put(key, styled)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun downloadImageBytes(startUrl: String): ByteArray? {
        var currentUrl = startUrl
        for (hop in 0..3) {
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    instanceFollowRedirects = false
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    setRequestProperty("User-Agent", USER_AGENT)
                    setRequestProperty("Accept", "image/webp,image/png,image/jpeg,image/*,*/*;q=0.8")
                }
                val code = conn.responseCode
                if (code in 300..399) {
                    val loc = conn.getHeaderField("Location").orEmpty().trim()
                    if (loc.isEmpty()) return null
                    currentUrl = resolveUrl(currentUrl, loc)
                    if (currentUrl.isEmpty()) return null
                    continue
                }
                if (code != HttpURLConnection.HTTP_OK) return null
                val buf = ByteArray(8192)
                val out = ByteArrayOutputStream()
                conn.inputStream.use { input ->
                    var total = 0
                    while (total < MAX_IMAGE_BYTES) {
                        val n = input.read(buf, 0, minOf(buf.size, MAX_IMAGE_BYTES - total))
                        if (n <= 0) break
                        out.write(buf, 0, n)
                        total += n
                    }
                }
                val arr = out.toByteArray()
                return if (arr.isNotEmpty()) arr else null
            } catch (_: Exception) {
                return null
            } finally {
                conn?.disconnect()
            }
        }
        return null
    }

    private fun decodeSampledBitmap(bytes: ByteArray, reqW: Int, reqH: Int): Bitmap? {
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            var sample = 1
            if (opts.outWidth > 0 && opts.outHeight > 0) {
                while ((opts.outWidth / (sample * 2)) >= reqW && (opts.outHeight / (sample * 2)) >= reqH) {
                    sample *= 2
                }
            }
            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
        } catch (_: Exception) {
            null
        }
    }

    private fun cropAndRoundBitmap(
        src: Bitmap,
        targetW: Int,
        targetH: Int,
        radiusPx: Float,
        roundAllCorners: Boolean
    ): Bitmap {
        val out = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val path = Path()
        val radii = if (roundAllCorners) {
            floatArrayOf(radiusPx, radiusPx, radiusPx, radiusPx, radiusPx, radiusPx, radiusPx, radiusPx)
        } else {
            // Notion Bookmark style: flush square left edge, rounded right corners matching card border
            floatArrayOf(0f, 0f, radiusPx, radiusPx, radiusPx, radiusPx, 0f, 0f)
        }
        path.addRoundRect(RectF(0f, 0f, targetW.toFloat(), targetH.toFloat()), radii, Path.Direction.CW)
        canvas.clipPath(path)

        val srcW = src.width.toFloat().coerceAtLeast(1f)
        val srcH = src.height.toFloat().coerceAtLeast(1f)
        val scale = maxOf(targetW / srcW, targetH / srcH)
        val cropW = (targetW / scale).toInt().coerceIn(1, src.width)
        val cropH = (targetH / scale).toInt().coerceIn(1, src.height)
        val left = ((src.width - cropW) / 2).coerceAtLeast(0)
        val top = ((src.height - cropH) / 2).coerceAtLeast(0)
        val srcRect = Rect(left, top, left + cropW, top + cropH)
        val dstRect = Rect(0, 0, targetW, targetH)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(src, srcRect, dstRect, paint)
        return out
    }
}
