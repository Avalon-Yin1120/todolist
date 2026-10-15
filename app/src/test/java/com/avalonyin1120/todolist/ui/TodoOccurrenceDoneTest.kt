package com.avalonyin1120.todolist.ui

import androidx.test.core.app.ApplicationProvider
import com.avalonyin1120.todolist.data.InMemorySettingsStore
import com.avalonyin1120.todolist.data.TodoRepository
import com.avalonyin1120.todolist.domain.Recurrence
import com.avalonyin1120.todolist.domain.RecurrenceException
import com.avalonyin1120.todolist.domain.RecurrenceExceptionKind
import com.avalonyin1120.todolist.domain.RecurrenceExpander
import com.avalonyin1120.todolist.domain.ReminderMode
import com.avalonyin1120.todolist.domain.Todo
import com.avalonyin1120.todolist.reminder.SchedulingCoordinator
import com.avalonyin1120.todolist.testing.FakeClock
import com.avalonyin1120.todolist.testing.FakeExactAlarmPermission
import com.avalonyin1120.todolist.testing.FakeReminderScheduler
import com.avalonyin1120.todolist.testing.FakeTodoDao
import com.avalonyin1120.todolist.testing.TestData
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 「周期待办按场次完成」在 ViewModel 这一层的行为。
 *
 * 用户报的 bug：勾掉周期待办的某一场，其它还没到的场次也一起显示已完成。
 * 界面上的「打勾」入口拿到的对象分两种——日/周/月视图里是**展开出来的当次场次**，
 * 总列表里是 [RecurrenceExpander.asListEntry] 给的「起始日那一场」——因此这里
 * 就按这两种入参分别验证：完成的只是这一场，整条记录的 `isDone` 不被改写。
 *
 * 放在独立的测试类里，是为了不和在改 `TodoViewModelTest` 的其它改动抢同一个文件。
 */
@RunWith(RobolectricTestRunner::class)
class TodoOccurrenceDoneTest {

