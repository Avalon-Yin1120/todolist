package com.example.todolist.ui

import androidx.test.core.app.ApplicationProvider
import com.example.todolist.domain.ReminderMode
import com.example.todolist.data.TodoRepository
import com.example.todolist.reminder.SchedulingCoordinator
import com.example.todolist.testing.FakeClock
import com.example.todolist.testing.FakeExactAlarmPermission
import com.example.todolist.testing.FakeReminderScheduler
import com.example.todolist.testing.FakeTodoDao
import com.example.todolist.testing.TestData
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
 * ViewModel 层的端到端行为：表单 → 落库 → 调度 → UI 状态。
 *
 * 注意：`uiState` 是 `combine(仓库 Flow, 编辑状态, 权限状态)` 的产物，
 * 必须等主调度器把首次发射跑完才能断言，因此每个用例都先 `advanceUntilIdle()`。
 */
@RunWith(RobolectricTestRunner::class)
class TodoViewModelTest {

    private lateinit var repository: TodoRepository
    private lateinit var exact: FakeReminderScheduler
    private lateinit var inexact: FakeReminderScheduler
    private lateinit var permission: FakeExactAlarmPermission
    private lateinit var clock: FakeClock
    private lateinit var coordinator: SchedulingCoordinator
    private lateinit var settings: com.example.todolist.data.InMemorySettingsStore
    private lateinit var viewModel: TodoViewModel

    private val now = TestData.NOW
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = TodoRepository(FakeTodoDao())
        exact = FakeReminderScheduler("exact")
        inexact = FakeReminderScheduler("inexact")
        permission = FakeExactAlarmPermission(granted = true)
        settings = com.example.todolist.data.InMemorySettingsStore(
            // 默认用通知方式：这样「排定到非精确调度器」的断言才有意义。
            // 闹钟式路径由专门的用例覆盖。
            useAlarmForReminders = false,
        )
        clock = FakeClock(now)
        coordinator = SchedulingCoordinator(repository, exact, inexact, clock, permission, TestData.ZONE)
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

    /** 驱动主调度器，直到合并状态完成首次发射。 */
    private fun TestScope.settle() = advanceUntilIdle()

    private fun fillValidForm(name: String = "小组会议") {
        viewModel.openCreateForm()
        viewModel.form.setName(name)
    }

    @Test
    fun `新建表单默认带出合理的起止时间`() = runTest(testDispatcher) {
        viewModel.openCreateForm()
        settle()

        val state = viewModel.uiState.value
        assertThat(state.editState.isOpen).isTrue()
        assertThat(state.editState.editingId).isEqualTo(0L)
        assertThat(viewModel.form.state.value.startAt).isNotNull()
        assertThat(viewModel.form.state.value.endAt).isNotNull()
    }

    @Test
    fun `名称为空时提交不会落库`() = runTest(testDispatcher) {
        viewModel.openCreateForm()
        viewModel.form.setName("")

        viewModel.submitForm()
        settle()

        assertThat(viewModel.form.errors.value).isNotEmpty()
        val state = viewModel.uiState.value
        assertThat(state.todos).isEmpty()
        assertThat(state.editState.isOpen).isTrue()  // 表单保持打开
    }

    @Test
    fun `提交合法表单后落库并排定提醒`() = runTest(testDispatcher) {
        fillValidForm()

        viewModel.submitForm()
        settle()

        val state = viewModel.uiState.value
        assertThat(state.todos).hasSize(1)
        assertThat(state.todos.first().name).isEqualTo("小组会议")
        assertThat(state.editState.isOpen).isFalse()
        assertThat(inexact.scheduled).hasSize(1)
    }

    @Test
    fun `精确策略缺权限时提交仍成功但给出降级提示`() = runTest(testDispatcher) {
        permission.granted = false
        fillValidForm()
        viewModel.form.setMode(ReminderMode.ALARM)

        viewModel.submitForm()
        settle()

        val state = viewModel.uiState.value
        assertThat(state.todos).hasSize(1)
        assertThat(state.notice).isEqualTo(Notice.ExactAlarmDegraded)
        assertThat(exact.scheduled).isEmpty()
        assertThat(inexact.scheduled).hasSize(1)
    }

    @Test
    fun `提示消费后清空`() = runTest(testDispatcher) {
        fillValidForm()
        viewModel.submitForm()
        settle()
        assertThat(viewModel.uiState.value.notice).isEqualTo(Notice.Saved)

        viewModel.consumeNotice()
        settle()

        assertThat(viewModel.uiState.value.notice).isNull()
    }

    @Test
    fun `编辑已有项会更新同名记录而非新增`() = runTest(testDispatcher) {
        fillValidForm("原名字")
        viewModel.submitForm()
        settle()
        val created = viewModel.uiState.value.todos.single()

        viewModel.openEditForm(created)
        viewModel.form.setName("新名字")
        viewModel.submitForm()
        settle()

        val todos = viewModel.uiState.value.todos
        assertThat(todos).hasSize(1)
        assertThat(todos.single().name).isEqualTo("新名字")
        assertThat(todos.single().id).isEqualTo(created.id)
    }

    @Test
    fun `切换完成状态会落库并撤销提醒`() = runTest(testDispatcher) {
        fillValidForm()
        viewModel.submitForm()
        settle()
        val created = viewModel.uiState.value.todos.single()

        viewModel.toggleDone(created)
        settle()

        assertThat(viewModel.uiState.value.todos.single().isDone).isTrue()
        assertThat(inexact.cancelled).contains(created.id)
    }

    @Test
    fun `删除后列表为空且表单关闭`() = runTest(testDispatcher) {
        fillValidForm()
        viewModel.submitForm()
        settle()
        val created = viewModel.uiState.value.todos.single()
        viewModel.openEditForm(created)

        viewModel.confirmDelete(created.id)
        settle()

        assertThat(viewModel.uiState.value.todos).isEmpty()
        assertThat(viewModel.uiState.value.editState.isOpen).isFalse()
    }

    @Test
    fun `权限被撤销时给出提示`() = runTest(testDispatcher) {
        settle()
        assertThat(viewModel.uiState.value.canScheduleExact).isTrue()

        permission.granted = false
        viewModel.refreshPermissions()
        settle()

        assertThat(viewModel.uiState.value.canScheduleExact).isFalse()
        assertThat(viewModel.uiState.value.notice).isEqualTo(Notice.ExactAlarmPermissionRevoked)
    }

    @Test
    fun `权限重新授予后横幅消失`() = runTest(testDispatcher) {
        permission.granted = false
        viewModel.refreshPermissions()
        settle()
        assertThat(viewModel.uiState.value.canScheduleExact).isFalse()

        permission.granted = true
        viewModel.refreshPermissions()
        settle()

        assertThat(viewModel.uiState.value.canScheduleExact).isTrue()
    }
}
