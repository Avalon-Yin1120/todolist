package com.avalonyin1120.todolist.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 判断两条待办「会不会真的撞在同一段时间上」。
 *
 * ## 为什么不能只比开始/结束时间戳
 *
 * 周期待办在库里只有**一条**记录，`startAt/endAt` 存的是**规则自身**（第一次的）时间，
 * 而不是之后每一场的时间。早先的冲突检测直接拿两个时间戳比大小，于是两头都错：
 *
 * * **误报**：每周一 9:00 与每周三 9:00 只在同一天创建时才会被比到，用户在相近的
 *   日期建第二条就被提示「该时间段已有其他待办」，可这两条永远不可能撞在一起
 *   （用户报的 bug）；
 * * **漏报**：每天 9:00 的规则与「某天 9:00」的一次性待办，只要那个日子不是规则的
 *   起始日，就检测不到。
 *
 * 所以这里按**真正的场次**比较：先比「一天之内的时段」是否可能重叠（不同时段直接
 * 判否，省掉后面的搜索），再找是否存在「两边都出现的那一天」。
 */
object Overlap {

    /** 两条都是周期规则时的搜索上限：一年之内必然能判出它们是否可能相遇。 */
    const val SEARCH_DAYS = 366L

    private const val SECONDS_PER_DAY = 24 * 60 * 60

    /**
     * [a] 与 [b] 是否存在任何一场时间重叠。
     *
     * 任意一条可以是周期待办；两条都可能是。一次性待办按原有语义（区间相交）。
     */
    fun intersects(a: Todo, b: Todo, zone: ZoneId): Boolean {
        val aRecurring = a.recurrence.isRecurring
        val bRecurring = b.recurrence.isRecurring

        // 都不重复：就是最朴素的区间相交。
        if (!aRecurring && !bRecurring) {
            return intervalsOverlap(a.startAt, a.endAt, b.startAt, b.endAt)
        }

        // 先做便宜的排除：一天之内的时段不重叠，规则再怎么叠也不会撞。
        if (!dailyWindowsOverlap(a, b, zone)) return false

        // 一条重复、一条一次性：只需看一次性那条所落的日期，及其前后各一天
        // （前后一天覆盖「22:00 开始、次日 02:00 结束」这种跨零点场次）。
        if (!aRecurring || !bRecurring) {
            val recurring = if (aRecurring) a else b
            val single = if (aRecurring) b else a
            var date = Instant.ofEpochMilli(single.startAt).atZone(zone).toLocalDate().minusDays(1)
            val last = Instant.ofEpochMilli(single.endAt).atZone(zone).toLocalDate().plusDays(1)
            while (!date.isAfter(last)) {
                if (RecurrenceExpander.occursOn(recurring, date, zone)) {
                    val window = RecurrenceExpander.occurrenceOn(recurring, date, zone)
                    if (intervalsOverlap(window.startAt, window.endAt, single.startAt, single.endAt)) {
                        return true
                    }
                }
                date = date.plusDays(1)
            }
            return false
        }

        // 两条都是周期：找一个「两边都出现」的日期。时段已确认可能重叠，
        // 所以只要日期能对上就是撞车，不必再逐场构造时间戳。
        var date = firstSearchDate(a, b, zone)
        var scanned = 0L
        while (scanned <= SEARCH_DAYS) {
            if (RecurrenceExpander.occursOn(a, date, zone) && occursNear(b, date, zone)) {
                return true
            }
            date = date.plusDays(1)
            scanned++
        }
        return false
    }

    /** [date] 当天或前后一天 [todo] 是否出现（跨零点场次用）。 */
    private fun occursNear(todo: Todo, date: LocalDate, zone: ZoneId): Boolean =
        RecurrenceExpander.occursOn(todo, date, zone) ||
            RecurrenceExpander.occursOn(todo, date.minusDays(1), zone) ||
            RecurrenceExpander.occursOn(todo, date.plusDays(1), zone)

    /** 从两条规则都开始生效的那天起找（更早的日子里至少有一条不存在）。 */
    private fun firstSearchDate(a: Todo, b: Todo, zone: ZoneId): LocalDate {
        val aStart = Instant.ofEpochMilli(a.startAt).atZone(zone).toLocalDate()
        val bStart = Instant.ofEpochMilli(b.startAt).atZone(zone).toLocalDate()
        val aFrom = RecurrenceExpander.effectiveStartDate(a, aStart)
        val bFrom = RecurrenceExpander.effectiveStartDate(b, bStart)
        return maxOf(aFrom, bFrom)
    }

    /** 半开区间 [s1,e1) 与 [s2,e2) 是否相交。 */
    private fun intervalsOverlap(s1: Long, e1: Long, s2: Long, e2: Long): Boolean =
        s1 < e2 && s2 < e1

    /**
     * 两条待办「各自在一天之内占的那几段时间」是否可能重叠。
     *
     * 周期场次只换日期、不换时分（[RecurrenceExpander.occurrenceOn] 就是这么算的），
     * 所以这一步能一次排除掉绝大多数不相干的组合：9:00 的待办与 15:00 的待办，
     * 不管规则怎么排都不会撞。
     */
    private fun dailyWindowsOverlap(a: Todo, b: Todo, zone: ZoneId): Boolean =
        segmentsOverlap(dailySegments(a, zone), dailySegments(b, zone))

    /**
     * 把一条待办的时段拆成「一天之内的秒区间」。
     *
     * 时长为 0（起止相同）时返回空列表——与旧的区间相交判定一致：没有长度的东西
     * 不占用任何时间，也就不该触发冲突提示。
     */
    private fun dailySegments(todo: Todo, zone: ZoneId): List<IntRange> {
        val start = Instant.ofEpochMilli(todo.startAt).atZone(zone)
        val startSecond = start.hour * 3600 + start.minute * 60 + start.second
        val duration = ((todo.endAt - todo.startAt) / 1000L).toInt()
        if (duration <= 0) return emptyList()
        if (duration >= SECONDS_PER_DAY) return listOf(0 until SECONDS_PER_DAY)
        val endSecond = startSecond + duration
        return if (endSecond <= SECONDS_PER_DAY) {
            listOf(startSecond until endSecond)
        } else {
            // 跨零点：拆成「今天剩下的那段」和「明天开头的那段」
            listOf(startSecond until SECONDS_PER_DAY, 0 until (endSecond - SECONDS_PER_DAY))
        }
    }

    private fun segmentsOverlap(a: List<IntRange>, b: List<IntRange>): Boolean =
        a.any { x -> b.any { y -> x.first < y.last + 1 && y.first < x.last + 1 } }
}
