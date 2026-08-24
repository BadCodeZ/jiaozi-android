package com.jiaozi.sz.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jiaozi.sz.App
import com.jiaozi.sz.data.AppRepository
import com.jiaozi.sz.data.MetaKeys
import com.jiaozi.sz.data.local.AiChatEntity
import com.jiaozi.sz.domain.AiChatEngine
import com.jiaozi.sz.domain.AiProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AiChatViewModel(app: Application) : AndroidViewModel(app) {
    private val repo: AppRepository = (app as App).repository

    val messages: Flow<List<AiChatEntity>> = repo.allAiChatFlow()

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()

    /** 流式输出暂存：非空时表示 AI 正在逐字生成，供 UI 实时追加显示 */
    private val _streaming = MutableStateFlow<String?>(null)
    val streaming: StateFlow<String?> = _streaming.asStateFlow()

    var error: String? = null
        private set

    fun clearError() { error = null }

    /** 发送一条用户消息并获取助手回复；结果写入本地对话历史（可同步 aiHistory） */
    fun send(text: String) {
        val content = text.trim()
        if (content.isBlank() || _sending.value) return
        _sending.value = true
        error = null
        viewModelScope.launch {
            try {
                val apiKey = repo.getMeta(MetaKeys.AI_KEY) ?: ""
                val provider = repo.getMeta(MetaKeys.AI_PROVIDER) ?: AiProvider.DEFAULT
                val model = repo.getMeta(MetaKeys.AI_MODEL) ?: ""
                val now = System.currentTimeMillis()
                repo.addAiChat(AiChatEntity(id = "U$now", role = "user", content = content, ts = now, _mt = now))
                if (apiKey.isBlank()) {
                    // 离线兜底：仍生成内置备考提示，使「AI 帮手」首开即可用，不再报错卡死
                    // 打字机式流式更新 _streaming，消除无 Key 用户「整段等待」的空窗感（与真流式 UI 同一路径）
                    val reply = AiChatEngine.offlineReply(content)
                    val sb = StringBuilder()
                    var i = 0
                    while (i < reply.length) {
                        val end = (i + 3).coerceAtMost(reply.length)
                        sb.append(reply.substring(i, end))
                        _streaming.value = sb.toString()
                        i = end
                        delay(16)
                    }
                    val aNow = System.currentTimeMillis()
                    repo.addAiChat(AiChatEntity(id = "A$aNow", role = "assistant", content = reply, ts = aNow, _mt = aNow))
                    return@launch
                }
                // 取最近 12 轮作为上下文
                val history = repo.allAiChatFlow().first().takeLast(12).map { it.role to it.content }
                val full = StringBuilder()
                AiChatEngine.chatStream(provider, apiKey, history, model) { piece ->
                    full.append(piece)
                    _streaming.value = full.toString()
                }
                val aNow = System.currentTimeMillis()
                repo.addAiChat(AiChatEntity(id = "A$aNow", role = "assistant", content = full.toString(), ts = aNow, _mt = aNow))
            } catch (e: Throwable) {
                error = "AI 调用失败：${e.message?.take(120) ?: e.javaClass.simpleName}"
            } finally {
                _streaming.value = null
                _sending.value = false
            }
        }
    }

    fun clearHistory() {
        viewModelScope.launch { repo.clearAiChat() }
    }
}
