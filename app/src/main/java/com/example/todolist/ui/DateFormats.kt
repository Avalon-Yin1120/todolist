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

    fun toLocalDate(epochMillis: Long, zone: ZoneId): LocalDate =
        java.time.Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()

    fun toLocalDateTime(epochMillis: Long, zone: ZoneId): LocalDateTime =
        java.time.Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDateTime()
}
