package com.example.todolist.ui

import com.example.todolist.domain.Todo
import java.time.LocalDate
import java.time.ZoneId

/**
 * 周视图里的一条「时间轴色块」。
 *
 * 存在的意义是把「画多高、画多宽、画在左边第几条」这几件事算清楚，
 * 于是这部分逻辑不依赖任何 Android / Compose 类型，可以在 JVM 单测里直接断言。
 *
 * @param startHour 从当天 00:00 起算的起始小时数，已裁剪到 0.0..24.0
 * @param endHour   从当天 00:00 起算的结束小时数，已裁剪到 0.0..24.0，且一定大于 [startHour]
 * @param lane      在本重叠簇里占第几条泳道（从 0 开始）
 * @param laneCount 本重叠簇实际用到的泳道数；为 1 时说明这条独占整列宽度
 */
data class WeekEventBlock(
    val todo: Todo,
    val startHour: Double,
    val endHour: Double,
    val lane: Int,
    val laneCount: Int,
)

/**
 * 周视图的色块排版算法。
 *
 * 目标：每一个待办都是一个独立的实心圆角矩形，高度与持续时长成正比；
 * 时间上重叠的待办平分列宽、左右错开并排，谁也不会被谁盖住。
 */
object WeekLayout {

    /** 一天的小时数。24 小时要压进一屏，所以这里的单位是「小时的小数」，不是格子。 */
    private const val HOURS_PER_DAY = 24.0

    private const val MILLIS_PER_HOUR = 3_600_000.0

    /** 一天的长度（毫秒）。按固定 24 小时算，与 [CalendarLayout] 的换算口径保持一致。 */
    private const val MILLIS_PER_DAY = 24 * 3_600_000L

    /**
     * 往回找几天，看有没有「早就开始、还没结束」的待办延续到当天。
     *
     * 取 6 是因为周视图正好展示 7 天：任何在展示的这周里开始、之后跨天延续的待办
     * 都会被这部分捞到；只看前一天的话，连开三天的待办从第二天起就会整个消失。
     */
    private const val SPILL_LOOKBACK_DAYS = 6

    /**
     * 某一天的色块，按开始时间升序。
     *
     * 分两步：先划分「重叠簇」，再在簇内贪心分配泳道。
     * 之所以要先分簇，是因为只有在同一个簇里，块之间才会互相遮挡，
     * 不同簇的块各自独占整列宽就好，不必为别的时段腾地方。
     */
    fun blocksOf(todos: List<Todo>, date: LocalDate, zone: ZoneId): List<WeekEventBlock> {
        val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd = dayStart + MILLIS_PER_DAY

        // 当天开始的事，外加前几天开始、跨过午夜还没结束的那一段。
        // CalendarLayout.todosOn 是按开始日期归类的，只看当天会把跨天的事整条漏掉，
        // 于是「23:00 到次日 01:00」在第二天那一列上什么都不剩。
        // 前一天必须查；再往前只有当「存在超过 24 小时的长待办」时才有意义，
        // 否则每画一周都要多做几十次重复规则展开，白白变慢。
        val longestMillis = todos.maxOfOrNull { it.endAt - it.startAt } ?: 0L
        val lookbackDays = if (longestMillis > MILLIS_PER_DAY) SPILL_LOOKBACK_DAYS else 1
        val spill = (1..lookbackDays).flatMap { back ->
            CalendarLayout.todosOn(todos, date.minusDays(back.toLong()), zone)
        }.filter { it.endAt > dayStart }

        val spans = (CalendarLayout.todosOn(todos, date, zone) + spill).mapNotNull { todo ->
            val start = ((maxOf(todo.startAt, dayStart) - dayStart) / MILLIS_PER_HOUR)
                .coerceIn(0.0, HOURS_PER_DAY)
            val end = ((minOf(todo.endAt, dayEnd) - dayStart) / MILLIS_PER_HOUR)
                .coerceIn(0.0, HOURS_PER_DAY)
            // 零时长、以及被当天边界裁没了（end <= start）的，画出来也没有可见高度
            if (end > start) Span(todo, start, end) else null
        }.sortedBy { it.start }

        val result = mutableListOf<WeekEventBlock>()
        var cursor = 0
        while (cursor < spans.size) {
            // 划分重叠簇：只要下一块的开始时间落在「本簇已见的最大结束时间」之前就属于同一簇。
            // 用最大结束时间而不是上一块的结束时间，A-B 重叠、B-C 重叠而 A-C 不重叠时
            // 三者仍在同一簇，泳道数才不会被低估。
            var clusterEnd = spans[cursor].end
            var clusterEndIndex = cursor + 1
            while (clusterEndIndex < spans.size && spans[clusterEndIndex].start < clusterEnd) {
                clusterEnd = maxOf(clusterEnd, spans[clusterEndIndex].end)
                clusterEndIndex++
            }

            // 簇内贪心分泳道：放进第一条「已经空出来」的泳道，都占着就新开一条。
            // 用 <= 而不是 < ：首尾相接（上一场刚结束下一场就开始）放在同一条泳道没问题。
            val laneEnds = mutableListOf<Double>()
            val lanes = IntArray(clusterEndIndex - cursor)
            for (i in cursor until clusterEndIndex) {
                val span = spans[i]
                val freeLane = laneEnds.indexOfFirst { it <= span.start }
                val lane = if (freeLane >= 0) {
                    laneEnds[freeLane] = span.end
                    freeLane
                } else {
                    laneEnds += span.end
                    laneEnds.size - 1
                }
                lanes[i - cursor] = lane
            }

            val laneCount = laneEnds.size
            for (i in cursor until clusterEndIndex) {
                val span = spans[i]
                result += WeekEventBlock(
                    todo = span.todo,
                    startHour = span.start,
                    endHour = span.end,
                    lane = lanes[i - cursor],
                    laneCount = laneCount,
                )
            }
            cursor = clusterEndIndex
        }
        return result
    }

    /** 簇划分用的内部区间：已裁剪到当天，避免在后续步骤里反复重算边界。 */
    private data class Span(val todo: Todo, val start: Double, val end: Double)
}
