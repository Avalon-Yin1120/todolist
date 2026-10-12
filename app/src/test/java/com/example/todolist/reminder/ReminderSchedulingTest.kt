package com.example.todolist.reminder

import com.example.todolist.data.TodoDraft
import com.example.todolist.data.TodoRepository
import com.example.todolist.data.toTodo
import com.example.todolist.domain.Recurrence
import com.example.todolist.domain.RecurrenceExpander
import com.example.todolist.domain.ReminderMode
import com.example.todolist.domain.ReminderStrategy
import com.example.todolist.testing.FakeClock
import com.example.todolist.testing.FakeExactAlarmPermission
import com.example.todolist.testing.FakeReminderScheduler
import com.example.todolist.testing.FakeTodoDao
import com.example.todolist.testing.TestData
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

/**
 * 调度编排的语义测试：排什么、什么时候排、用哪个调度器、什么时候取消。
 * 全部用 Fake，因此没有真实时间流逝，也不依赖 Android 运行时。
 */
class ReminderSchedulingTest {

    private lateinit var repository: TodoRepository
    private lateinit var exact: FakeReminderScheduler
    private lateinit var inexact: FakeReminderScheduler
    private lateinit var work: FakeReminderScheduler
    private lateinit var permission: FakeExactAlarmPermission
    private lateinit var clock: FakeClock
    private lateinit var coordinator: SchedulingCoordinator

    private val now = TestData.NOW

    @Before
    fun setUp() {
        repository = TodoRepository(FakeTodoDao())
        exact = FakeReminderScheduler("exact")
        inexact = FakeReminderScheduler("inexact")
        work = FakeReminderScheduler("work")
        permission = FakeExactAlarmPermission(granted = true)
        clock = FakeClock(now)
        coordinator = SchedulingCoordinator(
            repository = repository,
            exactScheduler = exact,
            inexactScheduler = inexact,
            workScheduler = work,
            clock = clock,
            exactAlarmPermission = permission,
            zone = TestData.ZONE,
        )
    }

    private fun draft(
        name: String = "小组会议",
        startAt: Long = now + 60 * TestData.MINUTE,
        endAt: Long = now + 120 * TestData.MINUTE,
        leadMinutes: Int = 0,
        mode: ReminderMode = ReminderMode.NOTIFICATION,
        recurrence: Recurrence = Recurrence.NONE,
    ) = TodoDraft(
        name = name,
        startAt = startAt,
        endAt = endAt,
        leadMinutes = leadMinutes,
        mode = mode,
        recurrence = recurrence,
    )

    // ------------------------------------------------------------------ 新增

    @Test
    fun `通知方式排到非精确调度器`() = runTest {
        val result = coordinator.create(draft(mode = ReminderMode.NOTIFICATION))

        assertThat(inexact.planFor(result.todoId)?.triggerAt).isEqualTo(now + 60 * TestData.MINUTE)
        assertThat(exact.planFor(result.todoId)).isNull()
        assertThat(inexact.strategyFor(result.todoId)).isEqualTo(ReminderStrategy.NOTIFICATION)
    }

    @Test
    fun `闹钟方式排到精确调度器`() = runTest {
        val result = coordinator.create(draft(mode = ReminderMode.ALARM))

        assertThat(exact.planFor(result.todoId)?.triggerAt).isEqualTo(now + 60 * TestData.MINUTE)
        assertThat(work.planFor(result.todoId)).isNull()
        assertThat(result.degraded).isFalse()
    }

    // -------------------------------------------------- 保险通道（真机漏提醒的兜底）

    @Test
    fun `通知方式会额外排一条 WorkManager 保险`() = runTest {
        val result = coordinator.create(draft(mode = ReminderMode.NOTIFICATION))

        // 主通道准时，保险晚 3 分钟；重复触发由「这一场是否已提醒」挡掉
        assertThat(inexact.planFor(result.todoId)?.triggerAt).isEqualTo(now + 60 * TestData.MINUTE)
        assertThat(work.planFor(result.todoId)?.triggerAt)
            .isEqualTo(now + 63 * TestData.MINUTE)
        assertThat(work.strategyFor(result.todoId)).isEqualTo(ReminderStrategy.WORK_FALLBACK)
        assertThat(work.planFor(result.todoId)?.occurrenceStart)
            .isEqualTo(inexact.planFor(result.todoId)?.occurrenceStart)
    }

