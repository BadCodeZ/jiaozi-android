package com.jiaozi.sz.domain

import com.jiaozi.sz.data.MergeReport
import com.jiaozi.sz.data.MetaKeys
import com.jiaozi.sz.data.Repository
import com.jiaozi.sz.data.remote.SyncState
import com.jiaozi.sz.data.remote.WebDavClient
import com.jiaozi.sz.data.remote.WebDavConfig
import com.jiaozi.sz.data.remote.WebDavException
import com.jiaozi.sz.domain.SyncCrypto

/**
 * WebDAV 同步编排（复用 MergeEngine 的合并规则）。
 * - upload：本地导出 → 上传。
 * - download：下载远程 → 合并进本地；远程无文件则报错提示。
 * - two-way：下载合并 → 重新导出 → 上传（等价于双向合并，单一信封文件）。
 * onState 回调驱动 UI 状态提示；所有异常收敛为 SyncState.Error。
 */
object WebDavSync {
    suspend fun sync(
        cfg: WebDavConfig,
        repo: Repository,
        onState: (SyncState) -> Unit
    ) {
        try {
            when (cfg.direction) {
                "upload" -> {
                    onState(SyncState.Syncing("正在上传到 WebDAV…"))
                    val json = repo.exportEnvelope()
                    val blob = if (cfg.encrypt) wrapOrThrow(cfg.syncPass, json) else json
                    WebDavClient.upload(cfg.url, cfg.remoteDir, WebDavClient.FILE_NAME, blob, cfg.user, cfg.pass)
                    onState(SyncState.Success("已上传（${if (cfg.encrypt) "已加密" else "明文"}）到服务器"))
                }
                "download" -> {
                    onState(SyncState.Syncing("正在从 WebDAV 下载…"))
                    val remote = WebDavClient.download(cfg.url, cfg.remoteDir, WebDavClient.FILE_NAME, cfg.user, cfg.pass)
                        ?: throw WebDavException("远程目录没有同步文件（请先在一台设备上传）", 404)
                    val json = unwrapOrThrow(cfg.encrypt, cfg.syncPass, remote)
                    val report = repo.importEnvelope(json)
                    // P2-C 增量水位：记录本次合并最大 _mt，供增量判断与展示
                    repo.setMeta(MetaKeys.LAST_SYNC_MT, report.maxMt.toString())
                    repo.setMeta(MetaKeys.LAST_SYNC_AT, System.currentTimeMillis().toString())
                    onState(SyncState.Success("已下载并合并 ${report.total} 条数据（${summarize(report)}）", report))
                }
                else -> { // two-way
                    onState(SyncState.Syncing("正在下载远程数据…"))
                    val remote = WebDavClient.download(cfg.url, cfg.remoteDir, WebDavClient.FILE_NAME, cfg.user, cfg.pass)
                    var report: MergeReport? = null
                    val mergedCount = if (remote != null) {
                        val json = unwrapOrThrow(cfg.encrypt, cfg.syncPass, remote)
                        report = repo.importEnvelope(json)
                        // P2-C 增量水位
                        repo.setMeta(MetaKeys.LAST_SYNC_MT, report.maxMt.toString())
                        repo.setMeta(MetaKeys.LAST_SYNC_AT, System.currentTimeMillis().toString())
                        onState(SyncState.Syncing("已合并 ${report.total} 条，正在上传回服务器…"))
                        report.total
                    } else {
                        onState(SyncState.Syncing("远程无文件，正在上传本地数据…"))
                        0
                    }
                    val merged = repo.exportEnvelope()
                    val blob = if (cfg.encrypt) wrapOrThrow(cfg.syncPass, merged) else merged
                    WebDavClient.upload(cfg.url, cfg.remoteDir, WebDavClient.FILE_NAME, blob, cfg.user, cfg.pass)
                    onState(SyncState.Success("双向同步完成（合并 $mergedCount 项${if (report != null) "，" + summarize(report) else ""}，已回传服务器${if (cfg.encrypt) "·已加密" else ""}）", report))
                }
            }
        } catch (e: WebDavException) {
            onState(SyncState.Error(e.message ?: "WebDAV 错误"))
        } catch (e: Exception) {
            onState(SyncState.Error("同步失败：${e.message ?: e.javaClass.simpleName}"))
        }
    }

    /** 把合并报告浓缩为一行摘要（供同步成功提示与报告卡展示） */
    private fun summarize(r: MergeReport): String {
        val parts = mutableListOf<String>()
        if (r.examAdded > 0 || r.examUpdated > 0) parts += "题库 +${r.examAdded}/更新${r.examUpdated}"
        if (r.lessonAdded > 0 || r.lessonUpdated > 0) parts += "教案 +${r.lessonAdded}/更新${r.lessonUpdated}"
        if (r.curricAdded > 0 || r.curricUpdated > 0) parts += "课标 +${r.curricAdded}"
        if (r.booksAdded > 0 || r.booksUpdated > 0) parts += "教材 +${r.booksAdded}"
        if (r.qstat > 0) parts += "进度 ${r.qstat}"
        if (r.corrections > 0) parts += "错题 ${r.corrections}"
        if (r.inboxAdded > 0 || r.inboxUpdated > 0) parts += "收集箱 +${r.inboxAdded}"
        if (r.aiHistoryAdded > 0 || r.aiHistoryUpdated > 0) parts += "对话 +${r.aiHistoryAdded}"
        if (r.removed > 0) parts += "移除 ${r.removed}"
        val body = if (parts.isEmpty()) "无新增（已是最新）" else parts.joinToString("，")
        return if (r.conflicts > 0) "$body，冲突已解决 ${r.conflicts}" else body
    }

    /** 加密；开启加密但口令为空时给出明确错误 */
    private fun wrapOrThrow(pass: String, json: String): String {
        if (pass.isBlank()) throw IllegalArgumentException("已开启同步加密但未填写「同步口令」，无法加密上传。")
        return SyncCrypto.wrap(pass, json)
    }

    /** 解密；明文包直接返回；密文包按口令解开；口令缺失给出明确错误 */
    private fun unwrapOrThrow(encrypt: Boolean, pass: String, pkg: String): String {
        val isEnc = pkg.trim().startsWith("SYNCPKG1:") || encrypt
        if (!isEnc) return pkg.trim()
        if (pass.isBlank()) throw IllegalArgumentException("远程同步包已加密，需要填写「同步口令」才能解密。")
        return SyncCrypto.unwrap(pass, pkg)
    }
}
