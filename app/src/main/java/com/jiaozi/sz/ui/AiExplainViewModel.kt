package com.jiaozi.sz.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jiaozi.sz.App
import com.jiaozi.sz.data.MetaKeys
import com.jiaozi.sz.domain.AiExplainEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AiExplainState(
    val explaining: Boolean = false,
    val text: String? = null,
    val error: String? = null
)

class AiExplainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as App).repository
    private val _state = MutableStateFlow(AiExplainState())
    val state: StateFlow<AiExplainState> = _state.asStateFlow()

    /** 讲评结果内存缓存：同一批错题（按内容签名）命中则直接复用，避免重复烧 Key */
    private val cache = LinkedHashMap<String, String>(16, 0.75f, true)

    private fun signature(items: List<AiExplainEngine.WrongItem>): String =
        items.joinToString("\u0000") { "${it.q}#${it.cause}" }

    /** 对错题清单生成讲评（对齐网页端 AI 讲评入口） */
    fun explain(provider: String, apiKey: String, items: List<AiExplainEngine.WrongItem>, model: String = "") {
        if (_state.value.explaining) return
        if (apiKey.isBlank()) {
            _state.value = _state.value.copy(error = "尚未配置 AI Key，请到「设置」填写后再使用 AI 讲评。")
            return
        }
        if (items.isEmpty()) {
            _state.value = _state.value.copy(error = "本次没有错题，无需讲评～")
            return
        }
        // 命中缓存：直接复用，不调网络
        val key = signature(items)
        val hit = cache[key]
        if (hit != null) {
            _state.value = _state.value.copy(explaining = false, error = null, text = hit)
            return
        }
        _state.value = _state.value.copy(explaining = true, error = null, text = null)
        viewModelScope.launch {
            try {
                val t = AiExplainEngine.explain(provider, apiKey, items, model)
                cache[key] = t
                cacheCauses(items) // 错因聚合缓存（跨会话持久化，统计页可复核）
                _state.value = _state.value.copy(explaining = false, text = t)
            } catch (e: Exception) {
                _state.value = _state.value.copy(explaining = false, error = e.message ?: "讲评生成失败")
            }
        }
    }

    /** 讲评后聚合错因并缓存到 meta（供统计页「AI 讲评错因聚合」跨会话展示） */
    private fun cacheCauses(items: List<AiExplainEngine.WrongItem>) {
        val counts = LinkedHashMap<String, Int>()
        items.forEach { it.cause.split(",").map { c -> c.trim() }.filter { it.isNotBlank() }.forEach { c -> counts[c] = (counts[c] ?: 0) + 1 } }
        val arr = org.json.JSONArray()
        counts.forEach { (c, n) -> arr.put(org.json.JSONObject().apply { put("c", c); put("n", n) }) }
        val json = org.json.JSONObject().apply { put("ts", System.currentTimeMillis()); put("causes", arr) }.toString()
        viewModelScope.launch { repo.setMeta(MetaKeys.AI_EXPLAIN_CACHE, json) }
    }

    fun clear() {
        _state.value = AiExplainState()
    }
}
