package com.jiaozi.sz.ui.island

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 灵动岛（上岛）状态总线：各业务屏把当前场景推到这里，
 * FloatingIslandService 订阅后在屏幕顶部悬浮胶囊中渲染。
 * 应用级单例，与现有 AppViewModel 解耦，避免新增架构。
 *
 * enter/leave 采用「活动场景集合」模型：同一时刻可能有多个屏持有岛（如首页↔练习互切），
 * 仅当最后一个场景 leave 后才延迟 250ms 隐藏，避免跨屏切换时胶囊闪一下。
 */
data class IslandState(
    val kind: String,            // 场景标识：practice / today / ai
    val title: String,           // 主标题，如「练习中」
    val detail: String,          // 副标题，如「科三 已练 3/20 · 连对 5」
    val progress: Float? = null  // 0..1 进度（可选，展开态显示进度条）
)

object IslandBus {
    private val _state = MutableStateFlow<IslandState?>(null)
    val state: StateFlow<IslandState?> = _state.asStateFlow()

    /** 最近一次失败原因（如未授予悬浮窗权限），设置页读取后展示并清空 */
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** 当前持有岛的活动场景集合（kind 去重）；用于跨屏无缝衔接 */
    private val activeKinds = LinkedHashSet<String>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var hideJob: Job? = null

    /** 某个场景开始占用岛：写入最新状态并取消待隐藏任务 */
    fun enter(kind: String, s: IslandState) {
        hideJob?.cancel()
        activeKinds.add(kind)
        _state.value = s
    }

    /** 某个场景结束占用岛：移除自身；若已无活动场景则延迟 250ms 隐藏（给跨屏切换留缓冲） */
    fun leave(kind: String) {
        activeKinds.remove(kind)
        if (activeKinds.isEmpty()) {
            hideJob?.cancel()
            hideJob = scope.launch {
                delay(250)
                if (activeKinds.isEmpty()) _state.value = null
            }
        }
    }

    /** 推送当前要显示的状态（兼容旧调用，等价于 enter 但无 kind 管理） */
    fun push(s: IslandState?) {
        if (s == null) {
            if (activeKinds.isEmpty()) _state.value = null
        } else {
            _state.value = s
        }
    }

    fun setError(msg: String?) {
        _error.value = msg
    }
}
