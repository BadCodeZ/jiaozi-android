package com.jiaozi.sz.domain

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * 用真实网页端备份 JSON 文件跑 parseBackup + merge，验证不抛 JsonObject is not JsonPrimitive。
 * 备份文件放在 D:/WorkBuddyPlace/软件/web_backup.json
 */
class RealBackupTest {

    @Test
    fun `parseBackup and merge with real web backup`() {
        val file = File("D:/WorkBuddyPlace/软件/web_backup.json")
        assertTrue("备份文件不存在", file.exists())
        val json = file.readText(Charsets.UTF_8)

        // 1. parseBackup（兼容网页端备份格式，无 v 时补打 v=2）
        val remote = MergeEngine.parseBackup(json)
        assertEquals("应补打 v=2", MergeEngine.ENVELOPE_VERSION, (remote["v"] as JsonPrimitive).int)
        assertNotNull("remote 应有 exam", remote["exam"])
        assertNotNull("remote 应有 meta", remote["meta"])
        assertNotNull("remote 应有 qstat", remote["qstat"])

        // 2. merge（与空信封合并，模拟首次导入场景）
        val local = MergeEngine.emptyEnvelope("美术")
        val merged = MergeEngine.merge(local, remote)

        // 3. 验证合并后关键字段的类型正确
        assertNotNull("merged.exam", merged["exam"])
        assertNotNull("merged.meta", merged["meta"])
        assertNotNull("merged.qstat", merged["qstat"])
        assertNotNull("merged.prefs", merged["prefs"])

        // 4. 验证 meta 里的 font 字段是对象（安全通过 as? JsonPrimitive）
        val meta = merged["meta"] as? JsonObject
        assertNotNull("meta should be JsonObject", meta)
        val font = meta!!["font"]
        assertNotNull("font should exist (as dict)", font)
        println("meta.font type: ${font!!::class.simpleName}")

        // 5. 验证 qstat 里字段是 JsonPrimitive 或被安全读取
        val qstat = merged["qstat"] as? JsonObject
        assertNotNull("qstat should be JsonObject", qstat)
        qstat!!.forEach { (qid, v) ->
            if (v is JsonObject) {
                val right = (v["right"] as? JsonPrimitive)?.intOrNull
                val wrong = (v["wrong"] as? JsonPrimitive)?.intOrNull
                val due = (v["due"] as? JsonPrimitive)?.longOrNull
                // 这些字段不存在也没关系，as? 返回 null 安全
            }
        }

        println("PASS: parseBackup + merge 无异常，meta.font 是 ${font!!::class.simpleName}")
    }
}