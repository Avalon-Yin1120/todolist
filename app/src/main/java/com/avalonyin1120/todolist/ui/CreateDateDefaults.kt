package com.avalonyin1120.todolist.ui

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/**
 * 「点 + 新建时，日期默认落在哪一天」。
 *
 * ## 规则
 *
 * 用户翻到哪一期，新待办就落在那一期里**与今天对位的那一天**：
 *
 * | 视图 | 默认日期 |
 * |---|---|
 * | 待办（列表） | 现在（不用日期） |
 * | 日视图 | 翻到的那一天本身 |
 * | 周视图 | 那一周里与**今天同一个星期几**的那一天 |
 * | 月视图 | 那个月里与**今天同一个号数**的那一天；该月没有这个号数（29/30/31）时取月末 |
 *
 * 举几个例子（今天是 10 月 24 日周四）：
 * * 周视图往后翻一周 → 默认 10 月 31 日（下周的周四）；
 * * 月视图翻到 11 月 → 默认 11 月 24 日；
 * * 月视图翻到 2 月（今天是 31 号时）→ 默认 2 月 28 日（月末兜底）；
 * * 日视图翻到 12 月 3 日 → 默认 12 月 3 日。
 *
 * ## 为什么不是「直接沿用当前选中日期」
 *
 * 月视图里「当前选中日期」有两种来源：pager 上报的**代表日**（1 号），
 * 以及用户上次真正关心的那一天（软写入保留下来的今天）。翻到别的月份时
 * 前者会留下「1 号」，用它当默认值就等于把每个新待办都排到 1 号——
 * 而用户脑子里的参照始终是「今天这个号数」。所以这里不用 selectedDate 的**号数**，
 * 只借它的**期**（哪一周 / 哪个月），号数与星期从 [today] 取。
 */
object CreateDateDefaults {

    /**
     * @param anchor 当前视图翻到的那一期里的任意一天（即 `selectedDate`）
     * @param today  今天
     * @return 默认日期；null 表示「跟随现在」
     */
    fun forView(tab: NavTab, anchor: LocalDate, today: LocalDate): LocalDate? = when (tab) {
        // 列表页没有「日期上下文」可言，新建就落在现在
        NavTab.TODO -> null
        NavTab.DAY -> anchor
        NavTab.WEEK -> dateWithDayOfWeek(anchor, today.dayOfWeek)
        NavTab.MONTH -> dateWithDayOfMonth(anchor, today.dayOfMonth)
    }

    /** [anchor] 所在那一周里、星期几等于 [dayOfWeek] 的那一天。 */
    fun dateWithDayOfWeek(anchor: LocalDate, dayOfWeek: DayOfWeek): LocalDate {
        val weekStart = CalendarLayout.weekStart(anchor)
        // DayOfWeek.value 是 1(周一)..7(周日)，而 weekStart 一定是周一，
        // 所以差值直接就是偏移量；+7 再取模是为了兜住「周日 - 周一」这类减法为负的情况
        val offset = (dayOfWeek.value - weekStart.dayOfWeek.value + 7) % 7
        return weekStart.plusDays(offset.toLong())
    }

    /**
     * [anchor] 所在那一月里、号数等于 [dayOfMonth] 的那一天；该月没有这一天就取月末。
     *
     * 月末兜底与月重复规则里的处理（`RecurrenceExpander.occursOnSelectedMonthDay`）一致：
     * 用户在 31 号这天翻到 2 月，期望是「2 月的最后一天」，而不是「什么都选不上」。
     */
    fun dateWithDayOfMonth(anchor: LocalDate, dayOfMonth: Int): LocalDate {
        val month = YearMonth.from(anchor)
        return month.atDay(dayOfMonth.coerceIn(1, month.lengthOfMonth()))
    }
}
