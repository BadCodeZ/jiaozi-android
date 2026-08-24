package com.jiaozi.sz.xiaomi

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.res.Resources
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.jiaozi.sz.R
import com.jiaozi.sz.ui.island.IslandBus
import com.jiaozi.sz.ui.island.IslandState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 全局悬浮灵动岛（对齐澎湃工具箱的「上岛」）：
 * 前台服务 + WindowManager(TYPE_APPLICATION_OVERLAY) 在状态栏下方居中渲染胶囊，
 * 退到其它 App 也常驻显示当前练习进度。点胶囊可展开/收起看进度条。
 */
class FloatingIslandService : Service(), LifecycleOwner {

    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private lateinit var wm: WindowManager
    private var view: View? = null
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val job = Job()

    private val owner = object : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
        override val lifecycle: Lifecycle get() = this@FloatingIslandService.lifecycle
        override val viewModelStore: ViewModelStore = ViewModelStore()
        private val controller = SavedStateRegistryController.create(this)
        override val savedStateRegistry: SavedStateRegistry get() = controller.savedStateRegistry
        init { controller.performAttach(); controller.performRestore(null) }
    }

    override fun onCreate() {
        super.onCreate()
        // 无悬浮窗权限时直接自停并回传提示，避免 WindowManager 异常崩溃
        if (!Settings.canDrawOverlays(this)) {
            IslandBus.setError("灵动岛需要「悬浮窗」权限。请到 设置→应用→综合教资备考平台→权限管理→显示悬浮窗 中开启。")
            // 关键：Android 12+ 要求前台服务启动后必须限时调用 startForeground()，
            // 否则系统抛 RemoteServiceException 直接杀进程。即便要自停也必须先 startForeground 满足契约，
            // 否则「开启灵动岛但未授权」会闪退。
            startForeground(NOTIF_ID, buildNotification())
            stopSelf()
            return
        }
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        startForeground(NOTIF_ID, buildNotification())
        addOverlay()
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED

        // 空闲自停：业务屏离开后会 push(null)，连续 3 秒无状态就结束服务，避免前台通知一直挂
        CoroutineScope(Dispatchers.Main + job).launch {
            var idleJob: Job? = null
            IslandBus.state.collect { s ->
                idleJob?.cancel()
                if (s == null) {
                    idleJob = launch {
                        delay(3000)
                        if (IslandBus.state.value == null) stopSelf()
                    }
                }
            }
        }
    }

    private fun buildNotification(): Notification {
        createChannel()
        val pi = PendingIntent.getActivity(
            this, 0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("灵动岛运行中")
            .setContentText("练习状态常驻顶部胶囊")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pi)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL_ID, "灵动岛", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    private fun addOverlay() {
        try {
            val composeView = ComposeView(this).apply {
                setViewTreeLifecycleOwner(owner)
                setViewTreeViewModelStoreOwner(owner)
                setViewTreeSavedStateRegistryOwner(owner)
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
                setContent {
                    val s by IslandBus.state.collectAsStateWithLifecycle()
                    IslandContent(s)
                }
            }
            val density = Resources.getSystem().displayMetrics.density
            val params = WindowManager.LayoutParams().apply {
                width = WindowManager.LayoutParams.WRAP_CONTENT
                height = WindowManager.LayoutParams.WRAP_CONTENT
                type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                format = PixelFormat.TRANSLUCENT
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                x = 0
                y = topMargin()
            }
            wm.addView(composeView, params)
            view = composeView
        } catch (e: Exception) {
            IslandBus.setError("悬浮窗添加失败（权限可能被拒绝）：${e.message}")
            stopSelf()
        }
    }

    private fun statusBarHeight(): Int {
        val rid = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (rid > 0) resources.getDimensionPixelSize(rid)
        else (24 * Resources.getSystem().displayMetrics.density).toInt()
    }

    /** 精确挖孔适配：优先用 DisplayCutout 的安全顶部插入（API 29+），让胶囊正好落在挖孔/状态栏下方；
     *  无挖孔设备回退到 status_bar_height。横向保持 CENTER_HORIZONTAL 对齐居中挖孔。 */
    private fun topMargin(): Int {
        val density = Resources.getSystem().displayMetrics.density
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val insetTop = wm.defaultDisplay?.cutout?.safeInsetTop ?: 0
            if (insetTop > 0) insetTop + (6 * density).toInt()
            else statusBarHeight() + (10 * density).toInt()
        } else {
            statusBarHeight() + (10 * density).toInt()
        }
    }

    @Composable
    private fun IslandContent(state: IslandState?) {
        if (state == null) return
        var expanded by remember { mutableStateOf(false) }
        val bg = Color(0xFF1C1C1E)
        Column(
            modifier = Modifier
                .padding(4.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(bg)
                .clickable { expanded = !expanded }
                .padding(horizontal = 18.dp, vertical = 9.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Text(state.title, color = Color.White, fontSize = 13.sp)
            }
            Spacer(Modifier.height(2.dp))
            androidx.compose.material3.Text(state.detail, color = Color.White.copy(alpha = 0.78f), fontSize = 11.sp)
            if (expanded && state.progress != null) {
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier.fillMaxWidth(0.7f).height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = 0.22f))
                ) {
                    Box(
                        Modifier.fillMaxWidth(state.progress).height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFF4CAF50))
                    )
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        view?.let { wm.removeView(it) }
        view = null
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        job.cancel()
    }

    companion object {
        const val CHANNEL_ID = "island_channel"
        const val NOTIF_ID = 90211
    }
}
