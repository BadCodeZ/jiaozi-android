@file:OptIn(ExperimentalMaterial3Api::class)

package com.jiaozi.sz.ui.screens

/**
 * 设置二级页 —— 灵动岛（路由 `settings_island`）。
 *
 * 🔴 2026-09-26 方案 B 重写（本页是「半成品融合」最严重的一处）：
 * 旧实现在 `SettingsSection("灵动岛（上岛）")` 内**又写了一遍** `Text("灵动岛（上岛）")`
 * ⇒ 同一文案连续出现两次（且外层还有主页行标题「灵动岛」⇒ 三重复）。
 * 现标题统一由 Hero 承载，页内只留说明与开关。
 *
 * 另：权限引导卡原嵌在 `SettingsSection` 的卡内 ⇒ **卡套卡**（违反 `SP-SETTINGS-CARD.forbidden`）。
 * 现改为**滚动区同级独立卡**（与开关卡并列，不嵌套）。
 */

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.jiaozi.sz.ui.AppViewModel
import com.jiaozi.sz.ui.LocalAppVm
import com.jiaozi.sz.ui.components.AppColors
import com.jiaozi.sz.ui.components.GroupTitle
import com.jiaozi.sz.ui.components.SettingSwitchRow
import com.jiaozi.sz.ui.island.IslandBus
import com.jiaozi.sz.xiaomi.FloatingIslandService

@Composable
fun SettingsIslandScreen(nav: NavHostController) {
    val appVm: AppViewModel = LocalAppVm.current
    val ctx = LocalContext.current

    val overlayLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        // 从授权页返回后尝试启动：MIUI/澎湃 OS 上 Settings.canDrawOverlays 可能误报 false，
        // 故交给 FloatingIslandService 内部最终兜底（无权限则先 startForeground 再自停，不崩）。
        // 必须门控权限，否则无权限时启动前台服务会闪退。
        IslandBus.setError(null)
        if (Settings.canDrawOverlays(ctx) && IslandBus.state.value != null) {
            ctx.startForegroundService(Intent(ctx, FloatingIslandService::class.java))
        }
    }

    /**
     * 跳转悬浮窗授权页。
     * 小米/澎湃 OS 上标准 ACTION_MANAGE_OVERLAY_PERMISSION 跳的是「特殊应用权限」列表，
     * 该列表不收录第三方应用（用户反馈"找不到 app"）；故小米系直接跳本应用详情页，
     * 用户在「权限管理 → 显示悬浮窗」中开启即可，一定能定位到本应用。
     */
    fun openOverlaySettings(c: Context) {
        val isXiaomi = Build.BRAND.equals("xiaomi", ignoreCase = true)
            || Build.MANUFACTURER.equals("xiaomi", ignoreCase = true)
            || Build.MODEL.contains("POCO", ignoreCase = true)
            || Build.MODEL.contains("Redmi", ignoreCase = true)
        val intent = if (isXiaomi) {
            // 小米/澎湃 OS：标准悬浮窗权限页不收录第三方应用，直接跳本应用详情页
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + c.packageName))
        } else {
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + c.packageName))
        }
        // 部分 OEM（三星/ColorOS 等）的「悬浮窗」列表同样不收录第三方应用，
        // 若目标 Intent 无可解析的 Activity，则回退到本应用详情页，保证一定能定位到本应用权限入口。
        val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + c.packageName))
        val target = if (intent.resolveActivity(c.packageManager) != null) intent else fallback
        // 用 launcher 启动，授权/设置页返回后由回调自动尝试启动服务
        overlayLauncher.launch(target)
    }

    fun handleIsland(v: Boolean) {
        appVm.setIslandEnabled(v)
        if (v) {
            // 开启后由业务屏（练习/首页/AI 帮手）按需自动拉起服务；
            // 若当前已处于某业务场景（已有 IslandState），则立即启动让胶囊直接出现。
            if (IslandBus.state.value != null) {
                if (Settings.canDrawOverlays(ctx)) {
                    IslandBus.setError(null)
                    ctx.startForegroundService(Intent(ctx, FloatingIslandService::class.java))
                } else {
                    openOverlaySettings(ctx)
                }
            }
        } else {
            ctx.stopService(Intent(ctx, FloatingIslandService::class.java))
        }
    }

    val islandEnabled by appVm.islandEnabled.collectAsStateWithLifecycle()
    val islandErr by IslandBus.error.collectAsStateWithLifecycle()
    val needPermission = islandEnabled && (!Settings.canDrawOverlays(ctx) || islandErr != null)

    SettingsDetailPage(title = "灵动岛", subtitle = "会话期顶部悬浮胶囊", icon = "island", nav = nav) {
        GroupTitle("悬浮胶囊", modifier = Modifier.padding(top = 8.dp))
        SettingsCard {
            SettingSwitchRow(
                icon = "island",
                title = "启用灵动岛",
                summary = "在「练习 / AI 帮手」等学习会话期间，进度常驻屏幕顶部胶囊；离开 App 也能看到实时状态。仅会话期间显示，平时不占资源与电量。",
                checked = islandEnabled,
                iconBg = AppColors.blue,
                onCheckedChange = { handleIsland(it) },
                showDivider = false
            )
        }

        // 未授予悬浮窗权限时的引导卡：与开关卡**同级并列**（不嵌套，避免卡套卡）
        if (needPermission) {
            GroupTitle("权限引导", modifier = Modifier.padding(top = 8.dp))
            // 语义提示卡：保留 errorContainer 语义色，但仍走 SettingsCard 规格（r16 / v6 / h14），
            // 不再另起裸 Card（SP-SETTINGS-CARD.forbidden 只禁「卡套卡」，语义色仍允许）。
            SettingsCard(containerColor = MaterialTheme.colorScheme.errorContainer) {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "灵动岛无法显示",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    islandErr?.let { err ->
                        Text(err, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                    Text(
                        "请按以下路径手动开启「悬浮窗」权限：\n设置 → 应用 → 综合教资备考平台 → 权限管理 → 显示悬浮窗\n开启后返回本页重新打开开关即可。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Button(onClick = { openOverlaySettings(ctx) }, Modifier.fillMaxWidth().height(44.dp)) {
                        Text("去授予悬浮窗权限")
                    }
                }
            }
        }
    }
}
