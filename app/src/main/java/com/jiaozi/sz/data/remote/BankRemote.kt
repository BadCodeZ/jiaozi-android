package com.jiaozi.sz.data.remote

import android.util.Log
import com.jiaozi.sz.data.BankStore
import com.jiaozi.sz.data.model.Bank
import com.jiaozi.sz.data.model.Question
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

/**
 * 题库远程拉取（2026-09-28 题库外置 · **学科 × 学段 双层拆分**）：从 GitHub 仓库 raw 文件下载科目包。
 *
 * 复用现有 [WebDavClient] 的零依赖思路（HttpURLConnection GET，不引入 Retrofit/OkHttp），
 * 保证离线仍可构建。目标仓库：[BadCodeZ/jiaozi-android]（题库与源码同仓），分支 `main`。
 *
 * 远端文件集共 **32 个**（见 `工具/split_bank_layer.py`）：
 * - 科目一《综合素质》→ `banks/ke1.json`；科目二《教育知识与能力》→ `banks/ke2.json`
 *   （官方「初中、高中相同」⇒ 恒单文件）；
 * - 科目三《学科知识与教学能力》单学段独有科：`banks/ke3_kexue.json` / `banks/ke3_lishiyushehui.json`
 *   （初中）/ `banks/ke3_sixiangzhengzhi.json` / `banks/ke3_tongyongjishu.json`（高中）⇒ 恒单文件；
 * - 科目三其余 13 个初高中同名分卷科：每科两份 `banks/ke3_<x>_junior.json` / `banks/ke3_<x>_senior.json`。
 *
 * 🔴 调用方通常持有**逻辑 code**（[BankStore.PACKS] 的 19 个固定名）；
 *   用 [fetchForStage] 自动换算为本学段对应的远端文件名。
 */
object BankRemote {
    const val OWNER = "BadCodeZ"
    const val REPO = "jiaozi-android"
    const val BRANCH = "main"
    const val BASE = "https://raw.githubusercontent.com/$OWNER/$REPO/$BRANCH/banks"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * 按**逻辑 code + 学段**下载：内部经 [BankStore.remoteCode] 换算为远端文件名。
     *
     * @param logicalCode [BankStore.PACKS] 中的固定 code，如 `ke3_meishu`
     * @param stage       目标学段（`"初中"` / `"高中"`）；为 null ⇒ 回退全量名（同名分卷科无全量文件时返回 null）
     */
    suspend fun fetchForStage(logicalCode: String, stage: String?): List<Question>? =
        fetchSubject(BankStore.remoteCode(logicalCode, stage))

    /**
     * 下载单个题库包（`banks/<remoteCode>.json`），返回解析后的题目列表。
     * 网络/解析失败或 404 时返回 null（调用方据此判定「该包下载失败」并提示重试）。
     */
    suspend fun fetchSubject(remoteCode: String): List<Question>? = withContext(Dispatchers.IO) {
        val url = "$BASE/$remoteCode.json"
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 30000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "JiaoziExam/2.8.0")
        }
        try {
            val resp = conn.responseCode
            if (resp == HttpURLConnection.HTTP_NOT_FOUND) return@withContext null
            if (resp !in 200..299) {
                Log.e("BankRemote", "fetch $remoteCode 失败 HTTP $resp")
                return@withContext null
            }
            val text = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            runCatching { json.decodeFromString<Bank>(text).exam }.getOrNull()
        } catch (e: Exception) {
            Log.e("BankRemote", "fetch $remoteCode 异常", e)
            null
        } finally {
            conn.disconnect()
        }
    }
}