    @Test
    fun `闹钟方式会额外排一条非精确闹钟保险`() = runTest {
        val result = coordinator.create(draft(mode = ReminderMode.ALARM))

        assertThat(exact.planFor(result.todoId)?.triggerAt).isEqualTo(now + 60 * TestData.MINUTE)
        assertThat(inexact.planFor(result.todoId)?.triggerAt)
            .isEqualTo(now + 63 * TestData.MINUTE)
        assertThat(inexact.strategyFor(result.todoId)).isEqualTo(ReminderStrategy.NOTIFICATION)
        assertThat(work.planFor(result.todoId)).isNull()
    }

    @Test
    fun `降级时主通道走非精确闹钟且保险走 WorkManager`() = runTest {
        permission.granted = false
        val result = coordinator.create(draft(mode = ReminderMode.ALARM))

        assertThat(result.degraded).isTrue()
        assertThat(exact.planFor(result.todoId)).isNull()
        assertThat(inexact.planFor(result.todoId)?.triggerAt).isEqualTo(now + 60 * TestData.MINUTE)
        assertThat(work.planFor(result.todoId)?.triggerAt).isEqualTo(now + 63 * TestData.MINUTE)
    }

    @Test
    fun `提前量体现在触发时刻上`() = runTest {
        val start = now + 60 * TestData.MINUTE
        val result = coordinator.create(draft(startAt = start, leadMinutes = 30))

        assertThat(inexact.planFor(result.todoId)?.triggerAt).isEqualTo(start - 30 * TestData.MINUTE)
    }

    @Test
    fun `提醒计划带上场次开始时间以便周期待办区分场次`() = runTest {
        val start = now + 60 * TestData.MINUTE
        val result = coordinator.create(draft(startAt = start))

        assertThat(inexact.planFor(result.todoId)?.occurrenceStart).isEqualTo(start)
    }

    // ------------------------------------------------------------------ 降级

    @Test
    fun `精确闹钟无权限时自动降级为普通通知`() = runTest {
        permission.granted = false
        val result = coordinator.create(draft(mode = ReminderMode.ALARM))

        assertThat(exact.planFor(result.todoId)).isNull()
        assertThat(inexact.planFor(result.todoId)?.strategy).isEqualTo(ReminderStrategy.NOTIFICATION)
        assertThat(result.degraded).isTrue()
    }

    @Test
    fun `通知方式不需要精确闹钟权限`() = runTest {
        permission.granted = false
        val result = coordinator.create(draft(mode = ReminderMode.NOTIFICATION))

        assertThat(inexact.planFor(result.todoId)).isNotNull()
        assertThat(result.degraded).isFalse()
    }

    // ------------------------------------------------------------------ 冲突

    @Test
    fun `时间段重叠时仍然创建成功但会把冲突项带回来`() = runTest {
        coordinator.create(draft(name = "已有会议"))

        val result = coordinator.create(
            draft(name = "冲突的新会议", startAt = now + 90 * TestData.MINUTE)
        )

        assertThat(result.overlapping.map { it.name }).containsExactly("已有会议")
        // 关键：冲突不阻塞创建
        assertThat(coordinator.findById(result.todoId)?.name).isEqualTo("冲突的新会议")
    }

    @Test
    fun `不重叠时没有冲突项`() = runTest {
        coordinator.create(draft(name = "早会", startAt = now + 60 * TestData.MINUTE))

        val result = coordinator.create(
            draft(
                name = "午会",
                startAt = now + 240 * TestData.MINUTE,
                endAt = now + 300 * TestData.MINUTE,
            )
        )

        assertThat(result.overlapping).isEmpty()
    }

