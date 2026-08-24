package com.jiaozi.sz.data.remote

import kotlinx.serialization.Serializable

/**
 * WebDAV 同步配置（持久化到 Room meta；密码仅本地存储，等同 AI Key 处理）。
 * direction: "upload" 仅上传 / "download" 仅下载 / "two-way" 双向合并。
 */
@Serializable
data class WebDavConfig(
    val url: String = "",            // 服务器地址，含协议，如 https://dav.example.com
    val user: String = "",
    val pass: String = "",
    val remoteDir: String = "artwb-default", // 远程空间目录（不含首尾斜杠）；与网页端同一空间才能互通
    val direction: String = "two-way", // upload / download / two-way
    val encrypt: Boolean = false,    // 是否 AES-GCM 加密（与网页端 SYNCPKG1 互通）
    val syncPass: String = ""        // 同步口令（加密时两端须一致；仅本地存储）
)
