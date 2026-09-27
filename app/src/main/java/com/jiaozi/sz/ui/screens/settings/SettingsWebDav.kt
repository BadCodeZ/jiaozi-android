@file:OptIn(ExperimentalMaterial3Api::class)

package com.jiaozi.sz.ui.screens

/**
 * 设置二级页 —— WebDAV 同步（路由 `settings_webdav`）。
 *
 * 🔴 2026-09-26 方案 B 重写，并统一**保存时机**：
 * 旧实现要手动点「保存配置」，与外观/科三的即时生效并存两套语义；
 * 且文本字段只存在本地 state，**不点保存直接返回 ⇒ 配置静默丢失**。
 *
 * 现改为**防抖自动保存**：任一字段变更后 600ms 自动写入（停止输入才落盘，避免逐字符写 SP）。
 * 「保存配置」按钮删除；「立即同步」保留（它是**动作**而非保存）。
 * 页内补「修改后自动保存」说明，消除「到底存没存」的疑虑。
 *
 * 兼容红线：`appVm.saveWebDavConfig` / `doSync` 与 SharedPreferences key **一字未改**。
 */

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.jiaozi.sz.data.remote.SyncState
import com.jiaozi.sz.data.remote.WebDavConfig
import com.jiaozi.sz.ui.AppViewModel
import com.jiaozi.sz.ui.LocalAppVm
import com.jiaozi.sz.ui.components.AppColors
import com.jiaozi.sz.ui.components.GroupTitle
import com.jiaozi.sz.ui.components.SettingSelectRow
import com.jiaozi.sz.ui.components.SettingSwitchRow
import com.jiaozi.sz.ui.components.appPainter
import com.jiaozi.sz.util.PATTERN_DATE_TIME
import com.jiaozi.sz.util.formatTs
import kotlinx.coroutines.delay

