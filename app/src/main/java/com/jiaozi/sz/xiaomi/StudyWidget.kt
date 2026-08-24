package com.jiaozi.sz.xiaomi

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.jiaozi.sz.MainActivity
import com.jiaozi.sz.R
import com.jiaozi.sz.data.AssetLoader
import com.jiaozi.sz.data.Repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * 桌面组件（小米桌面长按添加）：点击进入 App 并开始练习。
 * 展示待复习题数（后台线程查 Room）。
 */
class StudyWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // 待复习数需查 Room；用 runBlocking 在后台线程取快照
        val dueCount = try {
            val repo = buildRepo(context)
            runBlocking {
                withContext(Dispatchers.IO) {
                    repo.dueFlow(System.currentTimeMillis()).first().size
                }
            }
        } catch (_: Exception) { -1 }

        appWidgetIds.forEach { id ->
            val views = RemoteViews(context.packageName, R.layout.widget_study)
            views.setTextViewText(R.id.widget_title, "综合教资备考平台")
            views.setTextViewText(
                R.id.widget_sub,
                if (dueCount < 0) "点击开始练习" else "待复习 $dueCount 题 · 点击开始"
            )
            val pi = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).apply { action = "com.jiaozi.sz.START_PRACTICE" },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pi)
            appWidgetManager.updateAppWidget(id, views)
        }
    }

    /** 仅用于组件展示：轻量重建仓储（不触发资产全量加载的副作用写） */
    private fun buildRepo(context: Context): Repository {
        // 复用 Application 中已构建的仓储，避免重复加载
        val app = context.applicationContext as? com.jiaozi.sz.App
        if (app != null) return app.repository
        // 兜底：仅在极端情况下构建（桌面组件独立进程）
        val db = com.jiaozi.sz.data.local.AppDatabase.build(context)
        return Repository(
            AssetLoader.loadBank(context),
            AssetLoader.loadSyllabus(context),
            AssetLoader.loadAutoSyll(context),
            AssetLoader.loadKnowledge(context),
            db.progressDao(), db.dailyStatDao(), db.metaDao(), db.userQuestionDao(),
            db.lessonDao(), db.inboxDao(), db.aiChatDao()
        )
    }
}
