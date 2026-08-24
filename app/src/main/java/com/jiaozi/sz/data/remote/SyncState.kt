package com.jiaozi.sz.data.remote

/** 同步状态：驱动 UI 的状态提示与错误展示 */
sealed interface SyncState {
    data object Idle : SyncState
    data class Syncing(val phase: String) : SyncState
    data class Success(val message: String) : SyncState
    data class Error(val message: String) : SyncState
}
