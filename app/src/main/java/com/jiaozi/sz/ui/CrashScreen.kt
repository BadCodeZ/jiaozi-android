package com.jiaozi.sz.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/**
 * 崩溃诊断屏：显示上次运行遗留的崩溃栈（App.kt 全局处理器写入 filesDir/crash.log）。
 * 用于无 logcat 的真机环境，把真实异常栈直接呈现给用户复制反馈。
 */
@Composable
fun CrashScreen(text: String, onClose: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    MaterialTheme {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer).padding(16.dp)) {
            Text(
                "检测到上次运行崩溃",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "请把下方崩溃栈复制并发给开发者，即可精准定位修复（复制到聊天软件或备忘录均可）。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier.fillMaxWidth().weight(1f)
                    .background(Color.Black.copy(alpha = 0.06f))
                    .padding(10.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { clipboard.setText(AnnotatedString(text)) }) { Text("复制崩溃栈") }
                OutlinedButton(onClick = onClose) { Text("关闭并返回") }
            }
        }
    }
}
