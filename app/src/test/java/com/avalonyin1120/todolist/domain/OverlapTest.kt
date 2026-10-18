package com.avalonyin1120.todolist.domain

import com.avalonyin1120.todolist.testing.TestData
import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Test

/**
 * 「时间段冲突」判定。
 *
 * 用户报的 bug：不同天的重复待办（例如每周一与每周三）在同一时刻仍提示
 * 「该段时间已有其他待办」。根因是旧实现只比了库里那条规则的**起始时间戳**，
 * 从没展开过场次。这里按场次把两个方向都钉住：不同天不误报、同一天不漏报。
 */
class OverlapTest {

    private val zone: ZoneId = TestData.ZONE

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int = 0): Long =
        LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant().toEpochMilli()

    private fun weekly(day: DayOfWeek, startAt: Long, endAt: Long, id: Long = 1L) = TestData.todo(
        id = id,
        startAt = startAt,
        endAt = endAt,
        recurrence = Recurrence.WEEKLY,
        weekDays = setOf(day),
    )

    // ---------------------------------------------------------- 误报：不同天

    @Test
    fun `每周一与每周三同一时刻不算冲突`() {
        // 2026-05-11 是周一，2026-05-13 是周三
        val monday = weekly(DayOfWeek.MONDAY, at(2026, 5, 11, 9), at(2026, 5, 11, 10), id = 1L)
        val wednesday = weekly(DayOfWeek.WEDNESDAY, at(2026, 5, 13, 9), at(2026, 5, 13, 10), id = 2L)

        assertThat(Overlap.intersects(monday, wednesday, zone)).isFalse()
        assertThat(Overlap.intersects(wednesday, monday, zone)).isFalse()
    }

    @Test
    fun `每月一号与每月十五号同一时刻不算冲突`() {
        val first = TestData.todo(
            id = 1L,
            startAt = at(2026, 5, 1, 9),
            endAt = at(2026, 5, 1, 10),
            recurrence = Recurrence.MONTHLY,
            monthDays = setOf(1),
        )
        val fifteenth = TestData.todo(
            id = 2L,
            startAt = at(2026, 5, 15, 9),
            endAt = at(2026, 5, 15, 10),
            recurrence = Recurrence.MONTHLY,
            monthDays = setOf(15),
        )

        assertThat(Overlap.intersects(first, fifteenth, zone)).isFalse()
    }

    @Test
    fun `一天里时段不同就不算冲突`() {
        val morning = TestData.todo(
            id = 1L,
            startAt = at(2026, 5, 11, 9),
            endAt = at(2026, 5, 11, 10),
            recurrence = Recurrence.DAILY,
        )
        val afternoon = TestData.todo(
            id = 2L,
            startAt = at(2026, 5, 13, 15),
            endAt = at(2026, 5, 13, 16),
            recurrence = Recurrence.NONE,
        )

        assertThat(Overlap.intersects(morning, afternoon, zone)).isFalse()
    }

    @Test
    fun `相邻但不重叠的时段不算冲突`() {
        val first = TestData.todo(
            id = 1L,
            startAt = at(2026, 5, 11, 9),
            endAt = at(2026, 5, 11, 10),
        )
        val second = TestData.todo(
            id = 2L,
            startAt = at(2026, 5, 11, 10),
            endAt = at(2026, 5, 11, 11),
        )

        assertThat(Overlap.intersects(first, second, zone)).isFalse()
    }

    // ---------------------------------------------------------- 漏报：同一天

    @Test
    fun `每天与某天的一次性待办会撞车`() {
        // 旧实现只比「规则的起始日」与「一次性待办那一天」，这条会漏报
        val daily = TestData.todo(
            id = 1L,
            startAt = at(2026, 5, 10, 9),
            endAt = at(2026, 5, 10, 10),
            recurrence = Recurrence.DAILY,
        )
        val once = TestData.todo(
            id = 2L,
            startAt = at(2026, 5, 20, 9, 30),
            endAt = at(2026, 5, 20, 10, 30),
        )

        assertThat(Overlap.intersects(daily, once, zone)).isTrue()
        assertThat(Overlap.intersects(once, daily, zone)).isTrue()
    }

    @Test
    fun `两个同样规则的周期待办会撞车`() {
        // 起始日不同，但都在每周一 9:00 → 一定撞
        val a = weekly(DayOfWeek.MONDAY, at(2026, 5, 11, 9), at(2026, 5, 11, 10), id = 1L)
        val b = weekly(DayOfWeek.MONDAY, at(2026, 6, 1, 9), at(2026, 6, 1, 10), id = 2L)

        assertThat(Overlap.intersects(a, b, zone)).isTrue()
    }

    @Test
    fun `每周一与周三点之后再创建仍能正确判断`() {
        // 周一 9:00-10:00 的规则，与「周一 9:30-9:45」的一次性（落在未来某个周一）
        val monday = weekly(DayOfWeek.MONDAY, at(2026, 5, 11, 9), at(2026, 5, 11, 10))
        val once = TestData.todo(
            id = 2L,
            startAt = at(2026, 5, 18, 9, 30),
            endAt = at(2026, 5, 18, 9, 45),
        )

        assertThat(Overlap.intersects(monday, once, zone)).isTrue()
        // 换成同一个周一的 10:00，就只是相邻，不重叠
        val adjacent = once.copy(id = 3L, startAt = at(2026, 5, 18, 10), endAt = at(2026, 5, 18, 11))
        assertThat(Overlap.intersects(monday, adjacent, zone)).isFalse()
    }

    // ---------------------------------------------------------- 边界

    @Test
    fun `跨零点的场次与次日凌晨仍算冲突`() {
        val overnight = TestData.todo(
            id = 1L,
            startAt = at(2026, 5, 11, 23),
            endAt = at(2026, 5, 12, 1),
            recurrence = Recurrence.DAILY,
        )
        val earlyMorning = TestData.todo(
            id = 2L,
            startAt = at(2026, 5, 20, 0, 30),
            endAt = at(2026, 5, 20, 1, 30),
        )

        assertThat(Overlap.intersects(overnight, earlyMorning, zone)).isTrue()
    }

    @Test
    fun `次数已经用完的规则不再参与冲突`() {
        val finishedRange = TestData.todo(
            id = 1L,
            startAt = at(2026, 5, 11, 9),
            endAt = at(2026, 5, 11, 10),
            recurrence = Recurrence.DAILY,
            recurrenceCount = 1, // 只有 5/11 这一场
        )
        val later = TestData.todo(
            id = 2L,
            startAt = at(2026, 5, 20, 9),
            endAt = at(2026, 5, 20, 10),
        )

        assertThat(Overlap.intersects(finishedRange, later, zone)).isFalse()
    }

    @Test
    fun `截止日期之后的场次不再参与冲突`() {
        val ended = TestData.todo(
            id = 1L,
            startAt = at(2026, 5, 11, 9),
            endAt = at(2026, 5, 11, 10),
            recurrence = Recurrence.DAILY,
            recurrenceEndDate = "2026-05-15",
        )
        val after = TestData.todo(
            id = 2L,
            startAt = at(2026, 5, 20, 9),
            endAt = at(2026, 5, 20, 10),
        )

        assertThat(Overlap.intersects(ended, after, zone)).isFalse()
    }
}
