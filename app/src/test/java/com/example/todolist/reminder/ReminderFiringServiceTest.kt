package com.example.todolist.reminder

import com.example.todolist.data.TodoDraft
import com.example.todolist.data.TodoRepository
import com.example.todolist.domain.Recurrence
import com.example.todolist.domain.ReminderMode
import com.example.todolist.domain.Todo
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
 * 触发后的落地逻辑：该不该响、响完有没有记下「哪一场已提醒」、
 * 周期待办有没有顺延到下一场。
 *
 * 这里用 Fake 记录「响过了」，因此是纯 JVM 测试，不需要 Android 运行时，
 * 也不会真的弹通知。
 */
class ReminderFiringServiceTest {

    private lateinit var repository: TodoRepository
    private lateinit var exact: FakeReminderScheduler
    private lateinit var inexact: FakeReminderScheduler
    private lateinit var coordinator: SchedulingCoordinator
    private lateinit var service: ReminderFiringService
    private val fired = mutableListOf<Long>()

    private val now = TestData.NOW

    @Before
    fun setUp() {
        repository = TodoRepository(FakeTodoDao())
        exact = FakeReminderScheduler("exact")
        inexact = FakeReminderScheduler("inexact")
        fired.clear()
        coordinator = SchedulingCoordinator(
            repository = repository,
            exactScheduler = exact,
            inexactScheduler = inexact,
            clock = FakeClock(now),
            exactAlarmPermission = FakeExactAlarmPermission(granted = true),
            zone = TestData.ZONE,
        )
        service = ReminderFiringService(coordinator) { todo: Todo -> fired += todo.id }
    }

    private suspend fun createTodo(
        startAt: Long = now + 10 * TestData.MINUTE,
        recurrence: Recurrence = Recurrence.NONE,
    ): Long = coordinator.create(
        TodoDraft(
            name = "小组会议",
            startAt = startAt,
            endAt = startAt + 60 * TestData.MINUTE,
            leadMinutes = 0,
            mode = ReminderMode.NOTIFICATION,
            recurrence = recurrence,
        )
    ).todoId

    @Test
    fun `触发后发出提醒并记下本次场次`() = runTest {
        val id = createTodo()
        val occurrence = coordinator.findById(id)!!.startAt

        service.fire(id, occurrence)

        assertThat(fired).containsExactly(id)
        assertThat(coordinator.findById(id)!!.remindedOccurrenceStart).isEqualTo(occurrence)
    }

    @Test
    fun `同一场次重复触发不会重复提醒`() = runTest {
        val id = createTodo()
        val occurrence = coordinator.findById(id)!!.startAt

        service.fire(id, occurrence)
        service.fire(id, occurrence)

        assertThat(fired).hasSize(1)
    }

    @Test
    fun `已完成的待办不提醒`() = runTest {
        val id = createTodo()
        coordinator.setDone(id, done = true)
        val occurrence = coordinator.findById(id)!!.startAt

        service.fire(id, occurrence)

        assertThat(fired).isEmpty()
    }

    @Test
    fun `不存在的待办安全返回`() = runTest {
        service.fire(999L, now)

        assertThat(fired).isEmpty()
    }

    @Test
    fun `周期待办触发后会顺延排定下一场`() = runTest {
        val first = now + 60 * TestData.MINUTE
        val id = createTodo(startAt = first, recurrence = Recurrence.DAILY)

        service.fire(id, first)

        val plan = inexact.planFor(id)
        assertThat(plan).isNotNull()
        assertThat(plan!!.occurrenceStart).isGreaterThan(first)
    }
}
