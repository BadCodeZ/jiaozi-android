@file:OptIn(ExperimentalMaterial3Api::class)

package com.jiaozi.sz.ui.screens

/**
 * 练习模块扩展：Context 找回 Activity（悬浮窗权限申请用）。
 *
 * 从 PracticeScreen.kt 拆分而来（纯物理拆分，逻辑未改）。
 */

import kotlin.math.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.app.Activity
import android.content.ContextWrapper

internal fun android.content.Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
