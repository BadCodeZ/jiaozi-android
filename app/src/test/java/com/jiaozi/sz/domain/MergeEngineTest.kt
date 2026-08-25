package com.jiaozi.sz.domain

import kotlinx.serialization.json.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MergeEngine 单元测试（对接 v2 信封 API：parse / merge / serialize / emptyEnvelope）。
 *
 * 旧版测试引用了已移除的 `MergeEngine.export(...)` 与 `ProgressEntity.qid` 体系，
 * 现按当前「数组集合按 id+_mt、对象集合按 key+_mt、墓碑 _del」语义重写，
 * 直接构造 v2 信封（JsonObject）喂给 MergeEngine.merge，断言合并结果。
 */
open class MergeEngineTest {

    private fun item(id: String, right: Int? = null, mt: Long, del: Boolean = false): JsonObject =
        buildJsonObject {
            put("id", id)
            if (right != null) put("right", right)
            put("_mt", mt)
            if (del) put("_del", true)
        }

    private fun env(vararg items: JsonObject): JsonObject =
        buildJsonObject {
            put("v", MergeEngine.ENVELOPE_VERSION)
            put("exam", JsonArray(items.toList()))
        }

    private fun examOf(env: JsonObject): JsonArray = env["exam"] as JsonArray

    private fun findById(env: JsonObject, id: String): JsonObject? =
        examOf(env).filterIsInstance<JsonObject>().firstOrNull {
            it["id"]?.jsonPrimitive?.content == id
        }

    @Test
    fun `同id较新者胜出`() {
        val local = env(item("a", right = 1, mt = 100))
        val remote = env(item("a", right = 5, mt = 200))
        val merged = MergeEngine.merge(local, remote)
        assertEquals(1, examOf(merged).size)
        assertEquals(5, findById(merged, "a")!!["right"]!!.jsonPrimitive.int)
    }

    @Test
    fun `墓碑更晚则移除`() {
        val local = env(item("a", right = 1, mt = 100))
        val remote = env(item("a", right = 0, mt = 300, del = true))
        val merged = MergeEngine.merge(local, remote)
        assertFalse(examOf(merged).any {
            (it as JsonObject)["id"]?.jsonPrimitive?.content == "a"
        })
    }

    @Test
    fun `不同id全保留`() {
        val local = env(item("a", mt = 100))
        val remote = env(item("b", mt = 200))
        val merged = MergeEngine.merge(local, remote)
        assertEquals(2, examOf(merged).size)
        assertTrue(findById(merged, "a") != null)
        assertTrue(findById(merged, "b") != null)
    }

    @Test
    fun `corrections对象集合_key较新者胜出`() {
        val local = buildJsonObject {
            put("v", MergeEngine.ENVELOPE_VERSION)
            put("corrections", buildJsonObject {
                put("q1", buildJsonObject { put("_mt", 100); put("wrongBook", true) })
            })
        }
        val remote = buildJsonObject {
            put("v", MergeEngine.ENVELOPE_VERSION)
            put("corrections", buildJsonObject {
                put("q1", buildJsonObject { put("_mt", 250); put("wrongBook", false) })
            })
        }
        val merged = MergeEngine.merge(local, remote)
        val corr = merged["corrections"] as JsonObject
        assertEquals(1, corr.size)
        assertEquals(false, (corr["q1"] as JsonObject)["wrongBook"]?.jsonPrimitive?.boolean)
    }

    @Test
    fun `parse拒绝版本过高的信封`() {
        val bad = """{"v":99,"gen":"artwb"}"""
        try {
            MergeEngine.parse(bad)
            org.junit.Assert.fail("应当抛出版本过高异常")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("版本过高"))
        }
    }

    @Test
    fun `parseBackup兼容网页端导出备份无v全量格式`() {
        // 模拟网页端「导出备份」S 全量：顶部无 v，但含 exam 等数据集合
        val webBackup = """{
            "exam":[{"id":"w1","q":"题","opt":"[]","subject":"科一","chapter":"ch","_mt":100}],
            "knowledge":[],"lesson":[],"corrections":{},"qstat":{},
            "meta":{"version":"2.0","theme":"xiomi"},"prefs":{}
        }"""
        val parsed = MergeEngine.parseBackup(webBackup)
        // 应补打入 v=2，成为合法信封
        assertEquals(MergeEngine.ENVELOPE_VERSION, (parsed["v"] as JsonPrimitive).int)
        // 数据保留
        assertEquals(1, (parsed["exam"] as JsonArray).size)
        // 再走 merge 应无异常
        val merged = MergeEngine.merge(MergeEngine.emptyEnvelope("美术"), parsed)
        assertEquals(1, (merged["exam"] as JsonArray).size)
    }

    @Test
    fun `parseBackup兼容v2信封原样通过`() {
        val env = """{"v":2,"exam":[],"meta":{}}"""
        val parsed = MergeEngine.parseBackup(env)
        assertEquals(2, (parsed["v"] as JsonPrimitive).int)
    }

    @Test
    fun `parseBackup拒绝无可识别数据且无版本的对象`() {
        try {
            MergeEngine.parseBackup("""{"foo":"bar"}""")
            org.junit.Assert.fail("应当抛出不可识别格式异常")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("不是可识别的备份格式"))
        }
    }
}
