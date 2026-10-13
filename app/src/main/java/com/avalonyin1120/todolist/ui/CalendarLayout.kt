package com.avalonyin1120.todolist.ui

import com.avalonyin1120.todolist.domain.RecurrenceExpander
import com.avalonyin1120.todolist.domain.Todo
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
     * 固定 6 行 × 7 列 = 42 天，用于滑动翻月时每一页高度一致。
     *
     * 为什么需要它：[monthGridDates] 返回 28/35/42 天，行数随月份变化
     * （例如 2026-02 是 28 天 = 4 行），放进横向分页里就会出现
     * 「有的页高有的页矮」，滑动时高度来回跳动。
     * 这里在**末尾**继续往后补日期到 42 天，保证每页都是 6 行。
     *
     * 只新增不改动 [monthGridDates]：月视图等既有调用方与测试依赖它原来的行数。
     */
    fun monthGridDatesFixed(month: YearMonth): List<LocalDate> {
        val dates = monthGridDates(month)
        if (dates.size >= FIXED_GRID_DAYS) return dates
        val last = dates.last()
        return dates + (1..(FIXED_GRID_DAYS - dates.size)).map { last.plusDays(it.toLong()) }
    }

    /** 滑动翻月时每页固定的格子数：6 行 × 7 列。 */
    private const val FIXED_GRID_DAYS = 42

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
     * 某一天的忙碌时长（毫秒）。
     *
     * 会把重叠的时间段合并，避免「两个待办各 1 小时但完全重叠」被算成 2 小时。
     */
    fun busyDurationMillis(todos: List<Todo>, date: LocalDate, zone: ZoneId): Long =
        (busyHours(todos, date, zone) * 3_600_000.0).toLong()

    /** 一天内的忙碌区间，以「从当天 00:00 起的小时数」表示，已裁剪到当天范围。 */
    fun busySpans(todos: List<Todo>, date: LocalDate, zone: ZoneId): List<Pair<Double, Double>> {
        val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
        return todosOn(todos, date, zone).mapNotNull { todo ->
            val start = ((maxOf(todo.startAt, dayStart) - dayStart) / 3_600_000.0)
                .coerceIn(0.0, HOURS_PER_DAY.toDouble())
            val end = ((minOf(todo.endAt, dayStart + 24 * 3_600_000L) - dayStart) / 3_600_000.0)
                .coerceIn(0.0, HOURS_PER_DAY.toDouble())
            if (end > start) start to end else null
        }.sortedBy { it.first }
    }

    /** 合并重叠区间。 */
    fun mergeSpans(spans: List<Pair<Double, Double>>): List<Pair<Double, Double>> {
        if (spans.isEmpty()) return emptyList()
        val result = mutableListOf<Pair<Double, Double>>()
        var current = spans.first()
        spans.drop(1).forEach { span ->
            current = if (span.first <= current.second) {
                current.first to maxOf(current.second, span.second)
            } else {
                result += current
                span
            }
        }
        result += current
        return result
    }

    /** 某一天的忙碌小时数（重叠已合并）。 */
    fun busyHours(todos: List<Todo>, date: LocalDate, zone: ZoneId): Double =
        mergeSpans(busySpans(todos, date, zone)).sumOf { it.second - it.first }

    /**
     * 月视图的深浅强度，连续取值 0..1（不再分档）。
     *
     * 用平方根而不是线性：绝大多数日子只忙一两小时，线性映射会让它们
     * 全部挤在最浅的一档、看不出差别；开方后低区间被拉开，高区间趋于饱和。
     */
    fun busyIntensity(hours: Double): Float {
        if (hours <= 0.0) return 0f
        return kotlin.math.sqrt((hours / SATURATION_HOURS).coerceIn(0.0, 1.0)).toFloat()
    }

    /** 强度达到 1 所需的小时数：一天忙满 10 小时就算最深。 */
    const val SATURATION_HOURS = 10.0

    /**
     * 兼容旧调用：仍然给出 0..4 的档位（图例与既有测试使用）。
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