@Composable
fun SettingsWebDavScreen(nav: NavHostController) {
    val appVm: AppViewModel = LocalAppVm.current

    // —— WebDAV 配置本地编辑态（初始从已保存配置载入一次）——
    var wdUrl by remember { mutableStateOf("") }
    var wdUser by remember { mutableStateOf("") }
    var wdPass by remember { mutableStateOf("") }
    var wdDir by remember { mutableStateOf("") }
    var wdMode by remember { mutableStateOf("") }
    var wdEncrypt by remember { mutableStateOf(false) }
    var wdSyncPass by remember { mutableStateOf("") }
    var pwVisible by remember { mutableStateOf(false) }
    var wdLoaded by remember { mutableStateOf(false) }

    if (!wdLoaded) {
        wdUrl = appVm.webDavUrl.value
        wdUser = appVm.webDavUser.value
        wdPass = appVm.webDavPass.value
        wdDir = appVm.webDavDir.value.ifBlank { "artwb-default" }
        wdMode = appVm.webDavMode.value.ifBlank { "two-way" }
        wdEncrypt = appVm.webDavEncrypt.value
        wdSyncPass = appVm.webDavSyncPass.value
        wdLoaded = true
    }

    val syncEnabled by appVm.syncEnabled.collectAsStateWithLifecycle()
    val syncState by appVm.syncState.collectAsStateWithLifecycle()
    val lastSyncAt by appVm.lastSyncAt.collectAsStateWithLifecycle()
    val isSyncing = syncState is SyncState.Syncing

    fun currentWebDavConfig() = WebDavConfig(
        url = wdUrl.trim(), user = wdUser.trim(), pass = wdPass,
        remoteDir = wdDir.trim().ifBlank { "artwb-default" }, direction = wdMode.ifBlank { "two-way" },
        encrypt = wdEncrypt, syncPass = wdSyncPass
    )

    // 🔴 即时生效：任一字段变更后 600ms 防抖落盘（首次载入不写，避免无谓回写）
    var skipFirstSave by remember { mutableStateOf(true) }
    LaunchedEffect(wdUrl, wdUser, wdPass, wdDir, wdMode, wdEncrypt, wdSyncPass) {
        if (skipFirstSave) { skipFirstSave = false; return@LaunchedEffect }
        delay(600)
        appVm.saveWebDavConfig(currentWebDavConfig())
    }

    SettingsDetailPage(title = "WebDAV 同步", subtitle = "多设备自动同步", icon = "layers", nav = nav) {
        GroupTitle("服务器", modifier = Modifier.padding(top = 8.dp))
        SettingsCard {
            // 服务商预设：就地展开选择，选常用服务自动填好地址模板
            val wdPresetDefs = listOf(
                "坚果云" to "https://dav.jianguoyun.com/dav/",
                "Nextcloud" to "https://你的域名/remote.php/dav/files/用户名/",
                "群晖 NAS" to "https://你的NAS:5006/remote.php/dav/files/用户名/",
                "其它" to ""
            )
            SettingSelectRow(
                icon = "link",
                title = "服务商预设",
                options = wdPresetDefs.map { (name, _) -> name to name },
                selected = wdPresetDefs.firstOrNull { it.second == wdUrl }?.first ?: "其它",
                iconBg = AppColors.blue,
                onSelect = { name ->
                    val tpl = wdPresetDefs.firstOrNull { it.first == name }?.second ?: ""
                    wdUrl = tpl
                    if (tpl.isNotEmpty()) wdDir = "artwb-default"
                }
            )
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = wdUrl, onValueChange = { wdUrl = it },
                    label = { Text("服务器地址（含协议，如 https://dav.example.com）") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = wdUser, onValueChange = { wdUser = it },
                        label = { Text("用户名") }, modifier = Modifier.weight(1f), singleLine = true
                    )
                    OutlinedTextField(
                        value = wdPass, onValueChange = { wdPass = it },
                        label = { Text("密码") }, modifier = Modifier.weight(1f), singleLine = true,
                        visualTransformation = if (pwVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { pwVisible = !pwVisible }) {
                                Icon(if (pwVisible) appPainter("eye_off") else appPainter("eye"), contentDescription = "切换密码可见")
                            }
                        }
                    )
                }
                OutlinedTextField(
                    value = wdDir, onValueChange = { wdDir = it },
                    label = { Text("空间目录（不含首尾斜杠，如 artwb-default）") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                Text(
                    "修改后自动保存，无需点保存按钮。",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.textSecondary
                )
            }
        }

        GroupTitle("同步", modifier = Modifier.padding(top = 8.dp))
        SettingsCard {
            SettingSwitchRow(
                icon = "cloud",
                title = "启用同步",
                summary = "打开 App 与后台定期自动同步",
                checked = syncEnabled,
                iconBg = AppColors.blue,
                onCheckedChange = { appVm.setSyncEnabled(it) }
            )

            SettingSwitchRow(
                icon = "key",
                title = "同步加密（AES-GCM 零知识）",
                summary = "开启后数据以密文存到服务器（服务器不可读）；需所有设备用 https/localhost 打开且口令一致。",
                checked = wdEncrypt,
                iconBg = AppColors.purple,
                onCheckedChange = { wdEncrypt = it }
            )

            if (wdEncrypt) {
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    OutlinedTextField(
                        value = wdSyncPass, onValueChange = { wdSyncPass = it },
                        label = { Text("同步口令（两端须一致）") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        visualTransformation = if (pwVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { pwVisible = !pwVisible }) {
                                Icon(if (pwVisible) appPainter("eye_off") else appPainter("eye"), contentDescription = "切换口令可见")
                            }
                        }
                    )
                }
            }

            SettingSelectRow(
                icon = "arrow",
                title = "同步方向",
                options = listOf("upload" to "仅上传", "download" to "仅下载", "two-way" to "双向合并"),
                selected = wdMode,
                iconBg = AppColors.blue,
                onSelect = { wdMode = it },
                showDivider = false
            )
        }

        GroupTitle("操作", modifier = Modifier.padding(top = 8.dp))
        SettingsCard {
            Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        val cfg = currentWebDavConfig()
                        appVm.saveWebDavConfig(cfg)
                        appVm.doSync(cfg)
                    },
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    enabled = !isSyncing && wdUrl.isNotBlank()
                ) { Text(if (isSyncing) "同步中…" else "立即同步") }

                if (lastSyncAt > 0) {
                    Text(
                        "上次同步：${formatTs(lastSyncAt, PATTERN_DATE_TIME)}",
                        style = MaterialTheme.typography.labelSmall, color = AppColors.textSecondary
                    )
                }
                // 同步状态提示
                when (val s = syncState) {
                    is SyncState.Syncing -> Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.padding(0.dp), strokeWidth = 2.dp)
                        Text(s.phase, style = MaterialTheme.typography.bodyMedium)
                    }
                    is SyncState.Success -> {
                        Text("✓ ${s.message}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        s.report?.let { r ->
                            // 🔴 内嵌结果面板：**不**用 Card —— 本块已在 SettingsCard 内，再套 Card 即
                            // 「卡片套卡片」（11 号规范 SP-SETTINGS-CARD.forbidden）。改为同色系内嵌面板
                            // （无边框 / 无 elevation，仅底色 + r12），视觉上仍是「卡内分区」而非第二层卡片。
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("本次合并报告", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                Text("合计 ${r.total} 条 · 冲突已解决 ${r.conflicts} · 数据水位 ${r.maxMt}", style = MaterialTheme.typography.bodySmall)
                                val lines = buildList {
                                    if (r.examAdded > 0 || r.examUpdated > 0) add("题库：新增 ${r.examAdded} / 更新 ${r.examUpdated}")
                                    if (r.lessonAdded > 0 || r.lessonUpdated > 0) add("教案：新增 ${r.lessonAdded} / 更新 ${r.lessonUpdated}")
                                    if (r.curricAdded > 0 || r.curricUpdated > 0) add("课标：新增 ${r.curricAdded} / 更新 ${r.curricUpdated}")
                                    if (r.booksAdded > 0 || r.booksUpdated > 0) add("教材：新增 ${r.booksAdded} / 更新 ${r.booksUpdated}")
                                    if (r.qstat > 0) add("进度统计：${r.qstat}")
                                    if (r.corrections > 0) add("错题本：${r.corrections}")
                                    if (r.inboxAdded > 0 || r.inboxUpdated > 0) add("收集箱：新增 ${r.inboxAdded} / 更新 ${r.inboxUpdated}")
                                    if (r.aiHistoryAdded > 0 || r.aiHistoryUpdated > 0) add("AI 对话：新增 ${r.aiHistoryAdded} / 更新 ${r.aiHistoryUpdated}")
                                    if (r.removed > 0) add("移除：${r.removed}")
                                }
                                lines.forEach { Text("· $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                        }
                    }
                    is SyncState.Error -> Text("✗ ${s.message}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    is SyncState.Idle -> Text("尚未同步", style = MaterialTheme.typography.labelSmall, color = AppColors.textSecondary)
                }
            }
        }
    }
}
