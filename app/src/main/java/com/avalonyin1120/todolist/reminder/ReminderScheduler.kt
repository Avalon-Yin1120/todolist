package com.avalonyin1120.todolist.reminder

import com.avalonyin1120.todolist.domain.ReminderPlan

/**
 * 提醒调度抽象：安放「某条待办应在某时刻以某策略被唤醒」这件事。
 *
 * 生产环境有两个实现（精确闹钟 / WorkManager），测试用 Fake 实现，
 * 于是调度语义可以在纯 JVM 上被确定性断言，不必真的等时间流逝。
 */
interface ReminderScheduler {

    /**
     * 排定一次提醒。
     *
     * @param plan 待办 id、触发时刻与策略
     */
    fun schedule(plan: ReminderPlan)

    /** 取消该待办的既有调度（不存在时静默返回）。 */
    fun cancel(todoId: Long)

    /** 批量取消，重排前使用。 */
    fun cancelAll(todoIds: Collection<Long>) {
        todoIds.forEach { cancel(it) }
    }
}
