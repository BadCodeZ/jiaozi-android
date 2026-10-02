package com.jiaozi.sz.ui.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/**
 * 轻量 Markdown 内联渲染（2026-10-02 新增）。
 *
 * 背景：AI 助手回复由模型直出，普遍带 Markdown 强调标记（`**笔试成绩**`、`**4月中下旬**`）。
 * 原实现用 `Text(String)` 直出 ⇒ 界面把星号原样显示出来，观感很差（杰哥真机截图反馈）。
 *
 * 策略：只处理**成对的 `**` 加粗** —— 转成真正的 [FontWeight.Bold] Span，星号本身不显示；
 * 落单的 `**`（模型偶发不对称输出）直接吞掉，不留残号。其余 Markdown（`- ` 列表等）原样保留，
 * 不做解析（本应用气泡内不需要标题 / 链接 / 代码块）。
 *
 * 注：不走 `Html.fromHtml` / 第三方 Markdown 库 —— 零新依赖，且能同时覆盖**流式输出中的半截文本**
 * （`**` 尚未闭合时也不会崩，最多把当帧剩余文本当普通段落）。
 */
fun String.toRichText(): AnnotatedString = buildAnnotatedString {
    // 以 `**` 切分：偶数段为普通文本、奇数段为加粗文本（空段自动跳过）
    split("**").forEachIndexed { idx, seg ->
        if (seg.isEmpty()) return@forEachIndexed
        if (idx % 2 == 1) {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(seg) }
        } else {
            append(seg)
        }
    }
}
