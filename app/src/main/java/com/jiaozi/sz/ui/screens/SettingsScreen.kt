package com.jiaozi.sz.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.jiaozi.sz.data.importEnvelopeFrom
import com.jiaozi.sz.ui.AppViewModel
import com.jiaozi.sz.ui.LocalAppVm
import com.jiaozi.sz.ui.components.AppColors
import com.jiaozi.sz.ui.components.CollapsingTopBlocks
import com.jiaozi.sz.ui.components.GroupTitle
import com.jiaozi.sz.ui.components.HeroHeader
import com.jiaozi.sz.ui.components.SettingRow
import com.jiaozi.sz.ui.components.appPainter
import com.jiaozi.sz.ui.components.hubDragToScroll
import kotlinx.coroutines.launch

/**
 * 设置主页（路由 `settings`）—— **纯导航列表**（对齐高保真图 2-1）。
 *
 * 🔴🔴 2026-09-26 重构（方案 B「独立二级页统一跳转」）：
 *
 * **改造前**：点击行**就地展开**各分区组件（`AnimatedVisibility` + 手风琴 `toggle`）。
 * 问题是展开体用的是另一套组件体系（`Prefs.kt` 的 `SettingsSection`，自带 Card + primary 小标题），
 * 与外层新体系（`GroupTitle` / `SettingsCard` / `SettingRow`）并置 ⇒
 * **卡中套卡、两种底色圆角、两套分隔线口径、标题三重复**（灵动岛甚至四重复）—— 即杰哥所指
 * 「新界面与旧界面的半成品式融合」。且违反 11 号规范 `SP-SETTINGS-CARD.forbidden`「卡片套卡片」。
 *
 * **改造后**：8 个分区全部成为**独立二级页**（`settings_appearance` … `settings_onboarding`），
 * 主页退化为**纯导航列表**（12 行入口 + 分组过滤）。收益：
 * 1. 消灭卡套卡 —— 每个二级页只有**一层**卡；
 * 2. 消灭标题重复 —— 页名由各页 Hero 承载，页内不再有旧体系小标题；
 * 3. 消灭手风琴 —— 不存在「展开态无反馈」问题（chevron 恒为「进入」语义，不再需要 ▸/▴ 切换）；
 * 4. 操作逻辑连贯 —— 全站二级页同一形态（沉浸 Hero + 唯一滚动容器）。
 *
 * ⚠️ 兼容性红线不变：本文件**只换外壳**，所有设置项仍走 `AppViewModel` 的既有读写
 * （SharedPreferences / meta 的 key 与存储结构一字未改）⇒ 旧版本升级零迁移、零丢失。
 * 行尾显示的当前值均为**只读摘要**，不写任何新字段。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(nav: NavHostController) {
    val appVm: AppViewModel = LocalAppVm.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    val theme by appVm.theme.collectAsStateWithLifecycle()
    val island by appVm.islandEnabled.collectAsStateWithLifecycle()
    val targetDay by appVm.targetDay.collectAsStateWithLifecycle()
    val disc by appVm.subject3Disc.collectAsStateWithLifecycle()
    val stage by appVm.examStage.collectAsStateWithLifecycle()
    val aiKey by appVm.aiKey.collectAsStateWithLifecycle()

    var msg by remember { mutableStateOf<String?>(null) }
    var groupFilter by remember { mutableStateOf("全部") }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }

    // 「数据导入」是唯一**不进二级页**的入口：它没有配置项，直接拉起系统文件选择器
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        pendingImportUri = uri
    }

    val scrollState = rememberScrollState()

    // 过滤 chips：Hero 下方一行。
    // 🔴 2026-09-26 增至 5 项：新增「AI 设置」—— 此前 AI 组仅能在「全部」下出现，
    //    选任一专项 chip 都整体消失且**没有任何 chip 能筛出它**（4 chip 与 4 分组不是一一对应）。
    val filterChips: @Composable RowScope.() -> Unit = {
        listOf("全部", "偏好设置", "数据与同步", "AI 设置", "其他").forEach { g ->
            FilterChip(selected = groupFilter == g, onClick = { groupFilter = g }, label = { Text(g, fontSize = 13.sp) })
        }
    }

    // 两段式布局：顶部固定带（Hero + 过滤 chip 行，常驻）+ 滚动区 weight(1f)
    Column(
        Modifier
            .fillMaxSize()
            .background(AppColors.bg)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // ── 固定带：Hero + 过滤 chip 行（常驻，手势直通下方列表）──
        CollapsingTopBlocks(spacing = 14.dp, modifier = Modifier.hubDragToScroll(scrollState)) {
            HeroHeader(
                "设置", "个性化你的备考工具",
                // 🔴 2026-09-23：齿轮改走 decorIcon —— 旧 icon 参数在传 onBack 时会被左端槽吞掉
                decorIcon = appPainter("settings"),
                // 🔴 2026-09-22 二级 Hero 统一沉浸通栏
                immersive = true, onBack = { nav.navigateUp() }
            )
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                content = filterChips
            )
        }

        // ── 滚动区：纯导航列表（weight(1f) 承接剩余高度）──
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(bottom = com.jiaozi.sz.ui.components.NavTokens.ContentBottomPad),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (groupFilter in listOf("全部", "偏好设置")) {
                GroupTitle("偏好设置", modifier = Modifier.padding(top = 8.dp))
                SettingsCard {
                    SettingRow(
                        icon = "palette", title = "外观主题", value = themeLabel(theme),
                        iconBg = AppColors.purple, trailing = { RowIcon() }
                    ) { nav.navigate("settings_appearance") }

                    // 🔴 灵动岛不再在行内放 Switch：开关已移入二级页，本行只承担「进入 + 显示当前值」，
                    //    消除「整行可点 + 行内开关」的双重语义冲突。
                    SettingRow(
                        icon = "island", title = "灵动岛", value = if (island) "已开启" else "已关闭",
                        iconBg = AppColors.teal, trailing = { RowIcon() }
                    ) { nav.navigate("settings_island") }

                    SettingRow(
                        icon = "target", title = "学习目标", value = targetDay.ifBlank { "未设置考试日" },
                        iconBg = AppColors.blue, trailing = { RowIcon() }
                    ) { nav.navigate("settings_goal") }

                    SettingRow(
                        icon = "school", title = "科目三 · 学科", value = disc.ifBlank { "未设置" },
                        iconBg = AppColors.danger, trailing = { RowIcon() }
                    ) { nav.navigate("settings_subject") }

                    // 🔴 2026-09-28 学段筛题：报考学段入口（与「科目三 · 学科」并列，
                    //    二者共同决定练习出题范围：学科定「哪个学科」，学段定「哪个学段的题」）
                    SettingRow(
                        icon = "exam", title = "报考学段", value = stage.ifBlank { "不限" },
                        iconBg = AppColors.purple, trailing = { RowIcon() }, showDivider = false
                    ) { nav.navigate("settings_stage") }
                }
            }

            if (groupFilter in listOf("全部", "数据与同步")) {
                GroupTitle("数据与同步", modifier = Modifier.padding(top = 8.dp))
                SettingsCard {
                    SettingRow(
                        icon = "cloud", title = "本地备份", iconBg = AppColors.blue, trailing = { RowIcon() }
                    ) { nav.navigate("settings_backup") }

                    SettingRow(
                        icon = "layers", title = "WebDAV同步",
                        // 🔴 2026-09-21 按稿：WebDAV 图标底色 = 紫（稿实测紫 layers）；原蓝与相邻「本地备份」撞色
                        iconBg = AppColors.purple, trailing = { RowIcon() }
                    ) { nav.navigate("settings_webdav") }

                    SettingRow(
                        icon = "import", title = "数据导入", value = "导入本地文件",
                        iconBg = AppColors.success, trailing = { RowIcon() }, showDivider = false
                    ) { importLauncher.launch(arrayOf("application/json", "text/plain")) }
                }
            }

            // 🔴 2026-09-26：AI 组由「仅『全部』可见」改为**独立可筛**（新增「AI 设置」chip）
            if (groupFilter in listOf("全部", "AI 设置")) {
                GroupTitle("AI 设置", modifier = Modifier.padding(top = 8.dp))
                SettingsCard {
                    SettingRow(
                        icon = "key", title = "AI 配置", value = if (aiKey.isBlank()) "未配置" else "已配置",
                        iconBg = AppColors.purple, trailing = { RowIcon() }, showDivider = false
                    ) { nav.navigate("settings_ai") }
                }
            }

            if (groupFilter in listOf("全部", "其他")) {
                GroupTitle("其他", modifier = Modifier.padding(top = 8.dp))
                SettingsCard {
                    SettingRow(
                        icon = "bulb", title = "新手引导", iconBg = AppColors.warning, trailing = { RowIcon() }
                    ) { nav.navigate("settings_onboarding") }

                    SettingRow(
                        icon = "info", title = "关于", iconBg = AppColors.textSecondary,
                        trailing = { RowIcon() }, showDivider = false
                    ) { nav.navigate("about") }
                }
            }
        }
    }

    // ── 以下弹窗与旧版完全一致（行为未变）──
    if (msg != null) {
        AlertDialog(
            onDismissRequest = { msg = null },
            title = { Text("提示") },
            text = { Text(msg!!) },
            confirmButton = { TextButton(onClick = { msg = null }) { Text("好") } }
        )
    }

    if (pendingImportUri != null) {
        AlertDialog(
            onDismissRequest = { pendingImportUri = null },
            title = { Text("确认导入合并") },
            text = { Text("所选备份将与本地数据按「时间较新者胜出」合并，不会删除本地独有数据（错题/进度/自定义题库/备课）。确认继续？") },
            confirmButton = {
                TextButton(onClick = {
                    val uri = pendingImportUri!!
                    pendingImportUri = null
                    scope.launch {
                        try {
                            val report = importEnvelopeFrom(ctx, appVm.repo, uri)
                            msg = "已合并 ${report.total} 条数据"
                        } catch (e: Exception) { msg = "导入失败：${e.message}" }
                    }
                }) { Text("合并") }
            },
            dismissButton = { TextButton(onClick = { pendingImportUri = null }) { Text("取消") } }
        )
    }
}

/** 主题值的中文摘要（读不到时回退「跟随系统」，不写任何新字段）。 */
private fun themeLabel(theme: String): String = when (theme) {
    "light" -> "浅色"
    "dark" -> "深色"
    else -> "跟随系统"
}

/**
 * 行尾 chevron（**进入**语义）。
 *
 * 🔴 2026-09-26：改造前本件同时承担「展开/收起」指示，但**从不随展开态变化**
 * （11 号规范线框要求「收起 ▸ / 展开 ▴」，实现完全没做）。改为二级页后，
 * 本件只需表达「可进入」，不再需要两态切换。
 */
@Composable
private fun RowIcon() {
    Icon(
        appPainter("chevron"),
        contentDescription = null,
        tint = AppColors.textSecondary.copy(alpha = 0.5f),
        modifier = Modifier.padding(start = 4.dp)
    )
}
