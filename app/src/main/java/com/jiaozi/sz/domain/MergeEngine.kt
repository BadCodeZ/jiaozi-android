package com.jiaozi.sz.domain

import kotlinx.serialization.json.*

/**
 * 多端同步合并引擎（v2 信封，对齐「综合教资备考工作台.html」）。
 *
 * 信封结构（与网页端逐字段一致）：
 *   { v:2, gen:'artwb', createdAt, subj3,
 *     meta:{theme,pack,font,targetDay,_mt},
 *     prefs:{practiceMode,lastSubject,weakFocus,proofTab,_mt},
 *     exam:[...], knowledge:[...], lesson:[...],
 *     corrections:{ [id]:{_mt,...} }, qstat:{ [id]:{_mt,...} } }
 *
 * 合并语义（与网页端 mergeInto 等价）：
 *  - 数组集合（exam/knowledge/lesson/…）按 id + _mt 较新者胜出；不同 id 全部保留（并集）；
 *    exam 的 cause 做错因并集（保护跨设备不同错因的诊断数据）。
 *  - 对象集合（corrections/qstat/…）按 key + _mt 较新者胜出。
 *  - meta/prefs 按 _mt 整体替换。
 *  - 信封级字段（v/gen/createdAt/subj3）保留本地。
 *  - 关键：对任意「未知集合/未知字段」一律无损保留——App 永远不会删除或吞掉
 *    网页端独有数据（如 knowledge/lesson 或未来新增集合），从根上杜绝静默丢数据。
 *
 * 版本协商：v ≤ 2 可解析；v > 2 拒绝（提示升级）。
 * 纯 Kotlin + kotlinx.serialization，可在 JVM 单测，已用网页端真实函数生成的黄金样本验证。
 */
object MergeEngine {
    const val ENVELOPE_VERSION = 2
    const val MAX_VERSION = 2
    private val SPECIAL_OBJS = setOf("meta", "prefs")
    private val ENVELOPE_META = setOf("v", "gen", "createdAt", "subj3")
    private val MAP_COLS = setOf("corrections", "qstat")

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(str: String): JsonObject {
        val el = json.parseToJsonElement(str)
        if (el !is JsonObject) throw IllegalArgumentException("同步包不是合法的 JSON 对象")
        val v = (el["v"] as? JsonPrimitive)?.intOrNull
        if (v != null && v > MAX_VERSION) {
            throw IllegalArgumentException("同步包版本过高（v$v），请升级应用后再导入。")
        }
        return el
    }

    fun serialize(obj: JsonObject): String = json.encodeToString(JsonObject.serializer(), obj)

    /** 构造空信封（首次使用或上次信封缺失时） */
    fun emptyEnvelope(subj3: String): JsonObject = buildJsonObject {
        put("v", ENVELOPE_VERSION)
        put("gen", "artwb")
        put("createdAt", System.currentTimeMillis())
        put("subj3", subj3)
        put("meta", buildJsonObject { put("_mt", 0) })
        put("prefs", buildJsonObject { put("_mt", 0) })
        put("exam", JsonArray(emptyList()))
        put("knowledge", JsonArray(emptyList()))
        put("lesson", JsonArray(emptyList()))
        put("corrections", JsonObject(emptyMap()))
        put("qstat", JsonObject(emptyMap()))
    }

    /** 合并：把远端 remote 合并进本地 local，返回合并后信封（本地应写回） */
    fun merge(local: JsonObject, remote: JsonObject): JsonObject {
        val result = local.toMutableMap()
        // meta / prefs：按 _mt 整体替换
        for (sp in SPECIAL_OBJS) {
            val l = local[sp] as? JsonObject
            val r = remote[sp] as? JsonObject
            if (r == null) continue
            if (l == null) { result[sp] = r; continue }
            if (mtOf(r) > mtOf(l)) result[sp] = r
        }
        // 其余集合（含 exam/knowledge/lesson/corrections/qstat 及任意未知集合）
        for ((key, rv) in remote) {
            if (key in SPECIAL_OBJS) continue
            if (key in ENVELOPE_META) continue
            val lv = local[key]
            when {
                rv is JsonObject && lv is JsonObject -> result[key] = mergeMapCollection(lv, rv)
                rv is JsonArray && lv is JsonArray -> result[key] = mergeArrayCollection(key, lv, rv)
                rv is JsonArray && lv !is JsonArray -> result[key] = mergeArrayCollection(key, JsonArray(emptyList()), rv)
                // 远端为标量而本地为集合：保留本地
            }
        }
        result["v"] = JsonPrimitive(ENVELOPE_VERSION)
        return JsonObject(result)
    }

    internal fun mtOf(o: JsonObject?): Long {
        val p = o?.get("_mt") as? JsonPrimitive
        return p?.longOrNull ?: 0L
    }

    internal fun mergeMapCollection(lv: JsonObject, rv: JsonObject): JsonObject {
        val m = lv.toMutableMap()
        for ((k, re) in rv) {
            val rObj = re as? JsonObject
            val cur = m[k] as? JsonObject
            val rt = mtOf(rObj); val lt = mtOf(cur)
            if (lt == 0L && rt == 0L) {
                if (k !in m) m[k] = re
            } else if (rt > lt) {
                m[k] = re
            }
            // rt <= lt：保留本地
        }
        return JsonObject(m)
    }

    internal fun mergeArrayCollection(name: String, lv: JsonArray, rv: JsonArray): JsonArray {
        val byId = LinkedHashMap<String, JsonObject>()
        for (e in lv) if (e is JsonObject) {
            val id = e["id"]?.jsonPrimitive?.contentOrNull
            if (id != null) byId[id] = e
        }
        for (re in rv) {
            if (re !is JsonObject) continue
            val id = re["id"]?.jsonPrimitive?.contentOrNull ?: continue
            val cur = byId[id]
            if (cur == null) {
                if ((re["_del"] as? JsonPrimitive)?.booleanOrNull != true) byId[id] = re
            } else {
                val rt = mtOf(re); val lt = mtOf(cur)
                when {
                    rt > lt -> {
                        if ((re["_del"] as? JsonPrimitive)?.booleanOrNull == true) byId.remove(id)
                        else byId[id] = if (name == "exam" && cur["cause"] is JsonArray && re["cause"] is JsonArray) unionCause(cur, re) else re
                    }
                    rt < lt -> {
                        if ((cur["_del"] as? JsonPrimitive)?.booleanOrNull == true) byId.remove(id)
                        else if (name == "exam" && cur["cause"] is JsonArray && re["cause"] is JsonArray) byId[id] = unionCause(cur, re)
                    }
                    rt == lt -> {
                        if ((re["_del"] as? JsonPrimitive)?.booleanOrNull == true && (cur["_del"] as? JsonPrimitive)?.booleanOrNull != true) byId.remove(id)
                    }
                }
            }
        }
        return JsonArray(byId.values.toList())
    }

    private fun unionCause(cur: JsonObject, re: JsonObject): JsonObject {
        val seen = LinkedHashSet<String>()
        (cur["cause"] as? JsonArray)?.forEach { (it as? JsonPrimitive)?.contentOrNull?.let { seen.add(it) } }
        (re["cause"] as? JsonArray)?.forEach { (it as? JsonPrimitive)?.contentOrNull?.let { seen.add(it) } }
        val m = re.toMutableMap()
        m["cause"] = JsonArray(seen.map { JsonPrimitive(it) })
        return JsonObject(m)
    }
}
