@file:OptIn(ExperimentalMaterial3Api::class)

package com.jiaozi.sz.ui.screens

/**
 * 🔴🔴 设置二级页统一外壳（2026-09-26「方案 B：独立二级页统一跳转」）。
 *
 * ## 背景（本轮要根治的半成品融合）
 * 改造前设置页是**新旧两套组件体系并置**：
 * - 新体系（`RedesignComponents.kt`）：`GroupTitle` + `SettingsCard` + `SettingRow`（实色图标块 + 行尾值 + chevron，实色 1dp 分隔线）；
 * - 旧体系（`Prefs.kt`）：`SettingsSection`（primary 小标题 + **自带 Card**）+ `PrefRow`（无图标，@0.5f 分隔线）。
 *
 * 点行就地展开旧体系 `SettingsSection` ⇒ **卡中套卡**（违反 11 号规范 `SP-SETTINGS-CARD.forbidden`
 * 「卡片套卡片」），且两套底色/圆角/分隔线并置、标题重复（灵动岛甚至同一文案连写两遍）。
 *
 * ## 本外壳确立的三条统一
 * 1. **展开方式统一**：8 个分区一律改为**独立二级页**（`nav.navigate`），设置主页退化为纯导航列表，
 *    彻底消灭手风琴与卡套卡（规范 `SP-SETTINGS-EXPAND` 相应改写，见 11 号 JSON `revision_20260926`）。
 * 2. **页面骨架统一**：全站二级页收敛为「沉浸 Hero（标题即分区名，返回内联左端槽）+ 唯一滚动容器 `weight(1f)`」，
 *    与教材/课标库/章节/知识库/图谱/设置等既有的 9 页**同一形态**。
 * 3. **标题唯一**：页名由 Hero 承载，页内**不再出现** `SettingsSection(...)` 的旧体系小标题 ——
 *    此前「GroupTitle → 行标题 → SettingsSection 标题」三重复（灵动岛为四重复）被消除。
 *
 * ⚠️ 兼容红线不变：本文件只提供外壳，**不触碰** AppViewModel 的任何读写与 SharedPreferences key。
 */

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.jiaozi.sz.ui.components.AppColors
import com.jiaozi.sz.ui.components.HeroHeader
import com.jiaozi.sz.ui.components.appPainter

/**
 * 设置二级页外壳：沉浸 Hero + 唯一滚动容器。
 *
 * 布局铁律（与主索引同）：**外框只放 Hero 与常驻栏，唯一滚动容器 `weight(1f)`**。
 *
 * @param title   页名，同时是 Hero 标题 —— **页内不要再写同名标题**。
 * @param subtitle Hero 副标题。红线：14sp 在「返回槽 + action 槽」下可用宽 ≈219dp ≈ **15 个中文字**，
 *                 必须单行可容纳；多行须按 `'\n'` 拆 `Text(maxLines = 1)`，禁单 `Text(maxLines = 2)`。
 * @param icon    Hero 装饰图标名（走 `decorIcon`，**不要**用 `icon` + `onBack` 组合）。
 */
@Composable
internal fun SettingsDetailPage(
    title: String,
    subtitle: String,
    icon: String,
    nav: NavHostController,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(AppColors.bg)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        HeroHeader(
            title,
            subtitle,
            decorIcon = appPainter(icon),
            immersive = true,
            onBack = { nav.navigateUp() }
        )
        // 滚动区：底部 76dp 避让悬浮导航栏（11 号规范：settings / about 均显示底部导航栏）
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 76.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

/**
 * 设置分组卡（11 号规范 `SP-SETTINGS-CARD`）。
 *
 * 原为 `SettingsScreen.kt` 私有件，2026-09-26 因 8 个二级页要共用而**上提为本文件公开件**（形态一字未改）。
 *
 * 🔴 **`forbidden` 仍生效**：**卡片套卡片** —— 卡内禁止再放 Card / `SettingsSection`（后者自带卡片）。
 * 展开体一律改为二级页，卡内只放行与行内控件。
 *
 * @param containerColor 卡底色。默认 `surfaceContainer`（规范基准）；
 *                       仅**语义提示卡**（如灵动岛悬浮窗权限引导）可传 `errorContainer` 等语义色，
 *                       但**仍须**沿用本卡的 r16 / 内距规格，禁止另起裸 `Card`（`SP-SETTINGS-CARD.forbidden`）。
 */
@Composable
internal fun SettingsCard(
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)) {
            content()
        }
    }
}
