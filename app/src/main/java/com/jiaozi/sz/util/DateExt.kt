package com.jiaozi.sz.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 日期/时间格式化统一出口（纯函数，无状态、无 IO）。
 *
 * 背景：工程内原先散落 7 处手写 `SimpleDateFormat(...)` / `LocalDate.format(...)`，
 * 且 Locale 混用（US / CHINA / getDefault()），格式串各写各的。本文件把「时间戳 → 字符串」
 * 与「LocalDate ↔ ISO / 毫秒」两类转换收口，替换所有手写实例。
 *
 * 约定：
 * - 所有 pattern 统一以 [Locale.US] 构造。纯数字 pattern（yyyy/MM/dd/HH/mm/ss）下
 *   US 与 CHINA、系统默认 Locale 输出完全一致，且可规避部分 Locale 的数字变体。
 * - 时区一律用系统默认 [ZoneId.systemDefault()]，与原 `SimpleDateFormat` 默认行为一致。
 * - 本文件只做格式化，不做解析（解析点分散且语义各异，保持原样）。
 */

/** 常用 pattern：「年-月-日 时:分」。 */
const val PATTERN_DATE_TIME = "yyyy-MM-dd HH:mm"

/** 常用 pattern：「月-日」。 */
const val PATTERN_MONTH_DAY = "MM-dd"

/** 文件名时间戳 pattern：「年月日_时分秒」。 */
const val PATTERN_FILE_STAMP = "yyyyMMdd_HHmmss"

/** 按 [pattern] 构造格式化器（固定 Locale.US，见类注释）。 */
private fun formatter(pattern: String): DateTimeFormatter =
    DateTimeFormatter.ofPattern(pattern, Locale.US)

/** 当前日期，ISO 格式（yyyy-MM-dd）。 */
fun todayIso(): String = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

/** 昨天日期，ISO 格式（yyyy-MM-dd）。 */
fun yesterdayIso(): String = LocalDate.now().minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE)

/** LocalDate → ISO 字符串（yyyy-MM-dd）。 */
fun LocalDate.toIso(): String = format(DateTimeFormatter.ISO_LOCAL_DATE)

/** 毫秒时间戳 → LocalDate（系统时区）。 */
fun Long.toLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()

/** 毫秒时间戳 → ISO 日期（yyyy-MM-dd）。 */
fun Long.toIsoDate(): String = toLocalDate().toIso()

/** LocalDate → 当天 0 点毫秒（系统时区）。 */
fun LocalDate.startOfDayMillis(): Long =
    atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

/** 今天 0 点毫秒（系统时区）。 */
fun todayStartMillis(): Long = LocalDate.now().startOfDayMillis()

/** 毫秒时间戳 → 按 [pattern] 格式化（系统时区），如 [PATTERN_DATE_TIME]。 */
fun formatTs(millis: Long, pattern: String): String =
    formatter(pattern).format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

/** 文件名时间戳（yyyyMMdd_HHmmss），默认取当前时刻。 */
fun formatFileStamp(millis: Long = System.currentTimeMillis()): String =
    formatTs(millis, PATTERN_FILE_STAMP)

/** 时间戳 > 0 才格式化，否则返回 [fallback]（用于「可能为空」的时间字段展示）。 */
fun formatTsOr(millis: Long, pattern: String, fallback: String = ""): String =
    if (millis > 0) formatTs(millis, pattern) else fallback

/**
 * [formatFileStamp] 的逆操作：把 `yyyyMMdd_HHmmss` 串解析回毫秒；不合法返回 null。
 * 用于从备份快照文件名还原时间点。
 */
fun parseFileStamp(stamp: String): Long? = runCatching {
    java.time.LocalDateTime.parse(stamp, formatter(PATTERN_FILE_STAMP))
        .atZone(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
}.getOrNull()
