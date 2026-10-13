package com.avalonyin1120.todolist.ui

import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Test

/**
 * 新增/编辑待办里的日期对话框：自绘日历 [CompactCalendar] 改成可以横滑翻月后，
 * 它依赖的两块纯逻辑必须钉死——这里不碰 Compose、不用 Robolectric。
 *
 * 1. 页 <-> 月份 的绝对映射 [pageOfMonth] / [monthOfPage]：
 *    HorizontalPager 的每一页都要能独立算出自己画哪个月，所以映射必须互逆、单调，
 *    且跨年（1 月 / 12 月）不能算错——算错的表现是「滑到 1 月却显示 12 月」。
 * 2. 固定 42 天网格 [CalendarLayout.monthGridDatesFixed]：
 *    横滑翻月时每页高度必须一致，否则滑动过程中整个日历会上下跳动。
 */
class CompactCalendarPagingTest {

    /** 覆盖跨年、跨世纪与页码两端边界。 */
    private val sampleMonths: List<YearMonth> = listOf(
        YearMonth.of(2026, 1), // 1 月往前跨年
        YearMonth.of(2026, 2),
        YearMonth.of(2026, 12), // 12 月往后跨年
        YearMonth.of(2027, 1),
        YearMonth.of(2025, 12),
        YearMonth.of(2024, 2), // 闰年 2 月（29 天）
        YearMonth.of(2000, 1), // 锚点月
        YearMonth.of(1900, 1), // 第 0 页（覆盖范围的最小端）
        YearMonth.of(2100, 1), // 最后一页（覆盖范围的最大端）
    )

    /** 一整年的 12 个月，供网格测试遍历（含闰年与非闰年）。 */
    private fun monthsOf(year: Int): List<YearMonth> = (1..12).map { YearMonth.of(year, it) }

    private val gridMonths: List<YearMonth> = monthsOf(2024) + monthsOf(2026)

    // ------------------------------------------------------------ 页 <-> 月份

    @Test
    fun `月份翻成页码再翻回来还是同一个月`() {
        sampleMonths.forEach { month ->
            assertThat(monthOfPage(pageOfMonth(month))).isEqualTo(month)
        }
    }

    @Test
    fun `页码翻成月份再翻回来还是同一页`() {
        // 覆盖锚点附近与覆盖范围两端：页码也必须是月份的严格反函数
        val pages = listOf(
            0,
            1,
            MONTH_MIDDLE - 500,
            MONTH_MIDDLE - 1,
            MONTH_MIDDLE,
            MONTH_MIDDLE + 1,
            MONTH_MIDDLE + 500,
            MONTH_PAGE_COUNT - 1,
        )
        pages.forEach { page ->
            assertThat(pageOfMonth(monthOfPage(page))).isEqualTo(page)
        }
    }

    @Test
    fun `相邻月份页码正好相差一`() {
        sampleMonths.forEach { month ->
            assertThat(pageOfMonth(month.plusMonths(1))).isEqualTo(pageOfMonth(month) + 1)
            assertThat(pageOfMonth(month.minusMonths(1))).isEqualTo(pageOfMonth(month) - 1)
        }
        // 12 月 -> 次年 1 月这种跨年也必须只走一页
        assertThat(pageOfMonth(YearMonth.of(2027, 1)) - pageOfMonth(YearMonth.of(2026, 12)))
            .isEqualTo(1)
    }

    @Test
    fun `锚点页与总页数保持对称`() {
        // HorizontalPager 的页数是固定的，两侧页数不等会让「往一个方向滑到头」提前出现
        assertThat(MONTH_PAGE_COUNT % 2).isEqualTo(1)
        assertThat(MONTH_MIDDLE).isEqualTo(MONTH_PAGE_COUNT / 2)
        // 2000-01 是锚点，前后各 100 年分别落在第 0 页与最后一页
        assertThat(pageOfMonth(YearMonth.of(2000, 1))).isEqualTo(MONTH_MIDDLE)
        assertThat(pageOfMonth(YearMonth.of(1900, 1))).isEqualTo(0)
        assertThat(pageOfMonth(YearMonth.of(2100, 1))).isEqualTo(MONTH_PAGE_COUNT - 1)
    }

    @Test
    fun `每一页的月份都在合法页号内`() {
        // 越界的页码交给 pager 只会得到一页空白，所以先把整个页号空间检查一遍
        for (page in 0 until MONTH_PAGE_COUNT) {
            assertThat(pageOfMonth(monthOfPage(page))).isEqualTo(page)
        }
    }

    // ------------------------------------------------------------ 固定 42 天网格

    @Test
    fun `每一天网格长度恒为 42 且包含该月所有日期`() {
        gridMonths.forEach { month ->
            val grid = CalendarLayout.monthGridDatesFixed(month)
            assertThat(grid).hasSize(42)
            // 1 号可能落在第一行的任意一列，所以按「包含」断言而不是只看第 0 格
            assertThat(grid).contains(month.atDay(1))
            (1..month.lengthOfMonth()).forEach { day ->
                assertThat(grid).contains(month.atDay(day))
            }
        }
    }

    @Test
    fun `每一天网格严格递增且不重复`() {
        gridMonths.forEach { month ->
            val grid = CalendarLayout.monthGridDatesFixed(month)
            assertThat(grid.toSet()).hasSize(42)
            for (i in 1 until grid.size) {
                assertThat(grid[i]).isEqualTo(grid[i - 1].plusDays(1))
            }
        }
    }

    @Test
    fun `每一天网格的第一天是周一最后一天是周日`() {
        gridMonths.forEach { month ->
            val grid = CalendarLayout.monthGridDatesFixed(month)
            assertThat(grid.first().dayOfWeek).isEqualTo(DayOfWeek.MONDAY)
            assertThat(grid.last().dayOfWeek).isEqualTo(DayOfWeek.SUNDAY)
            // 42 天 = 6 整行，每行 7 天
            assertThat(grid.size % 7).isEqualTo(0)
        }
    }

    @Test
    fun `固定网格只是在原来的月网格末尾补齐`() {
        // 既有函数的行为不能变（月视图等调用方与测试依赖它原来的行数：
        // 2 月可能是 4 行），固定网格只是把它原样当作前缀再往后补。
        gridMonths.forEach { month ->
            val original = CalendarLayout.monthGridDates(month)
            val fixed = CalendarLayout.monthGridDatesFixed(month)
            assertThat(listOf(28, 35, 42)).contains(original.size)
            // 前缀必须与 monthGridDates 完全一致（用 List 相等，逐元素比较）
            assertThat(fixed.take(original.size)).isEqualTo(original)
            assertThat(fixed.size).isAtLeast(original.size)
        }
    }

    @Test
    fun `闰年与非闰年的二月都能补齐到 42 天`() {
        val leap = CalendarLayout.monthGridDatesFixed(YearMonth.of(2024, 2))
        val common = CalendarLayout.monthGridDatesFixed(YearMonth.of(2026, 2))
        assertThat(leap).hasSize(42)
        assertThat(common).hasSize(42)
        // 闰年 2 月有 29 天，必须真的出现在网格里
        assertThat(leap).contains(LocalDate.of(2024, 2, 29))
    }
}
