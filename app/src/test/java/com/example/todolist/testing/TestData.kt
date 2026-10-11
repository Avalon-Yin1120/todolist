package com.example.todolist.testing

import com.example.todolist.domain.Clock
import com.example.todolist.domain.SystemClock
import com.example.todolist.domain.Todo

/** 测试数据工厂，集中时间常量避免各测试各写一遍。 */
object TestData {

    const val MINUTE = 60_000L

    /** 固定「现在」：2025-01-15 10:00:00 UTC。 */
    const val NOW = 1_736_935_200_000L

    fun clock(now: Long = NOW): Clock = SystemClock().let { FakeClock(now) }

    fun todo(
        id: Long = 1L,
        name: String = "小组会议",
        startAt: Long = NOW + 60 * MINUTE,
        endAt: Long = NOW + 120 * MINUTE,
        leadMinutes: Int = 0,
        useExactAlarm: Boolean = false,
        isDone: Boolean = false,
        notifiedAt: Long? = null,
    ): Todo = Todo(
        id = id,
        name = name,
        startAt = startAt,
        endAt = endAt,
        leadMinutes = leadMinutes,
        useExactAlarm = useExactAlarm,
        isDone = isDone,
        notifiedAt = notifiedAt,
        createdAt = NOW,
        updatedAt = NOW,
    )
}
