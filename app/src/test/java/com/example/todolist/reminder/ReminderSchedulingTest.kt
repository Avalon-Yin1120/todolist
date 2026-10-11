package com.example.todolist.reminder

import com.example.todolist.data.TodoRepository
import com.example.todolist.data.toTodo
import com.example.todolist.data.TodoDraft
import com.example.todolist.domain.Clock
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

    private lateinit var dao: FakeTodoDao
    private lateinit var repository: TodoRepository
    private lateinit var exact: FakeReminderScheduler
    private lateinit var inexact: FakeReminderScheduler
    private lateinit var permission: FakeExactAlarmPermission
    private lateinit var clock: FakeClock
    private lateinit var coordinator: SchedulingCoordinator

    private val now = TestData.NOW

    @Before
    fun setUp() {
        dao = FakeTodoDao()
        repository = TodoRepository(dao)
        exact = FakeReminderScheduler("exact")
        inexact = FakeReminderScheduler("inexact")
        permission = FakeExactAlarmPermission(granted = true)
        clock = FakeClock(now)
        coordinator = SchedulingCoordinator(
            repository = repository,
            exactScheduler = exact,
            inexactScheduler = inexact,
            clock = clock,
            exactAlarmPermission = permission,
        )
    }

    private fun draft(
        name: String = "小组会议",
        startAt: Long = now + 60 * TestData.MINUTE,
        endAt: Long = now + 120 * TestData.MINUTE,
        leadMinutes: Int = 0,
        useExactAlarm: Boolean = false,
    ) = TodoDraft(
        name = name,
        startAt = startAt,
        endAt = endAt,
        leadMinutes = leadMinutes,
        useExactAlarm = useExactAlarm,
    )

    // ------------------------------------------------------------------ 新增

    @Test
    fun `新增普通通知策略的待办只排到非精确调度器`() = runTest {
        val id = coordinator.create(draft().toTodo(existing = null, now = now))

        assertThat(inexact.planFor(id)?.triggerAt).isEqualTo(now + 60 * TestData.MINUTE)
        assertThat(exact.planFor(id)).isNull()
        assertThat(inexact.strategyFor(id)).isEqualTo(ReminderStrategy.NOTIFICATION)
    }

    @Test
    fun `新增精确闹钟策略的待办只排到精确调度器`() = runTest {
        val id = coordinator.create(draft(useExactAlarm = true).toTodo(existing = null, now = now))

        assertThat(exact.planFor(id)?.triggerAt).isEqualTo(now + 60 * TestData.MINUTE)
        assertThat(inexact.planFor(id)).isNull()
        assertThat(coordinator.lastScheduleWasDegraded).isFalse()
    }

    @Test
    fun `提前量体现在触发时刻上`() = runTest {
        val start = now + 60 * TestData.MINUTE
        val id = coordinator.create(
            draft(startAt = start, leadMinutes = 30).toTodo(existing = null, now = now)
        )

        assertThat(inexact.planFor(id)?.triggerAt).isEqualTo(start - 30 * TestData.MINUTE)
    }

    // ------------------------------------------------------------------ 降级

    @Test
    fun `精确闹钟无权限时自动降级为普通通知`() = runTest {
        permission.granted = false
        val id = coordinator.create(draft(useExactAlarm = true).toTodo(existing = null, now = now))

        assertThat(exact.planFor(id)).isNull()
        assertThat(inexact.planFor(id)?.strategy).isEqualTo(ReminderStrategy.NOTIFICATION)
        assertThat(coordinator.lastScheduleWasDegraded).isTrue()
    }

    @Test
    fun `普通通知策略不需要精确闹钟权限`() = runTest {
        permission.granted = false
        val id = coordinator.create(draft(useExactAlarm = false).toTodo(existing = null, now = now))

        assertThat(inexact.planFor(id)).isNotNull()
        assertThat(coordinator.lastScheduleWasDegraded).isFalse()
    }

    @Test
    fun `降级标记不会残留到下一次排定`() = runTest {
        permission.granted = false
        coordinator.create(draft(useExactAlarm = true).toTodo(existing = null, now = now))
        assertThat(coordinator.lastScheduleWasDegraded).isTrue()

        permission.granted = true
        coordinator.create(
            draft(name = "第二件事", useExactAlarm = true).toTodo(existing = null, now = now)
        )
        assertThat(coordinator.lastScheduleWasDegraded).isFalse()
    }

    // ------------------------------------------------------------------ 编辑

    @Test
    fun `编辑时间后会撤销旧调度并按新时间重排`() = runTest {
        val id = coordinator.create(
            draft(startAt = now + 60 * TestData.MINUTE).toTodo(existing = null, now = now)
        )
        val existing = coordinator.findById(id)!!

        val newStart = now + 180 * TestData.MINUTE
        coordinator.update(
            draft(startAt = newStart, endAt = newStart + 60 * TestData.MINUTE)
                .toTodo(existing = existing, now = now)
        )

        assertThat(inexact.cancelled).contains(id)
        assertThat(inexact.planFor(id)?.triggerAt).isEqualTo(newStart)
    }

    @Test
    fun `从普通通知改成精确闹钟时会清掉旧的非精确调度`() = runTest {
        val id = coordinator.create(draft(useExactAlarm = false).toTodo(existing = null, now = now))
        val existing = coordinator.findById(id)!!

        coordinator.update(
            draft(useExactAlarm = true).toTodo(existing = existing, now = now)
        )

        assertThat(inexact.cancelled).contains(id)
        assertThat(inexact.planFor(id)).isNull()
        assertThat(exact.planFor(id)).isNotNull()
    }

    @Test
    fun `改动提醒时刻会清掉已提醒标记从而重新排定`() = runTest {
        val id = coordinator.create(draft().toTodo(existing = null, now = now))
        repository.markNotified(id, now)
        val notified = coordinator.findById(id)!!
        assertThat(notified.notifiedAt).isNotNull()

        val newStart = now + 300 * TestData.MINUTE
        coordinator.update(
            draft(startAt = newStart, endAt = newStart + 60 * TestData.MINUTE)
                .toTodo(existing = notified, now = now)
        )

        assertThat(coordinator.findById(id)!!.notifiedAt).isNull()
        assertThat(inexact.planFor(id)).isNotNull()
    }

    @Test
    fun `只改名称不会清掉已提醒标记`() = runTest {
        val id = coordinator.create(draft().toTodo(existing = null, now = now))
        repository.markNotified(id, now)
        val notified = coordinator.findById(id)!!

        coordinator.update(
            draft(name = "改了名字").toTodo(existing = notified, now = now)
        )

        assertThat(coordinator.findById(id)!!.notifiedAt).isEqualTo(now)
    }

    // ------------------------------------------------------------------ 完成

    @Test
    fun `标记完成后撤销提醒`() = runTest {
        val id = coordinator.create(draft().toTodo(existing = null, now = now))

        coordinator.setDone(id, done = true)

        assertThat(inexact.cancelled).contains(id)
        assertThat(inexact.planFor(id)).isNull()
        assertThat(coordinator.findById(id)!!.isDone).isTrue()
    }

    @Test
    fun `取消完成且提醒时刻未到会重新排定`() = runTest {
        val id = coordinator.create(draft().toTodo(existing = null, now = now))
        coordinator.setDone(id, done = true)
        inexact.clear()

        coordinator.setDone(id, done = false)

        assertThat(inexact.planFor(id)).isNotNull()
    }

    @Test
    fun `取消完成但提醒时刻已过则不再排定`() = runTest {
        val id = coordinator.create(draft().toTodo(existing = null, now = now))
        coordinator.setDone(id, done = true)
        inexact.clear()

        // 时间已经越过提醒时刻，此时再取消完成不应重新排定
        clock.initial = now + 120 * TestData.MINUTE
        coordinator.setDone(id, done = false)

        assertThat(inexact.planFor(id)).isNull()
    }

    @Test
    fun `更新到提醒时刻已过的时间不会排定`() = runTest {
        val id = coordinator.create(draft().toTodo(existing = null, now = now))
        val existing = coordinator.findById(id)!!

        // 把起始时间改到未来，但提前量大到提醒时刻已经过去
        coordinator.update(
            draft(startAt = now + 5 * TestData.MINUTE, leadMinutes = 30)
                .toTodo(existing = existing, now = now)
        )

        assertThat(inexact.planFor(id)).isNull()
    }

    // ------------------------------------------------------------------ 删除

    @Test
    fun `删除会同时撤销调度与记录`() = runTest {
        val id = coordinator.create(draft().toTodo(existing = null, now = now))

        coordinator.delete(id)

        assertThat(inexact.cancelled).contains(id)
        assertThat(coordinator.findById(id)).isNull()
    }

    // ------------------------------------------------------------------ 对账

    @Test
    fun `对账只为真正需要提醒的事项排定`() = runTest {
        val daoForReconcile = FakeTodoDao()
        val repo = TodoRepository(daoForReconcile)
        val coordinator = SchedulingCoordinator(
            repository = repo,
            exactScheduler = exact,
            inexactScheduler = inexact,
            clock = clock,
            exactAlarmPermission = permission,
        )
        // 需要提醒
        repo.insert(draft(name = "要提醒").toTodo(existing = null, now = now))
        // 已完成
        repo.insert(
            draft(name = "已完成", startAt = now + 90 * TestData.MINUTE)
                .toTodo(existing = null, now = now).copy(isDone = true)
        )
        // 已提醒过
        val notifiedId = repo.insert(
            draft(name = "已提醒", startAt = now + 100 * TestData.MINUTE)
                .toTodo(existing = null, now = now)
        )
        repo.markNotified(notifiedId, now)
        // 提醒时刻已过
        repo.insert(
            draft(name = "过期", startAt = now - 10 * TestData.MINUTE).toTodo(existing = null, now = now)
        )

        val scheduledCount = coordinator.reconcileAll()

        assertThat(scheduledCount).isEqualTo(1)
        assertThat(inexact.scheduled).hasSize(1)
    }

    @Test
    fun `对账会覆盖两种策略`() = runTest {
        val repo = TodoRepository(FakeTodoDao())
        val coordinator = SchedulingCoordinator(
            repository = repo,
            exactScheduler = exact,
            inexactScheduler = inexact,
            clock = clock,
            exactAlarmPermission = permission,
        )
        repo.insert(draft(name = "普通").toTodo(existing = null, now = now))
        repo.insert(
            draft(name = "精确", startAt = now + 200 * TestData.MINUTE, useExactAlarm = true)
                .toTodo(existing = null, now = now)
        )

        coordinator.reconcileAll()

        assertThat(inexact.scheduled).hasSize(1)
        assertThat(exact.scheduled).hasSize(1)
    }
}
