package com.example.todolist.domain

import com.example.todolist.testing.TestData
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * 提醒时间与状态判定的边界情况。
 *
 * 这些是所有功能里最容易出错、也最值得先测的部分（纯 JVM，毫秒级执行）。
 */
class ReminderTimeTest {

    private val minute = TestData.MINUTE

    @Test
    fun `提前量为零时提醒时刻等于起始时间`() {
        val start = 1_700_000_000_000L
        assertThat(ReminderTime.remindAt(start, 0)).isEqualTo(start)
    }

    @Test
    fun `提前量按分钟往前推`() {
        val start = 1_700_000_000_000L
        assertThat(ReminderTime.remindAt(start, 30)).isEqualTo(start - 30 * minute)
        assertThat(ReminderTime.remindAt(start, 1440)).isEqualTo(start - 24 * 60 * minute)
    }

    @Test
    fun `跨月与跨年计算正确`() {
        // 2025-01-01 00:30 UTC 往前推 1 天 -> 2024-12-31 00:30 UTC
        val start = 1_735_691_400_000L
        val expected = start - 1440 * minute
        assertThat(ReminderTime.remindAt(start, 1440)).isEqualTo(expected)
    }

    @Test
    fun `提前量上限是七天`() {
        assertThat(ReminderTime.isValidLead(0)).isTrue()
        assertThat(ReminderTime.isValidLead(10080)).isTrue()
        assertThat(ReminderTime.isValidLead(10081)).isFalse()
        assertThat(ReminderTime.isValidLead(-1)).isFalse()
    }

    @Test
    fun `终止时间必须晚于起始时间`() {
        assertThat(ReminderTime.isValidRange(1000L, 2000L)).isTrue()
        assertThat(ReminderTime.isValidRange(2000L, 1000L)).isFalse()
        assertThat(ReminderTime.isValidRange(1000L, 1000L)).isFalse()
    }

    @Test
    fun `名称不能为空且不能超过上限`() {
        assertThat(ReminderTime.isValidName("  ")).isFalse()
        assertThat(ReminderTime.isValidName("会议")).isTrue()
        assertThat(ReminderTime.isValidName("a".repeat(80))).isTrue()
        assertThat(ReminderTime.isValidName("a".repeat(81))).isFalse()
        // 首尾空白不算长度
        assertThat(ReminderTime.isValidName(" a ".repeat(26) + "a")).isTrue()
    }

    @Test
    fun `状态判定在边界上正确`() {
        val now = TestData.NOW
        val todo = TestData.todo(startAt = now + 10 * minute, endAt = now + 20 * minute)

        assertThat(ReminderTime.statusOf(todo, now)).isEqualTo(TodoStatus.UPCOMING)
        // 恰好等于起始时间 -> 进行中
        assertThat(ReminderTime.statusOf(todo, now + 10 * minute)).isEqualTo(TodoStatus.RUNNING)
        // 恰好等于终止时间 -> 已结束
        assertThat(ReminderTime.statusOf(todo, now + 20 * minute)).isEqualTo(TodoStatus.ENDED)
        assertThat(ReminderTime.statusOf(todo.copy(isDone = true), now)).isEqualTo(TodoStatus.DONE)
    }

    @Test
    fun `已经提醒过的待办不再排定`() {
        val now = TestData.NOW
        val base = TestData.todo()
        assertThat(ReminderTime.shouldSchedule(base, now)).isTrue()
        assertThat(ReminderTime.shouldSchedule(base.copy(notifiedAt = now), now)).isFalse()
    }

    @Test
    fun `已完成的事项不再排定`() {
        assertThat(ReminderTime.shouldSchedule(TestData.todo(isDone = true), TestData.NOW)).isFalse()
    }

    @Test
    fun `提醒时刻已过则不排定以免立刻刷屏`() {
        val now = TestData.NOW
        // 起始时间已经过去
        assertThat(ReminderTime.shouldSchedule(TestData.todo(startAt = now - minute), now)).isFalse()
        // 起始时间在未来，但提前量把提醒时刻推到了过去
        assertThat(
            ReminderTime.shouldSchedule(
                TestData.todo(startAt = now + 10 * minute, leadMinutes = 30),
                now,
            )
        ).isFalse()
        // 提醒时刻恰好等于现在 -> 不排定
        assertThat(
            ReminderTime.shouldSchedule(
                TestData.todo(startAt = now + 10 * minute, leadMinutes = 10),
                now,
            )
        ).isFalse()
        // 提醒时刻还有一秒 -> 要排定
        assertThat(
            ReminderTime.shouldSchedule(
                TestData.todo(startAt = now + 10 * minute + 1000, leadMinutes = 10),
                now,
            )
        ).isTrue()
    }

    @Test
    fun `待办自身的派生属性与纯函数一致`() {
        val todo = TestData.todo(startAt = TestData.NOW + 60 * minute, leadMinutes = 15)
        assertThat(todo.remindAt).isEqualTo(ReminderTime.remindAt(todo.startAt, 15))
        assertThat(todo.status(TestData.NOW)).isEqualTo(TodoStatus.UPCOMING)
    }

    @Test
    fun `策略由精确开关决定`() {
        assertThat(TestData.todo(useExactAlarm = true).toReminderPlan().strategy)
            .isEqualTo(ReminderStrategy.EXACT_ALARM)
        assertThat(TestData.todo(useExactAlarm = false).toReminderPlan().strategy)
            .isEqualTo(ReminderStrategy.NOTIFICATION)
    }
}
