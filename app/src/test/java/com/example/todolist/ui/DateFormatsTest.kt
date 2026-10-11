package com.example.todolist.ui

import com.example.todolist.domain.Recurrence
import com.example.todolist.testing.TestData
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

/**
 * 日期手动输入解析、月视图深浅强度、以及重叠时长的合并。
 *
 * 这三处都对应本轮的缺陷修复，而且都是纯函数，适合放在 JVM 单测里钉死。
 */
class DateFormatsTest {

    // -------------------------------------------------- 手动输入年月日（第 9 项）

    @Test
    fun `单数字的月与日也能解析`() {
        // 这正是原来的缺陷：要求 2026/01/01，用户输入 2026/1/1 会被判非法
        assertThat(DateFormats.parseDateParts("2026", "1", "1"))
            .isEqualTo(LocalDate.of(2026, 1, 1))
    }

    @Test
    fun `补零写法同样可以解析`() {
        assertThat(DateFormats.parseDateParts("2026", "01", "01"))
            .isEqualTo(LocalDate.of(2026, 1, 1))
    }

    @Test
    fun `前后空格不影响解析`() {
        assertThat(DateFormats.parseDateParts(" 2026 ", " 1 ", " 1 "))
            .isEqualTo(LocalDate.of(2026, 1, 1))
    }

    @Test
    fun `不存在的日期返回空`() {
        // 2026 不是闰年，没有 2 月 29 日
        assertThat(DateFormats.parseDateParts("2026", "2", "29")).isNull()
        assertThat(DateFormats.parseDateParts("2026", "4", "31")).isNull()
        assertThat(DateFormats.parseDateParts("2026", "13", "1")).isNull()
        assertThat(DateFormats.parseDateParts("2026", "0", "1")).isNull()
    }

    @Test
    fun `空输入与非数字返回空`() {
        assertThat(DateFormats.parseDateParts("", "1", "1")).isNull()
        assertThat(DateFormats.parseDateParts("2026", "", "1")).isNull()
        assertThat(DateFormats.parseDateParts("2026", "1", "")).isNull()
        assertThat(DateFormats.parseDateParts("abcd", "1", "1")).isNull()
    }

    @Test
    fun `闰年的二月二十九日可以解析`() {
        assertThat(DateFormats.parseDateParts("2028", "2", "29"))
            .isEqualTo(LocalDate.of(2028, 2, 29))
    }

    @Test
    fun `超出允许年份范围返回空`() {
        assertThat(DateFormats.parseDateParts("1899", "1", "1")).isNull()
        assertThat(DateFormats.parseDateParts("2201", "1", "1")).isNull()
        assertThat(DateFormats.parseDateParts("1900", "1", "1")).isNotNull()
        assertThat(DateFormats.parseDateParts("2200", "1", "1")).isNotNull()
    }

    // -------------------------------------------------- 月视图深浅（第 7 项）

    @Test
    fun `忙碌强度是连续值而不是几个档位`() {
        // 0.2 小时与 0.4 小时应当给出不同的强度，这正是「平滑」的含义
        val a = CalendarLayout.busyIntensity(0.2)
        val b = CalendarLayout.busyIntensity(0.4)
        assertThat(a).isGreaterThan(0f)
        assertThat(b).isGreaterThan(a)
    }

    @Test
    fun `忙碌强度单调不减且不超过一`() {
        val samples = listOf(0.0, 0.5, 1.0, 2.0, 4.0, 8.0, 10.0, 24.0)
        val intensities = samples.map { CalendarLayout.busyIntensity(it) }
        intensities.zipWithNext { x, y -> assertThat(y).isAtLeast(x) }
        assertThat(intensities.all { it in 0f..1f }).isTrue()
        assertThat(intensities.last()).isWithin(1e-4f).of(1f)
    }

    @Test
    fun `没有待办时强度为零`() {
        assertThat(CalendarLayout.busyIntensity(0.0)).isEqualTo(0f)
        assertThat(CalendarLayout.busyIntensity(-1.0)).isEqualTo(0f)
    }

