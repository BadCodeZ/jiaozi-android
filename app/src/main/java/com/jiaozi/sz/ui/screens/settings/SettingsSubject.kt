@file:OptIn(ExperimentalMaterial3Api::class)

package com.jiaozi.sz.ui.screens

/**
 * 设置二级页 —— 科目三学科（路由 `settings_subject`）。
 *
 * 🔴 2026-09-26 方案 B 重写：去掉旧体系 `SettingsSection` 的卡与重复小标题，标题交给 Hero。
 * 🔴 2026-09-28 名称对齐大纲：科目三官方名《学科知识与教学能力》，下设 17 个子科目。
 * 即时生效（原本就是即时，本页仅换外壳）。
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

@Composable
fun SettingsSubjectScreen(nav: NavHostController) {
    val appVm: AppViewModel = LocalAppVm.current
    val disc by appVm.subject3Disc.collectAsStateWithLifecycle()
    val discList = appVm.repo.discList

    SettingsDetailPage(title = "科目三 · 学科", subtitle = "《学科知识与教学能力》· 影响出题与模考", icon = "school", nav = nav) {
        GroupTitle("学科", modifier = Modifier.padding(top = 8.dp))
        SettingsCard {
            SettingSelectRow(
                icon = "school",
                title = "当前学科",
                summary = "影响科目三出题与模考 · 当前：$disc",
                options = discList.map { it to it },
                selected = disc,
                iconBg = AppColors.blue,
                onSelect = { appVm.setSubject3Disc(it) },
                showDivider = false
            )
        }
    }
}
