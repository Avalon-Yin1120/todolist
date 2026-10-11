package com.example.todolist.domain

import java.time.DayOfWeek

/** 提醒方式。 */
enum class ReminderMode {
    /** 发一条通知。 */
    NOTIFICATION,

    /** 像闹钟一样：响铃 + 亮屏 + 全屏提醒页（需要精确闹钟权限）。 */
    ALARM,
}

/** 周期规则。 */
enum class Recurrence {
    NONE,
    DAILY,
    WEEKLY,
    MONTHLY,
    ;

    val isRecurring: Boolean get() = this != NONE
}

/**
 * 重复待办的「异常日期」。
 *
 * 修改或删除某一次重复待办时，不改动整条规则，而是把这个日期记进异常列表，
 * 于是「仅本次」的操作不会影响其它场次。
 *
 * @param date 以本地日期表示，格式 yyyy-MM-dd
 */
data class RecurrenceException(
    val date: String,
    val kind: RecurrenceExceptionKind,
)

enum class RecurrenceExceptionKind {
    /** 这一次被删除（跳过）。 */
    SKIPPED,

    /** 这一次被单独改过，具体内容存在派生待办里。 */
    OVERRIDDEN,
}

/**
 * 周重复选中的星期集合。
 *
 * 存「周几」而不是「每 7 天」：用户说「每周一三五」，语义就是这几个星期几，
 * 与首次开始日期无关。集合为空时回落到开始日期所在的星期，兼容旧数据。
 */
typealias WeekDays = Set<DayOfWeek>

/** 月重复选中的「几号」集合，取值 1..31。 */
typealias MonthDays = Set<Int>

/** 供界面选择用的常量。 */
object RecurrenceOptions {

    /** 周一到周日的顺序，界面按这个顺序排列，符合中文习惯。 */
    val WEEK_ORDER: List<DayOfWeek> = listOf(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY,
        DayOfWeek.SATURDAY,
        DayOfWeek.SUNDAY,
    )

    /** 月重复可选的日子。 */
    val MONTH_DAYS: List<Int> = (1..31).toList()

    /** 单字标签，用于紧凑的按钮/芯片。 */
    fun shortLabel(day: DayOfWeek): String = when (day) {
        DayOfWeek.MONDAY -> "一"
        DayOfWeek.TUESDAY -> "二"
        DayOfWeek.WEDNESDAY -> "三"
        DayOfWeek.THURSDAY -> "四"
        DayOfWeek.FRIDAY -> "五"
        DayOfWeek.SATURDAY -> "六"
        DayOfWeek.SUNDAY -> "日"
    }

    fun fullLabel(day: DayOfWeek): String = "周" + shortLabel(day)
}