    @Test
    fun `月的深浅颜色随小时数变化且不透明`() {
        val light = TodoColors.monthSmoothColor(0.5)
        val dark = TodoColors.monthSmoothColor(6.0)
        assertThat(dark).isNotEqualTo(light)
        // 深浅应当确实更深：红色分量不降、绿蓝分量下降
        assertThat(dark.green).isLessThan(light.green)
        assertThat(dark.blue).isLessThan(light.blue)
        assertThat(TodoColors.monthSmoothColor(0.0).alpha).isEqualTo(0f)
    }

    // -------------------------------------------------- 重叠时长合并（第 6 项）

    @Test
    fun `完全重叠的两条待办只算一次时长`() {
        val date = LocalDate.of(2026, 5, 11)
        val todos = listOf(
            TestData.todo(id = 1, startAt = at(2026, 5, 11, 9), endAt = at(2026, 5, 11, 10)),
            TestData.todo(id = 2, startAt = at(2026, 5, 11, 9), endAt = at(2026, 5, 11, 10)),
        )
        // 各 1 小时但完全重叠：合并后是 1 小时，不是 2 小时
        assertThat(CalendarLayout.busyHours(todos, date, TestData.ZONE)).isWithin(1e-6).of(1.0)
    }

    @Test
    fun `首尾相接的区间不会被合并`() {
        val date = LocalDate.of(2026, 5, 11)
        val todos = listOf(
            TestData.todo(id = 1, startAt = at(2026, 5, 11, 9), endAt = at(2026, 5, 11, 10)),
            TestData.todo(id = 2, startAt = at(2026, 5, 11, 10), endAt = at(2026, 5, 11, 11)),
        )
        assertThat(CalendarLayout.busyHours(todos, date, TestData.ZONE)).isWithin(1e-6).of(2.0)
    }

    @Test
    fun `部分重叠只合并重叠段`() {
        val date = LocalDate.of(2026, 5, 11)
        val todos = listOf(
            TestData.todo(id = 1, startAt = at(2026, 5, 11, 9), endAt = at(2026, 5, 11, 11)),
            TestData.todo(id = 2, startAt = at(2026, 5, 11, 10), endAt = at(2026, 5, 11, 12)),
        )
        // 9-11 与 10-12 合并成 9-12 = 3 小时
        assertThat(CalendarLayout.busyHours(todos, date, TestData.ZONE)).isWithin(1e-6).of(3.0)
    }

    @Test
    fun `忙碌区间按真实时刻给出起止小时`() {
        val date = LocalDate.of(2026, 5, 11)
        // 9:30 - 10:15
        val todos = listOf(
            TestData.todo(
                id = 1,
                startAt = at(2026, 5, 11, 9) + 30 * 60_000L,
                endAt = at(2026, 5, 11, 10) + 15 * 60_000L,
            ),
        )
        val spans = CalendarLayout.busySpans(todos, date, TestData.ZONE)
        assertThat(spans).hasSize(1)
        assertThat(spans[0].first).isWithin(1e-6).of(9.5)
        assertThat(spans[0].second).isWithin(1e-6).of(10.25)
    }

    // -------------------------------------------------- 重复范围（第 12 项）

    @Test
    fun `按次数推算截止日期`() {
        // 每周一，共 3 次：5/11、5/18、5/25
        val todo = TestData.todo(
            startAt = at(2026, 5, 11),
            endAt = at(2026, 5, 11, 10),
            recurrence = Recurrence.WEEKLY,
            weekDays = setOf(java.time.DayOfWeek.MONDAY),
        )
        val end = com.example.todolist.domain.RecurrenceExpander
            .endDateForCount(todo, 3, TestData.ZONE)
        assertThat(end).isEqualTo(LocalDate.of(2026, 5, 25))
    }

    @Test
    fun `按截止日期推算次数`() {
        val todo = TestData.todo(
            startAt = at(2026, 5, 11),
            endAt = at(2026, 5, 11, 10),
            recurrence = Recurrence.WEEKLY,
            weekDays = setOf(java.time.DayOfWeek.MONDAY),
        )
        val count = com.example.todolist.domain.RecurrenceExpander
            .countUntil(todo, LocalDate.of(2026, 5, 25), TestData.ZONE)
        assertThat(count).isEqualTo(3)
    }

