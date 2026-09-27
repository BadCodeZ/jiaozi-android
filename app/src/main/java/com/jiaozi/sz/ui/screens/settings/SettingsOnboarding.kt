@file:OptIn(ExperimentalMaterial3Api::class)

package com.jiaozi.sz.ui.screens

/**
 * 设置二级页 —— 新手引导（路由 `settings_onboarding`）。
 *
 * 🔴 2026-09-26 方案 B 重写，并统一**反馈方式**：
 * 旧实现点击后弹系统 `Toast`（与 App 设计语言脱节，且是设置页里唯一的 Toast 反馈 —— 其余用
 * AlertDialog 或页内文案 ⇒ 三种反馈方式并存）。现改为**页内提示条**（2.5s 自动消失），
 * 与页面同语言、可预期。
 *
 * 操作入口也由裸 `Button` 改为 [SettingRow]，与其它设置页同一形态。
 */

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.jiaozi.sz.ui.AppViewModel
import com.jiaozi.sz.ui.LocalAppVm
import com.jiaozi.sz.ui.components.AppColors
import com.jiaozi.sz.ui.components.GroupTitle
import com.jiaozi.sz.ui.components.SettingRow
import kotlinx.coroutines.delay

@Composable
fun SettingsOnboardingScreen(nav: NavHostController) {
    val appVm: AppViewModel = LocalAppVm.current

    // 页内反馈条（替代 Toast）：重置后显示，2.5s 自动消失
    var notice by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(notice) {
        if (notice == null) return@LaunchedEffect
        delay(2500)
        notice = null
    }

    SettingsDetailPage(title = "新手引导", subtitle = "重置首开轻引导", icon = "bulb", nav = nav) {
        GroupTitle("首开引导", modifier = Modifier.padding(top = 8.dp))
        SettingsCard {
            SettingRow(
                icon = "bulb",
                title = "重置新手引导",
                value = "下次进首页重播",
                iconBg = AppColors.warning,
                showDivider = false
            ) {
                appVm.setOnboarded(false)
                notice = "已重置，返回首页将重新显示引导"
            }
        }

        GroupTitle("说明", modifier = Modifier.padding(top = 8.dp))
        SettingsCard {
            Text(
                "首开轻引导（欢迎语 + 设置目标日）默认只显示一次。重置后，下次进入首页会重新弹出。",
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.textSecondary,
                modifier = Modifier.padding(vertical = 10.dp)
            )
        }

        // 反馈条：与页面同语言，2.5s 后自动消失
        if (notice != null) {
            GroupTitle("状态", modifier = Modifier.padding(top = 8.dp))
            SettingsCard {
                Text(
                    notice!!,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 10.dp)
                )
            }
        }
    }
}
