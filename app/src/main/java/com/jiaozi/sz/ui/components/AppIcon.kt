package com.jiaozi.sz.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import com.jiaozi.sz.R

/**
 * 自研描边线性图标（复刻网页端/预览方案 ICONS sprite：stroke-width=2、currentColor 风格），
 * 替代 Material 实心图标，使安卓端视觉与网页端一致。
 * 用法：Icon(appPainter("play"), contentDescription = null)
 */
@Composable
fun appPainter(name: String): Painter {
    val res = when (name) {
        "today" -> R.drawable.ic_today
        "edit" -> R.drawable.ic_edit
        "book" -> R.drawable.ic_book
        "bars" -> R.drawable.ic_bars
        "chart" -> R.drawable.ic_chart
        "person" -> R.drawable.ic_person
        "gear" -> R.drawable.ic_gear
        "settings" -> R.drawable.ic_gear
        "chat" -> R.drawable.ic_chat
        "lesson" -> R.drawable.ic_lesson
        "inbox" -> R.drawable.ic_inbox
        "proof" -> R.drawable.ic_proof
        "graph" -> R.drawable.ic_graph
        "search" -> R.drawable.ic_search
        "palette" -> R.drawable.ic_palette
        "text" -> R.drawable.ic_text
        "moon" -> R.drawable.ic_moon
        "calendar" -> R.drawable.ic_calendar
        "key" -> R.drawable.ic_key
        "cloud" -> R.drawable.ic_cloud
        "bell" -> R.drawable.ic_bell
        "info" -> R.drawable.ic_info
        "chevron" -> R.drawable.ic_chevron
        "back" -> R.drawable.ic_back
        "play" -> R.drawable.ic_play
        "exam" -> R.drawable.ic_exam
        "menu" -> R.drawable.ic_menu
        "sun" -> R.drawable.ic_sun
        "plus" -> R.drawable.ic_plus
        "trash" -> R.drawable.ic_trash
        "star" -> R.drawable.ic_star
        "check" -> R.drawable.ic_check
        "close" -> R.drawable.ic_close
        "download" -> R.drawable.ic_download
        "upload" -> R.drawable.ic_upload
        "filter" -> R.drawable.ic_filter
        "flag" -> R.drawable.ic_flag
        "clock" -> R.drawable.ic_clock
        "target" -> R.drawable.ic_target
        "brain" -> R.drawable.ic_brain
        "grid" -> R.drawable.ic_grid
        "eye" -> R.drawable.ic_eye
        "eye_off" -> R.drawable.ic_eye_off
        "school" -> R.drawable.ic_school
        "share" -> R.drawable.ic_share
        "link" -> R.drawable.ic_link
        "note" -> R.drawable.ic_note
        "tree" -> R.drawable.ic_tree
        "send" -> R.drawable.ic_send
        else -> R.drawable.ic_info
    }
    return painterResource(res)
}
