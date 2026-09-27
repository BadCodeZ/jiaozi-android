package com.jiaozi.sz.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/** 反馈入口（与关于页「作者主页」同一出口，见 SettingsAbout.kt:138） */
private const val FEEDBACK_URL = "https://github.com/BadCodeZ"

/**
 * 崩溃诊断屏：显示上次运行遗留的崩溃栈（App.kt 全局处理器写入 filesDir/crash.log）。
 * 用于无 logcat 的真机环境，把真实异常栈直接呈现给用户复制反馈。
 *
 * 🔴 2026-09-21 按 14 号高保真稿（导航与浮层总览）重写：
 * - 标题「页面出现了问题」、正文「我们已记录该错误，您可以尝试重新进入应用，或联系客服。」
 *   （原「检测到上次运行崩溃 / 请把下方崩溃栈复制并发给开发者」为稿前旧文案）
 * - 动作由「复制崩溃栈 + 关闭并返回」改为稿面的「复制错误信息 + 提交反馈」
 *   （规范 CMP-CRASH anatomy：『标题 + 错误摘要 + 复制/反馈动作』）
 * - 背景由 `errorContainer`（红）改为浅色 `surface` —— 稿面实测 #F8FAFE；
 *   且 14 号 forbidden 仅禁「HeroHeader 渐变」，并未要求红色告警底。
 * - 🔴 退出靠**系统返回键**（本屏由 MainActivity 直接 setContent，无导航可回退 ⇒
 *   返回键必然 finish）；故不再设「关闭并返回」按钮，与稿面按钮数一致。
 */
@Composable
fun CrashScreen(text: String) {
    val ctx = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val cs = MaterialTheme.colorScheme

    /** 复制崩溃栈并提示；[then] 为复制后的附加动作（反馈用）。 */
    fun copyStack(then: () -> Unit = {}) {
        clipboard.setText(AnnotatedString(text))
        Toast.makeText(ctx, "已复制错误信息", Toast.LENGTH_SHORT).show()
        then()
    }

    MaterialTheme {
        Column(
            Modifier
                .fillMaxSize()
                .background(cs.surface)
                .padding(20.dp)
        ) {
            Text(
                "页面出现了问题",
                style = MaterialTheme.typography.titleLarge,
                color = cs.onSurface
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "我们已记录该错误，您可以尝试重新进入应用，或联系客服。",
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(cs.surfaceContainerLow)
                    .padding(10.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = cs.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { copyStack() }) { Text("复制错误信息") }
                OutlinedButton(
                    onClick = {
                        copyStack {
                            runCatching {
                                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(FEEDBACK_URL)))
                            }
                        }
                    }
                ) { Text("提交反馈") }
            }
        }
    }
}
