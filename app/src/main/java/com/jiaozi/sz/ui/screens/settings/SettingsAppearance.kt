@file:OptIn(ExperimentalMaterial3Api::class)

package com.jiaozi.sz.ui.screens

/**
 * 设置二级页 —— 外观主题（路由 `settings_appearance`）。
 *
 * 🔴 2026-09-26 方案 B 重写：由「主页行内就地展开的 `SettingsAppearanceSection`」改为**独立二级页**。
 * 变化：① 去掉外层 `SettingsSection`（旧体系自带 Card ⇒ 卡套卡）与其重复小标题，标题交给 Hero；
 *      ② 外壳统一走 [SettingsDetailPage]；③ 全部改为**即时生效**（改完即写，无保存按钮）。
 * 兼容红线：`AppViewModel` 的读写方法与 SharedPreferences key **一字未改**。
 */

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.jiaozi.sz.ui.AppViewModel
import com.jiaozi.sz.ui.LocalAppVm
import com.jiaozi.sz.ui.components.AppColors
import com.jiaozi.sz.ui.components.GroupTitle
import com.jiaozi.sz.ui.components.SettingSelectRow
import com.jiaozi.sz.ui.components.SettingSwitchRow

@Composable
fun SettingsAppearanceScreen(nav: NavHostController) {
    val appVm: AppViewModel = LocalAppVm.current
    val theme by appVm.theme.collectAsStateWithLifecycle()
    val dynamicColor by appVm.dynamicColor.collectAsStateWithLifecycle()
    val fontScale by appVm.fontScale.collectAsStateWithLifecycle()

    SettingsDetailPage(title = "外观主题", subtitle = "主题模式与字号取色", icon = "palette", nav = nav) {
        // 🔴 区块头前导 8dp：卡间距 12dp + 8dp = 区块间距 20dp（E 类双轨，禁改全局 verticalArrangement）
        GroupTitle("显示", modifier = Modifier.padding(top = 8.dp))
        SettingsCard {
            SettingSelectRow(
                icon = "moon",
                title = "主题模式",
                options = listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色"),
                selected = theme,
                iconBg = AppColors.blue,
                onSelect = { appVm.setTheme(it) }
            )
            SettingSelectRow(
                icon = "text",
                title = "字体大小",
                summary = "对齐网页端 setFont",
                options = listOf("sm" to "小", "md" to "标准", "lg" to "大", "xl" to "特大"),
                selected = fontScale,
                iconBg = AppColors.blue,
                onSelect = { appVm.setFontScale(it) }
            )
            SettingSwitchRow(
                icon = "palette",
                title = "跟随系统壁纸取色",
                summary = "Android 12 以上生效；关闭则使用统一的企鹅蓝主色。",
                checked = dynamicColor,
                iconBg = AppColors.blue,
                onCheckedChange = { appVm.setDynamicColor(it) },
                showDivider = false
            )
        }
    }
}
