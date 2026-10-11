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
 * 4. 锚点无关：换算只依赖绝对基准，不受任何会话状态影响；
 * 5. 预算够用：日常会用到的日期都必须落在 0 until PAGE_COUNT（见下方「页码预算」）。
 *
 * 第 5 条是补上的：曾经 MIDDLE = 6000（锚点 2000-01-01），日视图 1 页 1 天，
 * 只覆盖到 2016 年，于是「回到今天」被 coerceIn 夹到末页——用户滑两下就"到头了"。
 */
class CalendarPagingTest {

    private val middle = MIDDLE

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

    // ------------------------------------------------------------ 页码预算
    //
    // 这一组对应真实 bug：日视图曾经只覆盖到 2016 年，
    // 「回到今天」算出的页号被夹到末页，表现是滑几下就到头、回不到今天。

    @Test
    fun `日视图的预算必须覆盖今天前后的长期跨度`() {
        val today = LocalDate.of(2026, 10, 11)
        // 断言：这些日期都在合法页号内（越界会抛，消息里带页码）
        checkPagingBudget(
            NavTab.DAY,
            today,
            today.minusYears(50),
            today.plusYears(50),
            LocalDate.of(1983, 8, 1), // 旧预算的下边界附近
            LocalDate.of(2016, 6, 1), // 旧预算的上边界附近
        )
    }

    @Test
    fun `周月视图的预算必须覆盖今天前后的长期跨度`() {
        val today = LocalDate.of(2026, 10, 11)
        for (tab in listOf(NavTab.WEEK, NavTab.MONTH)) {
            checkPagingBudget(tab, today, today.minusYears(50), today.plusYears(50))
        }
    }

    @Test
    fun `回到今天算出的是今天那一页而不是被夹到边界`() {
        val today = LocalDate.of(2026, 10, 11)
        for (tab in listOf(NavTab.DAY, NavTab.WEEK, NavTab.MONTH)) {
            val page = pageOfDate(tab, today)
            // 旧实现下这里会等于 PAGE_COUNT - 1（末页），显示的日期远离今天
            assertThat(page).isGreaterThan(0)
            assertThat(page).isLessThan(PAGE_COUNT - 1)
            // 今天这一页round-trip 回来必须还是今天所属的那一期
            assertThat(dateOfPage(tab, page))
                .isEqualTo(
                    when (tab) {
                        NavTab.WEEK -> CalendarLayout.weekStart(today)
                        NavTab.MONTH -> today.withDayOfMonth(1)
                        else -> today
                    },
                )
        }
    }

    @Test
    fun `预算不会因为年份增长而失效`() {
        // 手机上日期只会往前走：把 2050、2100 年的「今天」也钉一遍，
        // 免得以后某天用户发现「回到今天」又在边界上。
        for (tab in listOf(NavTab.DAY, NavTab.WEEK, NavTab.MONTH)) {
            checkPagingBudget(tab, LocalDate.of(2050, 1, 1), LocalDate.of(2100, 1, 1))
        }
    }

    @Test
    fun `锚点页与总页数保持对称`() {
        // MIDDLE 两侧页数必须相等，否则「滑到头」会在某一侧提前出现
        assertThat(PAGE_COUNT % 2).isEqualTo(1)
        assertThat(MIDDLE).isEqualTo(PAGE_COUNT / 2)
    }

    @Test
    fun `越界时断言会抛出并指出页码`() {
        val error = runCatching { checkPagingBudget(NavTab.DAY, LocalDate.of(2900, 1, 1)) }
        assertThat(error.isFailure).isTrue()
        assertThat(error.exceptionOrNull()).isInstanceOf(IllegalStateException::class.java)
        assertThat(error.exceptionOrNull()!!.message).contains("翻页预算不够")
    }
}
