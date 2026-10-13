package com.avalonyin1120.todolist.domain

/**
 * 周视图纵向显示的时间窗。
 *
 * 周视图把一天压进一屏，窗口取多宽直接决定每条待办能画多高：
 * 24 小时全显示时 1 小时只有 20 多 dp，一小时的事几乎看不清名字；
 * 只显示 06:00~22:00 则每小时能多出三分之一的高度。
 *
 * 放在 `domain` 而不是 `ui`：它是数据层（设置项）与界面层共同遵守的规则，
 * 两边各写一份夹取逻辑迟早会不一致。纯 Kotlin，无 Android 依赖，可直接单测。
 */
object WeekWindow {

    /** 默认起始：早上 6 点。凌晨那一截极少有人排待办。 */
    const val DEFAULT_START_HOUR = 6

    /** 默认结束：晚上 22 点。 */
    const val DEFAULT_END_HOUR = 22

    /** 窗口至少要覆盖多少小时，否则时间轴会被压成几根线，失去意义。 */
    const val MIN_HOURS = 2

    /** 把起始小时夹进「0 .. 结束小时 - MIN_HOURS」。 */
    fun clampStart(hour: Int, endHour: Int): Int {
        val safeEnd = clampEndRaw(endHour)
        return hour.coerceIn(0, safeEnd - MIN_HOURS)
    }

    /** 把结束小时夹进「起始小时 + MIN_HOURS .. 24」。 */
    fun clampEnd(hour: Int, startHour: Int): Int {
        val safeStart = clampStartRaw(startHour)
        return hour.coerceIn(safeStart + MIN_HOURS, 24)
    }

    /** 起始小时的可选范围（供设置页生成候选项）。 */
    fun startOptions(endHour: Int): IntRange = 0..(clampEndRaw(endHour) - MIN_HOURS)

    /** 结束小时的可选范围（供设置页生成候选项）。 */
    fun endOptions(startHour: Int): IntRange = (clampStartRaw(startHour) + MIN_HOURS)..24

    /** 窗口小时数，一定不小于 [MIN_HOURS]。 */
    fun span(startHour: Int, endHour: Int): Int = clampEnd(endHour, startHour) - clampStart(startHour, endHour)

    /**
     * 时间轴上每隔几小时画一个刻度。
     *
     * 目标是「够用但不拥挤」：窗口 16 小时时每 2 小时一个（9 个刻度，间距约 89dp），
     * 24 小时时每 4 小时一个。规则写成「找最小的步长使区间数不超过 [MAX_INTERVALS]」，
     * 于是改窗口宽度时刻度密度会自动跟着变，不需要再调参数。
     */
    fun labelStepHours(startHour: Int, endHour: Int): Int =
        STEPS.firstOrNull { span(startHour, endHour) / it <= MAX_INTERVALS } ?: STEPS.last()

    /** 时间轴刻度的小时列表（含起点，不一定含终点）。 */
    fun labelHours(startHour: Int, endHour: Int): List<Int> {
        val start = clampStart(startHour, endHour)
        val end = clampEnd(endHour, startHour)
        val step = labelStepHours(start, end)
        return generateSequence(start) { it + step }.takeWhile { it <= end }.toList()
    }

    /** 原始起始小时的兜底（不依赖另一个值，避免互相递归）。 */
    private fun clampStartRaw(hour: Int): Int = hour.coerceIn(0, 24 - MIN_HOURS)

    /** 原始结束小时的兜底。 */
    private fun clampEndRaw(hour: Int): Int = hour.coerceIn(MIN_HOURS, 24)

    /** 依次尝试的刻度步长（小时）。 */
    private val STEPS = listOf(1, 2, 3, 4, 6, 12)

    /** 刻度之间的区间数上限；实际画出的刻度数最多是这个值 + 1。 */
    private const val MAX_INTERVALS = 8
}
