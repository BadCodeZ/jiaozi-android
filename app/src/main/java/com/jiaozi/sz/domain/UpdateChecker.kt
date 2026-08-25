package com.jiaozi.sz.domain

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.jiaozi.sz.BuildConfig

/**
 * 更新检测：拉取远程 version.json，与本地 versionCode 比对。
 * 网络层复用工程统一的 HttpURLConnection（与 AiProvider / WebDavClient 一致），零新增依赖。
 *
 * 托管约定（杰哥选 GitHub raw）：复用现有公开 repo `BadCodeZ/jiaozi-android`，
 * 在 main 分支根目录放一份 version.json，App 读其 raw 地址。字段：
 *   versionCode   Int    新版本号（> 本地 BuildConfig.VERSION_CODE 即视为有更新）
 *   versionName   String 新版本名（如 "2.77"）
 *   downloadUrl   String 下载地址（GitHub Release / 自有托管），「前往下载」按钮打开
 *   changelog     String 更新说明（\n 换行）
 *   forceUpdate   Boolean 是否强制（预留，本期不强制）
 * 网络失败 / 解析失败均返回 null，由调用方降级为「检查失败」，不阻断使用。
 */
object UpdateChecker {
    const val UPDATE_URL =
        "https://raw.githubusercontent.com/BadCodeZ/jiaozi-android/main/version.json"

    data class UpdateInfo(
        val hasUpdate: Boolean,
        val latestVersionCode: Int,
        val latestVersionName: String,
        val downloadUrl: String,
        val changelog: String,
        val forceUpdate: Boolean
    )

    suspend fun checkUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        val conn = try {
            URL(UPDATE_URL).openConnection() as HttpURLConnection
        } catch (e: Exception) {
            return@withContext null
        }
        try {
            conn.requestMethod = "GET"
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            val code = conn.responseCode
            if (code !in 200..299) return@withContext null
            val text = conn.inputStream.bufferedReader().readText()
            val obj = JSONObject(text)
            val latestCode = obj.optInt("versionCode", 0)
            val latestName = obj.optString("versionName", "")
            val url = obj.optString("downloadUrl", "")
            val log = obj.optString("changelog", "")
            val force = obj.optBoolean("forceUpdate", false)
            val has = latestCode > BuildConfig.VERSION_CODE
            UpdateInfo(has, latestCode, latestName, url, log, force)
        } catch (e: Exception) {
            null
        } finally {
            try { conn.disconnect() } catch (_: Exception) {}
        }
    }
}
