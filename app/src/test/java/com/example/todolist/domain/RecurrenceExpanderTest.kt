package com.example.todolist.domain

import com.example.todolist.testing.TestData
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * 周期规则的展开逻辑。
 *
 * 这些用例覆盖「某天到底有没有这一场」这个核心判断，
 * 也是周/月视图与提醒排定的共同基础。
 */
class RecurrenceExpanderTest {

    private val zone: ZoneId = TestData.ZONE

    /** 用本地时间构造一个 epoch 毫秒，避免测试里手算时区偏移。 */
    private fun at(year: Int, month: Int, day: Int, hour: Int = 9, minute: Int = 0): Long =
        LocalDate.of(year, month, day).atTime(hour, minute)
            .atZone(zone).toInstant().toEpochMilli()

    private fun date(year: Int, month: Int, day: Int): LocalDate = LocalDate.of(year, month, day)

    // ------------------------------------------------------------ 一次性

    @Test
    fun `一次性待办只在当天出现`() {
        val todo = TestData.todo(startAt = at(2026, 5, 10), endAt = at(2026, 5, 10, 10))

        assertThat(RecurrenceExpander.occursOn(todo, date(2026, 5, 10), zone)).isTrue()
        assertThat(RecurrenceExpander.occursOn(todo, date(2026, 5, 11), zone)).isFalse()
        assertThat(RecurrenceExpander.occursOn(todo, date(2026, 5, 9), zone)).isFalse()
    }

    @Test
    fun `展开一次性待办不改变时间`() {
        val start = at(2026, 5, 10, 14, 30)
        val todo = TestData.todo(startAt = start, endAt = start + 3_600_000)

        val occurrence = RecurrenceExpander.occurrenceOn(todo, date(2026, 5, 10), zone)

        assertThat(occurrence.startAt).isEqualTo(start)
        assertThat(occurrence.endAt).isEqualTo(start + 3_600_000)
    }

    // ------------------------------------------------------------ 每天

    @Test
    fun `每天重复从起始日为起点每天出现`() {
        val todo = TestData.todo(
            startAt = at(2026, 5, 10),
            endAt = at(2026, 5, 10, 10),
            recurrence = Recurrence.DAILY,
        )

        assertThat(RecurrenceExpander.occursOn(todo, date(2026, 5, 9), zone)).isFalse()
        assertThat(RecurrenceExpander.occursOn(todo, date(2026, 5, 10), zone)).isTrue()
        assertThat(RecurrenceExpander.occursOn(todo, date(2026, 5, 11), zone)).isTrue()
        assertThat(RecurrenceExpander.occursOn(todo, date(2026, 6, 30), zone)).isTrue()
    }

    @Test
    fun `每天重复的每场保留原来的时分`() {
        val todo = TestData.todo(
            startAt = at(2026, 5, 10, 7, 45),
            endAt = at(2026, 5, 10, 8, 15),
            recurrence = Recurrence.DAILY,
        )

        val later = RecurrenceExpander.occurrenceOn(todo, date(2026, 5, 13), zone)
        val expectedStart = at(2026, 5, 13, 7, 45)
        val expectedEnd = at(2026, 5, 13, 8, 15)

        assertThat(later.startAt).isEqualTo(expectedStart)
        assertThat(later.endAt).isEqualTo(expectedEnd)
    }

    // ------------------------------------------------------------ 每周

    @Test
    fun `每周重复只在同一星期几出现`() {
        // 2026-05-11 是周一
        val monday = TestData.todo(
            startAt = at(2026, 5, 11),
            endAt = at(2026, 5, 11, 10),
            recurrence = Recurrence.WEEKLY,
        )

        assertThat(RecurrenceExpander.occursOn(monday, date(2026, 5, 11), zone)).isTrue()  // 下周一
        assertThat(RecurrenceExpander.occursOn(monday, date(2026, 5, 18), zone)).isTrue()  // 再下周一
        assertThat(RecurrenceExpander.occursOn(monday, date(2026, 5, 12), zone)).isFalse() // 周二
        assertThat(RecurrenceExpander.occursOn(monday, date(2026, 5, 17), zone)).isFalse() // 周日
    }

    // ------------------------------------------------------------ 每月

