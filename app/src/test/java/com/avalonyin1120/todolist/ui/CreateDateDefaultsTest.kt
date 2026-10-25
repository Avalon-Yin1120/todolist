package com.avalonyin1120.todolist.ui

import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Test

/**
 * 「点 + 新建时默认落在哪一天」。
 *
 * 全部用固定日期手算期望值，不依赖「今天」：这类规则一旦依赖运行当天的日期，
 * 测试就会在月底、闰年、跨年这些边界上时红时绿。
 */
class CreateDateDefaultsTest {

    /** 示例里的「今天」：2025-01-15，周三。 */
    private val today = LocalDate.of(2025, 1, 15)

    @Test
    fun `列表页没有日期上下文`() {
        assertThat(CreateDateDefaults.forView(NavTab.TODO, today, today)).isNull()
    }

    @Test
    fun `日视图就用翻到的那一天`() {
        val anchor = LocalDate.of(2025, 3, 3)
        assertThat(CreateDateDefaults.forView(NavTab.DAY, anchor, today)).isEqualTo(anchor)
    }

    @Test
    fun `周视图取那一周里与今天同一个星期几`() {
        // 本周（周一 1/13）里与今天同为周三的那一天 = 1/15
        assertThat(CreateDateDefaults.forView(NavTab.WEEK, LocalDate.of(2025, 1, 13), today))
            .isEqualTo(LocalDate.of(2025, 1, 15))
        // 下一周（周一 1/20）里的周三 = 1/22
        assertThat(CreateDateDefaults.forView(NavTab.WEEK, LocalDate.of(2025, 1, 20), today))
            .isEqualTo(LocalDate.of(2025, 1, 22))
        // 月视图里 pager 上报的代表日是 1 号，用它当锚点也应该算对
        assertThat(CreateDateDefaults.forView(NavTab.WEEK, LocalDate.of(2025, 1, 1), today))
            .isEqualTo(LocalDate.of(2025, 1, 1))
    }

    @Test
    fun `周视图在周日也不越到下下周`() {
        // 锚点是 2025-01-13（周一），要周日 → 1/19，而不是 1/26
        assertThat(CreateDateDefaults.dateWithDayOfWeek(LocalDate.of(2025, 1, 13), DayOfWeek.SUNDAY))
            .isEqualTo(LocalDate.of(2025, 1, 19))
        // 周日跨月时也不能因为减法为负而跑偏
        assertThat(CreateDateDefaults.dateWithDayOfWeek(LocalDate.of(2025, 2, 24), DayOfWeek.SUNDAY))
            .isEqualTo(LocalDate.of(2025, 3, 2))
    }

    @Test
    fun `月视图取那个月里与今天同一个号数`() {
        assertThat(CreateDateDefaults.forView(NavTab.MONTH, LocalDate.of(2025, 2, 1), today))
            .isEqualTo(LocalDate.of(2025, 2, 15))
        assertThat(CreateDateDefaults.forView(NavTab.MONTH, LocalDate.of(2025, 6, 1), today))
            .isEqualTo(LocalDate.of(2025, 6, 15))
    }

    @Test
    fun `月份没有那一天时落到月末`() {
        // 「今天」是 31 号，翻到 2 月 → 2 月 28 日（2025 非闰年）
        val day31 = LocalDate.of(2025, 1, 31)
        assertThat(CreateDateDefaults.forView(NavTab.MONTH, LocalDate.of(2025, 2, 1), day31))
            .isEqualTo(LocalDate.of(2025, 2, 28))
        // 闰年 2 月 → 29 日
        assertThat(CreateDateDefaults.forView(NavTab.MONTH, LocalDate.of(2024, 2, 1), day31))
            .isEqualTo(LocalDate.of(2024, 2, 29))
        // 30 号遇 2 月同样落到月末
        val day30 = LocalDate.of(2025, 1, 30)
        assertThat(CreateDateDefaults.forView(NavTab.MONTH, LocalDate.of(2025, 2, 1), day30))
            .isEqualTo(LocalDate.of(2025, 2, 28))
        // 31 号遇 30 天的小月 → 4 月 30 日
        assertThat(CreateDateDefaults.forView(NavTab.MONTH, LocalDate.of(2025, 4, 1), day31))
            .isEqualTo(LocalDate.of(2025, 4, 30))
    }

    @Test
    fun `跨年翻月也对`() {
        assertThat(CreateDateDefaults.forView(NavTab.MONTH, LocalDate.of(2026, 1, 1), today))
            .isEqualTo(LocalDate.of(2026, 1, 15))
    }
}
