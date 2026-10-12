package com.example.todolist.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.todolist.AppGraph
import com.example.todolist.TodoApp
import com.example.todolist.requireGraph
import com.example.todolist.data.SettingsStore
import com.example.todolist.data.TodoDraft
import com.example.todolist.data.TodoRepository
import com.example.todolist.domain.Clock
import com.example.todolist.domain.FormError
import com.example.todolist.domain.Recurrence
import com.example.todolist.domain.ReminderMode
import com.example.todolist.domain.Todo
import com.example.todolist.reminder.SchedulingCoordinator
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/** 底部导航的视图。 */
enum class NavTab { TODO, DAY, WEEK, MONTH }

/** 顶层界面：主界面、设置页、表单页与两个说明页。 */
enum class Screen { MAIN, SETTINGS, FORM, REMINDER_HEALTH, ABOUT, PRIVACY }

/** 列表内的分组方式。 */
enum class ListGrouping { BY_TIME, BY_DONE }

/** 编辑表单是否打开。 */
data class EditState(
    val isOpen: Boolean = false,
    val editingId: Long = 0L,
    /**
     * 「仅修改这一次」的来源重复待办 id。
     *
     * 非 0 表示：保存时要新建一条独立待办，并在来源规则上把这一天标记为例外。
     * 保存前不写库，所以用户中途返回不会留下多余记录。
     */
    val oneOffSourceId: Long = 0L,
)

/** 需要用户确认或注意的一次性交互。 */
sealed interface DialogRequest {
    /** 删除前二次确认。 */
    data class ConfirmDelete(val todoId: Long) : DialogRequest

    /** 重复待办：只改本次 / 改整个系列。 */
    data class RecurrenceScope(val todoId: Long, val action: RecurrenceAction) : DialogRequest
}

/** 待办操作的类型（用于「本次 / 整个」选择）。 */
enum class RecurrenceAction { EDIT, DELETE }

/** 一次性提示。 */
sealed interface Notice {
    data object ExactAlarmDegraded : Notice
    data object ExactAlarmPermissionRevoked : Notice
    data object Saved : Notice

    /** 该时间段已有其它待办：仍然创建成功，只是提示。 */
    data class OverlapWarning(val others: List<String>) : Notice

    /** 重复待办只删了本次。 */
    data object OccurrenceSkipped : Notice
}

/** 列表页的集合状态。 */
data class TodoUiState(
    val todos: List<Todo> = emptyList(),
    val editState: EditState = EditState(),
    val canScheduleExact: Boolean = true,
    /** 是否已允许后台运行（电池优化白名单） */
    val batteryOptimizationIgnored: Boolean = true,
    val notice: Notice? = null,
    val dialog: DialogRequest? = null,
    /** 默认进入日视图 */
    val tab: NavTab = NavTab.DAY,
    val selectedDate: LocalDate = LocalDate.now(),
    /**
     * 用户主动跳转的序号。
     *
     * 日历 pager 只在这个值变化时才滚动（外加 tab 变化）。
     * 不能直接以 [selectedDate] 驱动 pager：pager 自己在翻页后会把「当前显示的那一期」
     * 写回 selectedDate，如果这个回写又反过来驱动 pager，两个方向就会互相打架——
     * 打开应用时 pager 先停在锚点页（2000-01-01），回写把 selectedDate 改成 2000-01-01，
     * 紧接着「滚到 selectedDate」的效应就把刚发起的「滚到今天」动画取消了，
     * 于是界面永远停在 2000 年（这正是用户报的「一打开就不对、怎么滑都在远古」）。
     */
    val jumpRequest: Long = 0,
    val grouping: ListGrouping = ListGrouping.BY_TIME,
    /** 当前顶层界面：主界面、设置页或表单页 */
    val screen: Screen = Screen.MAIN,
    /** 通知权限是否已授予（Android 13+ 需要运行时权限，系统设置里也能被关掉） */
    val canPostNotifications: Boolean = true,
    /** 闹钟式提醒能否弹出整屏提醒页（Android 14+ 需用户允许全屏通知） */
    val fullScreenIntentAllowed: Boolean = true,
    /** 当前是不是需要额外开「自启动」的国产 ROM（只影响提示文案） */
    val needsOemBackgroundGuide: Boolean = false,
    val settings: SettingsUiState = SettingsUiState(),
    /** 表单是否打开（整屏表单页），供界面判断是否需要初始聚焦等行为 */
    val formOpen: Boolean = false,
    /** 当前表单是否来自「仅修改重复待办的这一次」 */
    val oneOffEdit: Boolean = false,
    /** 周期待办「仅修改本次」时的场次时间。 */
    val pendingOccurrenceStart: Long = 0L,
    /** 正在处理的重复待办原始副本，供「仅修改本次」构造独立记录。 */
    val pendingRecurringTodo: Todo? = null,
    /** 版本与构建信息，供设置页的「版本号」与关于页展示。 */
    val version: VersionInfo = VersionInfo(),
)

