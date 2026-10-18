package com.avalonyin1120.todolist.reminder

import com.avalonyin1120.todolist.data.TodoRepository
import com.avalonyin1120.todolist.data.TodoDraft
import com.avalonyin1120.todolist.data.toTodo
import com.avalonyin1120.todolist.domain.Clock
import com.avalonyin1120.todolist.domain.Overlap
import com.avalonyin1120.todolist.domain.RecurrenceException
import com.avalonyin1120.todolist.domain.RecurrenceExceptionKind
import com.avalonyin1120.todolist.domain.RecurrenceExpander
import com.avalonyin1120.todolist.domain.ReminderMode
import com.avalonyin1120.todolist.domain.ReminderStrategy
import com.avalonyin1120.todolist.domain.Todo
import com.avalonyin1120.todolist.notify.ReminderLog
import com.avalonyin1120.todolist.domain.toReminderPlanFor
import com.avalonyin1120.todolist.sys.ExactAlarmPermission
import java.time.ZoneId

/**
 * 调度编排：把「仓库里的数据」与「系统的调度能力」接起来。
 *
 * 所有会改变提醒语义的操作都从这里走，于是「取消旧调度 / 排定新调度 / 开机重排」
 * 三件事只有一处实现，测试也只需要盯住这一处。
 *
 * @param zone 用于把周期规则展开成具体场次；注入是为了让测试固定时区。
 */
