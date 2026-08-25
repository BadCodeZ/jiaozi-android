package com.jiaozi.sz.data.remote

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jiaozi.sz.App
import com.jiaozi.sz.data.MetaKeys
import com.jiaozi.sz.domain.WebDavSync

/**
 * WebDAV 周期自动备份 Worker（WorkManager 每 12h 触发一次）。
 * 仅当「启用同步」开启且已配置地址/用户名时才真正同步；未启用或无配置则直接 success 跳过。
 * 复用 WebDavSync 的合并规则，与手动「立即同步」行为一致；成功后再补写一次同步时间戳水位。
 */
class WebDavWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val repo = (applicationContext as App).repository
        if (repo.getMeta(MetaKeys.SYNC_ENABLED) != "true") return Result.success()
        val url = repo.getMeta(MetaKeys.WEBDAV_URL)?.trim().orEmpty()
        if (url.isBlank()) return Result.success()
        val user = repo.getMeta(MetaKeys.WEBDAV_USER)?.trim().orEmpty()
        if (user.isBlank()) return Result.success()
        val dirRaw = repo.getMeta(MetaKeys.WEBDAV_DIR)?.trim()?.let { if (it.isBlank()) null else it }
        val modeRaw = repo.getMeta(MetaKeys.WEBDAV_DIRMODE)?.trim()?.let { if (it.isBlank()) null else it }
        val cfg = WebDavConfig(
            url = url,
            user = user,
            pass = repo.getMeta(MetaKeys.WEBDAV_PASS).orEmpty(),
            remoteDir = dirRaw ?: "artwb-default",
            direction = modeRaw ?: "two-way",
            encrypt = repo.getMeta(MetaKeys.SYNC_ENCRYPT) == "true",
            syncPass = repo.getMeta(MetaKeys.SYNC_PASS).orEmpty()
        )
        // 用普通变量在回调里记录成功（回调非挂起上下文，不能在里面调挂起 setMeta）
        var success = false
        WebDavSync.sync(cfg, repo) { st ->
            if (st is SyncState.Success) success = true
        }
        // 上传方向 WebDavSync 不写水位，这里统一补，保证「上次同步」时间准确
        if (success) repo.setMeta(MetaKeys.LAST_SYNC_AT, System.currentTimeMillis().toString())
        // 周期任务自带节奏，无论本次成败都返回 success 避免无谓重试风暴
        return Result.success()
    }
}
