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
 *
 * 🔴🔴 **多源回退 + 放宽超时（2026-09-29 真机试验修复）**：
 *   真机实测 `raw.githubusercontent.com` 下行抖动极大 —— 单个 113KB 包耗时 **46s / 61s / 69s**
 *   （1.6–7.1 KB/s），而原 `readTimeout = 30000`（30s）⇒ **下载 100% 失败**
 *   （`SocketTimeoutException: timeout` + `SocketException: Socket closed`），且全链路**零重试**。
 *   同资产经镜像实测：`ghproxy.net` **1.6s**、`cdn.jsdelivr.net` **3.1s**，快 20–40 倍。
 *   ⇒ 现改为**三源回退链**（ghproxy → raw → jsDelivr）+ **超时放宽至 90s** + **快速失败同源重试**。
 *
 * 源顺序含义：ghproxy 为实时反向代理（最快且内容实时）⇒ 首位；raw 为权威实时源 ⇒ 兜底；
 * jsDelivr 走 CDN（`@main` 分支内容存在缓存延迟，**不适合「刷新最新题库」语义**）⇒ 仅末位兜底。
 */
object BankRemote {
    const val OWNER = "BadCodeZ"
    const val REPO = "jiaozi-android"
    const val BRANCH = "main"
    const val BASE = "https://raw.githubusercontent.com/$OWNER/$REPO/$BRANCH/banks"

    /** ghproxy 反向代理（实时转发 raw 内容，无需 CDN 缓存；国内可达且速度远超直连） */
    private const val GH_PROXY = "https://ghproxy.net"

    /** jsDelivr CDN（`@main` 分支内容有缓存延迟，仅作末位兜底） */
    private const val JSDELIVR = "https://cdn.jsdelivr.net/gh"

    /** 建连超时（不变） */
    private const val CONNECT_TIMEOUT = 15_000

    /** 读超时：30s → **90s**（覆盖实测最慢 69s，留足抖动余量） */
    private const val READ_TIMEOUT = 90_000

    /** 低于该耗时的失败视为「快速失败」（建连被拒 / 立即断开）⇒ 值得同源重试一次 */
    private const val RETRY_FAST_MS = 5_000

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** 单个下载源（`name` 仅用于日志定位） */
    private data class Source(val name: String, val url: String)

    /** 单包的三源回退链（顺序 = 优先级） */
    private fun sources(remoteCode: String): List<Source> = listOf(
        Source(
            "ghproxy",
            "$GH_PROXY/https://raw.githubusercontent.com/$OWNER/$REPO/$BRANCH/banks/$remoteCode.json"
        ),
        Source("raw", "$BASE/$remoteCode.json"),
        Source("jsdelivr", "$JSDELIVR/$OWNER/$REPO@$BRANCH/banks/$remoteCode.json")
    )

    /** 单次拉取结果：区分「成功」「该源确无此文件(404)」「网络/解析失败」三种语义 */
    private sealed interface FetchResult {
        data class Ok(val questions: List<Question>) : FetchResult
        object NotFound : FetchResult
        object Error : FetchResult
    }

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
     *
     * 依次尝试 [sources] 中的下载源；任一并发成功即返回；全部失败返回 null
     * （调用方据此判定「该包下载失败」并提示重试）。
     *
     * 重试策略：**慢源不重试、快源才重试** —— 出现非 404 失败时，若本次耗时 < [RETRY_FAST_MS]
     * （建连被拒 / 立即断开）则同源再试一次；若已耗时很久（超时）则直接换源，
     * 避免在 raw 这类慢源上反复长拖。
     */
    suspend fun fetchSubject(remoteCode: String): List<Question>? = withContext(Dispatchers.IO) {
        for (src in sources(remoteCode)) {
            val t0 = System.currentTimeMillis()
            var r = tryFetch(src, remoteCode)
            if (r is FetchResult.Ok) {
                Log.i("BankRemote", "fetch $remoteCode 成功（${src.name}，${r.questions.size} 题）")
                return@withContext r.questions
            }
            if (r is FetchResult.NotFound) {
                Log.w("BankRemote", "fetch $remoteCode 在 ${src.name} 返回 404，换源")
                continue
            }
            // Error：仅「快速失败」才同源重试；超时直接换源
            val cost = System.currentTimeMillis() - t0
            if (cost < RETRY_FAST_MS) {
                r = tryFetch(src, remoteCode)
                if (r is FetchResult.Ok) {
                    Log.i("BankRemote", "fetch $remoteCode 重试成功（${src.name}，${r.questions.size} 题）")
                    return@withContext r.questions
                }
            } else {
                Log.w("BankRemote", "fetch $remoteCode 在 ${src.name} 耗时 ${cost}ms 仍未成功，换源")
            }
        }
        Log.e("BankRemote", "fetch $remoteCode 全部下载源失败")
        null
    }

    /** 单源单次拉取（不发重试逻辑，由 [fetchSubject] 编排） */
    private fun tryFetch(src: Source, remoteCode: String): FetchResult {
        val conn = try {
            (URL(src.url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT
                readTimeout = READ_TIMEOUT
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "JiaoziExam/2.8.0")
            }
        } catch (e: Exception) {
            Log.e("BankRemote", "fetch $remoteCode 建连异常（${src.name}）", e)
            return FetchResult.Error
        }
        return try {
            val resp = conn.responseCode
            if (resp == HttpURLConnection.HTTP_NOT_FOUND) return FetchResult.NotFound
            if (resp !in 200..299) {
                Log.e("BankRemote", "fetch $remoteCode 失败 HTTP $resp（${src.name}）")
                return FetchResult.Error
            }
            val text = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val qs = runCatching { json.decodeFromString<Bank>(text).exam }.getOrNull()
            if (qs.isNullOrEmpty()) FetchResult.Error else FetchResult.Ok(qs)
        } catch (e: Exception) {
            Log.e("BankRemote", "fetch $remoteCode 异常（${src.name}）", e)
            FetchResult.Error
        } finally {
            conn.disconnect()
        }
    }
}