/** 把校验错误枚举翻译成用户可见文案。 */
fun messageOf(error: FormError): String = when (error) {
    FormError.NAME_EMPTY -> "请填写名称"
    FormError.NAME_TOO_LONG -> "名称不能超过 80 个字"
    FormError.END_NOT_AFTER_START -> "终止时间必须晚于起始时间"
    FormError.LEAD_OUT_OF_RANGE -> "提前量需在 0 到 10080 分钟（7 天）之间"
}

/**
 * 主界面 ViewModel。
 *
 * 依赖全部从构造函数注入，因此可以在测试里直接 new 出来并替换任意一层。
 * 视图状态用一个内部 [LocalState] 聚合，避免五六个独立 StateFlow 的组合爆炸。
 */
class TodoViewModel(
    application: Application,
    private val repository: TodoRepository,
    private val coordinator: SchedulingCoordinator,
    private val settings: SettingsStore,
    private val clock: Clock,
    private val canScheduleExactAlarms: () -> Boolean,
    /** 通知权限是否已授予；默认 true 便于测试只关心其它逻辑 */
    private val canPostNotifications: () -> Boolean = { true },
    /** Android 14+ 的全屏通知是否被允许；默认 true */
    private val fullScreenIntentAllowed: () -> Boolean = { true },
    /** 是否需要提示「国产 ROM 要额外开自启动」 */
    private val oemBackgroundGuideNeeded: () -> Boolean = { false },
    /** 后台运行（电池优化白名单）能力；为空表示不检查（测试里常用） */
    private val batteryOptimization: com.example.todolist.sys.BatteryOptimizationPermission? = null,
    private val zone: ZoneId = ZoneId.systemDefault(),
    /** 版本信息；为空时从运行环境读取（测试里可注入固定值） */
    private val versionInfo: VersionInfo? = null,
) : AndroidViewModel(application) {

    /** 表单状态独立于列表状态，避免列表刷新打断用户输入。 */
    val form = DraftFormStateHolder(
        clock = clock,
        rememberedLeadMinutes = { settings.lastLeadMinutes },
        defaultMode = {
            if (settings.useAlarmForReminders) ReminderMode.ALARM else ReminderMode.NOTIFICATION
        },
    )

    private val editState = MutableStateFlow(EditState())

    private val localState = MutableStateFlow(
        LocalState(
            canScheduleExact = canScheduleExactAlarms(),
            batteryOptimizationIgnored = batteryOptimization?.isIgnoringBatteryOptimizations() ?: true,
            canPostNotifications = canPostNotifications(),
            fullScreenIntentAllowed = fullScreenIntentAllowed(),
            needsOemBackgroundGuide = oemBackgroundGuideNeeded(),
            settings = readSettings(),
            version = versionInfo ?: VersionInfo.of(application),
            // 「今天」必须取自注入的时钟/时区，而不是系统时钟：
            // 否则测试里 FakeClock 的日期和界面认的日期会不一致，
            // 日历首帧就会落到错误的页上。
            selectedDate = LocalDate.ofInstant(java.time.Instant.ofEpochMilli(clock.now()), zone),
        )
    )

    val uiState: StateFlow<TodoUiState> = combine(
        repository.observeAll(),
        editState,
        localState,
    ) { todos, edit, local ->
        TodoUiState(
            todos = todos,
            editState = edit,
            canScheduleExact = local.canScheduleExact,
            batteryOptimizationIgnored = local.batteryOptimizationIgnored,
            notice = local.notice,
            dialog = local.dialog,
            tab = local.tab,
            selectedDate = local.selectedDate,
            jumpRequest = local.jumpRequest,
            grouping = local.grouping,
            screen = if (edit.isOpen) Screen.FORM else local.screen,
            canPostNotifications = local.canPostNotifications,
            fullScreenIntentAllowed = local.fullScreenIntentAllowed,
            needsOemBackgroundGuide = local.needsOemBackgroundGuide,
            oneOffEdit = edit.oneOffSourceId != 0L,
            formOpen = edit.isOpen,
            settings = local.settings,
            pendingOccurrenceStart = local.pendingOccurrenceStart,
            pendingRecurringTodo = local.pendingRecurringTodo,
            version = local.version,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, TodoUiState())

    /**
     * 当前时刻，每秒发一次。
     *
     * 刻意做成冷流、由界面收集，而不是在 init 里起一个永不结束的 while 循环：
     * * 常驻轮询即使界面不可见也在跑，白耗电；
     * * 无限循环会让测试里的虚拟调度器永远「不空闲」，`runTest` 会一直等下去。
     */
    val timeFlow: Flow<Long> = flow {
        while (true) {
            emit(clock.now())
            delay(TICK_INTERVAL_MS)
        }
    }

    /** 当前时刻（用于倒计时与时段判定）。 */
    fun now(): Long = clock.now()

    /** 当前时区。 */
    fun zone(): ZoneId = zone

    // ---------------------------------------------------------------- 导航

    fun selectTab(tab: NavTab) = updateLocal { it.copy(tab = tab) }

    /** 点某一天：跳到日视图。同时发一次跳转请求，让日历滚到那一天。 */
    fun selectDate(date: LocalDate) = updateLocal {
        it.copy(selectedDate = date, tab = NavTab.DAY, jumpRequest = it.jumpRequest + 1)
    }

    /**
     * 只更新「当前看的日期」，不切换视图。
     *
     * 日历视图翻页时用这个：翻到下一周不该把用户从周视图踢到日视图。
     *
     * 传入的 [date] 有两种来源，处理方式不同：
     * * pager 上报的「这一期的代表日」（周=周一、月=1 号）——粒度比用户实际在看的日期粗，
     *   如果当前 selectedDate 就在这一期里，就保留它（连同「几号」）；
     * * 上层显式给的某一天（例如「回到今天」把日期落回今天）——照收，
     *   否则月视图里存着 1 号时会把「今天」挡掉，切回日视图就永远停在 1 号。
     */
    fun selectDateSoft(date: LocalDate) = updateLocal { local ->
        val keepFinerDate = isPeriodAnchor(local.tab, date) &&
            samePeriodAs(local.tab, local.selectedDate, date)
        if (keepFinerDate) local else local.copy(selectedDate = date)
    }

    /** [date] 是否正好落在本粒度的期首（周=周一、月=1 号；日粒度下每天都是期首）。 */
    private fun isPeriodAnchor(tab: NavTab, date: LocalDate): Boolean = when (tab) {
        NavTab.WEEK -> CalendarLayout.weekStart(date) == date
        NavTab.MONTH -> date.dayOfMonth == 1
        else -> true
    }

    /** 两个日期是否属于同一期（同一周 / 同一月 / 同一天）。 */
    private fun samePeriodAs(tab: NavTab, a: LocalDate, b: LocalDate): Boolean = when (tab) {
        NavTab.WEEK -> CalendarLayout.weekStart(a) == CalendarLayout.weekStart(b)
        NavTab.MONTH -> java.time.YearMonth.from(a) == java.time.YearMonth.from(b)
        else -> a == b
    }

    /** 左右滑动切换：日视图 ±1 天，周视图 ±7 天。 */
    fun shiftDate(days: Long) = updateLocal { it.copy(selectedDate = it.selectedDate.plusDays(days)) }

    /** 月视图翻月。 */
    fun shiftMonth(months: Long) =
        updateLocal { it.copy(selectedDate = it.selectedDate.plusMonths(months)) }

    fun setGrouping(grouping: ListGrouping) = updateLocal { it.copy(grouping = grouping) }

    // ---------------------------------------------------------------- 表单

    fun openCreateForm() {
        form.open()
        editState.value = EditState(isOpen = true, editingId = 0L)
    }

    fun openEditForm(todo: Todo) {
        if (todo.recurrence.isRecurring) {
            // 重复待办的编辑需要先问清作用范围
            updateLocal {
                it.copy(
                    dialog = DialogRequest.RecurrenceScope(todo.id, RecurrenceAction.EDIT),
                    pendingRecurringTodo = todo,
                    pendingOccurrenceStart = todo.startAt,
                )
            }
            return
        }
        openEditFormDirect(todo)
    }

    /** 直接打开编辑表单（不询问作用范围）。 */
    fun openEditFormDirect(todo: Todo) {
        form.open(
            id = todo.id,
            name = todo.name,
            startAt = todo.startAt,
            endAt = todo.endAt,
            leadMinutes = todo.leadMinutes,
            mode = todo.mode,
            recurrence = todo.recurrence,
            weekDays = todo.weekDays,
            monthDays = todo.monthDays,
            recurrenceEndDate = todo.recurrenceEndDate,
            recurrenceCount = todo.recurrenceCount,
        )
        editState.value = EditState(isOpen = true, editingId = todo.id)
    }

    /**
     * 打开「仅修改重复待办的这一次」的表单。
     *
     * 关键点：**不预先落库**。早先的实现是先 create 一条独立待办再打开编辑表单，
     * 用户点「返回」不保存时那条记录已经存在了，表现为「只是想改一下却多出一条待办」。
     * 现在只把表单预填好并记住来源，等用户真的点保存时才写库。
     */
    fun openOneOffEditForm(source: Todo, occurrenceStart: Long) {
        val duration = source.endAt - source.startAt
        form.open(
            id = 0L,
            name = source.name,
            startAt = occurrenceStart,
            endAt = occurrenceStart + duration,
            leadMinutes = source.leadMinutes,
            mode = source.mode,
            recurrence = Recurrence.NONE,
        )
        editState.value = EditState(
            isOpen = true,
            editingId = 0L,
            oneOffSourceId = source.id,
        )
    }

    fun dismissForm() {
        editState.value = EditState()
        updateLocal { it.copy(pendingRecurringTodo = null, pendingOccurrenceStart = 0L) }
    }

    /** 保存表单。 */
    fun submitForm() {
        if (form.validate().isNotEmpty()) return

        val draft = form.toDraft()
        val oneOffSourceId = editState.value.oneOffSourceId
        viewModelScope.launch {
            // 记住这次的提前量，下次新建时作为默认值
            settings.lastLeadMinutes = draft.leadMinutes

            val result = if (draft.id == 0L) {
                coordinator.create(draft)
            } else {
                coordinator.update(draft)
            }
            // 「仅修改这一次」：独立待办保存成功后，才在来源规则上把这一天标记为例外，
            // 避免同一天既出现原规则的场次、又出现这条独立待办
            if (oneOffSourceId != 0L) {
                coordinator.skipOccurrence(oneOffSourceId, draft.startAt)
            }
            if (result != null) {
                // 提示优先级：降级（闹钟式退化为通知）> 时间段冲突 > 保存成功。
                // 降级必须让用户知道，否则他以为会像闹钟一样准时响。
                when {
                    result.degraded -> notify(Notice.ExactAlarmDegraded)
                    result.overlapping.isNotEmpty() && settings.warnOnOverlap ->
                        notify(Notice.OverlapWarning(result.overlapping.map { it.name }.take(3)))
                    else -> notify(Notice.Saved)
                }
            }
            dismissForm()
        }
    }

    // ---------------------------------------------------------------- 删除

    /** 点击删除：按设置决定是否先确认；重复待办先问作用范围。 */
    fun requestDelete(todo: Todo) {
        if (todo.recurrence.isRecurring) {
            updateLocal {
                it.copy(
                    dialog = DialogRequest.RecurrenceScope(todo.id, RecurrenceAction.DELETE),
                    pendingRecurringTodo = todo,
                    pendingOccurrenceStart = todo.startAt,
                )
            }
            return
        }
        if (settings.confirmBeforeDelete) {
            updateLocal { it.copy(dialog = DialogRequest.ConfirmDelete(todo.id)) }
        } else {
            confirmDelete(todo.id)
        }
    }

    /** 用户在确认框里点了「删除」。 */
    fun confirmDelete(todoId: Long) {
        updateLocal { it.copy(dialog = null) }
        viewModelScope.launch {
            coordinator.delete(todoId)
            if (editState.value.editingId == todoId) dismissForm()
        }
    }

    /**
     * 处理重复待办的作用范围选择。
     *
     * * 整个系列：编辑时改规则；删除时删规则（含派生场次）。
     * * 仅本次：编辑时把这一次单独落成一条待办（parentId 指向原规则），
     *   并在原规则对应日期记一条例外；删除时只记一条 SKIPPED 例外。
     */
    fun resolveRecurrenceScope(entireSeries: Boolean) {
        val local = localState.value
        val todo = local.pendingRecurringTodo
        val action = (local.dialog as? DialogRequest.RecurrenceScope)?.action
        updateLocal { it.copy(dialog = null) }
        if (todo == null || action == null) return

        when (action) {
            RecurrenceAction.DELETE -> viewModelScope.launch {
                if (entireSeries) {
                    coordinator.delete(todo.id)
                } else {
                    coordinator.skipOccurrence(todo.id, local.pendingOccurrenceStart)
                    notify(Notice.OccurrenceSkipped)
                }
                updateLocal { it.copy(pendingRecurringTodo = null, pendingOccurrenceStart = 0L) }
            }

            RecurrenceAction.EDIT -> {
                if (entireSeries) {
                    openEditFormDirect(todo)
                } else {
                    // 只预填表单，不落库：用户若点「返回」就什么都不该留下
                    openOneOffEditForm(todo, local.pendingOccurrenceStart)
                    updateLocal { it.copy(pendingRecurringTodo = null, pendingOccurrenceStart = 0L) }
                }
            }
        }
    }

    fun dismissDialog() = updateLocal { it.copy(dialog = null) }

    fun toggleDone(todo: Todo) {
        viewModelScope.launch { coordinator.setDone(todo.id, !todo.isDone) }
    }

    // ---------------------------------------------------------------- 设置

    fun openSettings() = updateLocal { it.copy(screen = Screen.SETTINGS) }

    fun closeSettings() = updateLocal { it.copy(screen = Screen.MAIN) }

    fun changeSetting(change: SettingsChange) {
        when (change) {
            is SettingsChange.UseAlarm -> settings.useAlarmForReminders = change.value
            is SettingsChange.ConfirmDelete -> settings.confirmBeforeDelete = change.value
            is SettingsChange.WarnOverlap -> settings.warnOnOverlap = change.value
            is SettingsChange.NotificationSound -> settings.notificationSound = change.value
        }
        updateLocal { it.copy(settings = readSettings()) }
    }

    private fun readSettings() = SettingsUiState(
        useAlarmForReminders = settings.useAlarmForReminders,
        confirmBeforeDelete = settings.confirmBeforeDelete,
        warnOnOverlap = settings.warnOnOverlap,
        lastLeadMinutes = settings.lastLeadMinutes,
        notificationSound = settings.notificationSound,
    )

    // ---------------------------------------------------------------- 权限与提示

    /** 从系统设置返回后刷新精确闹钟权限状态。 */
    /** 请求允许后台运行（电池优化白名单）。 */
    fun requestIgnoreBatteryOptimization() {
        batteryOptimization?.requestIgnore()
    }

    /** 从系统设置返回后重新读取权限状态。 */
    fun refreshBatteryOptimization() {
        updateLocal {
            it.copy(
                batteryOptimizationIgnored =
                    batteryOptimization?.isIgnoringBatteryOptimizations() ?: true,
            )
        }
    }

    fun refreshPermissions() {
        val granted = canScheduleExactAlarms()
        val previous = localState.value.canScheduleExact
        val notice = if (previous && !granted) {
            Notice.ExactAlarmPermissionRevoked
        } else {
            localState.value.notice
        }
        updateLocal {
            it.copy(
                canScheduleExact = granted,
                // 这几项用户都可能在系统设置里改，回到前台时一起重读
                canPostNotifications = canPostNotifications(),
                fullScreenIntentAllowed = fullScreenIntentAllowed(),
                batteryOptimizationIgnored =
                    batteryOptimization?.isIgnoringBatteryOptimizations()
                        ?: it.batteryOptimizationIgnored,
                notice = notice,
            )
        }
    }

    /**
     * 打开「提醒可靠性检查」页。
     *
     * 记住是从哪进来的：从设置页进来就退回设置页，从关于页进来就退回关于页。
     * 早先无论从哪进都退回主界面，用户想接着看别的设置项就得重新点一遍。
     */
    fun openReminderHealth() = updateLocal {
        it.copy(healthReturnScreen = it.screen, screen = Screen.REMINDER_HEALTH)
    }

    /** 关闭「提醒可靠性检查」页，回到进入它的那一页。 */
    fun closeReminderHealth() = updateLocal { it.copy(screen = it.healthReturnScreen) }

    /** 打开「关于待办提醒」页。 */
    fun openAbout() = updateLocal { it.copy(screen = Screen.ABOUT) }

    fun closeAbout() = updateLocal { it.copy(screen = Screen.SETTINGS) }

    /** 打开「隐私」页。 */
    fun openPrivacy() = updateLocal { it.copy(screen = Screen.PRIVACY) }

    fun closePrivacy() = updateLocal { it.copy(screen = Screen.SETTINGS) }

    fun consumeNotice() = updateLocal { it.copy(notice = null) }

    private fun notify(notice: Notice) = updateLocal { it.copy(notice = notice) }

    private fun updateLocal(transform: (LocalState) -> LocalState) {
        localState.value = transform(localState.value)
    }

    /** 视图状态的内部聚合；放在内部是为了不给外部暴露可变入口。 */
    private data class LocalState(
        val canScheduleExact: Boolean,
        val batteryOptimizationIgnored: Boolean = true,
        val canPostNotifications: Boolean = true,
        val fullScreenIntentAllowed: Boolean = true,
        val needsOemBackgroundGuide: Boolean = false,
        val settings: SettingsUiState,
        val notice: Notice? = null,
        val dialog: DialogRequest? = null,
        /** 默认进入日视图 */
        val tab: NavTab = NavTab.DAY,
        val selectedDate: LocalDate = LocalDate.now(),
        /** 见 [TodoUiState.jumpRequest] */
        val jumpRequest: Long = 0,
        val grouping: ListGrouping = ListGrouping.BY_TIME,
        val screen: Screen = Screen.MAIN,
        /** 进入「提醒可靠性检查」页之前所在的页面，用于返回 */
        val healthReturnScreen: Screen = Screen.MAIN,
        val pendingOccurrenceStart: Long = 0L,
        val pendingRecurringTodo: Todo? = null,
        val version: VersionInfo = VersionInfo(),
    )

    companion object {
        private const val TICK_INTERVAL_MS = 1_000L

        /** 生产装配：从应用依赖图取依赖。 */
        fun factory(application: Application): ViewModelProvider.Factory {
            val todoApp = application as? TodoApp
            checkNotNull(todoApp) { "Application 必须是 TodoApp" }
            val graph: AppGraph = todoApp.requireGraph()
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    TodoViewModel(
                        application = application,
                        repository = graph.repository,
                        coordinator = graph.coordinator,
                        settings = graph.settings,
                        clock = graph.clock,
                        canScheduleExactAlarms = {
                            graph.exactAlarmPermission.canScheduleExactAlarms()
                        },
                        canPostNotifications = {
                            com.example.todolist.sys.ReminderHealth
                                .canPostNotifications(application)
                        },
                        fullScreenIntentAllowed = {
                            com.example.todolist.sys.ReminderHealth
                                .canUseFullScreenIntent(application)
                        },
                        oemBackgroundGuideNeeded = {
                            com.example.todolist.sys.ReminderHealth.needsOemBackgroundGuide()
                        },
                        batteryOptimization = graph.batteryOptimization,
                    ) as T
            }
        }
    }
}
