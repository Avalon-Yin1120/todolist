package com.example.todolist.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/**
 * 周期待办的展开规则。
 *
 * 设计选择：**不为每个场次建数据库行**。
 * 只存一条规则与例外日期，展示某个日期范围时按需展开。
 * 这样「修改整个周期」只需改一条记录，也不会随时间推移堆积无用数据。
 */
object RecurrenceExpander {

    private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    /** 把 epoch 毫秒转成「本地日期字符串」，作为例外列表的键。 */
    fun dateKeyOf(epochMillis: Long, zone: ZoneId): String =
        Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate().format(DATE_FORMATTER)

    /**
     * 判断某条待办是否在 [date] 这一天出现。
     *
     * 一次性待办只看开始日期是否等于该日；周期待办按其规则判断，
     * 并且会跳过「被删除的那一次」。
     */
    fun occursOn(todo: Todo, date: LocalDate, zone: ZoneId): Boolean {
        if (todo.recurrenceExceptions.any { it.date == date.format(DATE_FORMATTER) }) {
            return false
        }
        val startDate = Instant.ofEpochMilli(todo.startAt).atZone(zone).toLocalDate()
        if (!todo.recurrence.isRecurring) {
            return startDate == date
        }
        if (date.isBefore(startDate)) return false
        return when (todo.recurrence) {
            Recurrence.DAILY -> true
            Recurrence.WEEKLY -> startDate.dayOfWeek == date.dayOfWeek
            Recurrence.MONTHLY -> occursOnSameDayOfMonth(startDate, date)
            Recurrence.NONE -> startDate == date
        }
    }

    /**
     * 把某条待办在 [date] 这一天展开成一条「当次」的待办。
     *
     * 时间部分取自规则，日期换成 [date]；一次性待办直接返回自身。
     * 结束时间用「原始时长」推算，跨夏令时也不会算错。
     */
    fun occurrenceOn(todo: Todo, date: LocalDate, zone: ZoneId): Todo {
        if (!todo.recurrence.isRecurring) return todo

        val startTime = Instant.ofEpochMilli(todo.startAt).atZone(zone).toLocalTime()
        val duration = todo.endAt - todo.startAt

        val occurrenceStart = date.atTime(startTime).atZone(zone).toInstant().toEpochMilli()
        return todo.copy(
            startAt = occurrenceStart,
            endAt = occurrenceStart + duration,
        )
    }

    /**
     * 展开某个日期范围内所有出现的待办（含周期场次），按开始时间升序。
     */
    fun expandBetween(
        todos: List<Todo>,
        fromDate: LocalDate,
        toDateExclusive: LocalDate,
        zone: ZoneId,
    ): List<Todo> {
        val result = mutableListOf<Todo>()
        var date = fromDate
        while (date.isBefore(toDateExclusive)) {
            todos.forEach { todo ->
                if (occursOn(todo, date, zone)) {
                    result += occurrenceOn(todo, date, zone)
                }
            }
            date = date.plusDays(1)
        }
        return result.sortedBy { it.startAt }
    }

    /**
     * 找出从 [now] 起、之后最近一次出现的时间；没有未来场次时返回 null。
     *
     * 用于「距离下次待办还有多久」的倒计时，以及编辑重复规则时展示下一次发生时间。
     */
    fun nextOccurrenceAfter(todo: Todo, now: Long, zone: ZoneId, searchDays: Long = 400): Long? {
        if (!todo.recurrence.isRecurring) {
            return todo.startAt.takeIf { it > now }
        }
        var date = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val endDate = date.plusDays(searchDays)
        while (!date.isAfter(endDate)) {
            if (occursOn(todo, date, zone)) {
                val candidate = occurrenceOn(todo, date, zone).startAt
                if (candidate > now) return candidate
            }
            date = date.plusDays(1)
        }
        return null
    }

    private fun occursOnSameDayOfMonth(startDate: LocalDate, date: LocalDate): Boolean {
        // 每月同一天；起始日大于该月天数时（如 31 日）落到该月最后一天
        val lastDayOfMonth = date.with(TemporalAdjusters.lastDayOfMonth()).dayOfMonth
        val target = minOf(startDate.dayOfMonth, lastDayOfMonth)
        return date.dayOfMonth == target
    }

    /** 供界面展示的周期描述。 */
    fun describe(recurrence: Recurrence): String = when (recurrence) {
        Recurrence.NONE -> "不重复"
        Recurrence.DAILY -> "每天"
        Recurrence.WEEKLY -> "每周"
        Recurrence.MONTHLY -> "每月"
    }

    /** 供界面展示的星期描述。 */
    fun describeDayOfWeek(dayOfWeek: DayOfWeek): String = when (dayOfWeek) {
        DayOfWeek.MONDAY -> "周一"
        DayOfWeek.TUESDAY -> "周二"
        DayOfWeek.WEDNESDAY -> "周三"
        DayOfWeek.THURSDAY -> "周四"
        DayOfWeek.FRIDAY -> "周五"
        DayOfWeek.SATURDAY -> "周六"
        DayOfWeek.SUNDAY -> "周日"
    }
}
