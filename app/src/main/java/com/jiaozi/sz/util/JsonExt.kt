package com.jiaozi.sz.util

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * JSON 提取 helper：统一替代工程中大量手写的 `(e["k"] as? JsonPrimitive)?.xxxOrNull ?: def` 样板。
 * 字符串字段缺失/类型不符一律返回默认值；原写法返回 null 的场景（用于 ?: continue / String? 赋值）
 * 用 `takeIf { it.isNotEmpty() }` 还原可空语义，避免空串被当成「存在」。
 */

/** 从 JsonObject（可为 null）按 key 取字段 */
fun JsonObject?.optStr(key: String, def: String = ""): String =
    (this?.get(key) as? JsonPrimitive)?.contentOrNull ?: def

fun JsonObject?.optInt(key: String, def: Int = 0): Int =
    (this?.get(key) as? JsonPrimitive)?.intOrNull ?: def

fun JsonObject?.optLong(key: String, def: Long = 0L): Long =
    (this?.get(key) as? JsonPrimitive)?.longOrNull ?: def

fun JsonObject?.optBool(key: String, def: Boolean = false): Boolean =
    (this?.get(key) as? JsonPrimitive)?.booleanOrNull ?: def

fun JsonObject?.optDouble(key: String, def: Double = 0.0): Double =
    (this?.get(key) as? JsonPrimitive)?.doubleOrNull ?: def

fun JsonObject?.optObj(key: String): JsonObject? = this?.get(key) as? JsonObject

fun JsonObject?.optArr(key: String): JsonArray? = this?.get(key) as? JsonArray

/** 已取下的 JsonElement（可为 null）解析为标量 */
fun JsonElement?.asStr(def: String = ""): String =
    (this as? JsonPrimitive)?.contentOrNull ?: def

fun JsonElement?.asInt(def: Int = 0): Int =
    (this as? JsonPrimitive)?.intOrNull ?: def

fun JsonElement?.asLong(def: Long = 0L): Long =
    (this as? JsonPrimitive)?.longOrNull ?: def

fun JsonElement?.asBool(def: Boolean = false): Boolean =
    (this as? JsonPrimitive)?.booleanOrNull ?: def

fun JsonElement?.asDouble(def: Double = 0.0): Double =
    (this as? JsonPrimitive)?.doubleOrNull ?: def

/** JsonArray → 字符串列表（跳过非 JsonPrimitive 元素） */
fun JsonArray.strs(): List<String> = mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
