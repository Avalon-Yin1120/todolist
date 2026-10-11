package com.example.todolist.ui

import com.example.todolist.domain.RecurrenceExpander
import com.example.todolist.domain.Todo
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * 视图层的日期与聚合计算。
 *
 * 全部是纯函数，输入固定时区，因此可以在 JVM 单测里精确断言
 * 「某天有几条、忙多久、颜色档位」这类逻辑。
 */
object CalendarLayout {

    /** 周视图每天的时间刻度：00:00 到 23:00。 */
    const val HOURS_PER_DAY = 24

    fun toLocalDate(epochMillis: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()

    /** 周视图的起止：以周一为一周的第一天。 */
    fun weekStart(date: LocalDate): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))

    fun weekDates(anchor: LocalDate): List<LocalDate> {
        val start = weekStart(anchor)
        return (0L until 7L).map { start.plusDays(it) }
    }

    /** 月视图的网格日期：含补齐到整周的上下月日期。 */
    fun monthGridDates(month: YearMonth): List<LocalDate> {
        val first = month.atDay(1)
        val gridStart = weekStart(first)
        val lastOfMonth = month.atEndOfMonth()
        val gridEnd = lastOfMonth.with(TemporalAdjusters.nextOrSame(java.time.DayOfWeek.SUNDAY))
        val dates = mutableListOf<LocalDate>()
        var date = gridStart
        while (!date.isAfter(gridEnd)) {
            dates += date
            date = date.plusDays(1)
        }
        return dates
    }

    /**
     * 把待办展开到某一天。
     *
     * 周期待办由 [RecurrenceExpander] 按需展开成当次，因此这里得到的是
     * 「这一天实际会发生的事」。
     */
    fun todosOn(todos: List<Todo>, date: LocalDate, zone: ZoneId): List<Todo> =
        todos.mapNotNull { todo ->
            if (RecurrenceExpander.occursOn(todo, date, zone)) {
                RecurrenceExpander.occurrenceOn(todo, date, zone)
            } else {
                null
            }
        }.sortedBy { it.startAt }

    /**
     * 某一天的忙碌时长（毫秒）。用于月视图红色深浅。
     */
    fun busyDurationMillis(todos: List<Todo>, date: LocalDate, zone: ZoneId): Long =
        todosOn(todos, date, zone).sumOf { (it.endAt - it.startAt).coerceAtLeast(0L) }

    /**
     * 月视图的颜色档位：0 表示没有待办，1..4 表示忙碌程度递增。
     *
     * 用「小时数」分档而不是精确毫秒：颜色只需表达相对多少，分档更稳定也更易读。
     */
    fun busyLevel(hours: Double): Int = when {
        hours <= 0.0 -> 0
        hours < 1.0 -> 1
        hours < 3.0 -> 2
        hours < 6.0 -> 3
        else -> 4
    }

    /** 周视图某个小时的格子内是否有待办。 */
    fun hasTodoInHour(todos: List<Todo>, date: LocalDate, hour: Int, zone: ZoneId): Boolean {
        val slotStart = date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
        val slotEnd = date.atTime(hour, 0).plusHours(1).atZone(zone).toInstant().toEpochMilli()
        return todosOn(todos, date, zone).any { it.startAt < slotEnd && it.endAt > slotStart }
    }

    /** 一天内的待办按小时归位，供日视图时间轴使用。 */
    fun todoHoursOf(todos: List<Todo>, date: LocalDate, zone: ZoneId): Set<Int> =
        (0 until HOURS_PER_DAY).filterTo(mutableSetOf()) { hasTodoInHour(todos, date, it, zone) }
}
