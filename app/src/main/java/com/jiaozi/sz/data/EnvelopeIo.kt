package com.jiaozi.sz.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 同步信封的「按 Uri 读写」统一出口。
 *
 * 背景：设置页原先有三处重复样板 —— 导出（`exportEnvelope` + `openOutputStream` 写）、
 * 单文件导入（`openInputStream` 读 + `importEnvelope`）、目录导入（同上）。
 * 三处 IO 细节与异常边界各写一遍，易漂移。本文件把「读 Uri 文本 / 写 Uri 文本 /
 * 导出到 Uri / 从 Uri 导入合并」四个动作收口，调用方只保留 try-catch 与提示文案。
 *
 * 约定：
 * - 读失败或 uri 不可访问时返回空串（与原实现 `?: ""` 语义一致），由 `importEnvelope`
 *   的版本校验兜底报错，不让空串被静默当成「合并 0 条」。
 * - 读写均下沉到 [Dispatchers.IO]，避免原实现把文件 IO 留在调用方协程（默认主线程）上。
 * - 本文件不引入 Repository 之外的依赖，也不改信封格式。
 */

/** 读取 Uri 全文；失败返回空串。 */
suspend fun Context.readTextFromUri(uri: Uri): String = withContext(Dispatchers.IO) {
    runCatching {
        contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
    }.getOrNull().orEmpty()
}

/** 把文本写入 Uri；写流不可用时抛异常（由调用方收敛为提示文案）。 */
suspend fun Context.writeTextToUri(uri: Uri, text: String) = withContext(Dispatchers.IO) {
    val out = contentResolver.openOutputStream(uri)
        ?: throw IllegalStateException("无法写入所选文件")
    out.use { it.write(text.toByteArray(Charsets.UTF_8)) }
}

/** 导出同步信封到 Uri。 */
suspend fun exportEnvelopeTo(ctx: Context, repo: Repository, uri: Uri) {
    ctx.writeTextToUri(uri, repo.exportEnvelope())
}

/** 从 Uri 读取备份并合并进本地，返回合并报告。 */
suspend fun importEnvelopeFrom(ctx: Context, repo: Repository, uri: Uri): MergeReport =
    repo.importEnvelope(ctx.readTextFromUri(uri))