    @Test
    fun `编辑时不会把自己算成冲突`() = runTest {
        val created = coordinator.create(draft())
        val existing = coordinator.findById(created.todoId)!!

        val result = coordinator.update(
            draft(name = "改了名字").copy(id = existing.id)
        )

        assertThat(result!!.overlapping).isEmpty()
    }

    // ------------------------------------------------------------------ 编辑

    @Test
    fun `编辑时间后会撤销旧调度并按新时间重排`() = runTest {
        val created = coordinator.create(draft(startAt = now + 60 * TestData.MINUTE))
        val existing = coordinator.findById(created.todoId)!!

        val newStart = now + 180 * TestData.MINUTE
        coordinator.update(
            draft(startAt = newStart, endAt = newStart + 60 * TestData.MINUTE).copy(id = existing.id)
        )

        assertThat(inexact.cancelled).contains(existing.id)
        assertThat(inexact.planFor(existing.id)?.triggerAt).isEqualTo(newStart)
    }

    @Test
    fun `从通知改成闹钟会清掉旧的非精确调度`() = runTest {
        val created = coordinator.create(draft(mode = ReminderMode.NOTIFICATION))
        val existing = coordinator.findById(created.todoId)!!

        coordinator.update(draft(mode = ReminderMode.ALARM).copy(id = existing.id))

        assertThat(inexact.cancelled).contains(existing.id)
        assertThat(exact.planFor(existing.id)).isNotNull()
        // 换成闹钟式之后，非精确通道上留下的是「保险」，而不是原来那条准时的主提醒
        assertThat(inexact.planFor(existing.id)?.strategy).isEqualTo(ReminderStrategy.NOTIFICATION)
        assertThat(inexact.planFor(existing.id)?.triggerAt)
            .isEqualTo(now + 63 * TestData.MINUTE)
    }

    @Test
    fun `改动提醒时刻会清掉已提醒标记从而重新排定`() = runTest {
        val created = coordinator.create(draft())
        coordinator.markRemindedAndReschedule(created.todoId, now + 60 * TestData.MINUTE)
        assertThat(coordinator.findById(created.todoId)!!.remindedOccurrenceStart).isNotNull()

        val existing = coordinator.findById(created.todoId)!!
        val newStart = now + 300 * TestData.MINUTE
        coordinator.update(
            draft(startAt = newStart, endAt = newStart + 60 * TestData.MINUTE).copy(id = existing.id)
        )

        assertThat(coordinator.findById(created.todoId)!!.remindedOccurrenceStart).isNull()
        assertThat(inexact.planFor(created.todoId)).isNotNull()
    }

    @Test
    fun `只改名称不会清掉已提醒标记`() = runTest {
        val created = coordinator.create(draft())
        coordinator.markRemindedAndReschedule(created.todoId, now + 60 * TestData.MINUTE)
        val current = coordinator.findById(created.todoId)!!

        coordinator.update(draft(name = "改了名字").copy(id = current.id))

        assertThat(coordinator.findById(created.todoId)!!.remindedOccurrenceStart).isNotNull()
    }

    // ------------------------------------------------------------------ 完成与删除

    @Test
    fun `标记完成后撤销提醒`() = runTest {
        val created = coordinator.create(draft())

        coordinator.setDone(created.todoId, done = true)

        assertThat(inexact.cancelled).contains(created.todoId)
        assertThat(inexact.planFor(created.todoId)).isNull()
        assertThat(coordinator.findById(created.todoId)!!.isDone).isTrue()
    }

    @Test
    fun `删除会同时撤销调度与记录`() = runTest {
        val created = coordinator.create(draft())

        coordinator.delete(created.todoId)

        assertThat(inexact.cancelled).contains(created.todoId)
        assertThat(coordinator.findById(created.todoId)).isNull()
    }

    // ------------------------------------------------------------------ 周期

    @Test
    fun `每日重复首次场次就是起始时间`() = runTest {
        val firstOccurrence = now + 24 * 60 * TestData.MINUTE
        val result = coordinator.create(
            draft(
                startAt = firstOccurrence,
                endAt = firstOccurrence + 30 * TestData.MINUTE,
                recurrence = Recurrence.DAILY,
            )
        )

        assertThat(inexact.planFor(result.todoId)?.occurrenceStart).isEqualTo(firstOccurrence)
    }

