package com.example.todolist.ui

import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

/**
 * 日历翻页的「页 <-> 日期」换算。
 *
 * 这段逻辑被反复改坏过（可变锚点导致映射漂移，出现「左滑日期不变」「好像翻到头了」），
 * 所以在这里把关键不变量钉死：
 *
 * 1. 互逆：pageOfDate(dateOfPage(page)) == page，对任意页都成立；
 * 2. 同页等价：同一周/同一月里的任意一天都映射到同一页；
 * 3. 单调：页号 +1 恰好前进一期，不会多跳或少跳；
 * 4. 锚点无关：换算只依赖绝对基准，不受任何会话状态影响。
 */
class CalendarPagingTest {

    private val middle = 6000

    // ------------------------------------------------------------ 互逆

    @Test
    fun `日视图页与日期互逆`() {
        for (page in listOf(middle - 50, middle - 1, middle, middle + 1, middle + 50)) {
            assertThat(pageOfDate(NavTab.DAY, dateOfPage(NavTab.DAY, page))).isEqualTo(page)
        }
    }

    @Test
    fun `周视图页与日期互逆`() {
        for (page in listOf(middle - 50, middle - 1, middle, middle + 1, middle + 50)) {
            assertThat(pageOfDate(NavTab.WEEK, dateOfPage(NavTab.WEEK, page))).isEqualTo(page)
        }
    }

    @Test
    fun `月视图页与日期互逆`() {
        for (page in listOf(middle - 50, middle - 1, middle, middle + 1, middle + 50)) {
            assertThat(pageOfDate(NavTab.MONTH, dateOfPage(NavTab.MONTH, page))).isEqualTo(page)
        }
    }

    // ------------------------------------------------------------ 单调前进

    @Test
    fun `页号加一恰好前进一期`() {
        val day = dateOfPage(NavTab.DAY, middle)
        assertThat(dateOfPage(NavTab.DAY, middle + 1)).isEqualTo(day.plusDays(1))

        val week = dateOfPage(NavTab.WEEK, middle)
        assertThat(dateOfPage(NavTab.WEEK, middle + 1)).isEqualTo(week.plusWeeks(1))

        val month = dateOfPage(NavTab.MONTH, middle)
        assertThat(dateOfPage(NavTab.MONTH, middle + 1)).isEqualTo(month.plusMonths(1))
    }

    @Test
    fun `左右翻页对称且能回到原处`() {
        // 这正是用户报的问题：右滑之后再左滑，应当回到原来那一期
        val startWeek = dateOfPage(NavTab.WEEK, middle)
        val afterRight = dateOfPage(NavTab.WEEK, middle + 1)
        val backLeft = dateOfPage(NavTab.WEEK, middle + 1 - 1)
        assertThat(afterRight).isEqualTo(startWeek.plusWeeks(1))
        assertThat(backLeft).isEqualTo(startWeek)

        val startMonth = dateOfPage(NavTab.MONTH, middle)
        assertThat(dateOfPage(NavTab.MONTH, middle + 1 - 1)).isEqualTo(startMonth)
    }

    // ------------------------------------------------------------ 同页等价

    @Test
    fun `同一周里的任意一天都映射到同一页`() {
        val monday = LocalDate.of(2026, 5, 11) // 周一
        val page = pageOfDate(NavTab.WEEK, monday)
        for (offset in 0L..6L) {
            assertThat(pageOfDate(NavTab.WEEK, monday.plusDays(offset))).isEqualTo(page)
        }
        // 下一天（下周一）才落到下一页
        assertThat(pageOfDate(NavTab.WEEK, monday.plusDays(7))).isEqualTo(page + 1)
    }

    @Test
    fun `同一月里的任意一天都映射到同一页`() {
        val page = pageOfDate(NavTab.MONTH, LocalDate.of(2026, 5, 1))
        assertThat(pageOfDate(NavTab.MONTH, LocalDate.of(2026, 5, 15))).isEqualTo(page)
        assertThat(pageOfDate(NavTab.MONTH, LocalDate.of(2026, 5, 31))).isEqualTo(page)
        assertThat(pageOfDate(NavTab.MONTH, LocalDate.of(2026, 6, 1))).isEqualTo(page + 1)
    }

    @Test
    fun `周视图以周一为起点`() {
        // 2026-05-17 是周日，属于 5/11 那一周
        val sunday = LocalDate.of(2026, 5, 17)
        assertThat(pageOfDate(NavTab.WEEK, sunday))
            .isEqualTo(pageOfDate(NavTab.WEEK, LocalDate.of(2026, 5, 11)))
        assertThat(dateOfPage(NavTab.WEEK, pageOfDate(NavTab.WEEK, sunday)).dayOfWeek)
            .isEqualTo(java.time.DayOfWeek.MONDAY)
    }

    // ------------------------------------------------------------ 锚点无关

    @Test
    fun `换算不受任何会话状态影响`() {
        // 同一个日期、同一个页，问多少次都得到同样的结果（没有可变锚点）
        val date = LocalDate.of(2026, 10, 11)
        val first = pageOfDate(NavTab.WEEK, date)
        repeat(5) {
            assertThat(pageOfDate(NavTab.WEEK, date)).isEqualTo(first)
            assertThat(dateOfPage(NavTab.WEEK, first)).isEqualTo(LocalDate.of(2026, 10, 5))
        }
    }

    @Test
    fun `回到今天等价于滚到今天所在页`() {
        val today = LocalDate.of(2026, 10, 11)
        val todayPage = pageOfDate(NavTab.WEEK, today)
        // 从别的页回到今天：页号必须落在 today 那一页，且该页渲染的就是本周
        assertThat(dateOfPage(NavTab.WEEK, todayPage))
            .isEqualTo(CalendarLayout.weekStart(today))
        assertThat(CalendarLayout.weekStart(dateOfPage(NavTab.WEEK, todayPage)))
            .isEqualTo(CalendarLayout.weekStart(today))
    }

    @Test
    fun `跨年跨月边界也互逆`() {
        // 12 月 31 日往前一周跨年
        val newYear = LocalDate.of(2026, 1, 1)
        val p = pageOfDate(NavTab.WEEK, newYear)
        assertThat(dateOfPage(NavTab.WEEK, p)).isEqualTo(CalendarLayout.weekStart(newYear))
        assertThat(dateOfPage(NavTab.WEEK, p - 1)).isEqualTo(CalendarLayout.weekStart(newYear).minusWeeks(1))

        // 1 月往前一个月跨年
        val jan = LocalDate.of(2026, 1, 15)
        val mp = pageOfDate(NavTab.MONTH, jan)
        assertThat(dateOfPage(NavTab.MONTH, mp)).isEqualTo(LocalDate.of(2026, 1, 1))
        assertThat(dateOfPage(NavTab.MONTH, mp - 1)).isEqualTo(LocalDate.of(2025, 12, 1))
    }
}