    @Test
    fun `不重复的待办次数恒为一`() {
        val todo = TestData.todo(
            startAt = at(2026, 5, 11),
            endAt = at(2026, 5, 11, 10),
            recurrence = Recurrence.NONE,
        )
        assertThat(
            com.example.todolist.domain.RecurrenceExpander
                .countUntil(todo, LocalDate.of(2026, 12, 31), TestData.ZONE),
        ).isEqualTo(1)
        assertThat(
            com.example.todolist.domain.RecurrenceExpander
                .endDateForCount(todo, 5, TestData.ZONE),
        ).isNull()
    }

    // -------------------------------------------------- 重复范围（第 12 项）

    @Test
    fun `不填生效起始日时从待办自身的起始日生效`() {
        val todo = TestData.todo(
            startAt = at(2026, 5, 11),
            endAt = at(2026, 5, 11, 10),
            recurrence = Recurrence.DAILY,
        )
        // 起始日当天就生效
        assertThat(occursOn(todo, LocalDate.of(2026, 5, 11))).isTrue()
        assertThat(occursOn(todo, LocalDate.of(2026, 5, 10))).isFalse()
    }

    @Test
    fun `填了生效起始日后之前不生效`() {
        val todo = TestData.todo(
            startAt = at(2026, 5, 11),
            endAt = at(2026, 5, 11, 10),
            recurrence = Recurrence.DAILY,
            recurrenceStartDate = "2026-06-01",
        )
        // 待办本身 5/11 开始，但重复要到 6/1 才生效
        assertThat(occursOn(todo, LocalDate.of(2026, 5, 11))).isFalse()
        assertThat(occursOn(todo, LocalDate.of(2026, 5, 31))).isFalse()
        assertThat(occursOn(todo, LocalDate.of(2026, 6, 1))).isTrue()
        assertThat(occursOn(todo, LocalDate.of(2026, 6, 2))).isTrue()
    }

    @Test
    fun `生效起始日影响次数与截止日期的互算`() {
        // 每周一，但从 6/1（周一）才生效
        val todo = TestData.todo(
            startAt = at(2026, 5, 11),
            endAt = at(2026, 5, 11, 10),
            recurrence = Recurrence.WEEKLY,
            weekDays = setOf(java.time.DayOfWeek.MONDAY),
            recurrenceStartDate = "2026-06-01",
        )
        // 3 次：6/1、6/8、6/15（5 月的周一不计入）
        assertThat(endDateForCount(todo, 3)).isEqualTo(LocalDate.of(2026, 6, 15))
        assertThat(countUntil(todo, LocalDate.of(2026, 6, 15))).isEqualTo(3)
        // 数到 5/31 时一次都还没生效，至少按 1 计
        assertThat(countUntil(todo, LocalDate.of(2026, 5, 31))).isEqualTo(1)
    }

    @Test
    fun `截止日期限制最后一场`() {
        val todo = TestData.todo(
            startAt = at(2026, 5, 11),
            endAt = at(2026, 5, 11, 10),
            recurrence = Recurrence.WEEKLY,
            weekDays = setOf(java.time.DayOfWeek.MONDAY),
            recurrenceEndDate = "2026-05-25",
        )
        assertThat(occursOn(todo, LocalDate.of(2026, 5, 25))).isTrue()
        assertThat(occursOn(todo, LocalDate.of(2026, 6, 1))).isFalse()
        assertThat(countUntil(todo, LocalDate.of(2026, 5, 25))).isEqualTo(3)
    }

    @Test
    fun `次数与截止日期同时存在时以先到者为准`() {
        val todo = TestData.todo(
            startAt = at(2026, 5, 11),
            endAt = at(2026, 5, 11, 10),
            recurrence = Recurrence.WEEKLY,
            weekDays = setOf(java.time.DayOfWeek.MONDAY),
            recurrenceEndDate = "2026-06-29", // 允许到 6/29
            recurrenceCount = 2, // 但只排 2 次：5/11、5/18
        )
        assertThat(occursOn(todo, LocalDate.of(2026, 5, 18))).isTrue()
        assertThat(occursOn(todo, LocalDate.of(2026, 5, 25))).isFalse()
    }

    @Test
    fun `每次都是同一时刻只换日期`() {
        val todo = TestData.todo(
            startAt = at(2026, 5, 11, 9, 30),
            endAt = at(2026, 5, 11, 10, 30),
            recurrence = Recurrence.DAILY,
        )
        val occurrence = com.example.todolist.domain.RecurrenceExpander
            .occurrenceOn(todo, LocalDate.of(2026, 5, 20), TestData.ZONE)
        assertThat(occurrence.startAt).isEqualTo(at(2026, 5, 20, 9, 30))
        assertThat(occurrence.endAt).isEqualTo(at(2026, 5, 20, 10, 30))
    }