    private lateinit var repository: TodoRepository
    private lateinit var exact: FakeReminderScheduler
    private lateinit var inexact: FakeReminderScheduler
    private lateinit var work: FakeReminderScheduler
    private lateinit var clock: FakeClock
    private lateinit var viewModel: TodoViewModel

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = TodoRepository(FakeTodoDao())
        exact = FakeReminderScheduler("exact")
        inexact = FakeReminderScheduler("inexact")
        work = FakeReminderScheduler("work")
        val permission = FakeExactAlarmPermission(granted = true)
        clock = FakeClock(TestData.NOW)
        // 默认走通知式：闹钟式要精确闹钟权限，会让这一批「按场次完成」的断言跑偏
        val settings = InMemorySettingsStore(defaultReminderMode = ReminderMode.NOTIFICATION)
        val coordinator = SchedulingCoordinator(
            repository = repository,
            exactScheduler = exact,
            inexactScheduler = inexact,
            workScheduler = work,
            clock = clock,
            exactAlarmPermission = permission,
            zone = TestData.ZONE,
        )
        viewModel = TodoViewModel(
            application = ApplicationProvider.getApplicationContext(),
            repository = repository,
            coordinator = coordinator,
            settings = settings,
            clock = clock,
            canScheduleExactAlarms = { permission.granted },
            zone = TestData.ZONE,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** 驱动主调度器，直到 uiState 完成首次发射 / 落库后的状态稳定下来。 */
    private fun TestScope.settle() = advanceUntilIdle()

    /** 用界面路径建一条周期待办（默认起始时间是「下一个整点」，在测试时区里就是今天）。 */
    private fun TestScope.createDaily(name: String = "每日晨会"): Todo {
        viewModel.openCreateForm()
        viewModel.form.setName(name)
        viewModel.form.setRecurrence(Recurrence.DAILY)
        viewModel.submitForm()
        settle()
        return viewModel.uiState.value.todos.single()
    }

    private fun TestScope.createOnce(name: String = "一次性事项"): Todo {
        viewModel.openCreateForm()
        viewModel.form.setName(name)
        viewModel.submitForm()
        settle()
        return viewModel.uiState.value.todos.single()
    }

    @Test
    fun `周期待办打勾只完成这一场`() = runTest(testDispatcher) {
        val created = createDaily()
        val today = DateFormats.toLocalDate(created.startAt, TestData.ZONE)
        val tomorrow = today.plusDays(1)

        // 日视图里点的是「今天这一场」，界面把展开后的场次传下来
        viewModel.toggleDone(RecurrenceExpander.occurrenceOn(created, today, TestData.ZONE))
        settle()

        val stored = viewModel.uiState.value.todos.single()
        // 整条待办不能被标记为已完成——这正是用户报的 bug
        assertThat(stored.isDone).isFalse()
        assertThat(stored.recurrenceExceptions).containsExactly(
            RecurrenceException(
                RecurrenceExpander.dateKeyOf(today),
                RecurrenceExceptionKind.COMPLETED,
            )
        )

        // 今天这一场显示已完成；列表（取起始日那一场）也跟着显示已完成
        assertThat(RecurrenceExpander.occurrenceOn(stored, today, TestData.ZONE).isDone).isTrue()
        assertThat(RecurrenceExpander.asListEntry(stored, TestData.ZONE).isDone).isTrue()

        // 明天、后天照常出现，且仍是未完成
        assertThat(RecurrenceExpander.occursOn(stored, tomorrow, TestData.ZONE)).isTrue()
        assertThat(RecurrenceExpander.occurrenceOn(stored, tomorrow, TestData.ZONE).isDone).isFalse()
        assertThat(RecurrenceExpander.occurrenceOn(stored, tomorrow.plusDays(1), TestData.ZONE).isDone)
            .isFalse()

        // 后续场次照常排定（三个通道里用到的那两个都必须指向明天那一场）
        val tomorrowOccurrence =
            RecurrenceExpander.occurrenceOn(stored, tomorrow, TestData.ZONE).startAt
        assertThat(inexact.planFor(stored.id)?.occurrenceStart).isEqualTo(tomorrowOccurrence)
        assertThat(work.planFor(stored.id)?.occurrenceStart).isEqualTo(tomorrowOccurrence)
    }

    @Test
    fun `对已完成的那一场再次打勾会取消完成`() = runTest(testDispatcher) {
        val created = createDaily()
        val today = DateFormats.toLocalDate(created.startAt, TestData.ZONE)
        viewModel.toggleDone(RecurrenceExpander.occurrenceOn(created, today, TestData.ZONE))
        settle()

        // 界面上的「这一条」现在带着 isDone=true（按场次的完成状态），再点一次是取消完成
        val completed = viewModel.uiState.value.todos.single()
        val todayOccurrence = RecurrenceExpander.occurrenceOn(completed, today, TestData.ZONE)
        assertThat(todayOccurrence.isDone).isTrue()

        viewModel.toggleDone(todayOccurrence)
        settle()

        val restored = viewModel.uiState.value.todos.single()
        assertThat(restored.isDone).isFalse()
        assertThat(restored.recurrenceExceptions).isEmpty()
        assertThat(RecurrenceExpander.occurrenceOn(restored, today, TestData.ZONE).isDone).isFalse()
        assertThat(inexact.planFor(restored.id)?.occurrenceStart)
            .isEqualTo(RecurrenceExpander.occurrenceOn(restored, today, TestData.ZONE).startAt)
    }

    @Test
    fun `一次性待办打勾仍然改整条状态`() = runTest(testDispatcher) {
        val created = createOnce()

        viewModel.toggleDone(created)
        settle()

        val done = viewModel.uiState.value.todos.single()
        assertThat(done.isDone).isTrue()
        // 一次性待办不产生任何例外
        assertThat(done.recurrenceExceptions).isEmpty()

        viewModel.toggleDone(done)
        settle()

        assertThat(viewModel.uiState.value.todos.single().isDone).isFalse()
    }
}
