package com.jiaozi.sz.data.remote

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** WebDAV 操作异常（携带 HTTP 状态码，便于 UI 提示） */
class WebDavException(message: String, val code: Int = 0) : IOException(message)

/** 读取响应/错误流的前 N 个字符，用于 UI 显示异常摘要 */
private fun HttpURLConnection.peekBody(max: Int = 160): String = try {
    val stream = if (responseCode in 200..299) inputStream else errorStream
    stream?.bufferedReader(Charsets.UTF_8)?.use {
        val sb = StringBuilder()
        val buf = CharArray(256)
        while (sb.length < max) {
            val n = it.read(buf, 0, minOf(buf.size, max - sb.length))
            if (n <= 0) break
            sb.append(buf, 0, n)
        }
        sb.toString()
    }?.trim() ?: ""
} catch (_: Throwable) { "" }

private fun bodyHint(body: String, max: Int = 120): String {
    val s = body.replace('\n', ' ').replace('\r', ' ').trim()
    return if (s.length <= max) s else s.take(max) + "…"
}

/**
 * 轻量 WebDAV 客户端：基于 HttpURLConnection + Basic 认证（HTTPS）。
 * 覆盖本项目所需的 GET / PUT / MKCOL / DELETE，不引入第三方库，保证离线可构建。
 * 适用：Nextcloud、群晖/威联通等 NAS、各类支持 Basic 认证的 WebDAV 服务。
 */
object WebDavClient {
    const val FILE_NAME = "sync.json"   // 与网页端共用同一文件名（同一空间目录 + 同一口令即可互通）

    private fun authHeader(user: String, pass: String): String {
        val token = "$user:$pass".toByteArray(Charsets.UTF_8)
        return "Basic " + Base64.encodeToString(token, Base64.NO_WRAP)
    }

    /** 拼接远程完整 URL：base(去掉末尾/) + / + dir(去两头/) + / + name */
    fun buildUrl(base: String, dir: String, name: String): String {
        val b = base.trimEnd('/')
        val d = dir.trim().trim('/')
        val path = if (d.isEmpty()) "/$name" else "/$d/$name"
        return runCatching { URL(URL(b), path).toString() }.getOrDefault("$b$path")
    }

    /** 远程目录 URL（以 / 结尾） */
    fun dirUrl(base: String, dir: String): String {
        val b = base.trimEnd('/')
        val d = dir.trim().trim('/')
        val path = if (d.isEmpty()) "/" else "/$d/"
        return runCatching { URL(URL(b), path).toString() }.getOrDefault("$b$path")
    }

    private fun newConn(url: String, method: String, user: String, pass: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 15000
        conn.readTimeout = 30000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("Authorization", authHeader(user, pass))
        conn.setRequestProperty("User-Agent", "JiaoziExam/1.0")
        return conn
    }

    /** 下载文件，返回文本；404 返回 null（远程尚无可同步文件） */
    suspend fun download(base: String, dir: String, name: String, user: String, pass: String): String? =
        withContext(Dispatchers.IO) {
            val url = buildUrl(base, dir, name)
            val conn = newConn(url, "GET", user, pass)
            try {
                val code = conn.responseCode
                if (code == HttpURLConnection.HTTP_NOT_FOUND) return@withContext null
                if (code !in 200..299) {
                    val body = conn.peekBody()
                    throw WebDavException("下载失败 HTTP $code, url=$url, body=${bodyHint(body)}", code)
                }
                val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                // 如果服务端把 WebDAV 路径当网页返回，提前给出明确提示
                if (body.trimStart().startsWith("<", ignoreCase = true) ||
                    conn.contentType?.startsWith("text/html", ignoreCase = true) == true
                ) {
                    throw WebDavException(
                        "远程返回了 HTML 页面而非 JSON，请检查服务器地址是否以 /dav 结尾。" +
                        "url=$url, contentType=${conn.contentType}, body=${bodyHint(body)}",
                        code
                    )
                }
                body
            } finally { conn.disconnect() }
        }

    /** 确保远程目录存在（逐层 MKCOL；已存在 405 / 父缺失 409 忽略） */
    suspend fun ensureDir(base: String, dir: String, user: String, pass: String) = withContext(Dispatchers.IO) {
        val segs = dir.trim().trim('/').split('/').filter { it.isNotEmpty() }
        val sb = StringBuilder()
        for (s in segs) {
            sb.append(s).append('/')
            mkcol(base, sb.toString(), user, pass)
        }
    }

    private fun mkcol(base: String, relPath: String, user: String, pass: String) {
        val conn = newConn(dirUrl(base, relPath), "MKCOL", user, pass)
        try {
            val code = conn.responseCode
            if (code !in 200..299 && code != 405 && code != 409) {
                throw WebDavException("创建目录失败 HTTP $code", code)
            }
        } finally { conn.disconnect() }
    }

    /** 上传/覆盖文件 */
    suspend fun upload(base: String, dir: String, name: String, content: String, user: String, pass: String) =
        withContext(Dispatchers.IO) {
            ensureDir(base, dir, user, pass)
            val url = buildUrl(base, dir, name)
            val conn = newConn(url, "PUT", user, pass)
            try {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(content.toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                if (code !in 200..299 && code != HttpURLConnection.HTTP_CREATED && code != HttpURLConnection.HTTP_NO_CONTENT) {
                    val body = conn.peekBody()
                    throw WebDavException("上传失败 HTTP $code, url=$url, body=${bodyHint(body)}", code)
                }
            } finally { conn.disconnect() }
        }

    suspend fun delete(base: String, dir: String, name: String, user: String, pass: String) = withContext(Dispatchers.IO) {
        val conn = newConn(buildUrl(base, dir, name), "DELETE", user, pass)
        try {
            val code = conn.responseCode
            if (code !in 200..299 && code != HttpURLConnection.HTTP_NO_CONTENT && code != HttpURLConnection.HTTP_NOT_FOUND) {
                throw WebDavException("删除失败 HTTP $code", code)
            }
        } finally { conn.disconnect() }
    }
}