    @Test
    fun `跳过某一场后记录例外且不再把它当作待提醒场次`() = runTest {
        val first = now + 60 * TestData.MINUTE
        val created = coordinator.create(
            draft(
                startAt = first,
                endAt = first + 30 * TestData.MINUTE,
                recurrence = Recurrence.DAILY,
            )
        )
        assertThat(coordinator.findById(created.todoId)!!.recurrenceExceptions).isEmpty()

        coordinator.skipOccurrence(created.todoId, first)

        val updated = coordinator.findById(created.todoId)!!
        assertThat(updated.recurrenceExceptions).hasSize(1)
        val next = coordinator.nextOccurrenceToRemind(updated, now)
        assertThat(next).isNotEqualTo(first)
    }

    @Test
    fun `例外日期按本地日期记录`() = runTest {
        val first = now + 60 * TestData.MINUTE
        val created = coordinator.create(draft(startAt = first, recurrence = Recurrence.DAILY))

        coordinator.skipOccurrence(created.todoId, first)

        val expectedKey = RecurrenceExpander.dateKeyOf(first, TestData.ZONE)
        assertThat(coordinator.findById(created.todoId)!!.recurrenceExceptions.map { it.date })
            .containsExactly(expectedKey)
    }

    @Test
    fun `周期待办在提醒过一场之后会排到下一场`() = runTest {
        val first = now + 60 * TestData.MINUTE
        val created = coordinator.create(
            draft(startAt = first, endAt = first + 30 * TestData.MINUTE, recurrence = Recurrence.DAILY)
        )

        // 模拟第一次触发
        coordinator.markRemindedAndReschedule(created.todoId, first)

        val plan = inexact.planFor(created.todoId)
        assertThat(plan).isNotNull()
        assertThat(plan!!.occurrenceStart).isGreaterThan(first)
    }

    // ------------------------------------------------------------------ 对账

    @Test
    fun `对账只为还需要提醒的事项排定`() = runTest {
        repository.insert(draft(name = "要提醒").toTodo(existing = null, now = now))
        repository.insert(
            draft(name = "已完成").toTodo(existing = null, now = now).copy(isDone = true)
        )
        repository.insert(
            // 超出宽限期很久的历史事项：不补发，否则装上新版本会被旧事项刷屏
            draft(name = "很久以前", startAt = now - 120 * TestData.MINUTE)
                .toTodo(existing = null, now = now)
        )

        val scheduledCount = coordinator.reconcileAll()

        assertThat(scheduledCount).isEqualTo(1)
        assertThat(inexact.scheduled).hasSize(1)
    }

    // -------------------------------------------------- 迟到的提醒（宽限期）

    @Test
    fun `刚过去不久的提醒仍会补排一次`() = runTest {
        // 系统深休眠/被厂商冻结导致没投递时，用户打开 App 还能收到这条提醒
        val result = coordinator.create(
            draft(name = "刚错过", startAt = now - 5 * TestData.MINUTE)
        )

        // 触发时刻已过 → 系统会立刻投递
        assertThat(inexact.planFor(result.todoId)?.triggerAt).isLessThan(now)
    }

    @Test
    fun `超过宽限期的提醒不再补发`() = runTest {
        val result = coordinator.create(
            draft(name = "很久以前", startAt = now - 30 * TestData.MINUTE)
        )

        assertThat(inexact.planFor(result.todoId)).isNull()
        assertThat(work.planFor(result.todoId)).isNull()
    }

    @Test
    fun `已经提醒过的场次不会因为宽限期重复提醒`() = runTest {
        val start = now + 60 * TestData.MINUTE
        val created = coordinator.create(draft(startAt = start))
        coordinator.markRemindedAndReschedule(created.todoId, start)

        // 把时钟拨到触发时刻之后 5 分钟再对账
        clock.initial = start + 5 * TestData.MINUTE
        coordinator.reconcileAll()

        // 这一场已经提醒过，不能再排一次
        assertThat(inexact.planFor(created.todoId)).isNull()
    }
}
