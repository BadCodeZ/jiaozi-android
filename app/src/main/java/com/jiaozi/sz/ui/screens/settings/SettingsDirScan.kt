@file:OptIn(ExperimentalMaterial3Api::class)

package com.jiaozi.sz.ui.screens

/**
 * 设置 - 目录导入扫描工具（SAF 递归扫描 .json 备份）。
 *
 * 从 SettingsScreen.kt 拆分而来（纯物理拆分 + 分区抽出，逻辑未改）。
 */

import android.content.Context
import android.net.Uri
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

// ==================== 目录导入工具函数 ====================

/**
 * 通过 SAF ContentResolver 递归扫描目录下的所有 .json 文件。
 * 返回 Pair<显示名, Uri> 列表，用于用户选择后通过 contentResolver 读取。
 */
internal fun scanDirDocuments(ctx: android.content.Context, treeUri: android.net.Uri): List<Pair<String, android.net.Uri>> {
    val results = mutableListOf<Pair<String, android.net.Uri>>()
    val childrenUri = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, android.provider.DocumentsContract.getTreeDocumentId(treeUri))
    val projection = arrayOf(
        android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE
    )
    scanChildren(ctx, childrenUri, projection, results)
    return results
}

internal fun scanChildren(
    ctx: android.content.Context,
    parentUri: android.net.Uri,
    projection: Array<String>,
    results: MutableList<Pair<String, android.net.Uri>>
) {
    var cursor: android.database.Cursor? = null
    try {
        cursor = ctx.contentResolver.query(parentUri, projection, null, null, null)
        cursor?.use { c ->
            while (c.moveToNext()) {
                val docId = c.getString(0) ?: continue
                val name = c.getString(1) ?: continue
                val mime = c.getString(2) ?: ""
                val docUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(parentUri, docId)
                if (mime == android.provider.DocumentsContract.Document.MIME_TYPE_DIR) {
                    // 递归扫描子目录
                    val childUri = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(parentUri, docId)
                    scanChildren(ctx, childUri, projection, results)
                } else if (name.endsWith(".json", ignoreCase = true)) {
                    results.add(name to docUri)
                }
            }
        }
    } catch (_: Exception) {
        // 遇到无权限访问的子目录静默跳过
    } finally {
        cursor?.close()
    }
}
