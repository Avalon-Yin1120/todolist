package com.avalonyin1120.todolist.ui

import androidx.test.core.app.ApplicationProvider
import com.avalonyin1120.todolist.domain.ReminderMode
import com.avalonyin1120.todolist.data.TodoRepository
import com.avalonyin1120.todolist.reminder.SchedulingCoordinator
import com.avalonyin1120.todolist.testing.FakeClock
import com.avalonyin1120.todolist.testing.FakeExactAlarmPermission
import com.avalonyin1120.todolist.testing.FakeReminderScheduler
import com.avalonyin1120.todolist.testing.FakeTodoDao
import com.avalonyin1120.todolist.domain.Recurrence
import com.avalonyin1120.todolist.domain.WeekWindow
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
    private lateinit var work: FakeReminderScheduler
    private lateinit var permission: FakeExactAlarmPermission
    private lateinit var clock: FakeClock
    private lateinit var coordinator: SchedulingCoordinator
    private lateinit var settings: com.avalonyin1120.todolist.data.InMemorySettingsStore
    private lateinit var viewModel: TodoViewModel

    /** 提醒可靠性相关的系统开关，可在用例里改。 */
    private var notificationsAllowed = true
    private var fullScreenAllowed = true
    private var oemGuideNeeded = false

    private val now = TestData.NOW
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        repository = TodoRepository(FakeTodoDao())
        exact = FakeReminderScheduler("exact")
        inexact = FakeReminderScheduler("inexact")
        work = FakeReminderScheduler("work")
        permission = FakeExactAlarmPermission(granted = true)
        settings = com.avalonyin1120.todolist.data.InMemorySettingsStore(
            // 默认用通知方式：这样「排定到非精确调度器」的断言才有意义。
            // 闹钟式路径由专门的用例覆盖。
            useAlarmForReminders = false,
        )
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
        viewModel = TodoViewModel(
            application = ApplicationProvider.getApplicationContext(),
            repository = repository,
            coordinator = coordinator,
            settings = settings,
            clock = clock,
            canScheduleExactAlarms = { permission.granted },
            canPostNotifications = { notificationsAllowed },
            fullScreenIntentAllowed = { fullScreenAllowed },
            oemBackgroundGuideNeeded = { oemGuideNeeded },
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

    // ------------------------------------------------ 「仅修改这一次」（第 14 项回归）

    @Test
    fun `仅修改这一次时中途返回不会多出待办`() = runTest(testDispatcher) {
        // 先用界面建一条重复待办
        fillValidForm("每周例会")
        viewModel.form.setRecurrence(Recurrence.WEEKLY)
        viewModel.submitForm()
        settle()
        val recurring = viewModel.uiState.value.todos.single()
        assertThat(recurring.recurrence).isEqualTo(Recurrence.WEEKLY)

        // 点「改」-> 选择「仅这一次」，然后直接返回（不保存）
        viewModel.openEditForm(recurring)
        settle()
        assertThat(viewModel.uiState.value.dialog).isNotNull()
        viewModel.resolveRecurrenceScope(entireSeries = false)
        settle()
        assertThat(viewModel.uiState.value.oneOffEdit).isTrue()
        viewModel.dismissForm()
        settle()

        // 库里仍然只有原来那一条：早先的实现会在打开表单时就先建一条，于是多出一条
        val todos = viewModel.uiState.value.todos
        assertThat(todos).hasSize(1)
        assertThat(todos.single().id).isEqualTo(recurring.id)
    }

    @Test
    fun `仅修改这一次保存后才落库并把原场次标记为例外`() = runTest(testDispatcher) {
        fillValidForm("每周例会")
        viewModel.form.setRecurrence(Recurrence.WEEKLY)
        viewModel.submitForm()
        settle()
        val recurring = viewModel.uiState.value.todos.single()

        viewModel.openEditForm(recurring)
        settle()
        viewModel.resolveRecurrenceScope(entireSeries = false)
        settle()
        viewModel.form.setName("这周改到下午")
        viewModel.submitForm()
        settle()

        val todos = viewModel.uiState.value.todos
        // 一条独立待办 + 原规则
        assertThat(todos).hasSize(2)
        val oneOff = todos.first { it.id != recurring.id }
        assertThat(oneOff.name).isEqualTo("这周改到下午")
        assertThat(oneOff.recurrence).isEqualTo(Recurrence.NONE)
        assertThat(viewModel.uiState.value.oneOffEdit).isFalse()

        // 原规则上该日期被标记为例外，同一天不会出现两条场次
        val updated = todos.first { it.id == recurring.id }
        assertThat(updated.recurrenceExceptions.map { it.date })
            .contains(DateFormats.toLocalDate(recurring.startAt, TestData.ZONE).toString())
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

    // ------------------------------------------------------ 日历跳转请求
    //
    // 日历 pager 只认 jumpRequest（和 tab）作为「滚过去」的信号。
    // 如果改用 selectedDate 当信号，pager 翻页后的回写（selectDateSoft）
    // 会把「滚到今天」的动画打断，应用就永久停在锚点页 2000-01-01。

    @Test
    fun `进入视图时带的是今天而不是锚点日`() = runTest(testDispatcher) {
        settle()
        val state = viewModel.uiState.value
        val today = java.time.LocalDate.ofInstant(java.time.Instant.ofEpochMilli(now), TestData.ZONE)
        assertThat(state.tab).isEqualTo(NavTab.DAY)
        assertThat(state.selectedDate).isEqualTo(today)
        // 首帧应当没有待处理的跳转请求，pager 直接以「今天」为初始页
        assertThat(state.jumpRequest).isEqualTo(0L)
    }

    @Test
    fun `点某一天会发一次跳转请求并切到日视图`() = runTest(testDispatcher) {
        settle()
        viewModel.selectTab(NavTab.MONTH)
        settle()
        val before = viewModel.uiState.value.jumpRequest

        viewModel.selectDate(java.time.LocalDate.of(2026, 5, 20))
        settle()

        val after = viewModel.uiState.value
        assertThat(after.tab).isEqualTo(NavTab.DAY)
        assertThat(after.selectedDate).isEqualTo(java.time.LocalDate.of(2026, 5, 20))
        assertThat(after.jumpRequest).isEqualTo(before + 1)
    }

    @Test
    fun `翻页回写不会发跳转请求`() = runTest(testDispatcher) {        settle()
        val before = viewModel.uiState.value.jumpRequest

        // 这是 pager 翻页后调用的回写路径：只更新日期，不能再驱动 pager
        viewModel.selectDateSoft(java.time.LocalDate.of(2026, 5, 20))
        settle()

        val after = viewModel.uiState.value
        assertThat(after.selectedDate).isEqualTo(java.time.LocalDate.of(2026, 5, 20))
        assertThat(after.jumpRequest).isEqualTo(before)
    }

    @Test
    fun `月视图回写只给代表日时保留今天这一天`() = runTest(testDispatcher) {
        settle()
        val today = viewModel.uiState.value.selectedDate

        // 月视图翻到「今天所在的月份」，pager 报上来的是 1 号
        viewModel.selectTab(NavTab.MONTH)
        settle()
        viewModel.selectDateSoft(today.withDayOfMonth(1))
        settle()
        assertThat(viewModel.uiState.value.selectedDate).isEqualTo(today)

        // 翻到别的月份时才接受代表日（此时 1 号是唯一可知的信息）
        val otherMonth = today.plusMonths(2).withDayOfMonth(1)
        viewModel.selectDateSoft(otherMonth)
        settle()
        assertThat(viewModel.uiState.value.selectedDate).isEqualTo(otherMonth)
    }

    @Test
    fun `上层显式给的日期会覆盖粗粒度的代表日`() = runTest(testDispatcher) {
        settle()
        val today = viewModel.uiState.value.selectedDate
        viewModel.selectTab(NavTab.MONTH)
        settle()

        // 月视图翻到别的月份，selectedDate 降级成那个月的 1 号
        val otherMonth = today.plusMonths(2).withDayOfMonth(1)
        viewModel.selectDateSoft(otherMonth)
        settle()
        assertThat(viewModel.uiState.value.selectedDate).isEqualTo(otherMonth)

        // 「回到今天」把日期显式落回今天：虽然和 1 号同一个月，也必须照收，
        // 否则切回日视图就永远停在 1 号
        viewModel.selectDateSoft(today)
        settle()
        assertThat(viewModel.uiState.value.selectedDate).isEqualTo(today)
    }

    @Test
    fun `周视图回写只给周一代表日时保留周内那一天`() = runTest(testDispatcher) {
        settle()
        viewModel.selectTab(NavTab.WEEK)
        settle()
        val today = viewModel.uiState.value.selectedDate
        val monday = CalendarLayout.weekStart(today)

        viewModel.selectDateSoft(monday)
        settle()
        assertThat(viewModel.uiState.value.selectedDate).isEqualTo(today)

        viewModel.selectDateSoft(monday.plusWeeks(3))
        settle()
        assertThat(viewModel.uiState.value.selectedDate).isEqualTo(monday.plusWeeks(3))
    }

    // ------------------------------------------------ 提醒可靠性检查（真机不响的首要原因）

    @Test
    fun `进入检查页时带上各项开关状态`() = runTest(testDispatcher) {
        notificationsAllowed = false
        fullScreenAllowed = false
        oemGuideNeeded = true
        val fresh = TodoViewModel(
            application = ApplicationProvider.getApplicationContext(),
            repository = repository,
            coordinator = coordinator,
            settings = settings,
            clock = clock,
            canScheduleExactAlarms = { permission.granted },
            canPostNotifications = { notificationsAllowed },
            fullScreenIntentAllowed = { fullScreenAllowed },
            oemBackgroundGuideNeeded = { oemGuideNeeded },
            zone = TestData.ZONE,
        )
        settle()

        val state = fresh.uiState.value
        assertThat(state.canPostNotifications).isFalse()
        assertThat(state.fullScreenIntentAllowed).isFalse()
        assertThat(state.needsOemBackgroundGuide).isTrue()
    }

    @Test
    fun `刷新权限会一并重读通知与全屏提醒开关`() = runTest(testDispatcher) {
        settle()
        assertThat(viewModel.uiState.value.canPostNotifications).isTrue()

        // 用户在系统设置里关掉了通知/全屏提醒，回到前台后必须能看到变化
        notificationsAllowed = false
        fullScreenAllowed = false
        viewModel.refreshPermissions()
        settle()

        assertThat(viewModel.uiState.value.canPostNotifications).isFalse()
        assertThat(viewModel.uiState.value.fullScreenIntentAllowed).isFalse()
    }

    @Test
    fun `打开与关闭提醒可靠性检查页`() = runTest(testDispatcher) {
        settle()
        viewModel.openReminderHealth()
        settle()
        assertThat(viewModel.uiState.value.screen).isEqualTo(Screen.REMINDER_HEALTH)

        viewModel.closeReminderHealth()
        settle()
        assertThat(viewModel.uiState.value.screen).isEqualTo(Screen.MAIN)
    }

    @Test
    fun `从设置页进入检查页后返回设置页`() = runTest(testDispatcher) {
        settle()
        viewModel.openSettings()
        settle()

        viewModel.openReminderHealth()
        settle()
        assertThat(viewModel.uiState.value.screen).isEqualTo(Screen.REMINDER_HEALTH)

        // 返回应回到「进来的那一页」，否则用户想接着改别的设置项就得重新点一遍
        viewModel.closeReminderHealth()
        settle()
        assertThat(viewModel.uiState.value.screen).isEqualTo(Screen.SETTINGS)
    }

    @Test
    fun `关于页与隐私页从设置页进出`() = runTest(testDispatcher) {
        settle()
        viewModel.openSettings()
        settle()

        viewModel.openAbout()
        settle()
        assertThat(viewModel.uiState.value.screen).isEqualTo(Screen.ABOUT)

        viewModel.closeAbout()
        settle()
        assertThat(viewModel.uiState.value.screen).isEqualTo(Screen.SETTINGS)

        viewModel.openPrivacy()
        settle()
        assertThat(viewModel.uiState.value.screen).isEqualTo(Screen.PRIVACY)

        viewModel.closePrivacy()
        settle()
        assertThat(viewModel.uiState.value.screen).isEqualTo(Screen.SETTINGS)
    }

    @Test
    fun `从关于页进入检查页后返回关于页`() = runTest(testDispatcher) {
        settle()
        viewModel.openSettings()
        viewModel.openAbout()
        settle()

        viewModel.openReminderHealth()
        settle()
        assertThat(viewModel.uiState.value.screen).isEqualTo(Screen.REMINDER_HEALTH)

        viewModel.closeReminderHealth()
        settle()
        assertThat(viewModel.uiState.value.screen).isEqualTo(Screen.ABOUT)
    }

    @Test
    fun `通知提示音开关会被写入设置并读回`() = runTest(testDispatcher) {
        settle()
        assertThat(viewModel.uiState.value.settings.notificationSound).isTrue()

        viewModel.changeSetting(SettingsChange.NotificationSound(false))
        settle()

        assertThat(viewModel.uiState.value.settings.notificationSound).isFalse()
        // 必须落到设置存储里，而不只是改内存里的快照
        assertThat(settings.notificationSound).isFalse()
    }

    @Test
    fun `界面状态里带有版本信息`() = runTest(testDispatcher) {
        settle()

        val version = viewModel.uiState.value.version
        assertThat(version.packageName).contains("todolist")
        assertThat(version.versionCode).isGreaterThan(0)
        assertThat(version.shortLabel()).contains("（")
    }

    @Test
    fun `默认提醒方式是通知而不是闹钟`() = runTest(testDispatcher) {
        settle()

        // 闹钟式会持续响铃并抢占屏幕，当默认值太吵；而且它依赖精确闹钟权限
        assertThat(viewModel.uiState.value.settings.useAlarmForReminders).isFalse()

        // 新建表单的默认方式要跟着设置走（这里顺带验证设置确实驱动了表单）
        viewModel.openCreateForm()
        settle()
        assertThat(viewModel.form.state.value.mode).isEqualTo(ReminderMode.NOTIFICATION)

        viewModel.dismissForm()
        settle()
        viewModel.changeSetting(SettingsChange.UseAlarm(true))
        viewModel.openCreateForm()
        settle()
        assertThat(viewModel.form.state.value.mode).isEqualTo(ReminderMode.ALARM)
    }

    @Test
    fun `周视图时段的默认值是六点到二十二点`() = runTest(testDispatcher) {
        settle()

        val settings = viewModel.uiState.value.settings
        assertThat(settings.weekViewStartHour).isEqualTo(WeekWindow.DEFAULT_START_HOUR)
        assertThat(settings.weekViewEndHour).isEqualTo(WeekWindow.DEFAULT_END_HOUR)
        assertThat(settings.weekWindowLabel()).isEqualTo("06:00 – 22:00")
    }

    @Test
    fun `修改周视图时段会落进设置`() = runTest(testDispatcher) {
        settle()

        viewModel.changeSetting(SettingsChange.WeekStartHour(8))
        viewModel.changeSetting(SettingsChange.WeekEndHour(20))
        settle()

        assertThat(viewModel.uiState.value.settings.weekViewStartHour).isEqualTo(8)
        assertThat(viewModel.uiState.value.settings.weekViewEndHour).isEqualTo(20)
        assertThat(settings.weekViewStartHour).isEqualTo(8)
        assertThat(settings.weekViewEndHour).isEqualTo(20)
    }

    @Test
    fun `周视图时段非法组合会被夹到合法值`() = runTest(testDispatcher) {
        settle()

        // 把起始推到 20 点、结束设成 3 点：两个值都必须被修正，不能留下 start >= end
        viewModel.changeSetting(SettingsChange.WeekStartHour(20))
        viewModel.changeSetting(SettingsChange.WeekEndHour(3))
        settle()

        val settings = viewModel.uiState.value.settings
        assertThat(settings.weekViewEndHour - settings.weekViewStartHour)
            .isAtLeast(WeekWindow.MIN_HOURS)
    }

    @Test
    fun `周视图时段可以恢复默认`() = runTest(testDispatcher) {
        settle()
        viewModel.changeSetting(SettingsChange.WeekStartHour(9))
        viewModel.changeSetting(SettingsChange.WeekEndHour(17))
        settle()

        viewModel.changeSetting(SettingsChange.ResetWeekWindow)
        settle()

        assertThat(viewModel.uiState.value.settings.weekWindowLabel()).isEqualTo("06:00 – 22:00")
    }
}