    @Test
    fun `每月重复在同一个日出现`() {
        val todo = TestData.todo(
            startAt = at(2026, 5, 15),
            endAt = at(2026, 5, 15, 10),
            recurrence = Recurrence.MONTHLY,
        )

        assertThat(RecurrenceExpander.occursOn(todo, date(2026, 5, 15), zone)).isTrue()
        assertThat(RecurrenceExpander.occursOn(todo, date(2026, 6, 15), zone)).isTrue()
        assertThat(RecurrenceExpander.occursOn(todo, date(2026, 6, 16), zone)).isFalse()
    }

    @Test
    fun `每月31日在小月落到当月最后一天`() {
        val todo = TestData.todo(
            startAt = at(2026, 1, 31),
            endAt = at(2026, 1, 31, 10),
            recurrence = Recurrence.MONTHLY,
        )

        // 2 月只有 28 天（2026 非闰年）
        assertThat(RecurrenceExpander.occursOn(todo, date(2026, 2, 28), zone)).isTrue()
        assertThat(RecurrenceExpander.occursOn(todo, date(2026, 2, 27), zone)).isFalse()
        // 4 月只有 30 天
        assertThat(RecurrenceExpander.occursOn(todo, date(2026, 4, 30), zone)).isTrue()
    }

    // ------------------------------------------------------------ 例外

    @Test
    fun `被跳过的日期不再出现`() {
        val todo = TestData.todo(
            startAt = at(2026, 5, 10),
            endAt = at(2026, 5, 10, 10),
            recurrence = Recurrence.DAILY,
            recurrenceExceptions = listOf(
                RecurrenceException("2026-05-12", RecurrenceExceptionKind.SKIPPED)
            ),
        )

        assertThat(RecurrenceExpander.occursOn(todo, date(2026, 5, 11), zone)).isTrue()
        assertThat(RecurrenceExpander.occursOn(todo, date(2026, 5, 12), zone)).isFalse()
        assertThat(RecurrenceExpander.occursOn(todo, date(2026, 5, 13), zone)).isTrue()
    }

    // ------------------------------------------------------------ 范围展开

    @Test
    fun `范围内展开会按天产生多场并按时间排序`() {
        val daily = TestData.todo(
            id = 1L,
            name = "晨会",
            startAt = at(2026, 5, 10, 9),
            endAt = at(2026, 5, 10, 9, 30),
            recurrence = Recurrence.DAILY,
        )
        val once = TestData.todo(
            id = 2L,
            name = "体检",
            startAt = at(2026, 5, 11, 8),
            endAt = at(2026, 5, 11, 10),
        )

        val expanded = RecurrenceExpander.expandBetween(
            todos = listOf(daily, once),
            fromDate = date(2026, 5, 10),
            toDateExclusive = date(2026, 5, 13),
            zone = zone,
        )

        // 5/10 晨会；5/11 体检(8:00) + 晨会(9:00)；5/12 晨会
        assertThat(expanded.map { it.name })
            .containsExactly("晨会", "体检", "晨会", "晨会").inOrder()
        assertThat(expanded.map { it.startAt })
            .isInOrder()
    }

    @Test
    fun `空范围内的周期待办不产生场次`() {
        val todo = TestData.todo(
            startAt = at(2026, 5, 10),
            endAt = at(2026, 5, 10, 10),
            recurrence = Recurrence.DAILY,
        )

        val expanded = RecurrenceExpander.expandBetween(
            todos = listOf(todo),
            fromDate = date(2026, 5, 11),
            toDateExclusive = date(2026, 5, 11),
            zone = zone,
        )

        assertThat(expanded).isEmpty()
    }

    // ------------------------------------------------------------ 下一次

    @Test
    fun `找不到未来场次时返回null`() {
        val past = TestData.todo(
            startAt = at(2026, 1, 1),
            endAt = at(2026, 1, 1, 10),
        )
        val now = at(2026, 5, 10)

        assertThat(RecurrenceExpander.nextOccurrenceAfter(past, now, zone)).isNull()
    }

    @Test
    fun `每天重复的下一次就是明天同一时刻`() {
        val now = at(2026, 5, 10, 12)
        val todo = TestData.todo(
            startAt = at(2026, 5, 10, 9),
            endAt = at(2026, 5, 10, 9, 30),
            recurrence = Recurrence.DAILY,
        )

        val next = RecurrenceExpander.nextOccurrenceAfter(todo, now, zone)

        assertThat(next).isEqualTo(at(2026, 5, 11, 9))
    }

    @Test
    fun `日期键按本地时区生成`() {
        val instant = at(2026, 5, 10, 23, 30)

        assertThat(RecurrenceExpander.dateKeyOf(instant, zone)).isEqualTo("2026-05-10")
    }
}
