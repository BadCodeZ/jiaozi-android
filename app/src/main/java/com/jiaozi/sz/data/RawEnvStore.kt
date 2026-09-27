package com.jiaozi.sz.data

import java.io.File

/**
 * 同步信封「原始基线」的落盘存储。
 *
 * 背景：`meta.sync_env_raw` 保存的是「上次合并后的完整信封」，会随题量/进度增长
 * （设备实测已到 1.26 MB）。原先它存在 meta 表**单行**里，超过设备 CursorWindow 上限后
 * `getMeta()` 直接抛 `SQLiteBlobTooBigException`，导致 `exportEnvelope()` 失败，
 * 连带 **本地自动快照 / 立即快照 / 导出数据 / WebDAV 同步** 全部静默失效。
 *
 * 改为文件存储后彻底摆脱行大小限制。**信封内容、序列化格式、MetaKeys 语义均未改动**，
 * 因此与网页端的互通完全不受影响 —— 传给网页端的始终是 `buildEnvelopeFromLocal(raw)`
 * 的产物，本类只改变这份本地缓存「存在哪里」。
 *
 * 写入策略：先写 `.tmp` 再 rename，避免出现半截文件；所有 IO 失败静默返回
 * （读不到即由调用方按空信封处理，与原 `getMeta` 返回 null 的语义一致）。
 */
class RawEnvStore(private val file: File) {

    /** 读取基线的原始 JSON；不存在、为空或 IO 失败均返回 null。 */
    fun read(): String? = runCatching {
        if (file.exists() && file.length() > 0) file.readText(Charsets.UTF_8) else null
    }.getOrNull()?.takeIf { it.isNotBlank() }

    /** 覆写基线（原子：.tmp → rename）。 */
    fun write(json: String) {
        runCatching {
            val dir = file.parentFile
            dir?.mkdirs()
            val tmp = File(dir, file.name + ".tmp")
            tmp.writeText(json, Charsets.UTF_8)
            if (file.exists() && !file.delete()) {
                // 删不掉就直接覆盖，避免残留旧值
                file.writeText(json, Charsets.UTF_8)
                tmp.delete()
                return@runCatching
            }
            if (!tmp.renameTo(file)) {
                file.writeText(json, Charsets.UTF_8)
                tmp.delete()
            }
        }
    }

    /** 清空基线（用于迁移后释放 meta 大行）。 */
    fun clear() {
        runCatching { if (file.exists()) file.delete() }
    }

    /** 仅供诊断：当前基线文件大小（字节）。 */
    fun sizeBytes(): Long = runCatching { if (file.exists()) file.length() else 0L }.getOrDefault(0L)
}
