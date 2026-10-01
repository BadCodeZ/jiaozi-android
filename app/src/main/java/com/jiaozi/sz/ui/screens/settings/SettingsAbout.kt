@file:OptIn(ExperimentalMaterial3Api::class)

package com.jiaozi.sz.ui.screens

/**
 * 关于页面（独立路由 "about"）。
 *
 * 从 SettingsScreen.kt 拆分而来（纯物理拆分 + 分区抽出，逻辑未改）。
 */

import com.jiaozi.sz.ui.components.AppColors
import com.jiaozi.sz.ui.components.appPainter
import com.jiaozi.sz.ui.components.GroupTitle
import com.jiaozi.sz.ui.components.SettingRow
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jiaozi.sz.ui.LocalAppVm
import com.jiaozi.sz.domain.UpdateChecker
import kotlinx.coroutines.launch

// ==================== 关于页面（独立路由 "about"）====================

/**
 * 关于页面 —— 卡片式布局（参考 Miuix 风格暗色卡片）。
 * 包含：版本/检查更新、赞助支持、社交链接（GitHub/小红书/抖音）、开源信息。
 */
@Composable
fun AboutScreen() {
    val ctx = LocalContext.current
    val appVm = LocalAppVm.current
    val isPro by appVm.isPro.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var checking by remember { mutableStateOf(false) }
    var updateMsg by remember { mutableStateOf<String?>(null) }
    var pendingUpdate by remember { mutableStateOf<UpdateChecker.UpdateInfo?>(null) }
    var showProDialog by remember { mutableStateOf(false) }

    // 尝试从 PackageManager 取当前版本名
    val versionName = remember {
        try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "2.58" }
        catch (_: Exception) { "2.58" }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).padding(bottom = com.jiaozi.sz.ui.components.NavTokens.ContentBottomPad),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 标题栏已移除（P2 清理）：about 路由处于 showFloating 集合内，全局悬浮返回键已提供
        // 「左上角圆形磨砂返回件 + 关于 标题胶囊」，行内再放一套返回键与标题属重复。
        // 🔴 2026-09-26 统一：原 `Spacer(8dp)` + `GroupTitle` 的 28dp 前导，改为与 8 个设置二级页
        // 同一写法 —— `GroupTitle(modifier = padding(top = 8.dp))`（内置 top 8dp + 8dp = 16dp），
        // 消除「同为设置族二级页、区块头前导却差 12dp」的最后一处不一致。
        GroupTitle("版本与会员", modifier = Modifier.padding(top = 8.dp))

        // ── 分组卡 1：检查更新 + Pro 会员（同卡内两行，行间 1dp 分隔）──
        // 🔴 2026-09-21 按高保真稿（关于页）校正：原「一行一张独立卡」改为「一组一张卡」，
        // 且每行补前导实色语义图标（云 / 星），副标题与右侧动作文案按稿对齐。
        AboutCard {
            AboutRow(
                icon = "cloud",
                iconBg = AppColors.blue,
                title = "检查更新",
                subtitle = "当前版本 $versionName",
                action = if (checking) null else "点击检查",
                loading = checking,
                showDivider = true
            ) {
                if (checking) return@AboutRow
                checking = true
                updateMsg = null
                pendingUpdate = null
                scope.launch {
                    val info = UpdateChecker.checkUpdate()
                    checking = false
                    when {
                        info == null -> updateMsg = "检查失败，请检查网络后重试"
                        info.hasUpdate -> pendingUpdate = info
                        else -> updateMsg = "已是最新版本（$versionName）"
                    }
                }
            }
            AboutRow(
                icon = "star",
                iconBg = AppColors.warning,
                title = if (isPro) "Pro 会员 · 已激活" else "Pro 会员",
                subtitle = if (isPro) "感谢支持，已解锁全部权益" else "解锁全部高级功能",
                action = if (isPro) "管理" else "去开通",
                showDivider = false
            ) { showProDialog = true }
        }

        GroupTitle("关注作者", modifier = Modifier.padding(top = 8.dp))

        // ── 分组卡 2：GitHub / 小红书 / 抖音（同卡三行；副标题即 @handle）──
        AboutCard {
            AboutRow(
                icon = "link",
                iconBg = AppColors.textPrimary,
                title = "GitHub",
                subtitle = "@BadCodeZ",
                showDivider = true
            ) {
                try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/BadCodeZ"))) }
                catch (_: Exception) {}
            }
            AboutRow(
                icon = "note",
                iconBg = AppColors.danger,
                title = "小红书",
                subtitle = "@决明子"
            ) {
                try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://xhslink.cn/m/8D9WQaseSL7"))) }
                catch (_: Exception) {}
            }
            AboutRow(
                icon = "play",
                iconBg = AppColors.textPrimary,
                title = "抖音",
                subtitle = "@BadCodeZ",
                showDivider = false
            ) {
                try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://v.douyin.com/qQmtgRmVY6A/"))) }
                catch (_: Exception) {}
            }
        }

        // 底部版权
        Text(
            "© 2026 BadCodeZ · 保留所有权利",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(top = 16.dp)
        )
    }

    // 更新结果提示（无更新 / 检查失败）
    if (updateMsg != null) {
        AlertDialog(
            onDismissRequest = { updateMsg = null },
            title = { Text("检查更新") },
            text = { Text(updateMsg!!) },
            confirmButton = { TextButton(onClick = { updateMsg = null }) { Text("好") } }
        )
    }

    // 发现新版本：前往下载
    if (pendingUpdate != null) {
        val info = pendingUpdate!!
        AlertDialog(
            onDismissRequest = { pendingUpdate = null },
            title = { Text("发现新版本 V${info.latestVersionName}") },
            text = { Text(if (info.changelog.isBlank()) "作者已发布新版本，建议更新以获得最新题库与修复。" else info.changelog) },
            confirmButton = {
                TextButton(onClick = {
                    try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.downloadUrl))) } catch (_: Exception) {}
                    pendingUpdate = null
                }) { Text("前往下载") }
            },
            dismissButton = { TextButton(onClick = { pendingUpdate = null }) { Text("稍后") } }
        )
    }

    // Pro 会员对话框（诚信激活）
    if (showProDialog) {
        AlertDialog(
            onDismissRequest = { showProDialog = false },
            title = { Text(if (isPro) "Pro 会员" else "开通 Pro 会员") },
            text = {
                if (isPro) {
                    Text("你已激活 Pro 会员，感谢支持！解锁的权益将持续扩充。", style = MaterialTheme.typography.bodyMedium)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Pro 权益（持续扩充）：", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        Text("· 知识卡片高级模板\n· 备课模板库\n· 课标精讲\n（具体范围后续版本逐步开放）", style = MaterialTheme.typography.bodySmall)
                        Text("微信 / 支付宝收款码（图待补）。扫码付费后点下方按钮诚信激活——本应用不联网验单，靠你的自觉。", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                }
            },
            confirmButton = {
                if (isPro) {
                    TextButton(onClick = { appVm.deactivatePro(); showProDialog = false }) { Text("撤销激活") }
                } else {
                    TextButton(onClick = { appVm.activatePro(); showProDialog = false }) { Text("我已付费 · 诚信激活") }
                }
            },
            dismissButton = { TextButton(onClick = { showProDialog = false }) { Text("关闭") } }
        )
    }
}

