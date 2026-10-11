package com.example.todolist.data

import com.example.todolist.domain.Clock
import com.example.todolist.domain.MonthDays
import com.example.todolist.domain.Recurrence
import com.example.todolist.domain.ReminderMode
import com.example.todolist.domain.ReminderTime
import com.example.todolist.domain.Todo
import com.example.todolist.domain.WeekDays

/**
 * 表单输入到落库之间的中间态。
 * 校验在表单状态/ViewModel 里做，这里只承载「用户填了什么」。
 */
data class TodoDraft(
    val id: Long = 0L,
    val name: String,
    val startAt: Long,
    val endAt: Long,
    val leadMinutes: Int,
    val mode: ReminderMode = ReminderMode.NOTIFICATION,
    val recurrence: Recurrence = Recurrence.NONE,
    /** 周重复选中的星期几。 */
    val weekDays: WeekDays = emptySet(),
    /** 月重复选中的几号。 */
    val monthDays: MonthDays = emptySet(),
    /** 重复截止日期 yyyy-MM-dd；为空表示不限。 */
    val recurrenceEndDate: String? = null,
    /** 重复总次数；为空表示不限。 */
    val recurrenceCount: Int? = null,
)

/**
 * 把草稿与已有记录合成一条可落库的实体。
 *
 * 三处刻意的取舍：
 * * 提醒相关字段变了就清掉「已提醒」标记，否则编辑后不会再响；
 * * 只改名称不清标记，避免用户改个错别字就再被提醒一次；
 * * 关闭周期（改成不重复）时清空例外列表——例外只对重复规则有意义。
 */
fun TodoDraft.toTodo(existing: Todo?, now: Long): Todo {
    val name = ReminderTime.normalizeName(name)
    if (existing == null) {
        return Todo(
            name = name,
            startAt = startAt,
            endAt = endAt,
            leadMinutes = leadMinutes,
            mode = mode,
            recurrence = recurrence,
            weekDays = weekDays,
            monthDays = monthDays,
            recurrenceEndDate = recurrenceEndDate,
            recurrenceCount = recurrenceCount,
            isDone = false,
            remindedOccurrenceStart = null,
            createdAt = now,
            updatedAt = now,
        )
    }

    val scheduleChanged = existing.startAt != startAt ||
        existing.leadMinutes != leadMinutes ||
        existing.mode != mode ||
        existing.recurrence != recurrence ||
        existing.weekDays != weekDays ||
        existing.monthDays != monthDays ||
        existing.recurrenceEndDate != recurrenceEndDate ||
        existing.recurrenceCount != recurrenceCount
    val recurrenceClosed = existing.recurrence.isRecurring && !recurrence.isRecurring

    return existing.copy(
        name = name,
        startAt = startAt,
        endAt = endAt,
        leadMinutes = leadMinutes,
        mode = mode,
        recurrence = recurrence,
        weekDays = weekDays,
        monthDays = monthDays,
        recurrenceEndDate = recurrenceEndDate,
        recurrenceCount = recurrenceCount,
        recurrenceExceptions = if (recurrenceClosed) emptyList() else existing.recurrenceExceptions,
        remindedOccurrenceStart = if (scheduleChanged) null else existing.remindedOccurrenceStart,
        updatedAt = now,
    )
}

/** 用于日志与状态文案的简单描述。 */
fun Todo.describe(clock: Clock): String =
    "Todo(id=$id, name=$name, start=$startAt, end=$endAt, lead=$leadMinutes, " +
        "mode=$mode, recurrence=$recurrence, done=$isDone, " +
        "reminded=$remindedOccurrenceStart, now=${clock.now()})"
