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
 * 学习计时前台服务：练习进行中启动，离开 App 仍持续。
 * 配合应用内「灵动胶囊」(Capsule) 形成完整"上岛"体验。
 * 注：Android 14 前台服务类型用 specialUse（无"计时"专用类型），已在 Manifest 声明。
 */
class StudyTimerService : Service() {
    companion object {
        const val CHANNEL_ID = "study_timer"
        const val NOTIF_ID = 1
        /** 已累计专注时长（毫秒）；切到其它 tab 暂停时不丢失 */
        @Volatile private var accumulatedMs = 0L
        /** 当前计时段的起点；0 表示当前未在计时（已暂停/未开始） */
        @Volatile private var segmentStartMs = 0L

        /** 累计 + 当前段 = 总专注秒数 */
        fun elapsedSeconds(): Int {
            val seg = if (segmentStartMs == 0L) 0L else (System.currentTimeMillis() - segmentStartMs)
            return ((accumulatedMs + seg) / 1000).toInt().coerceAtLeast(0)
        }
        /** 开始/继续一段计时（幂等：已在计时则不重置，避免切 tab 重新计时） */
        fun startSegment() {
            if (segmentStartMs == 0L) segmentStartMs = System.currentTimeMillis()
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
