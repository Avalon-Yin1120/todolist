package com.example.todolist.ui

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 界面上日期/时间的统一格式化。
 *
 * 集中在一处，避免同一个日期在各视图里出现不同写法。
 */
object DateFormats {

    private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val MONTH_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd")
    private val MONTH_DAY_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")
    private val FULL_DATE: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy 年 M 月 d 日 EEEE", Locale.SIMPLIFIED_CHINESE)
    private val SHORT_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("M 月 d 日")

    /** 日视图标题：2026 年 5 月 11 日 星期一。 */
    fun fullDate(date: LocalDate): String = date.format(FULL_DATE)

    /** 紧凑日期：5 月 11 日。 */
    fun shortDate(date: LocalDate): String = date.format(SHORT_DATE)

    /** 周视图标题：5 月 11 日 - 5 月 17 日。 */
    fun dateRange(start: LocalDate, end: LocalDate): String {
        val sameMonth = start.year == end.year && start.month == end.month
        return if (sameMonth) {
            "${shortDate(start)} - ${end.dayOfMonth} 日"
        } else {
            "${shortDate(start)} - ${shortDate(end)}"
        }
    }

    /** 月视图标题：2026 年 5 月。 */
    fun monthLabel(date: LocalDate): String = "${date.year} 年 ${date.monthValue} 月"

    /** 时间：14:30。 */
    fun time(epochMillis: Long, zone: ZoneId): String =
        java.time.Instant.ofEpochMilli(epochMillis).atZone(zone).format(TIME)

    /** 月-日 时:分。 */
    fun monthDayTime(epochMillis: Long, zone: ZoneId): String =
        java.time.Instant.ofEpochMilli(epochMillis).atZone(zone).format(MONTH_DAY_TIME)

    /** 月-日。 */
    fun monthDay(epochMillis: Long, zone: ZoneId): String =
        java.time.Instant.ofEpochMilli(epochMillis).atZone(zone).format(MONTH_DAY)

    /**
     * 由「年 / 月 / 日」三个输入框的文本构造日期，非法时返回 null。
     *
     * 抽成纯函数是为了能直接单测：这段解析决定了用户手填的日期能不能被接受，
     * 而它过去正是缺陷来源（要求 2026/01/01、拒绝 2026/1/1）。
     */
    fun parseDateParts(yearText: String, monthText: String, dayText: String): LocalDate? {
        val year = yearText.trim().toIntOrNull() ?: return null
        val month = monthText.trim().toIntOrNull() ?: return null
        val day = dayText.trim().toIntOrNull() ?: return null
        if (year !in MIN_YEAR..MAX_YEAR || month !in 1..12 || day !in 1..31) return null
        return runCatching { LocalDate.of(year, month, day) }.getOrNull()
    }

    /** 手动输入允许的年份范围。 */
    const val MIN_YEAR = 1900
    const val MAX_YEAR = 2200

    fun toLocalDate(epochMillis: Long, zone: ZoneId): LocalDate =
        java.time.Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()

    fun toLocalDateTime(epochMillis: Long, zone: ZoneId): LocalDateTime =
        java.time.Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDateTime()
}
