package com.avalonyin1120.todolist.domain

/**
 * [ReminderPlan] 使用的调度通道。
 */
enum class ReminderStrategy {
    /** 精确闹钟：AlarmManager.setAlarmClock / setExactAndAllowWhileIdle，需要精确闹钟权限。 */
    EXACT_ALARM,

    /** 普通通知：非精确闹钟（setAndAllowWhileIdle），无需权限，系统可能延后但 Doze 下仍会投递。 */
    NOTIFICATION,

    /**
     * 最后的保险：WorkManager 延迟任务。
     *
     * 只在「主通道之外再兜一次」时使用——它在国产 ROM 上最不可靠
     * （JobScheduler 会被省电策略推迟到用户下次打开 App），单独用它当主通道会漏提醒。
     */
    WORK_FALLBACK,
}

/**
 * 「这条待办该在什么时候、用什么策略提醒」的决策结果。
 *
 * 把决策与执行分开，是为了让调度语义可以在纯 JVM 上断言，
 * 见 reminder/ReminderSchedulingTest。
 *
 * @param occurrenceStart 本次要提醒的场次开始时间。
 *   周期待办用它标记「哪一场已经提醒过」，从而既不会漏提醒也不会重复提醒。
 */
data class ReminderPlan(
    val todoId: Long,
    val triggerAt: Long,
    val strategy: ReminderStrategy,
    val occurrenceStart: Long,
)

/**
 * 由待办本身推导出提醒计划（针对当前这一场场次）。
 *
 * 返回 null 表示「没有要排的提醒」（[ReminderMode.NONE] 一档）。
 * 它只是 [toReminderPlanFor] 在 `occurrenceStart = startAt` 时的特例，因此共用同一份
 * 策略判断：各写一份的话，两处的策略迟早会走偏。
 */
fun Todo.toReminderPlan(): ReminderPlan? = toReminderPlanFor(startAt)

/**
 * 针对指定场次构造提醒计划；[ReminderMode.NONE] 返回 null。
 *
 * 「没有计划」直接用 null 表达，而不是排一条很久以后的闹钟：
 * 后者会把「没有」伪装成「有」——系统里一直占着一个闹钟槽位，
 * 还得再补一套「到点也不许响」的判断，任何一处漏写就变成真的会响。
 * 返回可空后，调用方必须显式处理「没得排」，编译器会盯着。
 */
fun Todo.toReminderPlanFor(occurrenceStart: Long): ReminderPlan? {
    val strategy = when (mode) {
        ReminderMode.NONE -> return null
        ReminderMode.ALARM -> ReminderStrategy.EXACT_ALARM
        ReminderMode.NOTIFICATION -> ReminderStrategy.NOTIFICATION
    }
    return ReminderPlan(
        todoId = id,
        triggerAt = ReminderTime.remindAt(occurrenceStart, leadMinutes),
        strategy = strategy,
        occurrenceStart = occurrenceStart,
    )
}
