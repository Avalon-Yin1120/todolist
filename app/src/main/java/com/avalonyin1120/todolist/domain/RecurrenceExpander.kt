package com.avalonyin1120.todolist.domain

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

    /** 把日期转成例外列表的键；与 [dateKeyOf] 同一套格式。 */
    fun dateKeyOf(date: LocalDate): String = date.format(DATE_FORMATTER)

    /**
     * 判断某条待办是否在 [date] 这一天出现。
     *
     * 一次性待办只看开始日期是否等于该日；周期待办按其规则判断，
     * 并且会跳过「被删除 / 被单独改过的那一次」。
     *
     * 「已完成的那一次」**不跳过**：它仍然要显示出来（带删除线与选中的勾选框），
     * 否则用户打完勾这一场就凭空消失了。
     */
    fun occursOn(todo: Todo, date: LocalDate, zone: ZoneId): Boolean {
        val dateKey = dateKeyOf(date)
        if (todo.recurrenceExceptions.any { it.date == dateKey && it.kind.hidesOccurrence }) {
            return false
        }
        val startDate = Instant.ofEpochMilli(todo.startAt).atZone(zone).toLocalDate()
        if (!todo.recurrence.isRecurring) {
            return startDate == date
        }
        // 生效起始日期：留空表示从待办自身的起始日生效
        val effectiveStart = effectiveStartDate(todo, startDate)
        if (date.isBefore(effectiveStart)) return false
        // 截止日期是硬边界：超过就彻底不再出现
        todo.recurrenceEndDate?.let { end ->
            runCatching { LocalDate.parse(end, DATE_FORMATTER) }.getOrNull()?.let {
                if (date.isAfter(it)) return false
            }
        }
        if (!matchesRule(todo, date, startDate)) return false
        // 次数限制：从生效首场数到这一场，超过总数就不再出现
        todo.recurrenceCount?.let { limit ->
            if (limit > 0 && occurrenceIndex(todo, date, zone, startDate) >= limit) return false
        }
        return true
    }

    /**
     * 重复实际从哪一天开始生效。
     *
     * 用户可单独指定生效起始日期（例如「从下个月 1 号开始每周一」）；
     * 留空则回落到待办自身的起始日。
     */
    fun effectiveStartDate(todo: Todo, startDate: LocalDate): LocalDate =
        todo.recurrenceStartDate
            ?.let { runCatching { LocalDate.parse(it, DATE_FORMATTER) }.getOrNull() }
            ?: startDate

    /** 只判断规则本身（星期/号数/每天），不含例外、截止与次数。 */
    private fun matchesRule(todo: Todo, date: LocalDate, startDate: LocalDate): Boolean =
        when (todo.recurrence) {
            Recurrence.DAILY -> true
            // 周重复按用户选中的星期几判断；没选过则回落到开始日期的星期
            Recurrence.WEEKLY -> effectiveWeekDays(todo, startDate).contains(date.dayOfWeek)
            // 月重复按用户选中的号数判断；没选过则回落到开始日期的号数
            Recurrence.MONTHLY -> occursOnSelectedMonthDay(todo, startDate, date)
            Recurrence.NONE -> startDate == date
        }

    /**
     * [date] 是第几场（从 0 开始数第一场）。
     *
     * 只统计符合规则的日期，忽略例外——次数限制的语义是「总共排几次」，
     * 用户手动跳过某一次不应该让后面的场次顺延。
     */
    fun occurrenceIndex(todo: Todo, date: LocalDate, zone: ZoneId, startDate: LocalDate): Int {
        var index = 0
        var cursor = effectiveStartDate(todo, startDate)
        while (cursor.isBefore(date)) {
            if (matchesRule(todo, cursor, startDate)) index++
            cursor = cursor.plusDays(1)
        }
        return index
    }

    /**
     * 由「重复次数」反推截止日期。
     *
     * 界面上次数与截止日期是联动的：用户改任意一个，另一个立刻跟着确定，
     * 避免「既填了 10 次又填了截止到明年」这种自相矛盾的输入。
     * 达到 [MAX_SEARCH_DAYS] 仍未数满（例如选了 31 号而中间全是小月）就停止。
     */
    fun endDateForCount(
        todo: Todo,
        count: Int,
        zone: ZoneId,
        searchDays: Int = 4000,
    ): LocalDate? {
        if (!todo.recurrence.isRecurring || count <= 0) return null
        val startDate = Instant.ofEpochMilli(todo.startAt).atZone(zone).toLocalDate()
        var seen = 0
        var cursor = effectiveStartDate(todo, startDate)
        repeat(searchDays) {
            if (matchesRule(todo, cursor, startDate)) {
                seen++
                if (seen == count) return cursor
            }
            cursor = cursor.plusDays(1)
        }
        return cursor.minusDays(1)
    }

    /** 由「截止日期」反推重复次数（含第一次）。 */
    fun countUntil(todo: Todo, endDate: LocalDate, zone: ZoneId): Int {
        if (!todo.recurrence.isRecurring) return 1
        val startDate = Instant.ofEpochMilli(todo.startAt).atZone(zone).toLocalDate()
        var seen = 0
        var cursor = effectiveStartDate(todo, startDate)
        while (!cursor.isAfter(endDate)) {
            if (matchesRule(todo, cursor, startDate)) seen++
            cursor = cursor.plusDays(1)
        }
        return seen.coerceAtLeast(1)
    }

    /**
     * 周重复实际生效的星期集合。
     *
     * 用户没选任何一天时回落到开始日期的星期，这样从旧版本升级上来的数据
     * （只有「每周」而没有具体星期）行为不变。
     */
    fun effectiveWeekDays(todo: Todo, startDate: LocalDate): Set<DayOfWeek> =
        todo.weekDays.ifEmpty { setOf(startDate.dayOfWeek) }

    /** 月重复实际生效的号数集合。 */
    fun effectiveMonthDays(todo: Todo, startDate: LocalDate): Set<Int> =
        todo.monthDays.ifEmpty { setOf(startDate.dayOfMonth) }

    /**
     * 月重复是否命中某一天。
     *
     * 选中 31 号而该月没有 31 号时，落到该月最后一天——
     * 否则「每月 31 号」在 2 月、4 月会整月不出现，不符合用户预期。
     */
    private fun occursOnSelectedMonthDay(todo: Todo, startDate: LocalDate, date: LocalDate): Boolean {
        val selected = effectiveMonthDays(todo, startDate)
        if (date.dayOfMonth in selected) return true
        val lastDayOfMonth = date.with(TemporalAdjusters.lastDayOfMonth()).dayOfMonth
        return date.dayOfMonth == lastDayOfMonth && selected.any { it > lastDayOfMonth }
    }

    /**
     * 把某条待办在 [date] 这一天展开成一条「当次」的待办。
     *
     * 时间部分取自规则，日期换成 [date]；一次性待办直接返回自身。
     * 结束时间用「原始时长」推算，跨夏令时也不会算错。
     *
     * 周期待办的 `isDone` 由**这一场有没有完成例外**决定，而不是取整条记录的 `isDone`：
     * 整条记录的完成状态对周期待办没有意义（那正是「勾一场、全变已完成」的成因）。
     */
    fun occurrenceOn(todo: Todo, date: LocalDate, zone: ZoneId): Todo {
        if (!todo.recurrence.isRecurring) return todo

        val startTime = Instant.ofEpochMilli(todo.startAt).atZone(zone).toLocalTime()
        val duration = todo.endAt - todo.startAt

        val occurrenceStart = date.atTime(startTime).atZone(zone).toInstant().toEpochMilli()
        return todo.copy(
            startAt = occurrenceStart,
            endAt = occurrenceStart + duration,
            isDone = isCompleted(todo, date),
        )
    }

    /**
     * 列表页展示用的「这一条」：周期待办取**今天这一场**，今天没有就取**下一场**。
     *
     * ## 为什么不再取「起始日那一场」
     *
     * 旧实现（`asListEntry`）把卡片固定成规则起始日那一场。于是：
     * * 一条从三周前开始的「每天 9:00」，卡片上永远写着三周前那天，勾选框勾的是那一天；
     * * 勾掉起始日那一场后，整条规则在卡片上显示「已完成」，而今天这一场根本没做；
     * * 顶栏的「待处理 / 距离下次」也跟着那一天算，出现「距离下次待办还有 …」与
     *   「有 N 项待处理」互相打架的情况（用户报的 bug）。
     *
     * 现在卡片就是「今天要做的那一场」：日期时间、完成状态、勾选动作三者统一。
     * 今天不是它的日子（例如「每周一」而今天是周三）就显示下一场，此时它还没开始，
     * 界面上不给勾选框（见 [TodoClassifier]：未来的事谈不上完成）。
     * 规则已经没有任何场次（次数用完 / 已过截止）时退回起始日那一场，
     * 保证卡片上总有东西可看、可改、可删。
     */
    fun listEntry(todo: Todo, now: Long, zone: ZoneId): Todo {
        if (!todo.recurrence.isRecurring) return todo

        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        if (occursOn(todo, today, zone)) return occurrenceOn(todo, today, zone)

        val next = nextOccurrenceAfter(todo, now, zone)
        if (next != null) {
            val nextDate = Instant.ofEpochMilli(next).atZone(zone).toLocalDate()
            return occurrenceOn(todo, nextDate, zone)
        }

        val startDate = Instant.ofEpochMilli(todo.startAt).atZone(zone).toLocalDate()
        return occurrenceOn(todo, startDate, zone)
    }

    /**
     * 从 [now] 起、之后最近一次**还没完成**的场次开始时间；没有则返回 null。
     *
     * 与 [nextOccurrenceAfter] 的区别只有一个：跳过已经打过勾的场次。
     * 顶栏的「距离下次待办还有多久」用它——今天是每天 9:00 的待办、已经勾掉了，
     * 那「下次」就该是**明天** 9:00，而不是仍然指着今天这场已完成的时间。
     */
    fun nextPendingOccurrence(todo: Todo, now: Long, zone: ZoneId, searchDays: Long = 400): Long? {
        if (!todo.recurrence.isRecurring) {
            return todo.startAt.takeIf { it > now && !todo.isDone }
        }
        var date = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val endDate = date.plusDays(searchDays)
        while (!date.isAfter(endDate)) {
            if (occursOn(todo, date, zone) && !isCompleted(todo, date)) {
                val candidate = occurrenceOn(todo, date, zone).startAt
                if (candidate > now) return candidate
            }
            date = date.plusDays(1)
        }
        return null
    }

    /** [date] 这一场是否已被标记完成。 */
    fun isCompleted(todo: Todo, date: LocalDate): Boolean {
        val dateKey = dateKeyOf(date)
        return todo.recurrenceExceptions.any {
            it.date == dateKey && it.kind == RecurrenceExceptionKind.COMPLETED
        }
    }

    /** [occurrenceStart] 那一场是否已被标记完成（供调度侧用开始时间定位场次）。 */
    fun isOccurrenceCompleted(todo: Todo, occurrenceStart: Long, zone: ZoneId): Boolean =
        isCompleted(todo, Instant.ofEpochMilli(occurrenceStart).atZone(zone).toLocalDate())

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


    /**
     * 供界面展示的周期描述。
     *
     * 带上具体的周几/几号，用户一眼能看出「每周一、三、五」而不是笼统的「每周」。
     */
    fun describe(todo: Todo, zone: ZoneId): String = when (todo.recurrence) {
        Recurrence.NONE -> "不重复"
        Recurrence.DAILY -> "每天"
        Recurrence.WEEKLY -> {
            val startDate = Instant.ofEpochMilli(todo.startAt).atZone(zone).toLocalDate()
            val days = effectiveWeekDays(todo, startDate)
            if (days.size == 7) {
                "每天"
            } else {
                "每" + RecurrenceOptions.WEEK_ORDER
                    .filter { it in days }
                    .joinToString("、") { RecurrenceOptions.shortLabel(it) }
            }
        }
        Recurrence.MONTHLY -> {
            val startDate = Instant.ofEpochMilli(todo.startAt).atZone(zone).toLocalDate()
            val days = effectiveMonthDays(todo, startDate).sorted()
            "每月 " + days.joinToString("、") + " 号"
        }
    }

    /** 粗略描述，仅按规则本身（用于表单上还没选具体日子时的提示）。 */
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
