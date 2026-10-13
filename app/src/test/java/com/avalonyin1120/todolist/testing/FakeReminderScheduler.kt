package com.avalonyin1120.todolist.testing

import com.avalonyin1120.todolist.domain.ReminderPlan
import com.avalonyin1120.todolist.domain.ReminderStrategy
import com.avalonyin1120.todolist.reminder.ReminderScheduler

/** 记录调度的假调度器，替代 AlarmManager / WorkManager。 */
class FakeReminderScheduler(
    private val name: String = "fake",
) : ReminderScheduler {

    data class Scheduled(val plan: ReminderPlan, val by: String)

    val scheduled = mutableListOf<Scheduled>()
    val cancelled = mutableListOf<Long>()

    override fun schedule(plan: ReminderPlan) {
        // 同一待办重复排定时，后来的覆盖先前的（模拟 AlarmManager / REPLACE 语义）。
        scheduled.removeAll { it.plan.todoId == plan.todoId }
        scheduled += Scheduled(plan, name)
    }

    override fun cancel(todoId: Long) {
        cancelled += todoId
        scheduled.removeAll { it.plan.todoId == todoId }
    }

    fun planFor(todoId: Long): ReminderPlan? =
        scheduled.firstOrNull { it.plan.todoId == todoId }?.plan

    /** 上次为该待办排定时用的策略。 */
    fun strategyFor(todoId: Long): ReminderStrategy? = planFor(todoId)?.strategy

    fun clear() {
        scheduled.clear()
        cancelled.clear()
    }
}
