package com.avalonyin1120.todolist.domain

import com.avalonyin1120.todolist.testing.TestData
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Test

/**
 * 「跨越天数」的判定。
 *
 * 这组测试盯的是**日历日**而不是时长：23:00→次日 01:00 只有 2 小时，
 * 却跨越 2 天；而 09:00→18:00 有 9 小时，只占 1 天。
 * 用户说的「跨天」永远是前者那种意思。
 */
class DaySpanTest {

    private val zone: ZoneId = TestData.ZONE

    private fun at(date: LocalDate, hour: Int, minute: Int = 0): Long =
        LocalDateTime.of(date, java.time.LocalTime.of(hour, minute))
            .atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `同一天内结束算 1 天`() {
        val day = LocalDate.of(2025, 1, 15)
        assertThat(DaySpan.spanDays(at(day, 9), at(day, 18), zone)).isEqualTo(1)
        assertThat(DaySpan.offsetDays(at(day, 9), at(day, 18), zone)).isEqualTo(0)
    }

    @Test
    fun `23 点到次日 1 点算 2 天`() {
        val day = LocalDate.of(2025, 1, 15)
        assertThat(DaySpan.spanDays(at(day, 23), at(day.plusDays(1), 1), zone)).isEqualTo(2)
        assertThat(DaySpan.offsetDays(at(day, 23), at(day.plusDays(1), 1), zone)).isEqualTo(1)
    }

    @Test
    fun `跨两个月也算对`() {
        assertThat(
            DaySpan.spanDays(
                at(LocalDate.of(2025, 1, 31), 22),
                at(LocalDate.of(2025, 2, 2), 6),
                zone,
            ),
        ).isEqualTo(3)
    }

    @Test
    fun `结束早于开始时按 0 偏移处理而不是负数`() {
        val day = LocalDate.of(2025, 1, 15)
        // 脏数据：结束时间在起始时间之前。这里只要求不抛异常、不给负数，
        // 因为调用方会拿它当循环上界（负数会让界面整段空掉）。
        assertThat(DaySpan.offsetDays(at(day, 18), at(day, 9), zone)).isEqualTo(0)
        assertThat(DaySpan.spanDays(at(day, 18), at(day, 9), zone)).isEqualTo(1)
    }

    @Test
    fun `改跨度时保留结束时刻的钟点`() {
        val day = LocalDate.of(2025, 1, 15)
        val start = at(day, 22)
        val end = at(day.plusDays(1), 1)

        val newEnd = DaySpan.endAtWithOffset(start, end, 2, zone)!!

        // 第 3 天（偏移 2）的 01:00，钟点一点没变
        assertThat(java.time.Instant.ofEpochMilli(newEnd).atZone(zone).toLocalDateTime())
            .isEqualTo(LocalDateTime.of(2025, 1, 17, 1, 0))
        assertThat(DaySpan.spanDays(start, newEnd, zone)).isEqualTo(3)
    }

    @Test
    fun `偏移会被夹在允许范围内`() {
        val day = LocalDate.of(2025, 1, 15)
        val start = at(day, 22)
        val end = at(day, 23)

        val newEnd = DaySpan.endAtWithOffset(start, end, 999, zone)!!
        assertThat(DaySpan.spanDays(start, newEnd, zone)).isEqualTo(DaySpan.MAX_SPAN_DAYS)
    }

    @Test
    fun `缺少起止时间时返回 null 而不是编一个值`() {
        assertThat(DaySpan.endAtWithOffset(null, 1L, 1, zone)).isNull()
        assertThat(DaySpan.endAtWithOffset(1L, null, 1, zone)).isNull()
    }

    @Test
    fun `提示文案写清楚跨越几天`() {
        assertThat(DaySpan.describe(2)).isEqualTo("该待办跨越了 2 天")
        assertThat(DaySpan.describeRecurring(3)).contains("该待办跨越了 3 天")
        assertThat(DaySpan.describeRecurring(3)).contains("每一场")
    }
}
