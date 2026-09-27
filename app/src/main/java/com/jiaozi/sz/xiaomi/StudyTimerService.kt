package com.jiaozi.sz.xiaomi

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.jiaozi.sz.R
import kotlin.math.max

/**
 * 学习计时前台服务：练习进行中启动，配合应用内「灵动胶囊」(Capsule) 形成"上岛"体验。
 *
 * 🔴 2026-09-24 计时语义变更（杰哥拍板「后台暂停 / 超时归零」）：
 * 原先「离开 App 仍持续累计」会跨夜累积出 `5:46:06` 这类失真读数（把锁屏/睡觉时间
 * 也算成专注），与「专注时长」的业务含义相悖。现改为：
 * - **前台可见才计**：`MainActivity.onStart/onStop` 驱动 [resumeFromBackground]/[pauseForBackground]；
 * - **退后台即暂停**：切走时把当前段并入累计并停止读秒（读数冻结在离开那一刻）；
 * - **超时归零**：若离开时长超过 [BACKGROUND_RESET_TIMEOUT_MS]，回来自动作废整段、从零重计，
 *   避免「隔夜回来还接着昨天的时间」。两条行为都只改计时口径，不动前台服务的"上岛"能力。
 *
 * 注：Android 14 前台服务类型用 specialUse（无"计时"专用类型），已在 Manifest 声明。
 */
class StudyTimerService : Service() {
    companion object {
        const val CHANNEL_ID = "study_timer"
        const val NOTIF_ID = 1
        /** 退后台超过该时长（毫秒）则本次专注作废归零，回来从零重计 */
        const val BACKGROUND_RESET_TIMEOUT_MS = 30 * 60 * 1000L
        /** 已累计专注时长（毫秒）；切到其它 tab 暂停时不丢失 */
        @Volatile private var accumulatedMs = 0L
        /** 当前计时段的起点；0 表示当前未在计时（已暂停/未开始） */
        @Volatile private var segmentStartMs = 0L
        /** 因退后台而暂停的时刻；0 表示不是"后台暂停"态（未开始 / 前台计时中） */
        @Volatile private var backgroundPausedAtMs = 0L

        /** 累计 + 当前段 = 总专注秒数 */
        fun elapsedSeconds(): Int {
            val seg = if (segmentStartMs == 0L) 0L else (System.currentTimeMillis() - segmentStartMs)
            return ((accumulatedMs + seg) / 1000).toInt().coerceAtLeast(0)
        }
        /** 开始/继续一段计时（幂等：已在计时则不重置，避免切 tab 重新计时） */
        fun startSegment() {
            if (segmentStartMs == 0L) segmentStartMs = System.currentTimeMillis()
        }

        /**
         * App 退到后台：暂停读秒，并记下暂停时刻（供回来时判断是否已超时）。
         * 幂等——已是后台暂停态时不重复打标。切 tab 不触发（Activity 仍在 onStart），
         * 只有真正退到后台（onStop）才暂停。
         *
         * ⚠️ 仅在**真正在计时**（segmentStartMs != 0）时打标：否则「打开 App 没开练就切走、
         * 再回来」会被 startSegment 误点着钟，出现未开练却在读秒的幽灵计时。
         */
        fun pauseForBackground() {
            if (backgroundPausedAtMs != 0L) return
            if (segmentStartMs == 0L) return   // 未在计时（未开练 / 已暂停），不打标
            pauseSegment()
            backgroundPausedAtMs = System.currentTimeMillis()
        }

        /**
         * App 回到前台：
         * - 未开始计时（segmentStartMs==0 且 accumulatedMs==0）⇒ 不启表，等 begin 开练；
         * - 离开超时 ⇒ 整段作废归零（返回 true）；
         * - 未超时 ⇒ 接着累计，后台停留时间不计入（返回 false）。
         */
        fun resumeFromBackground(): Boolean {
            val pausedAt = backgroundPausedAtMs
            backgroundPausedAtMs = 0L
            if (pausedAt == 0L) return false                 // 非后台暂停态（如从未开练），不处理
            val awayMs = System.currentTimeMillis() - pausedAt
            return if (awayMs > BACKGROUND_RESET_TIMEOUT_MS) {
                // 整段作废后立刻重开一段：会话可能仍在进行中（用户回来继续答），
                // 只 resetAll 不加 startSegment 会让钟冻在 00:00。
                resetAll(); startSegment(); true
            } else {
                startSegment(); false
            }
        }
        /** 暂停：把当前段时间并入累计，避免把其它 tab 停留时间也算进专注，也防止服务重建后归零 */
        fun pauseSegment() {
            if (segmentStartMs != 0L) {
                accumulatedMs += System.currentTimeMillis() - segmentStartMs
                segmentStartMs = 0L
            }
        }
        /** 整段练习结束：清零，下次开练从头计 */
        fun resetAll() {
            accumulatedMs = 0L
            segmentStartMs = 0L
            backgroundPausedAtMs = 0L
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 仅在没有计时段时开启新段；重复 startForegroundService（如 begin + SessionView 双触发）不再重置
        startSegment()
        startForeground(NOTIF_ID, buildNotification())
        return START_STICKY
    }

    override fun onDestroy() {
        pauseSegment() // 服务被销毁前先固化当前段，防止专注时长丢失
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        val mgr = getSystemService(NotificationManager::class.java)
        if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "学习计时", NotificationManager.IMPORTANCE_LOW)
            )
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("综合教资备考平台")
            .setContentText("专注学习中…")
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .build()
    }
}
