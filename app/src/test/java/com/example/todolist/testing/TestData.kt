package com.example.todolist.testing

import com.example.todolist.domain.Clock
import com.example.todolist.domain.MonthDays
import com.example.todolist.domain.Recurrence
import com.example.todolist.domain.RecurrenceException
import com.example.todolist.domain.ReminderMode
import com.example.todolist.domain.Todo
import com.example.todolist.domain.WeekDays
import java.time.ZoneId

/** 测试数据工厂，集中时间常量避免各测试各写一遍。 */
object TestData {

    const val MINUTE = 60_000L

    /** 固定「现在」：2025-01-15 10:00:00 UTC。 */
    const val NOW = 1_736_935_200_000L

    /** 测试固定时区，避免依赖机器时区。 */
    val ZONE: ZoneId = ZoneId.of("Asia/Shanghai")

    fun clock(now: Long = NOW): Clock = FakeClock(now)

    fun todo(
        id: Long = 1L,
        name: String = "小组会议",
        startAt: Long = NOW + 60 * MINUTE,
        endAt: Long = NOW + 120 * MINUTE,
        leadMinutes: Int = 0,
        mode: ReminderMode = ReminderMode.NOTIFICATION,
        recurrence: Recurrence = Recurrence.NONE,
        weekDays: WeekDays = emptySet(),
        monthDays: MonthDays = emptySet(),
        recurrenceStartDate: String? = null,
        recurrenceEndDate: String? = null,
        recurrenceCount: Int? = null,
        recurrenceExceptions: List<RecurrenceException> = emptyList(),
        parentId: Long? = null,
        isDone: Boolean = false,
        remindedOccurrenceStart: Long? = null,
    ): Todo = Todo(
        id = id,
        name = name,
        startAt = startAt,
        endAt = endAt,
        leadMinutes = leadMinutes,
        mode = mode,
        recurrence = recurrence,
        weekDays = weekDays,
        monthDays = monthDays,
        recurrenceStartDate = recurrenceStartDate,
        recurrenceEndDate = recurrenceEndDate,
        recurrenceCount = recurrenceCount,
        recurrenceExceptions = recurrenceExceptions,
        parentId = parentId,
        isDone = isDone,
        remindedOccurrenceStart = remindedOccurrenceStart,
        createdAt = NOW,
        updatedAt = NOW,
    )
}
