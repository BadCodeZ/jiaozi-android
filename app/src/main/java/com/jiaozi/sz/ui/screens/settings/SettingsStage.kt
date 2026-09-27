@file:OptIn(ExperimentalMaterial3Api::class)

package com.jiaozi.sz.ui.screens

/**
 * 设置二级页 —— 报考学段（路由 `settings_stage`）。
 *
 * 🔴 2026-09-28 学段筛题新增。
 * 官方口径（ntce.neea.edu.cn《中小学教师资格考试(笔试)科目代码列表》）：
 * - 科目一《综合素质》(301) / 科目二《教育知识与能力》(302) —— 备注「**初中、高中相同**」，初高中同卷；
 * - 科目三《学科知识与教学能力》—— **初高中分卷**（初中 303–317 / 高中 403–418）。
 * ⇒ 学段只看科三（及初中独有的「历史与社会 / 科学」、高中独有的「思想政治 / 通用技术」），
 *   但为口径统一，练习链路全科统一受限，见 `BankStore.stageMatches`。
 *
 * 即时生效（同本组其余二级页）：选中即写 `AppViewModel.setExamStage` → meta `exam_stage`。
 */

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.jiaozi.sz.data.BankStore
import com.jiaozi.sz.ui.AppViewModel
import com.jiaozi.sz.ui.LocalAppVm
import com.jiaozi.sz.ui.components.AppColors
import com.jiaozi.sz.ui.components.GroupTitle
import com.jiaozi.sz.ui.components.SettingSelectRow

/** 空值哨兵：`SettingSelectRow` 以 value 匹配，此处用空串代表「不限」。 */
private const val ANY = ""

@Composable
fun SettingsStageScreen(nav: NavHostController) {
    val appVm: AppViewModel = LocalAppVm.current
    val stage by appVm.examStage.collectAsStateWithLifecycle()

    SettingsDetailPage(
        title = "报考学段",
        subtitle = "影响出题范围 · 本学段 + 通用题",
        icon = "exam",
        nav = nav
    ) {
        GroupTitle("学段", modifier = Modifier.padding(top = 8.dp))
        SettingsCard {
            SettingSelectRow(
                icon = "exam",
                title = "当前学段",
                summary = if (stage.isBlank()) {
                    "未设置 · 全部题目可见"
                } else {
                    "仅出「$stage 专属题 + 通用题」· 当前：$stage"
                },
                options = BankStore.STAGE_OPTIONS.map { it to it } + (ANY to "不限"),
                selected = stage,
                iconBg = AppColors.blue,
                onSelect = { appVm.setExamStage(it) },
                showDivider = false
            )
        }

        GroupTitle("说明", modifier = Modifier.padding(top = 8.dp))
        SettingsCard {
            StageNoteRow(
                "科目一 · 科目二",
                "初中、高中同卷 ⇒ 全部题目为通用题，不受学段影响。",
                showDivider = true
            )
            StageNoteRow(
                "科目三 · 学科知识",
                "初中与高中分卷。初中独有「历史与社会 / 科学」，高中独有「思想政治 / 通用技术」。",
                showDivider = true
            )
            StageNoteRow(
                "未设置",
                "不启用筛选，全部题目可见（兼容旧数据与首次安装）。",
                showDivider = false
            )
        }
    }
}

/** 说明行（纯文本、无交互）；内边距与设置卡内既有行对齐。 */
@Composable
private fun StageNoteRow(title: String, desc: String, showDivider: Boolean) {
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 16.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(4.dp))
        Text(desc, style = MaterialTheme.typography.bodySmall, color = AppColors.textSecondary)
    }
    if (showDivider) {
        // 与设置卡内既有行分隔线同口径（实色 1dp outlineVariant，见 SettingSelectRow / SettingRow）
        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
    }
}
