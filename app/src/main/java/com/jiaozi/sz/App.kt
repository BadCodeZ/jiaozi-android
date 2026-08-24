package com.jiaozi.sz

import android.app.Application
import android.os.Build
import com.jiaozi.sz.data.AppRepository
import com.jiaozi.sz.data.local.AppDatabase
import com.jiaozi.sz.data.AssetLoader
import com.jiaozi.sz.data.model.Bank
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

class App : Application() {
    /** 内置题库 + Room 仓储，首次启动构建一次 */
    lateinit var repository: AppRepository
        private set

    /** 启动期数据加载错误（降级后填充，供 UI 提示）；正常为 null */
    var loadError: String? = null
        private set

    override fun onCreate() {
        super.onCreate()
        // 全局兜底：任何未捕获异常写入文件，便于后续连机诊断真实堆栈
        installCrashHandler()

        val db = AppDatabase.build(this)

        // 各资源独立兜底：单个文件缺失/解析失败时降级为空数据，避免整进程崩溃
        val bank = runCatching { AssetLoader.loadBank(this) }.getOrElse { e ->
            recordLoadError("题库 bank.json", e); Bank(emptyList(), emptyList())
        }
        val syllabus = runCatching { AssetLoader.loadSyllabus(this) }.getOrElse { e ->
            recordLoadError("大纲 default_syllabus.json", e); emptyList()
        }
        val autoSyll = runCatching { AssetLoader.loadAutoSyll(this) }.getOrElse { e ->
            recordLoadError("归类 auto_syll.json", e); emptyList()
        }
        val knowledge = runCatching { AssetLoader.loadKnowledge(this) }.getOrElse { e ->
            recordLoadError("知识卡 knowledge.json", e); emptyList()
        }

        repository = AppRepository(
            bank, syllabus, autoSyll, knowledge,
            db.progressDao(), db.dailyStatDao(), db.metaDao(), db.userQuestionDao(),
            db.lessonDao(), db.inboxDao(), db.aiChatDao()
        )
    }

    private fun recordLoadError(tag: String, e: Throwable) {
        val msg = "$tag 加载失败：${e.message}"
        loadError = if (loadError.isNullOrBlank()) msg else "$loadError\n$msg"
        android.util.Log.e("App", msg, e)
    }

    /** 全局未捕获异常写文件（filesDir/crash.log），便于后续连机诊断 */
    private fun installCrashHandler() {
        val default = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val f = File(filesDir, "crash.log")
                val sw = StringWriter()
                sw.append("=== ${System.currentTimeMillis()} model=${Build.MODEL} api=${Build.VERSION.SDK_INT} thread=${thread.name} ===\n")
                throwable.printStackTrace(PrintWriter(sw))
                sw.append("\n")
                f.appendText(sw.toString())
            } catch (_: Throwable) { /* 忽略，不能让 handler 自身再崩 */ }
            default?.uncaughtException(thread, throwable)
        }
    }
}