class SchedulingCoordinator(
    private val repository: TodoRepository,
    /** 闹钟通道：闹钟式提醒的主通道（`setAlarmClock`，系统按「闹钟」对待）。 */
    val exactScheduler: ReminderScheduler,
    /**
     * 通知方式的主通道（`setExactAndAllowWhileIdle`，精确到点），
     * 同时充当闹钟通道的「保险」。
     *
     * 参数名从 v19 起刻意保留 `inexactScheduler`：它现在也是精确的（清单声明了
     * `USE_EXACT_ALARM`），改名会牵动一批测试与调用点的可读性，而它表达的是
     * 「第二条闹钟通道」，与精确与否无关。真正的选择在 `AlarmReminderScheduler.AlarmStyle`。
     */
    private val inexactScheduler: ReminderScheduler,
    /**
     * WorkManager 通道：只作为最后一道保险。
     *
     * 它在国产 ROM 上最不可靠（JobScheduler 会被整体推迟到用户下次打开 App），
     * 正因为如此才需要前面那两条 AlarmManager 通道；留着它是为了「万一闹钟也被冻结」。
     */
    private val workScheduler: ReminderScheduler,
    private val clock: Clock,
    private val exactAlarmPermission: ExactAlarmPermission,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {

    /** 本次排定是否因缺少精确闹钟权限而降级为普通通知。 */
    var lastScheduleWasDegraded: Boolean = false
        private set

    suspend fun findById(id: Long): Todo? = repository.findById(id)

    /** 写入结果。 */
    data class WriteResult(
        val todoId: Long,
        /** 与本次时间段重叠的其它待办：按需求仍然创建成功，只是需要提示用户。 */
        val overlapping: List<Todo>,
        val degraded: Boolean,
    )

    // ------------------------------------------------------------------ 写操作

    /** 新增待办并排定提醒。 */
    suspend fun create(draft: TodoDraft): WriteResult {
        val todo = draft.toTodo(existing = null, now = clock.now())
        val overlapping = detectOverlap(todo, excludeId = 0L)
        val id = repository.insert(todo)
        val created = repository.findById(id) ?: todo.copy(id = id)
        scheduleFor(created)
        return WriteResult(id, overlapping, lastScheduleWasDegraded)
    }

    /** 更新待办：先撤掉旧调度，再按新值重排。 */
    suspend fun update(draft: TodoDraft): WriteResult? {
        val existing = repository.findById(draft.id) ?: return null
        val updated = draft.toTodo(existing = existing, now = clock.now())
        return updateTodo(updated)
    }

    /** 直接更新一条已构造好的待办（供「仅修改本次」等流程使用）。 */
    suspend fun updateTodo(todo: Todo): WriteResult? {
        if (repository.findById(todo.id) == null) return null
        val updated = todo.copy(updatedAt = clock.now())
        val overlapping = detectOverlap(updated, excludeId = updated.id)

        cancelSchedules(updated.id)
        repository.update(updated)
        val fresh = repository.findById(updated.id) ?: return null
        scheduleFor(fresh)
        return WriteResult(fresh.id, overlapping, lastScheduleWasDegraded)
    }

    /** 删除待办：连同调度、派生场次与已发出的通知一起清掉。 */
    suspend fun delete(todoId: Long) {
        val todo = repository.findById(todoId) ?: return
        cancelSchedules(todoId)
        // 「仅修改本次」生成的独立待办属于这条规则，一并清理
        repository.findChildren(todoId).forEach { child ->
            cancelSchedules(child.id)
            repository.delete(child)
        }
        repository.delete(todo)
    }

    /**
     * 只跳过重复规则的某一场：在该日期上记一条 SKIPPED 例外。
     *
     * 这样「删除本次」不影响其它场次，也不必为每个场次建数据库行。
     */
    suspend fun skipOccurrence(todoId: Long, occurrenceStart: Long) {
        val todo = repository.findById(todoId) ?: return
        val dateKey = RecurrenceExpander.dateKeyOf(occurrenceStart, zone)
        if (todo.recurrenceExceptions.any { it.date == dateKey }) return
        val updated = todo.copy(
            recurrenceExceptions = todo.recurrenceExceptions +
                RecurrenceException(dateKey, RecurrenceExceptionKind.SKIPPED),
            updatedAt = clock.now(),
        )
        cancelSchedules(todoId)
        repository.update(updated)
        val fresh = repository.findById(todoId) ?: return
        scheduleFor(fresh)
    }

    /**
     * 切换完成状态。
     *
     * 一次性待办：照旧用整条的 `isDone`，标记完成时撤销未触发的提醒，取消完成时按需重排。
     *
     * 周期待办：完成是**按场次**的（见 [setOccurrenceDone]），因此这里只做转发：
     * 不传 [occurrenceStart] 的调用方（通知里的「完成」、闹钟页的「完成」）拿不到场次，
     * 落到这条待办「刚提醒过的那一场」——那正是它们要完成的那一场。
     */
    suspend fun setDone(todoId: Long, done: Boolean, occurrenceStart: Long? = null) {
        val todo = repository.findById(todoId) ?: return
        if (todo.recurrence.isRecurring) {
            setOccurrenceDone(
                todoId = todoId,
                occurrenceStart = occurrenceStart ?: todo.remindedOccurrenceStart ?: todo.startAt,
                done = done,
            )
            return
        }
        val updated = todo.copy(isDone = done, updatedAt = clock.now())
        cancelSchedules(todoId)
        repository.update(updated)
        if (!done) {
            scheduleFor(updated)
        }
    }

    /**
     * 只把周期待办的**某一场**标记为完成（或取消完成）：在该日期上加/去一条
     * [RecurrenceExceptionKind.COMPLETED] 例外。
     *
     * 为什么不改 `isDone`：`isDone` 是整条待办的状态，而周期待办展开时每一场都会
     * 继承它，于是「勾今天这一场」会让明天、后天的场次一起显示已完成——这正是用户报的
     * 「周期待办打勾后其它场次也变已完成」。改成例外之后，完成状态天然是按场次的。
     *
     * 重排只重排**这一条待办的下一次提醒**（调度器按待办 id 覆盖，同一时刻只有一场在排），
     * 因此完成的这一场不再提醒、后续场次照常；不能走「取消了就不再排」那条路，
     * 否则整个周期会一起哑掉。
     */
    suspend fun setOccurrenceDone(todoId: Long, occurrenceStart: Long, done: Boolean) {
        val todo = repository.findById(todoId) ?: return
        // 非周期待办只有一个「场次」，直接落到整条的 isDone 上，语义不变
        if (!todo.recurrence.isRecurring) {
            setDone(todoId, done)
            return
        }
        val dateKey = RecurrenceExpander.dateKeyOf(occurrenceStart, zone)
        // 只动这一天的 COMPLETED，其它例外（SKIPPED / OVERRIDDEN）原样保留：
        // 「删除本次」「仅修改本次」的既有行为不能被完成/取消完成碰坏
        val others = todo.recurrenceExceptions.filterNot {
            it.date == dateKey && it.kind == RecurrenceExceptionKind.COMPLETED
        }
        val next = if (done) {
            others + RecurrenceException(dateKey, RecurrenceExceptionKind.COMPLETED)
        } else {
            others
        }
        // 状态没变化（例如对着同一个状态连点两次）就不写库，省掉一次无效的取消 + 重排
        if (next == todo.recurrenceExceptions) return

        val updated = todo.copy(recurrenceExceptions = next, updatedAt = clock.now())
        cancelSchedules(todoId)
        repository.update(updated)
        val fresh = repository.findById(todoId) ?: return
        scheduleFor(fresh)
    }

    /**
     * 全量对账：把还需要提醒的事项重新排一遍。
     * 触发时机：App 启动、开机完成、系统时间/时区变更。
     */
    suspend fun reconcileAll(): Int {
        var scheduled = 0
        repository.findAll().forEach { todo ->
            if (scheduleFor(todo)) scheduled++
        }
        return scheduled
    }

    /** [occurrenceStart] 那一场是否已被标记完成（用本类的时区解析场次日期）。 */
    fun isOccurrenceCompleted(todo: Todo, occurrenceStart: Long): Boolean =
        RecurrenceExpander.isOccurrenceCompleted(todo, occurrenceStart, zone)

    // ------------------------------------------------------------------ 查询

    /**
     * 与 [todo] 真正会撞在同一段时间上的未完成待办（用于创建前的提示）。
     *
     * 不能只比 `startAt/endAt`：周期待办在库里只存规则自身的第一次时间，
     * 之后每一场的时间戳根本没落库。判定细节见 [Overlap]——
     * 它按「场次」比较，因此「每周一 9:00」与「每周三 9:00」不再互相误报。
     */
    suspend fun findOverlapping(todo: Todo, excludeId: Long = 0L): List<Todo> =
        repository.findAll()
            .filter { it.id != excludeId && !it.isDone }
            .filter { Overlap.intersects(todo, it, zone) }
            .sortedBy { it.startAt }

    private suspend fun detectOverlap(todo: Todo, excludeId: Long): List<Todo> =
        findOverlapping(todo, excludeId = excludeId)

    // ------------------------------------------------------------------ 调度

    /**
     * 计算该提醒哪一场并排进对应调度器；返回是否真的排定了。
     *
     * 跳过的情况：选了「不通知」；已完成；找不到未来的场次；这一场已经提醒过。
     *
     * 排定策略是「主通道 + 保险通道」：
     * * 闹钟式 → 精确闹钟（`setAlarmClock`）+ 非精确闹钟（晚 [BACKSTOP_DELAY_MS]）
     * * 通知式 → 非精确闹钟（准时）+ WorkManager（晚 [BACKSTOP_DELAY_MS]）
     *
     * 多排一条不会导致重复提醒：[ReminderFiringService] 会用「这一场是否已经提醒过」挡掉。
     * 这一步是为真机（尤其 ColorOS / MIUI 这类省电激进的 ROM）准备的——
     * 单通道一旦被冻结，用户就是「到点了什么都没发生」。
     */
    private suspend fun scheduleFor(todo: Todo): Boolean {
        // 先清掉上一次调用留下的降级标记：本函数有多条「什么都不排」的提前返回
        //（不通知 / 已完成 / 没有未来场次），不清的话这些情况会沿用上一条待办的
        // 降级结果，界面上就会弹出与实际无关的「精确闹钟权限不足」提示。
        lastScheduleWasDegraded = false

        // 「不通知」：没有要排的提醒，但必须先撤掉这条待办已有的调度再返回。
        // 顺序不能反过来依赖下面 nextOccurrenceToRemind 返回 null——那样只是「不排新的」，
        // 从闹钟式/通知改成不通知时，系统里那条旧闹钟会照点响，用户等于没关掉提醒。
        if (todo.mode == ReminderMode.NONE) {
            cancelSchedules(todo.id)
            return false
        }

        val now = clock.now()
        val occurrence = nextOccurrenceToRemind(todo, now) ?: return false

        // 上面已经挡掉 NONE，这里必然非空；保留 `?:` 是为了以后再加档位时
        // 不会悄悄排一条空计划出去（编译器会强制在这里做决定）
        val plan = todo.toReminderPlanFor(occurrence) ?: return false
        val effective = if (plan.strategy == ReminderStrategy.EXACT_ALARM &&
            !exactAlarmPermission.canScheduleExactAlarms()
        ) {
            lastScheduleWasDegraded = true
            plan.copy(strategy = ReminderStrategy.NOTIFICATION)
        } else {
            plan
        }
        schedulerFor(effective.strategy).schedule(effective)

        val backstop = effective.copy(
            strategy = if (effective.strategy == ReminderStrategy.EXACT_ALARM) {
                ReminderStrategy.NOTIFICATION
            } else {
                ReminderStrategy.WORK_FALLBACK
            },
            triggerAt = effective.triggerAt + BACKSTOP_DELAY_MS,
        )
        schedulerFor(backstop.strategy).schedule(backstop)
        return true
    }

    /**
     * 下一次需要提醒的场次开始时间；不需要提醒时返回 null。
     *
     * 允许「迟到」[MISSED_GRACE_MS]：提醒时刻刚过去不久（例如系统深度休眠、
     * 或被厂商省电策略冻结而没投递）时仍然返回这一场，系统会立刻补投一次。
     * 超过宽限期就不再补发，避免装上新版本后被历史事项刷屏。
     *
     * 周期待办会跳过**已完成的那一场**（按场次完成，见 [setOccurrenceDone]）：
     * 勾掉今天这一场之后，排的下一条就是明天那一场。
     */
    suspend fun nextOccurrenceToRemind(todo: Todo, now: Long): Long? {
        // 「不通知」根本没有提醒时刻，放在最前面挡掉：宽限期与周期展开都不必参与
        if (todo.mode == ReminderMode.NONE) return null
        // isDone 只对一次性待办有意义。周期待办的完成按场次记在例外列表里，
        // 整条记录上遗留的 true（旧版本打勾时写进去的）不该让整个系列从此不再提醒。
        if (todo.isDone && !todo.recurrence.isRecurring) return null
        val graceStart = now - MISSED_GRACE_MS

        if (!todo.recurrence.isRecurring) {
            // 一次性待办：已提醒过就不再提醒
            if (todo.remindedOccurrenceStart == todo.startAt) return null
            val triggerAt = todo.startAt - todo.leadMinutes * MILLIS_PER_MINUTE
            return todo.startAt.takeIf { triggerAt > graceStart }
        }

        // 周期待办：逐场向前找第一个「还没提醒过、还没完成、且提醒时刻仍在（宽限期内）未来」的场次
        var searchAfter = graceStart
        repeat(MAX_OCCURRENCE_LOOKAHEAD) {
            val occurrence = RecurrenceExpander.nextOccurrenceAfter(todo, searchAfter, zone)
                ?: return null
            val triggerAt = occurrence - todo.leadMinutes * MILLIS_PER_MINUTE
            val alreadyReminded = todo.remindedOccurrenceStart == occurrence
            val completed = RecurrenceExpander.isOccurrenceCompleted(todo, occurrence, zone)
            if (!alreadyReminded && !completed && triggerAt > graceStart) return occurrence
            searchAfter = occurrence
        }
        return null
    }

    /** 供触发后重排使用：把指定场次标记为已提醒并排定下一场。 */
    suspend fun markRemindedAndReschedule(todoId: Long, occurrenceStart: Long) {
        val todo = repository.findById(todoId) ?: return
        if (todo.remindedOccurrenceStart == occurrenceStart) return
        repository.markReminded(todoId, occurrenceStart)
        cancelSchedules(todoId)
        val fresh = repository.findById(todoId) ?: return
        scheduleFor(fresh)
    }

    private fun cancelSchedules(todoId: Long) {
        exactScheduler.cancel(todoId)
        inexactScheduler.cancel(todoId)
        workScheduler.cancel(todoId)
    }

    private fun schedulerFor(strategy: ReminderStrategy): ReminderScheduler = when (strategy) {
        ReminderStrategy.EXACT_ALARM -> exactScheduler
        ReminderStrategy.NOTIFICATION -> inexactScheduler
        ReminderStrategy.WORK_FALLBACK -> workScheduler
    }

    private companion object {
        const val MILLIS_PER_MINUTE = 60_000L

        /** 保险通道比主通道晚多久；太短会浪费电，太长则用户已经错过。 */
        const val BACKSTOP_DELAY_MS = 3 * MILLIS_PER_MINUTE

        /**
         * 「迟到」宽限期：提醒时刻已过但在该窗口内时仍然补投一次。
         *
         * 取值权衡：太短则系统深休眠/被杀进程导致的漏提醒无法补救；
         * 太长则用户打开 App 会被很久以前的提醒打扰。
         */
        const val MISSED_GRACE_MS = 15 * MILLIS_PER_MINUTE

        /** 周期规则向前搜索的最大场次数，避免异常数据导致死循环。 */
        const val MAX_OCCURRENCE_LOOKAHEAD = 400
    }
}

/**
 * 提醒真正轮到触发时的处理器，由 Alarm Receiver 与 Worker 共用。
 */
fun interface ReminderFireHandler {
    /**
     * @param todoId 待办 id
     * @param occurrenceStart 本次触发的场次开始时间（周期待办靠它区分是哪一场）
     */
    suspend fun fire(todoId: Long, occurrenceStart: Long)
}

/**
 * 触发后的落地处理：交给外部回调（发通知或拉起闹钟页），
 * 然后记录「哪一场已提醒」并为下一场重排。
 *
 * 对周期待办，重排这一步是必需的，否则周期提醒只会响第一次。
 */
class ReminderFiringService(
    private val coordinator: SchedulingCoordinator,
    private val onFire: suspend (Todo) -> Unit,
) : ReminderFireHandler {

    override suspend fun fire(todoId: Long, occurrenceStart: Long) {
        val todo = coordinator.findById(todoId)
        if (todo == null) {
            ReminderLog.add(ReminderLog.CAT_FIRE, "触发时找不到待办（可能已被删除）：todo=$todoId")
            return
        }
        ReminderLog.add(ReminderLog.CAT_FIRE, "到点触发：${todo.name}｜方式=${todo.mode}｜todo=$todoId")
        // isDone 只对一次性待办有意义：周期待办的完成按场次记，整条记录上遗留的 true
        // 如果在这里也照挡，就会出现「排定了却永远不响」的哑火
        if (todo.isDone && !todo.recurrence.isRecurring) {
            ReminderLog.add(ReminderLog.CAT_FIRE, "跳过：已完成")
            return
        }
        // 提醒已经排出去之后，用户才把它改成「不通知」时，可能还剩一条在途的触发
        //（广播已投递、Worker 已开跑），取消调度来不及拦住它。这里再挡一次，
        // 否则用户明明关掉了提醒却仍然收到通知——这正是「不通知」最不能出的错。
        if (todo.mode == ReminderMode.NONE) {
            ReminderLog.add(ReminderLog.CAT_FIRE, "跳过：这条待办选了「不通知」")
            return
        }
        // 这一场已经被勾掉了（提前完成，或通知还挂着时用户已经在列表里完成）：
        // 不能提醒。也不需要在这里补一次重排——完成那一步已经 scheduleFor 把下一场排好了。
        if (coordinator.isOccurrenceCompleted(todo, occurrenceStart)) {
            ReminderLog.add(ReminderLog.CAT_FIRE, "跳过：这一场已被标记完成")
            return
        }
        // 必须先判断「这一场是否已经提醒过」，再去标记与重排。
        // 若顺序颠倒，第二次触发会先被重新排定，导致同一场次重复提醒。
        if (todo.remindedOccurrenceStart == occurrenceStart) {
            ReminderLog.add(ReminderLog.CAT_FIRE, "跳过：这一场已经提醒过了（保险通道/重复投递）")
            return
        }
        onFire(todo)
        coordinator.markRemindedAndReschedule(todoId, occurrenceStart)
    }
}
