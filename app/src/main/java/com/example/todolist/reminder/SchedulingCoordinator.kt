package com.example.todolist.reminder

import com.example.todolist.data.TodoRepository
import com.example.todolist.domain.Clock
import com.example.todolist.domain.ReminderPlan
import com.example.todolist.domain.ReminderStrategy
import com.example.todolist.domain.ReminderTime
import com.example.todolist.domain.Todo
import com.example.todolist.domain.toReminderPlan
import com.example.todolist.notify.NotificationHelper
import com.example.todolist.sys.ExactAlarmPermission

/**
 * 调度编排：把「仓库里的数据」与「系统的调度能力」接起来。
 *
 * 所有会改变提醒语义的操作都从这里走，于是「取消旧调度 / 排定新调度 / 开机重排」
 * 三件事只有一处实现，测试也只需要盯住这一处。
 */
class SchedulingCoordinator(
    private val repository: TodoRepository,
    private val exactScheduler: ReminderScheduler,
    private val inexactScheduler: ReminderScheduler,
    private val clock: Clock,
    private val exactAlarmPermission: ExactAlarmPermission,
) {

    /**
     * 当前是否允许使用精确闹钟。
     * 无权限时 [scheduleFor] 会自动降级为普通通知，并把降级结果返回给调用方。
     */
    var lastScheduleWasDegraded: Boolean = false
        private set

    suspend fun findById(id: Long): Todo? = repository.findById(id)

    /** 新增待办并排定提醒，返回新记录 id。 */
    suspend fun create(todo: Todo): Long {
        val id = repository.insert(todo)
        val created = repository.findById(id) ?: return id
        scheduleFor(created)
        return id
    }

    /** 更新待办：先撤掉旧调度，再按新值重排。 */
    suspend fun update(todo: Todo) {
        exactScheduler.cancel(todo.id)
        inexactScheduler.cancel(todo.id)
        repository.update(todo)
        val fresh = repository.findById(todo.id) ?: return
        scheduleFor(fresh)
    }

    /** 删除待办：连同调度与已发出的通知一起清掉。 */
    suspend fun delete(todoId: Long) {
        val todo = repository.findById(todoId) ?: return
        exactScheduler.cancel(todoId)
        inexactScheduler.cancel(todoId)
        repository.delete(todo)
    }

    /** 切换完成状态；标记完成时撤销未触发的提醒，取消完成时按需重排。 */
    suspend fun setDone(todoId: Long, done: Boolean) {
        val todo = repository.findById(todoId) ?: return
        val updated = todo.copy(isDone = done, updatedAt = clock.now())
        exactScheduler.cancel(todoId)
        inexactScheduler.cancel(todoId)
        repository.update(updated)
        if (!done) {
            scheduleFor(updated)
        }
    }

    /**
     * 全量对账：把数据库里「还需要提醒」的事项重新排一遍。
     *
     * 触发时机：App 启动、开机完成、系统时间/时区变更。
     * 已提醒、已完成、提醒时刻已过的项会被跳过（由 ReminderTime.shouldSchedule 判定）。
     */
    suspend fun reconcileAll(): Int {
        val now = clock.now()
        val pending = repository.findAll().filter { ReminderTime.shouldSchedule(it, now) }
        pending.forEach { scheduleFor(it) }
        return pending.size
    }

    /**
     * 按策略排定；精确策略缺权限时降级为普通通知，并把 [lastScheduleWasDegraded] 置位。
     */
    /**
     * 按策略排定；精确策略缺权限时降级为普通通知，并把 [lastScheduleWasDegraded] 置位。
     *
     * 提醒时刻已经过去、已完成或已提醒过的事项直接跳过：把过去的时刻交给
     * AlarmManager / WorkManager 会导致「立刻响一次」，那是明显的打扰。
     * 上层的全量对账也做同样判断，这里再兜一层，保证从任何入口进来的更新都安全。
     */
    private fun scheduleFor(todo: Todo) {
        if (!ReminderTime.shouldSchedule(todo, clock.now())) {
            return
        }
        // 每次真正排定前先复位降级标记，避免上一次的结果污染这一次的判断。
        lastScheduleWasDegraded = false
        val plan = todo.toReminderPlan()
        val effective = if (plan.strategy == ReminderStrategy.EXACT_ALARM &&
            !exactAlarmPermission.canScheduleExactAlarms()
        ) {
            lastScheduleWasDegraded = true
            plan.copy(strategy = ReminderStrategy.NOTIFICATION)
        } else {
            plan
        }
        schedulerFor(effective.strategy).schedule(effective)
    }

    private fun schedulerFor(strategy: ReminderStrategy): ReminderScheduler = when (strategy) {
        ReminderStrategy.EXACT_ALARM -> exactScheduler
        ReminderStrategy.NOTIFICATION -> inexactScheduler
    }
}

/**
 * 提醒真正轮到触发时的处理器，由 Alarm Receiver 与 Worker 共用。
 */
fun interface ReminderFireHandler {
    suspend fun fire(todoId: Long)
}

/**
 * 生产实现：读取待办 → 发通知 → 打上「已提醒」标记。
 * 打标记放在发通知之后且只做一次，配合 Worker 的幂等检查避免重复提醒。
 */
class ReminderFiringService(
    private val repository: TodoRepository,
    private val notificationHelper: NotificationHelper,
    private val clock: Clock,
) : ReminderFireHandler {

    override suspend fun fire(todoId: Long) {
        val todo = repository.findById(todoId) ?: return
        if (todo.isDone || todo.notifiedAt != null) return
        notificationHelper.notifyReminder(todo)
        repository.markNotified(todoId, clock.now())
    }
}

/** 供 UI 展示的提醒计划预览（不触发任何系统调用）。 */
fun previewPlan(todo: Todo): ReminderPlan = todo.toReminderPlan()
