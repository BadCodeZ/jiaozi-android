@file:OptIn(ExperimentalMaterial3Api::class)

package com.jiaozi.sz.ui.screens

/**
 * 设置二级页 —— 学习目标（路由 `settings_goal`）。
 *
 * 🔴 2026-09-26 方案 B 重写，同时统一**保存时机**：
 * 旧实现是「选日期 → 点『保存目标日』」两步，且 `picked` 只是**本地 state**
 * （不点保存就离开页面 ⇒ 选择静默丢失）；同一页面里外观/科三却是即时生效 ⇒ 4 套保存语义并存。
 *
 * 现统一为**即时生效**：DatePicker 确认即写 `appVm.setTargetDay`，**删除保存按钮**。
 * 反馈由行尾当前值自身承担（选完立刻变成所选日期），不再用 Toast（Toast 与 App 设计语言脱节）。
 */

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.jiaozi.sz.ui.AppViewModel
import com.jiaozi.sz.ui.LocalAppVm
import com.jiaozi.sz.ui.components.AppColors
import com.jiaozi.sz.ui.components.GroupTitle
import com.jiaozi.sz.ui.components.SettingRow
import com.jiaozi.sz.ui.components.appPainter
import com.jiaozi.sz.util.todayStartMillis
import com.jiaozi.sz.util.toIso
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme

@Composable
fun SettingsGoalScreen(nav: NavHostController) {
    val appVm: AppViewModel = LocalAppVm.current
    val targetDay by appVm.targetDay.collectAsStateWithLifecycle()

    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = runCatching { java.time.LocalDate.parse(targetDay) }.getOrNull()
            ?.atStartOfDay(java.time.ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
            ?: todayStartMillis()
    )

    // 目标考试日（倒计时锚点）—— 对齐网页端：用原生 DatePicker，避免手敲格式错误被静默吞掉
    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                Button(onClick = {
                    // 🔴 即时生效：确认即写入，无「保存」按钮
                    datePickerState.selectedDateMillis?.let { ms ->
                        val iso = java.time.Instant.ofEpochMilli(ms)
                            .atZone(java.time.ZoneId.systemDefault()).toLocalDate().toIso()
                        appVm.setTargetDay(iso)
                    }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    val hasTarget = targetDay.isNotBlank()

    SettingsDetailPage(title = "学习目标", subtitle = "设置目标考试日", icon = "target", nav = nav) {
        GroupTitle("考试日", modifier = Modifier.padding(top = 8.dp))
        SettingsCard {
            SettingRow(
                icon = "calendar",
                title = "目标考试日",
                value = if (hasTarget) targetDay else "未设置",
                iconBg = AppColors.blue,
                trailing = {
                    Icon(
                        appPainter("chevron"),
                        contentDescription = null,
                        tint = AppColors.textSecondary.copy(alpha = 0.5f)
                    )
                }
            ) { showDatePicker = true }

            // 仅在已设置时出现清除行（未设置时不占位，避免无效入口）
            if (hasTarget) {
                SettingRow(
                    icon = "close",
                    title = "清除目标日",
                    iconBg = AppColors.danger,
                    showDivider = false
                ) { appVm.setTargetDay("") }
            }
        }

        if (hasTarget) {
            GroupTitle("说明", modifier = Modifier.padding(top = 8.dp))
            SettingsCard {
                Text(
                    "目标考试日用于首页倒计时锚点，修改后立即生效。",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.textSecondary,
                    modifier = Modifier.padding(vertical = 10.dp)
                )
            }
        }
    }
}
