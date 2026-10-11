package com.example.todolist.reminder

import androidx.test.core.app.ApplicationProvider
import com.example.todolist.data.TodoDraft
import com.example.todolist.data.TodoRepository
import com.example.todolist.data.toTodo
import com.example.todolist.domain.Todo
import com.example.todolist.notify.NotificationHelper
import com.example.todolist.testing.FakeClock
import com.example.todolist.testing.FakeTodoDao
import com.example.todolist.testing.TestData
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 提醒落地逻辑：该不该响、响完有没有打标记、重复触发会不会重复响。
 *
 * 用 Robolectric 提供真实 Context（NotificationHelper 构造需要它），
 * 但把发通知的方法覆盖掉，只记录调用，不真正打扰系统通知栏。
 */
@RunWith(RobolectricTestRunner::class)
class ReminderFiringServiceTest {

    private class RecordingNotificationHelper(context: android.content.Context) :
        NotificationHelper(context) {

        val notified = mutableListOf<Long>()

        override fun notifyReminder(todo: Todo) {
            notified += todo.id
        }
    }

    private lateinit var repository: TodoRepository
    private lateinit var notifications: RecordingNotificationHelper
    private lateinit var service: ReminderFiringService

    private val now = TestData.NOW

    @Before
    fun setUp() {
        repository = TodoRepository(FakeTodoDao())
        notifications = RecordingNotificationHelper(ApplicationProvider.getApplicationContext())
        service = ReminderFiringService(repository, notifications, FakeClock(now))
    }

    private suspend fun insertTodo(isDone: Boolean = false): Long =
        repository.insert(
            TodoDraft(
                name = "小组会议",
                startAt = now + 10 * TestData.MINUTE,
                endAt = now + 70 * TestData.MINUTE,
                leadMinutes = 0,
                useExactAlarm = false,
            ).toTodo(existing = null, now = now).copy(isDone = isDone)
        )

    @Test
    fun `到点后发出通知并打上已提醒标记`() = runTest {
        val id = insertTodo()

        service.fire(id)

        assertThat(notifications.notified).containsExactly(id)
        assertThat(repository.findById(id)!!.notifiedAt).isEqualTo(now)
    }

    @Test
    fun `重复触发不会重复发通知`() = runTest {
        val id = insertTodo()

        service.fire(id)
        service.fire(id)

        assertThat(notifications.notified).hasSize(1)
    }

    @Test
    fun `已完成的待办不发通知`() = runTest {
        val id = insertTodo(isDone = true)

        service.fire(id)

        assertThat(notifications.notified).isEmpty()
        assertThat(repository.findById(id)!!.notifiedAt).isNull()
    }

    @Test
    fun `不存在的待办安全返回`() = runTest {
        service.fire(999L)

        assertThat(notifications.notified).isEmpty()
    }
}