    // -------------------------------------------------- 改规则后重算截止日期

    @Test
    fun `改重复于哪些天后截止日期跟着重算`() {
        val start = at(2026, 5, 11, 9) // 2026-05-11 是周一
        // 只勾周三，重复 3 次 -> 5/13、5/20、5/27
        val wednesdayOnly = DraftFormState(
            startAt = start,
            endAt = start + 3_600_000,
            recurrence = Recurrence.WEEKLY,
            weekDays = setOf(java.time.DayOfWeek.WEDNESDAY),
            recurrenceCount = 3,
        )
        assertThat(deadlineIsoForCurrentRule(wednesdayOnly, TestData.ZONE))
            .isEqualTo("2026-05-27")

        // 再勾上周一：第 3 场变成 5/18，截止日期必须跟着变
        val mondayAndWednesday = wednesdayOnly.copy(
            weekDays = setOf(java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.WEDNESDAY),
        )
        assertThat(deadlineIsoForCurrentRule(mondayAndWednesday, TestData.ZONE))
            .isEqualTo("2026-05-18")
    }

    @Test
    fun `改每月几号后截止日期跟着重算`() {
        val start = at(2026, 5, 1, 9)
        val day1 = DraftFormState(
            startAt = start,
            endAt = start + 3_600_000,
            recurrence = Recurrence.MONTHLY,
            monthDays = setOf(1),
            recurrenceCount = 3,
        )
        // 5/1、6/1、7/1
        assertThat(deadlineIsoForCurrentRule(day1, TestData.ZONE)).isEqualTo("2026-07-01")

        // 改成每月 1 号与 15 号：5/1、5/15、6/1 -> 6/1
        val day1And15 = day1.copy(monthDays = setOf(1, 15))
        assertThat(deadlineIsoForCurrentRule(day1And15, TestData.ZONE)).isEqualTo("2026-06-01")
    }

    @Test
    fun `没有次数时不推算截止日期`() {
        val noCount = DraftFormState(
            startAt = at(2026, 5, 11, 9),
            endAt = at(2026, 5, 11, 10),
            recurrence = Recurrence.WEEKLY,
            weekDays = setOf(java.time.DayOfWeek.MONDAY),
            recurrenceCount = null,
        )
        assertThat(deadlineIsoForCurrentRule(noCount, TestData.ZONE)).isNull()

        // 一次性待办也不该推算
        val oneOff = noCount.copy(recurrence = Recurrence.NONE, recurrenceCount = 5)
        assertThat(deadlineIsoForCurrentRule(oneOff, TestData.ZONE)).isNull()
    }

    @Test
    fun `生效起始日参与截止日期推算`() {
        val state = DraftFormState(
            startAt = at(2026, 5, 11, 9),
            endAt = at(2026, 5, 11, 10),
            recurrence = Recurrence.WEEKLY,
            weekDays = setOf(java.time.DayOfWeek.MONDAY),
            recurrenceStartDate = "2026-06-01",
            recurrenceCount = 3,
        )
        // 6/1、6/8、6/15（5 月的周一不计入）
        assertThat(deadlineIsoForCurrentRule(state, TestData.ZONE)).isEqualTo("2026-06-15")
    }

    // -------------------------------------------------- 通用

    private fun occursOn(todo: com.example.todolist.domain.Todo, date: LocalDate): Boolean =
        com.example.todolist.domain.RecurrenceExpander.occursOn(todo, date, TestData.ZONE)

    private fun endDateForCount(todo: com.example.todolist.domain.Todo, count: Int): LocalDate? =
        com.example.todolist.domain.RecurrenceExpander.endDateForCount(todo, count, TestData.ZONE)

    private fun countUntil(todo: com.example.todolist.domain.Todo, end: LocalDate): Int =
        com.example.todolist.domain.RecurrenceExpander.countUntil(todo, end, TestData.ZONE)

    private fun at(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0): Long =
        LocalDate.of(year, month, day).atTime(hour, minute)
            .atZone(TestData.ZONE).toInstant().toEpochMilli()
}
