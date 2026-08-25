package com.jiaozi.sz.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jiaozi.sz.App
import com.jiaozi.sz.data.Repository
import com.jiaozi.sz.data.local.UserQuestionEntity
import com.jiaozi.sz.domain.AiGenEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AiGenState(
    val generating: Boolean = false,
    val preview: List<UserQuestionEntity> = emptyList(), // 待审阅（尚未入库）
    val committed: Int = 0,                              // 最近一次已入库条数
    val error: String? = null,
    val offline: Boolean = false,                        // P2-A：本次为离线样例（无 AI Key）
    val lastSubject: String = "",
    val lastScope: String = ""
)

class AiGenViewModel(app: Application) : AndroidViewModel(app) {
    private val repo: Repository = (app as App).repository
    private val _state = MutableStateFlow(AiGenState())
    val state: StateFlow<AiGenState> = _state.asStateFlow()

    /** 生成题目但不入库，结果进入 preview 供 UI 预览审阅（对齐网页端） */
    fun preview(provider: String, apiKey: String, subject: String, scope: String, count: Int, model: String = "") {
        if (_state.value.generating) return
        _state.value = _state.value.copy(
            generating = true, error = null, preview = emptyList(),
            committed = 0, lastSubject = subject, lastScope = scope
        )
        viewModelScope.launch {
            try {
                // P2-A：无 AI Key 时走离线样例，不再直接报错
                val list = if (apiKey.isBlank()) {
                    AiGenEngine.offlineGenerate(subject, scope, count)
                } else {
                    AiGenEngine.previewGenerate(repo, provider, apiKey, subject, scope, count, model)
                }
                _state.value = _state.value.copy(generating = false, preview = list, offline = apiKey.isBlank())
            } catch (e: Exception) {
                _state.value = _state.value.copy(generating = false, error = e.message ?: "生成失败")
            }
        }
    }

    /** 将审阅选中的题目入库 */
    fun commit(list: List<UserQuestionEntity>) {
        viewModelScope.launch {
            AiGenEngine.commitGenerated(repo, list)
            _state.value = _state.value.copy(committed = list.size, preview = emptyList())
        }
    }

    /** 放弃本次预览 */
    fun discard() {
        _state.value = _state.value.copy(preview = emptyList())
    }
}
