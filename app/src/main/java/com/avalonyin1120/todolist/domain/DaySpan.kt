package com.avalonyin1120.todolist.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * 「这条待办跨越了几天」。
 *
 * ## 为什么单独抽出来
 *
 * 跨越天数不是时长：23:00 到次日 01:00 只有两个小时，却**占了两天**。
 * 用户说「跨了一天」指的是日历上多翻了一页，不是「持续了 24 小时」。
 * 周期待办尤其容易踩这个坑：一条跨天的单次待办被改成周期后，
 * 每一场都会跨天，但界面上两个时间框只显示 `23:00` 和 `01:00`，
 * 看起来像「结束早于开始」的错误输入——用户会以为是自己填错了。
 *
 * 所以这里按**本地日历的日期差**算天数：起始时刻所在日期与结束时刻所在日期之间差几天，
 * 再 +1（当天开始当天结束 = 1 天）。
 *
 * 时区必须显式传入：测试固定 `Asia/Shanghai`，真机用系统时区，
 * 而两者在跨日边界上的结论可能不同（这是「本地运行绿、CI 上红」的经典来源之一）。
 */
object DaySpan {

    /**
     * 允许的最大跨越天数。
     *
     * 31 天足够覆盖「月底到次月初」这类真实用法，同时挡住手滑把结束时间选到几年后的输入：
     * 周期待办每一场都按这个跨度展开，跨度越大，单屏内能看到的场次越少，也越容易重叠。
     */
    const val MAX_SPAN_DAYS: Int = 31

    /** 起始时刻所在日期（本地日历）。 */
    fun startDate(startAt: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(startAt).atZone(zone).toLocalDate()

    /** 结束时刻所在日期（本地日历）。 */
    fun endDate(endAt: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(endAt).atZone(zone).toLocalDate()

    /**
     * 结束日期相对起始日期差几天：同一天是 0，次日是 1，以此类推。
     *
     * 结束时刻早于起始时刻（脏数据，正常流程进不来）时回落到 0，
     * 而不是返回负数——调用方拿它做循环上界，负数会让界面直接空掉。
     */
    fun offsetDays(startAt: Long, endAt: Long, zone: ZoneId): Long =
        ChronoUnit.DAYS.between(startDate(startAt, zone), endDate(endAt, zone)).coerceAtLeast(0)

    /**
     * 跨越天数：被这条待办占掉的日历页数，最小 1。
     *
     * 23:00 → 次日 01:00 是 2 天；09:00 → 10:00 是 1 天。
     */
    fun spanDays(startAt: Long, endAt: Long, zone: ZoneId): Int =
        (offsetDays(startAt, endAt, zone) + 1).toInt().coerceAtLeast(1)

    /**
     * 把「结束日期相对起始日期差几天」改成 [offset]，**保留结束时刻的钟点**。
     *
     * 界面上两个时间框在周期态下只让用户改钟点（日期由重复范围决定），
     * 所以改跨度时不能把 01:00 改成 00:00 或别的什么：
     * 用户看到的是「02:00 → 次日 01:00」，跨度 +1 之后应该变成「02:00 → 第 3 天 01:00」。
     *
     * 起始时间缺失（还没选）时返回 null，由调用方决定怎么处理。
     */
    fun endAtWithOffset(
        startAt: Long?,
        endAt: Long?,
        offset: Long,
        zone: ZoneId,
    ): Long? {
        if (startAt == null) return null
        val safeOffset = offset.coerceIn(0L, (MAX_SPAN_DAYS - 1).toLong())
        val endTime = endAt
            ?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalTime() }
            ?: return null
        return startDate(startAt, zone)
            .plusDays(safeOffset)
            .atTime(endTime)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
    }

    /** 界面上的整句提示：用户报「会造成误解」的地方就用这一句把话说清楚。 */
    fun describe(spanDays: Int): String = "该待办跨越了 $spanDays 天"

    /** 周期态下的一句话：每一场都按同样的跨度展开。 */
    fun describeRecurring(spanDays: Int): String =
        "该待办跨越了 $spanDays 天——每一场都跨越 $spanDays 天"
}
