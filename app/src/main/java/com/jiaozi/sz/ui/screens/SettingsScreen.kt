package com.jiaozi.sz.ui.screens
import com.jiaozi.sz.ui.components.appPainter

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.jiaozi.sz.data.remote.SyncState
import com.jiaozi.sz.data.remote.WebDavConfig
import com.jiaozi.sz.ui.AppViewModel
import com.jiaozi.sz.ui.LocalAppVm
import com.jiaozi.sz.ui.island.IslandBus
import com.jiaozi.sz.ui.components.NavPref
import com.jiaozi.sz.ui.components.PrefChipGroup
import com.jiaozi.sz.ui.components.SettingsSection
import com.jiaozi.sz.ui.components.SwitchPref
import com.jiaozi.sz.xiaomi.FloatingIslandService
import com.jiaozi.sz.domain.AiProvider
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(nav: NavHostController) {
    val appVm: AppViewModel = LocalAppVm.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val theme by appVm.theme.collectAsStateWithLifecycle()
    val dynamicColor by appVm.dynamicColor.collectAsStateWithLifecycle()
    val fontScale by appVm.fontScale.collectAsStateWithLifecycle()
    val targetDay by appVm.targetDay.collectAsStateWithLifecycle()
    val disc by appVm.subject3Disc.collectAsStateWithLifecycle()
    val discList = appVm.repo.discList
    val syncEnabled by appVm.syncEnabled.collectAsStateWithLifecycle()
    val syncState by appVm.syncState.collectAsStateWithLifecycle()

    var aiKeyText by remember { mutableStateOf(appVm.aiKey.value) }
    var showAiDialog by remember { mutableStateOf(false) }
    val aiProvider by appVm.aiProvider.collectAsStateWithLifecycle()
    val aiModel by appVm.aiModel.collectAsStateWithLifecycle()
    var msg by remember { mutableStateOf<String?>(null) }

    // —— WebDAV 配置本地编辑态（初始从已保存配置载入一次）——
    var wdUrl by remember { mutableStateOf("") }
    var wdUser by remember { mutableStateOf("") }
    var wdPass by remember { mutableStateOf("") }
    var wdDir by remember { mutableStateOf("artwb-default") }
    var wdMode by remember { mutableStateOf("two-way") }
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

    // 导出：写 JSON 到用户选定文件
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val json = appVm.repo.exportEnvelope()
                ctx.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                msg = "已导出数据"
            } catch (e: Exception) { msg = "导出失败：${e.message}" }
        }
    }
    // 导入：读取用户选定文件并合并
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val json = ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: ""
                val n = appVm.repo.importEnvelope(json)
                msg = "已合并 $n 条数据"
            } catch (e: Exception) { msg = "导入失败：${e.message}" }
        }
    }

    // 灵动岛：授予悬浮窗权限后启动前台服务（未授权则跳系统设置页引导）
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
     * 该列表不收录第三方应用（用户反馈“找不到 app”）；故小米系直接跳本应用详情页，
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

    val isSyncing = syncState is SyncState.Syncing

    fun currentWebDavConfig() = WebDavConfig(
        url = wdUrl.trim(), user = wdUser.trim(), pass = wdPass,
        remoteDir = wdDir.trim().ifBlank { "artwb-default" }, direction = wdMode.ifBlank { "two-way" },
        encrypt = wdEncrypt, syncPass = wdSyncPass
    )

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

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp).padding(bottom = 76.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // —— 外观（复刻 Miuix 设置页：分组标题 + 偏好行）——
        val themePack by appVm.themePack.collectAsStateWithLifecycle()
        SettingsSection("外观") {
            PrefChipGroup(
                title = "主题模式",
                options = listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色"),
                selected = theme,
                onSelect = { appVm.setTheme(it) }
            )
            PrefChipGroup(
                title = "主题配色",
                summary = "默认墨绿（网页端同款）· 小米蓝/青/墨/锦可选",
                options = listOf("默认" to "墨绿", "小米蓝" to "小米蓝", "青" to "青", "墨" to "墨", "锦" to "锦"),
                selected = themePack,
                onSelect = { appVm.setThemePack(it) }
            )
            PrefChipGroup(
                title = "字体大小",
                summary = "对齐网页端 setFont",
                options = listOf("sm" to "小", "md" to "标准", "lg" to "大", "xl" to "特大"),
                selected = fontScale,
                onSelect = { appVm.setFontScale(it) },
                showDivider = false
            )
            SwitchPref(
                title = "跟随系统壁纸取色",
                summary = "Android 12 以上生效；关闭则使用默认墨绿主题。优先级高于主题配色。",
                checked = dynamicColor,
                onCheckedChange = { appVm.setDynamicColor(it) },
                showDivider = false
            )
        }

        // 灵动岛（上岛）：全局悬浮胶囊 —— 仅为测试功能，不算正式功能（按用户要求标注）
        val islandEnabled by appVm.islandEnabled.collectAsStateWithLifecycle()
        val islandErr by IslandBus.error.collectAsStateWithLifecycle()
        SettingsSection("灵动岛（上岛 · 测试功能）") {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("灵动岛（上岛 · 测试功能）", style = MaterialTheme.typography.titleMedium)
                Text(
                    "开启后，练习中的进度会常驻显示在屏幕顶部胶囊（需授予「悬浮窗」权限），退到其它 App 也能看到实时状态。当前先支持「练习中」场景。",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline
                )
                SwitchPref(
                    title = "启用灵动岛",
                    summary = "测试功能，非正式功能 · 练习中状态常驻顶部胶囊（需悬浮窗权限）",
                    checked = islandEnabled,
                    onCheckedChange = { handleIsland(it) },
                    showDivider = false
                )
                // 未授予悬浮窗权限时显示引导卡（小米/澎湃 OS 上标准入口找不到本应用，需手动在应用设置开启）
                if (islandEnabled && (!Settings.canDrawOverlays(ctx) || islandErr != null)) {
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                "灵动岛无法显示",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            val err = islandErr
                            if (err != null) {
                                Text(err, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                            Text(
                                "请按以下路径手动开启「悬浮窗」权限：\n设置 → 应用 → 综合教资备考平台 → 权限管理 → 显示悬浮窗\n开启后返回本页重新打开开关即可。",
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Button(onClick = { openOverlaySettings(ctx) }) { Text("去授予悬浮窗权限") }
                        }
                    }
                }
            }
        }

        // 目标考试日（倒计时锚点）—— 对齐网页端：用原生 DatePicker，避免手敲格式错误被静默吞掉
        val todayMillis = java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        var picked by remember {
            mutableStateOf(runCatching { java.time.LocalDate.parse(targetDay) }.getOrNull())
        }
        var showDatePicker by remember { mutableStateOf(false) }
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = picked?.atStartOfDay(java.time.ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
                ?: todayMillis
        )
        if (showDatePicker) {
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    Button(onClick = {
                        datePickerState.selectedDateMillis?.let { ms ->
                            picked = java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                        }
                        showDatePicker = false
                    }) { Text("确定") }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) { Text("取消") }
                }
            ) {
                DatePicker(state = datePickerState)
            }
        }
        Text("目标考试日（倒计时锚点）", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { showDatePicker = true }, Modifier.weight(1f)) {
                Text(if (picked != null) "已选：${picked!!.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)}" else "选择日期")
            }
            if (picked != null) {
                TextButton(onClick = { picked = null }) { Text("清除") }
            }
        }
        Button(
            onClick = {
                if (picked != null) {
                    val iso = picked!!.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
                    appVm.setTargetDay(iso)
                    android.widget.Toast.makeText(ctx, "已保存目标日：$iso", android.widget.Toast.LENGTH_SHORT).show()
                } else {
                    appVm.setTargetDay("")
                    android.widget.Toast.makeText(ctx, "已清除目标日", android.widget.Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (picked != null) "保存目标日" else "清除目标日")
        }

        // 科三学科（可折叠，节省空间）
        var discExpanded by remember { mutableStateOf(false) }
        SettingsSection("科三学科") {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    Modifier.fillMaxWidth().clickable { discExpanded = !discExpanded },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("当前科三学科", style = MaterialTheme.typography.titleMedium)
                        Text("影响科三出题与模考 · 当前：$disc", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    }
                    Text(if (discExpanded) "收起 ▴" else "展开 ▾", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                if (discExpanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        discList.forEach { d ->
                            FilterChip(selected = disc == d, onClick = { appVm.setSubject3Disc(d) }, label = { Text(d) })
                        }
                    }
                }
            }
        }

        // AI 出题 / 讲评（需 API Key）
        SettingsSection("AI 出题 / 讲评") {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("AI 出题 / 讲评（需 API Key）", style = MaterialTheme.typography.titleMedium)
                // 服务商选择（deepseek / openai / moonshot）
                Text("服务商", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AiProvider.options().forEach { (id, label) ->
                        FilterChip(
                            selected = aiProvider == id,
                            onClick = { appVm.setAiProvider(id) },
                            label = { Text(label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = aiKeyText,
                    onValueChange = { aiKeyText = it },
                    label = { Text("API Key") },
                    placeholder = { Text(AiProvider.get(aiProvider).hint) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
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
                Button(
                    onClick = {
                        appVm.saveAiKey(aiKeyText.trim())
                        showAiDialog = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("保存 Key 并去出题") }
                if (appVm.aiKey.value.isNotBlank()) {
                    Text(
                        "已保存 Key · 当前服务商：${AiProvider.get(aiProvider).label}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // 多端同步（本地文件）
        SettingsSection("本地数据迁移") {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("本地数据迁移（设备间手动导出/导入合并）", style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("启用同步（打开 App 时自动 WebDAV 同步）", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(checked = syncEnabled, onCheckedChange = { appVm.setSyncEnabled(it) })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { exportLauncher.launch("jiaozi_exam_${System.currentTimeMillis() / 1000}.json") }, Modifier.weight(1f)) { Text("导出数据") }
                    OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json")) }, Modifier.weight(1f)) { Text("导入合并") }
                }
            }
        }

        // WebDAV 远程同步
        SettingsSection("WebDAV 远程同步") {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("WebDAV 远程同步", style = MaterialTheme.typography.titleMedium)
                Text(
                    "配置 WebDAV 服务器（Nextcloud / 群晖 NAS 等），在设备间自动同步学习进度、错题与 AI 题库。同步文件名为 sync.json，与网页端共用同一「空间目录」和「同步口令」即可互通。",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline
                )

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

                // 加密同步
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("同步加密（AES-GCM 零知识）", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "开启后数据以密文存到服务器（服务器不可读）；需所有设备用 https/localhost 打开且口令一致。",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline
                        )
                    }
                    Switch(checked = wdEncrypt, onCheckedChange = { wdEncrypt = it })
                }
                if (wdEncrypt) {
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

                Text("同步方向", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("upload" to "仅上传", "download" to "仅下载", "two-way" to "双向合并").forEach { (v, label) ->
                        FilterChip(selected = wdMode == v, onClick = { wdMode = v }, label = { Text(label) })
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { appVm.saveWebDavConfig(currentWebDavConfig()) },
                        modifier = Modifier.weight(1f)
                    ) { Text("保存配置") }
                    Button(
                        onClick = {
                            val cfg = currentWebDavConfig()
                            appVm.saveWebDavConfig(cfg)
                            appVm.doSync(cfg)
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isSyncing && wdUrl.isNotBlank()
                    ) { Text(if (isSyncing) "同步中…" else "立即同步") }
                }

                // 同步状态提示
                when (val s = syncState) {
                    is SyncState.Syncing -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(modifier = Modifier.padding(0.dp), strokeWidth = 2.dp)
                        Text(s.phase, style = MaterialTheme.typography.bodyMedium)
                    }
                    is SyncState.Success -> Text("✓ ${s.message}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    is SyncState.Error -> Text("✗ ${s.message}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    is SyncState.Idle -> Text("尚未同步", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
            }
        }

        Text("综合教资备考平台 · v1.0", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
    }

    if (showAiDialog) {
        AlertDialog(
            onDismissRequest = { showAiDialog = false },
            title = { Text("去 AI 出题") },
            text = { Text("Key 已保存。到「练习」页底部的「AI 题库」选择科目与数量即可生成。") },
            confirmButton = { TextButton(onClick = { showAiDialog = false; nav.navigate("practice") }) { Text("去练习") } },
            dismissButton = { TextButton(onClick = { showAiDialog = false }) { Text("稍后") } }
        )
    }

    if (msg != null) {
        AlertDialog(
            onDismissRequest = { msg = null },
            title = { Text("提示") },
            text = { Text(msg!!) },
            confirmButton = { TextButton(onClick = { msg = null }) { Text("好") } }
        )
    }
}
