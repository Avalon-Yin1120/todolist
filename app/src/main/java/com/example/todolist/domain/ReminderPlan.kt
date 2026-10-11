package com.example.todolist.domain

/**
 * [ReminderPlan] 由哪条策略产出。
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
 */
data class ReminderPlan(
    val todoId: Long,
    val triggerAt: Long,
    val strategy: ReminderStrategy,
)

/** 由待办本身推导出提醒计划。 */
fun Todo.toReminderPlan(): ReminderPlan = ReminderPlan(
    todoId = id,
    triggerAt = remindAt,
    strategy = if (useExactAlarm) ReminderStrategy.EXACT_ALARM else ReminderStrategy.NOTIFICATION,
)
