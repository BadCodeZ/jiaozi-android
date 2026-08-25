package com.jiaozi.sz.data

import android.content.Context
import com.jiaozi.sz.domain.MergeEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 本地备份管理器（P0 安全网，与 WebDAV 远程同步互不替代）。
 *
 * 职责：
 * 1) 本地快照：把用户数据信封（复用 Repository.exportEnvelope）写入 App 私有外部目录
 *    `.../files/backups/`，文件名带时间戳，滚动保留最近 [MAX_SNAPSHOTS] 份（约 24h 触发一次）。
 * 2) 还原：从快照文件读回并走 Repository.importEnvelope（复用 MergeEngine 合并语义）。
 *
 * 设计要点：
 * - 落盘位置用 getExternalFilesDir(null)（App 私有外部存储），无需任何存储权限，
 *   卸载 App 时随之清除；用户级可携备份请走设置页「导出数据」（SAF 自选位置）。
 * - 快照/还原均复用既有同步信封与合并引擎，零新增数据格式，杜绝数据漂移。
 * - 全部 IO 收口在 Dispatchers.IO，避免阻塞主线程；异常由调用方收敛为提示文案。
 */
object BackupManager {
    private const val MAX_SNAPSHOTS = 7
    private const val SNAPSHOT_PREFIX = "jiaozi_snapshot_"
    private const val SNAPSHOT_SUFFIX = ".json"
    private const val AUTO_SNAPSHOT_INTERVAL_MS = 24L * 3600 * 1000

    private fun backupsDir(context: Context): File {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        val dir = File(base, "backups")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun timeFormat() = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    fun snapshotFile(context: Context, time: Long = System.currentTimeMillis()): File =
        File(backupsDir(context), "$SNAPSHOT_PREFIX${timeFormat().format(Date(time))}$SNAPSHOT_SUFFIX")

    /** 写入一份本地快照（先导出信封再落盘），随后滚动裁剪到 [MAX_SNAPSHOTS] 份。 */
    suspend fun takeSnapshot(context: Context, repo: Repository): File = withContext(Dispatchers.IO) {
        val json = repo.exportEnvelope()
        val f = snapshotFile(context)
        f.writeText(json, Charsets.UTF_8)
        pruneSnapshots(context)
        f
    }

    data class SnapshotInfo(
        val file: File,
        val name: String,
        val sizeBytes: Long,
        val timeMillis: Long
    )

    /** 列出快照（新 → 旧）。 */
    fun listSnapshots(context: Context): List<SnapshotInfo> {
        val dir = backupsDir(context)
        val files = dir.listFiles { f ->
            f.isFile && f.name.startsWith(SNAPSHOT_PREFIX) && f.name.endsWith(SNAPSHOT_SUFFIX)
        } ?: return emptyList()
        return files.map { f ->
            val t = parseTime(f.name) ?: f.lastModified()
            SnapshotInfo(f, f.name, f.length(), t)
        }.sortedByDescending { it.timeMillis }
    }

    private fun parseTime(name: String): Long? {
        val core = name.removePrefix(SNAPSHOT_PREFIX).removeSuffix(SNAPSHOT_SUFFIX)
        return runCatching { timeFormat().parse(core)?.time }.getOrNull()
    }

    /** 仅保留最新 [MAX_SNAPSHOTS] 份，删除更早的。 */
    private fun pruneSnapshots(context: Context) {
        val list = listSnapshots(context).toMutableList()
        while (list.size > MAX_SNAPSHOTS) {
            val oldest = list.removeLast()
            runCatching { oldest.file.delete() }
        }
    }

    /** 从快照还原（合并进本地）。返回 MergeReport（各集合增量 + 冲突 + 最大 _mt）；信封版本过高或解析失败抛异常由调用方收敛。 */
    suspend fun restoreSnapshot(repo: Repository, file: File): MergeReport = withContext(Dispatchers.IO) {
        val json = file.readText(Charsets.UTF_8)
        MergeEngine.parse(json) // 版本校验，失败即抛
        repo.importEnvelope(json)
    }

    /**
     * 应用启动时的自动快照：距上次快照超过约 24h（或从未快照）才生成，避免每次冷启都写盘。
     * 在 Application.onCreate 的 IO 协程中调用；失败静默忽略（自动快照非关键路径）。
     */
    suspend fun maybeAutoSnapshot(context: Context, repo: Repository) {
        val last = listSnapshots(context).firstOrNull()
        val now = System.currentTimeMillis()
        if (last == null || now - last.timeMillis > AUTO_SNAPSHOT_INTERVAL_MS) {
            takeSnapshot(context, repo)
        }
    }
}
