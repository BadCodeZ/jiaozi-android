@file:OptIn(ExperimentalMaterial3Api::class)

package com.jiaozi.sz.ui.screens

/**
 * 设置二级页 —— AI 配置（路由 `settings_ai`）。
 *
 * 🔴 2026-09-26 方案 B 重写，并统一**保存时机**：
 * 旧实现 Key 存在本地 state，**必须点「保存 Key 并去出题」**才写入，随后弹 AlertDialog 引导去练习 ——
 * 与外观/科三的即时生效是两套语义，且直接返回会静默丢失 Key。
 *
 * 现改为**防抖自动保存**（600ms），删除保存按钮与引导弹窗（弹窗改为页内常驻的「去练习出题」入口行，
 * 用户想走再走，不再被打断）。
 *
 * 🔴 凭据红线沿用：`API Key` **默认掩码**（`PasswordVisualTransformation`），eye 键临时切明文。
 */

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.jiaozi.sz.domain.AiProvider
import com.jiaozi.sz.ui.AppViewModel
import com.jiaozi.sz.ui.LocalAppVm
import com.jiaozi.sz.ui.components.AppColors
import com.jiaozi.sz.ui.components.GroupTitle
import com.jiaozi.sz.ui.components.SettingRow
import com.jiaozi.sz.ui.components.SettingSelectRow
import com.jiaozi.sz.ui.components.appPainter
import kotlinx.coroutines.delay

@Composable
fun SettingsAiScreen(nav: NavHostController) {
    val appVm: AppViewModel = LocalAppVm.current
    var aiKeyText by remember { mutableStateOf(appVm.aiKey.value) }
    var aiKeyVisible by remember { mutableStateOf(false) }
    val aiProvider by appVm.aiProvider.collectAsStateWithLifecycle()
    val aiModel by appVm.aiModel.collectAsStateWithLifecycle()

    // 🔴 即时生效：Key 变更后 600ms 防抖落盘（首次载入不写）
    var skipFirstSave by remember { mutableStateOf(true) }
    LaunchedEffect(aiKeyText) {
        if (skipFirstSave) { skipFirstSave = false; return@LaunchedEffect }
        delay(600)
        appVm.saveAiKey(aiKeyText.trim())
    }

    SettingsDetailPage(title = "AI 配置", subtitle = "服务商、Key 与模型", icon = "key", nav = nav) {
        GroupTitle("服务商", modifier = Modifier.padding(top = 8.dp))
        SettingsCard {
            SettingSelectRow(
                icon = "key",
                title = "服务商",
                summary = "默认模型：${AiProvider.get(aiProvider).defaultModel}",
                options = AiProvider.options(),
                selected = aiProvider,
                iconBg = AppColors.blue,
                onSelect = { appVm.setAiProvider(it) },
                showDivider = false
            )
        }

        GroupTitle("凭据", modifier = Modifier.padding(top = 8.dp))
        SettingsCard {
            Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = aiKeyText,
                    onValueChange = { aiKeyText = it },
                    label = { Text("API Key") },
                    placeholder = { Text(AiProvider.get(aiProvider).hint) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (aiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { aiKeyVisible = !aiKeyVisible }) {
                            Icon(
                                if (aiKeyVisible) appPainter("eye_off") else appPainter("eye"),
                                contentDescription = "切换 API Key 可见"
                            )
                        }
                    }
                )
                // 模型（可选，覆盖该服务商默认模型）
                OutlinedTextField(
                    value = aiModel,
                    onValueChange = { appVm.setAiModel(it) },
                    label = { Text("模型（可选，留空用默认）") },
                    placeholder = { Text("默认：${AiProvider.get(aiProvider).defaultModel}") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Text(
                    "修改后自动保存，无需点保存按钮。",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.textSecondary
                )
            }
        }

        GroupTitle("下一步", modifier = Modifier.padding(top = 8.dp))
        SettingsCard {
            SettingRow(
                icon = "play",
                title = "去练习出题",
                value = "到练习页生成题目",
                iconBg = AppColors.blue,
                showDivider = false
            ) { nav.navigate("practice") }
        }

        if (appVm.aiKey.value.isNotBlank()) {
            GroupTitle("状态", modifier = Modifier.padding(top = 8.dp))
            SettingsCard {
                Text(
                    "已保存 Key · 当前服务商：${AiProvider.get(aiProvider).label}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(vertical = 10.dp)
                )
            }
        }
    }
}
