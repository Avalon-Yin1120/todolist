package com.example.todolist.domain

/**
 * [ReminderPlan] 使用的调度通道。
 */
enum class ReminderStrategy {
    /** 精确闹钟：AlarmManager.setExactAndAllowWhileIdle，需要精确闹钟权限。 */
    EXACT_ALARM,

    /** 普通通知：WorkManager 延迟任务，无需权限，但系统可能延后。 */
    NOTIFICATION,
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