// ==================== 关于页行卡原子（本页私有） ====================

/**
 * 关于页分组卡。
 *
 * 🔴 2026-09-26 统一：直接复用设置组公共件 `SettingsCard`（`surfaceContainer` / r16 / elevation 0 /
 * 内距 h14 · v6）—— 原本页私有 `AboutCard` 为同规格复制体，但**纵向 padding 缺省**（只写 h14），
 * 与 11 号规范 `SP-SETTINGS-CARD.anatomy`「Column(padding h14 v6)」不一致，导致关于页卡片首末行
 * 比设置组卡片各多贴 6dp。改为别名直通，规格由公共件唯一承载，避免同规格两处漂移。
 */
@Composable
private fun AboutCard(content: @Composable () -> Unit) = SettingsCard(content = content)

/**
 * 关于页行 = 新体系 `SettingRow`（CMP-LISTROW / `setting` 变体）**直用**，不再自建行骨架。
 *
 * 🔴 2026-09-26 统一（本轮消除的四处偏差）：
 *  1. **分隔线**：原 `outlineVariant.copy(alpha = 0.5f)`（设置族最后一处半透明线）→ `SettingRow` 的
 *     实色 1dp `outlineVariant`，与设置组口径一致；
 *  2. **行内距**：原 `horizontal = 4.dp` → `SettingRow` 的 `16.dp`（卡 14dp + 行 16dp = 文字左缘 30dp，
 *     与设置主页/其余二级页同齐）；
 *  3. **副文案**：原页内自建 `Column` 第二行 → `SettingRow(summary = …)` 内容槽（新增参数，同口径）；
 *  4. **末行抑制**：原 `first` 参数复用为「非首行画线」→ 改走 `SettingRow.showDivider`，语义与设置组一致。
 *
 * `action` / `loading` 仍由本页组合进 `trailing` 槽（业务行为一字未改）。
 */
@Composable
private fun AboutRow(
    icon: String,
    iconBg: Color,
    title: String,
    subtitle: String? = null,
    action: String? = null,
    loading: Boolean = false,
    showDivider: Boolean = true,
    onClick: () -> Unit
) {
    SettingRow(
        icon = icon,
        title = title,
        summary = subtitle,
        iconBg = iconBg,
        showDivider = showDivider,
        trailing = {
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else if (action != null) {
                Text(action, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            Icon(
                appPainter("chevron"),
                contentDescription = null,
                tint = AppColors.textSecondary.copy(alpha = 0.5f),
                modifier = Modifier.size(18.dp)
            )
        },
        onClick = onClick
    )
}
