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

/** 由待办本身推导出提醒计划（针对当前这一场场次）。 */
fun Todo.toReminderPlan(): ReminderPlan = ReminderPlan(
    todoId = id,
    triggerAt = remindAt,
    strategy = if (mode == ReminderMode.ALARM) {
        ReminderStrategy.EXACT_ALARM
    } else {
        ReminderStrategy.NOTIFICATION
    },
    occurrenceStart = startAt,
)

/** 针对指定场次构造提醒计划。 */
fun Todo.toReminderPlanFor(occurrenceStart: Long): ReminderPlan = ReminderPlan(
    todoId = id,
    triggerAt = ReminderTime.remindAt(occurrenceStart, leadMinutes),
    strategy = if (mode == ReminderMode.ALARM) {
        ReminderStrategy.EXACT_ALARM
    } else {
        ReminderStrategy.NOTIFICATION
    },
    occurrenceStart = occurrenceStart,
)
