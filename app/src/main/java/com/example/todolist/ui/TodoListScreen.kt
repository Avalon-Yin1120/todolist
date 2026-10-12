package com.example.todolist.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import android.util.Log
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.todolist.BuildConfig
import com.example.todolist.domain.ListOrdering
import com.example.todolist.domain.RecurrenceExpander
import com.example.todolist.domain.ReminderMode
import com.example.todolist.domain.ReminderTime
import com.example.todolist.domain.Todo
import java.time.LocalDate
import java.time.ZoneId

/** 列表页入口：把 ViewModel 状态接到纯展示组件上。 */
/** 页面切换动画的时长，允许在调试构建里被放慢以便观测。 */
object TodoRouteAnim {

    /** 默认时长：足够看出方向，又不拖沓。 */
    const val DEFAULT_MS = 280

    /**
     * 调试用覆盖值。
     *
     * 只在调试构建里通过 `adb shell setprop debug.todo.anim_ms 2500` 生效：
     * 截图连拍的间隔约 600ms，正常时长（280ms）根本抓不到过渡帧，
     * 放慢之后才能客观验证动画确实存在。发布构建始终返回默认值。
     */
    val durationMs: Int
        get() {
            if (!BuildConfig.DEBUG) return DEFAULT_MS
            return runCatching {
                System.getProperty(PROP_ANIM_MS)?.toIntOrNull()
            }.getOrNull()?.takeIf { it in 200..10_000 } ?: DEFAULT_MS
        }

    private const val PROP_ANIM_MS = "debug.todo.anim_ms"
}

@Composable
fun TodoRoute(
    viewModel: TodoViewModel,
    onRequestExactAlarmPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
) {
    val animMs = TodoRouteAnim.durationMs
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // 从系统设置（电池优化白名单/精确闹钟）返回时刷新权限状态
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissions()
                viewModel.refreshBatteryOptimization()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    // 时间由界面按需收集（每秒一次），ViewModel 不做常驻轮询
    val now by viewModel.timeFlow.collectAsStateWithLifecycle(initialValue = viewModel.now())
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.notice) {
        val notice = uiState.notice ?: return@LaunchedEffect
        val message = when (notice) {
            Notice.ExactAlarmDegraded -> "精确闹钟不可用，已改为普通通知（可能延后）"
            Notice.ExactAlarmPermissionRevoked -> "精确闹钟权限已关闭，提醒会退化为普通通知"
            Notice.Saved -> "已保存"
            Notice.OccurrenceSkipped -> "已跳过这一次，其它场次不受影响"
            is Notice.OverlapWarning -> buildString {
                append("该时间段已有：")
                append(notice.others.joinToString("、"))
                append("（已按你的设置创建）")
            }
        }
        snackbarHostState.showSnackbar(message)
        viewModel.consumeNotice()
    }

    val zone = viewModel.zone()
    val screen = uiState.screen
    val settings = uiState.settings

    // 记录当前所在页面，供返回键使用
    var lastScreen by remember { mutableStateOf(Screen.MAIN) }
    LaunchedEffect(screen) { lastScreen = screen }

    // 返回键：设置页/表单页/检查页按返回应回到主界面，而不是直接退出应用
    BackHandler(enabled = screen != Screen.MAIN) {
        when (screen) {
            Screen.FORM -> viewModel.dismissForm()
            Screen.SETTINGS -> viewModel.closeSettings()
            Screen.REMINDER_HEALTH -> viewModel.closeReminderHealth()
            Screen.MAIN -> Unit
        }
    }

    AnimatedContent(
        targetState = screen,
        transitionSpec = {
            // 打开子页面：新页面从右侧滑入；返回：新页面从左侧滑入。
            // 两者方向相反，才不会「进也右滑、出也右滑」。
            val opening = targetState != Screen.MAIN
            if (opening) {
                slideInHorizontally(tween(animMs)) { width -> width } togetherWith
                    slideOutHorizontally(tween(animMs)) { width -> -width / 4 } +
                    fadeOut(tween(animMs))
            } else {
                slideInHorizontally(tween(animMs)) { width -> -width / 4 } +
                    fadeIn(tween(animMs)) togetherWith
                    slideOutHorizontally(tween(animMs)) { width -> width }
            }
        },
        label = "screen",
    ) { target ->
        if (target == Screen.FORM) {
            // 整屏表单：字段多，需要完整高度
            TodoFormScreen(
                form = viewModel.form,
                isEditing = uiState.editState.editingId != 0L,
                onBack = viewModel::dismissForm,
                onSubmit = viewModel::submitForm,
            )
            return@AnimatedContent
        }

        if (target == Screen.SETTINGS) {
            SettingsScreen(
                state = settings,
                onChange = viewModel::changeSetting,
                onBack = {
                    viewModel.closeSettings()
                    // 从系统设置返回后重新读一次白名单状态
                    viewModel.refreshBatteryOptimization()
                },
                exactAlarmAllowed = uiState.canScheduleExact,
                onRequestExactAlarmPermission = onRequestExactAlarmPermission,
                batteryOptimizationIgnored = uiState.batteryOptimizationIgnored,
                onRequestIgnoreBatteryOptimization = viewModel::requestIgnoreBatteryOptimization,
                onOpenReminderHealth = viewModel::openReminderHealth,
            )
            return@AnimatedContent
        }

        if (target == Screen.REMINDER_HEALTH) {
            ReminderHealthScreen(
                canPostNotifications = uiState.canPostNotifications,
                canScheduleExact = uiState.canScheduleExact,
                batteryOptimizationIgnored = uiState.batteryOptimizationIgnored,
                fullScreenIntentAllowed = uiState.fullScreenIntentAllowed,
                needsOemBackgroundGuide = uiState.needsOemBackgroundGuide,
                alarmModeInUse = settings.useAlarmForReminders ||
                    uiState.todos.any { it.mode == ReminderMode.ALARM && !it.isDone },
                onRequestNotificationPermission = onRequestNotificationPermission,
                onRequestExactAlarm = onRequestExactAlarmPermission,
                onRequestIgnoreBatteryOptimization = viewModel::requestIgnoreBatteryOptimization,
                onBack = viewModel::closeReminderHealth,
            )
            return@AnimatedContent
        }

        TodoListScreen(
            uiState = uiState,
            now = now,
            zone = zone,
            animMs = animMs,
            snackbarHostState = snackbarHostState,
            onAdd = viewModel::openCreateForm,
            onToggleDone = viewModel::toggleDone,
            onEdit = viewModel::openEditForm,
            onDelete = viewModel::requestDelete,
            onConfirmDelete = viewModel::confirmDelete,
            onResolveScope = viewModel::resolveRecurrenceScope,
            onDismissDialog = viewModel::dismissDialog,
            onSelectTab = viewModel::selectTab,
            onSelectDate = viewModel::selectDate,
            onSelectDateSoft = viewModel::selectDateSoft,
            onSetGrouping = viewModel::setGrouping,
            onOpenSettings = viewModel::openSettings,
            onRequestExactAlarmPermission = onRequestExactAlarmPermission,
            onRequestNotificationPermission = onRequestNotificationPermission,
            onOpenReminderHealth = viewModel::openReminderHealth,
            form = viewModel.form,
            onDismissForm = viewModel::dismissForm,
            onSubmitForm = viewModel::submitForm,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoListScreen(
    uiState: TodoUiState,
    now: Long,
    zone: ZoneId,
    /** 切换动画时长；调试构建可通过系统属性放慢，便于观测与验证 */
    animMs: Int,
    snackbarHostState: SnackbarHostState,
    onAdd: () -> Unit,
    onToggleDone: (Todo) -> Unit,
    onEdit: (Todo) -> Unit,
    onDelete: (Todo) -> Unit,
    onConfirmDelete: (Long) -> Unit,
    onResolveScope: (Boolean) -> Unit,
    onDismissDialog: () -> Unit,
    onSelectTab: (NavTab) -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    /** 只改当前看的日期，不切视图（日历翻页用） */
    onSelectDateSoft: (LocalDate) -> Unit,
    onSetGrouping: (ListGrouping) -> Unit,
    onOpenSettings: () -> Unit,
    onRequestExactAlarmPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    /** 打开「提醒可靠性检查」页（横幅入口） */
    onOpenReminderHealth: () -> Unit,
    form: DraftFormStateHolder,
    onDismissForm: () -> Unit,
    onSubmitForm: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopHeader(
                uiState = uiState,
                now = now,
                zone = zone,
                onOpenSettings = onOpenSettings,
                onAdd = onAdd,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = uiState.tab == NavTab.TODO,
                    onClick = { onSelectTab(NavTab.TODO) },
                    icon = { Text("≡") },
                    label = { Text("待办") },
                    modifier = Modifier.testTag(TAG_TAB_TODO),
                )
                NavigationBarItem(
                    selected = uiState.tab == NavTab.DAY,
                    onClick = { onSelectTab(NavTab.DAY) },
                    icon = { Text("日") },
                    label = { Text("日视图") },
                    modifier = Modifier.testTag(TAG_TAB_DAY),
                )
                NavigationBarItem(
                    selected = uiState.tab == NavTab.WEEK,
                    onClick = { onSelectTab(NavTab.WEEK) },
                    icon = { Text("周") },
                    label = { Text("周视图") },
                    modifier = Modifier.testTag(TAG_TAB_WEEK),
                )
                NavigationBarItem(
                    selected = uiState.tab == NavTab.MONTH,
                    onClick = { onSelectTab(NavTab.MONTH) },
                    icon = { Text("月") },
                    label = { Text("月视图") },
                    modifier = Modifier.testTag(TAG_TAB_MONTH),
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            PermissionBanner(
                canScheduleExact = uiState.canScheduleExact,
                canPostNotifications = uiState.canPostNotifications,
                batteryOptimizationIgnored = uiState.batteryOptimizationIgnored,
                onOpenReminderHealth = onOpenReminderHealth,
            )

            if (uiState.tab == NavTab.TODO) {
                TodoOverview(
                    uiState = uiState,
                    now = now,
                    zone = zone,
                    onToggleDone = onToggleDone,
                    onEdit = onEdit,
                    onDelete = onDelete,
                    onSetGrouping = onSetGrouping,
                )
            } else {
                CalendarPager(
                    tab = uiState.tab,
                    // selectedDate 只作为跳转目标（进入视图那一刻的日期）
                    selectedDate = uiState.selectedDate,
                    // 只有用户主动跳转才驱动 pager，见 CalendarPager 的说明
                    jumpRequest = uiState.jumpRequest,
                    today = CalendarLayout.toLocalDate(now, zone),
                    // 显示的那一期变了：只更新上层日期用于倒计时与切回视图，不反向驱动 pager
                    onDisplay = { date ->
                        if (date != uiState.selectedDate) onSelectDateSoft(date)
                    },
                    onPickDate = onSelectDate,
                ) { periodDate ->
                    when (uiState.tab) {
                        NavTab.DAY -> DailyView(
                            todos = uiState.todos,
                            date = periodDate,
                            now = now,
                            zone = zone,
                            onToggleDone = onToggleDone,
                            onEdit = onEdit,
                            onDelete = onDelete,
                        )
                        NavTab.WEEK -> WeeklyView(
                            todos = uiState.todos,
                            anchor = periodDate,
                            zone = zone,
                            onPickDate = onSelectDate,
                        )
                        else -> MonthlyView(
                            todos = uiState.todos,
                            anchor = periodDate,
                            zone = zone,
                            today = CalendarLayout.toLocalDate(now, zone),
                            onPickDate = onSelectDate,
                        )
                    }
                }
            }
        }

        if (uiState.editState.isOpen) {
            // 表单已改为独立整屏页面（见上面的 Screen.FORM 分支），这里不再弹对话框
        }

        when (val dialog = uiState.dialog) {
            is DialogRequest.ConfirmDelete -> ConfirmDeleteDialog(
                todoName = uiState.todos.firstOrNull { it.id == dialog.todoId }?.name.orEmpty(),
                onConfirm = { onConfirmDelete(dialog.todoId) },
                onDismiss = onDismissDialog,
            )

            is DialogRequest.RecurrenceScope -> RecurrenceScopeDialog(
                action = dialog.action,
                onEntireSeries = { onResolveScope(true) },
                onThisOccurrence = { onResolveScope(false) },
                onDismiss = onDismissDialog,
            )

            null -> Unit
        }
    }
}

/**
 * 顶部：标题居中，左右各一个对称的图标按钮（设置 / 新增）；
 * 标题下方一行显示当前视图对应的日期与倒计时信息。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopHeader(
    uiState: TodoUiState,
    now: Long,
    zone: ZoneId,
    onOpenSettings: () -> Unit,
    onAdd: () -> Unit,
) {
    val pending = uiState.todos.filterNot { it.isDone }
    val overdue = pending.filter { ReminderTime.remindAt(it.startAt, it.leadMinutes) <= now }

    Column {
        CenterAlignedTopAppBar(
            title = {
                Text(
                    text = "待办提醒",
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.testTag(TAG_TITLE),
                )
            },
            navigationIcon = {
                // 左上：设置
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.testTag(TAG_SETTINGS_BUTTON),
                ) {
                    Icon(Icons.Filled.Settings, contentDescription = "设置")
                }
            },
            actions = {
                // 右上：新增。与设置左右对称，且不再悬浮遮挡列表内容
                IconButton(
                    onClick = onAdd,
                    modifier = Modifier.testTag(TAG_ADD_FAB),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "新增待办")
                }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
        )

        // 当前时间范围：明确告诉用户「现在看的是哪一天 / 哪一周 / 哪一月」
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = periodLabel(uiState, zone),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag(TAG_PERIOD_LABEL),
                )
                Text(
                    text = countdownText(pending, overdue, now, zone),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag(TAG_COUNTDOWN),
                )
            }
        }
    }
}

/** 当前视图的时间范围文案。 */
private fun periodLabel(uiState: TodoUiState, zone: ZoneId): String {
    val date = uiState.selectedDate
    return when (uiState.tab) {
        NavTab.TODO -> "全部待办"
        NavTab.DAY -> DateFormats.fullDate(date)
        NavTab.WEEK -> {
            val start = CalendarLayout.weekStart(date)
            val end = start.plusDays(6)
            DateFormats.dateRange(start, end)
        }
        NavTab.MONTH -> DateFormats.monthLabel(date)
    }
}

/**
 * 倒计时文案。
 *
 * 优先显示「已到提醒时间但还没处理」的数量——那是用户最需要知道的；
 * 否则显示下一次待办还有多久。
 */
private fun countdownText(
    pending: List<Todo>,
    overdue: List<Todo>,
    now: Long,
    zone: ZoneId,
): String {
    if (pending.isEmpty()) return "暂无待办"

    if (overdue.isNotEmpty()) {
        val nearest = overdue.minByOrNull { it.startAt }!!
        return "有 ${overdue.size} 项待处理 · 最近：${nearest.name}"
    }

    val next = pending
        .map { it to RecurrenceExpander.nextOccurrenceAfter(it, now, zone) }
        .filter { it.second != null }
        .minByOrNull { it.second!! }

    if (next == null || next.second == null) return "共 ${pending.size} 项待办"
    val target = next.second!!
    return "距离下次待办还有 ${formatCountdown(target - now)} · ${next.first.name}"
}

private fun formatCountdown(millis: Long): String {
    if (millis <= 0) return "0 分钟"
    val totalMinutes = millis / 60_000
    val days = totalMinutes / (24 * 60)
    val hours = (totalMinutes % (24 * 60)) / 60
    val minutes = totalMinutes % 60
    return when {
        days > 0 -> "$days 天 $hours 小时"
        hours > 0 -> "$hours 小时 $minutes 分钟"
        else -> "$minutes 分钟"
    }
}

/** 总列表：可切换排序方式。 */
@Composable
private fun TodoOverview(
    uiState: TodoUiState,
    now: Long,
    zone: ZoneId,
    onToggleDone: (Todo) -> Unit,
    onEdit: (Todo) -> Unit,
    onDelete: (Todo) -> Unit,
    onSetGrouping: (ListGrouping) -> Unit,
) {
    val grouped: List<Todo> = when (uiState.grouping) {
        ListGrouping.BY_TIME -> ListOrdering.byTime(uiState.todos)
        ListGrouping.BY_DONE -> ListOrdering.byDone(uiState.todos)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("排序：", style = MaterialTheme.typography.labelMedium)
            TextButton(
                onClick = { onSetGrouping(ListGrouping.BY_TIME) },
                modifier = Modifier.testTag(TAG_SORT_BY_TIME),
            ) { Text(if (uiState.grouping == ListGrouping.BY_TIME) "按时间 ✓" else "按时间") }
            TextButton(
                onClick = { onSetGrouping(ListGrouping.BY_DONE) },
                modifier = Modifier.testTag(TAG_SORT_BY_DONE),
            ) { Text(if (uiState.grouping == ListGrouping.BY_DONE) "按状态 ✓" else "按状态") }
        }

        if (grouped.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("还没有待办事项", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "点击右上角 + 添加第一条待办",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(grouped, key = { "${it.id}-${it.startAt}" }) { todo ->
                    TodoRowCard(
                        todo = todo,
                        now = now,
                        zone = zone,
                        onToggleDone = onToggleDone,
                        onEdit = onEdit,
                        onDelete = onDelete,
                    )
                }
            }
        }
    }
}


/**
 * 日/周/月视图的横向分页容器。
 *
 * 用 [HorizontalPager] 而不是自定义手势，因为它天然提供「跟手」体验：
 * 手指滑到一半停住时内容就停在半途，松手才吸附到最近的一页；
 * 左右按钮也走同一套 [PagerState.animateScrollToPage]，动效一致。
 * 之前的实现只有「累积位移超过阈值才跳一页」，所以会滑不动、也没有中间态。
 *
 * 页码与日期的映射基于一个固定锚点日期：[MIDDLE] 对应锚点，
 * 于是任意页都能独立算出它该显示哪一天，滑动过程中每页内容都正确。
 */
@Composable
private fun CalendarPager(
    tab: NavTab,
    /** 上层要求跳转到的日期（点某天 / 切视图那一刻的日期） */
    selectedDate: LocalDate,
    /**
     * 用户主动跳转的序号，见 [TodoUiState.jumpRequest]。
     *
     * 只有它变化（或 [tab] 变化）才把 pager 滚过去。**不能**直接拿 [selectedDate] 当键：
     * pager 翻页后会把当前显示的那一期通过 [onDisplay] 写回上层，若上层再拿它驱动 pager，
     * 就会和「滚到今天」的动画互相打断，最终停在锚点页 2000-01-01。
     */
    jumpRequest: Long,
    /** 今天，用于「回到今天/本周/本月」 */
    today: LocalDate,
    /** 当前显示的这一期变了（滑动或翻页后）通知上层，用于更新倒计时等 */
    onDisplay: (LocalDate) -> Unit,
    /** 点某一天；这个动作的语义是「跳到日视图」 */
    onPickDate: (LocalDate) -> Unit,
    content: @Composable (LocalDate) -> Unit,
) {
    val scope = rememberCoroutineScope()
    // 初始页就是「当前日期」在本粒度下的那一页，首帧即正确（不会先闪一下锚点页再滚）
    val pagerState = rememberPagerState(
        initialPage = pageOfDate(tab, selectedDate),
        pageCount = { PAGE_COUNT },
    )

    // 当前显示的这一期，直接由 pager 的当前位置推导。
    // 不在函数里开任何可变的「锚点」状态，避免映射随会话漂移。
    val shownDate = dateOfPage(tab, pagerState.currentPage)

    // 下面的效应只以 (tab, jumpRequest) 为键，会长期运行。
    // LaunchedEffect 不会看到重建之后的新 lambda，所以回调必须用 rememberUpdatedState 取最新值，
    // 否则 collect 里用的还是首次组合时那个 onDisplay：
    // 它内部闭包着 uiState.selectedDate，会用**过期的日期**做「是否变化」的判断，
    // 结果是上报被静默丢弃、顶部标题停在上一期（实测：连点下一个再上一个，标题回到不去）。
    val currentOnDisplay by rememberUpdatedState(onDisplay)

    // 页码预算自检：只在调试构建里跑。
    // 之前 MIDDLE = 6000 时，日视图只够覆盖到 2016 年，「回到今天」会被夹到末页，
    // 而这种错在编译期完全看不出来。把断言放在这里，以后谁再改常量都会立刻炸出来。
    if (BuildConfig.DEBUG) {
        LaunchedEffect(tab, today, selectedDate) {
            checkPagingBudget(
                tab,
                today,
                selectedDate,
                today.minusYears(50),
                today.plusYears(50),
            )
        }
    }

    /**
     * 带动画地翻到某一页：左右按钮与「回到今天/本周/本月」用。
     *
     * * 以 [PagerState.targetPage]（**动画目标页**）为准判断是否已经在道上。
     *   用 [PagerState.currentPage]（离吸附点最近的那一页）判断会吞点击：
     *   动画还没结束时 currentPage 仍是旧页，用户连点两次「下一个」会算出同一个目标，
     *   第二次点击就被当成"已经在目标页"丢弃。以目标页为基准则连点会依次累加。
     *   （Compose Foundation 1.7.6 里这个属性叫 targetPage，不是 currentTargetPage；
     *   程序化滚动一开始就写入它，滚动结束后复位为 currentPage。）
     * * 不需要 Mutex：animateScrollToPage 本身会取消上一次动画，后一次点击天然覆盖前一次。
     */
    fun scrollToPage(target: Int) {
        // 越界只可能是预算算错了（见 MIDDLE / PAGE_COUNT 的说明），夹一下保证不崩
        val clamped = target.coerceIn(0, PAGE_COUNT - 1)
        if (clamped == pagerState.targetPage) return
        scope.launch {
            try {
                pagerState.animateScrollToPage(clamped)
            } catch (e: CancellationException) {
                throw e // 被新的翻页动画取消是正常路径，不能吞掉
            } catch (e: Throwable) {
                Log.w("TodoListScreen", "翻页动画失败：page=$clamped", e)
            }
        }
    }

    /**
     * 「切视图 / 上层要求跳转」与「上报当前显示的那一期」必须由同一个效应按固定顺序完成。
     *
     * 页码是「第几期」，含义随粒度变化：同一个页码在日视图是某一天、在周视图是某一周。
     * 所以切视图时**必须先把页码对齐到新粒度**，再开始上报；一旦拆成两个效应，
     * 切视图后的第一帧就会拿旧页码按新粒度换算（例如日视图的第 41396 页按周算是公元 2400 年），
     * 这个垃圾日期会被当成「当前日期」写回上层、污染 selectedDate
     * （实测：切到月视图再切回日视图会停在 1 号；更早还表现为打开就停在 2000-01-01）。
     *
     * 这里用瞬时跳转 [PagerState.scrollToPage] 而不是动画：视图切换本来就不该有滑动动画，
     * 也避免与用户正在进行的翻页动画互相打断（那曾导致「连点下一个被吞」）。
     */
    LaunchedEffect(tab, jumpRequest) {
        // 1) 先对齐到本视图下「上层给的那个日期」所在的那一期
        pagerState.scrollToPage(pageOfDate(tab, selectedDate))
        // 2) 之后页码每次变化都上报（滑动、翻页按钮、回到今天都经过这里）
        snapshotFlow { pagerState.currentPage }
            .collect { page -> currentOnDisplay(dateOfPage(tab, page)) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        PeriodNavigator(
            label = when (tab) {
                NavTab.WEEK -> DateFormats.dateRange(
                    CalendarLayout.weekStart(shownDate),
                    CalendarLayout.weekStart(shownDate).plusDays(6),
                )
                NavTab.MONTH -> DateFormats.monthLabel(shownDate)
                else -> DateFormats.fullDate(shownDate)
            },
            backLabel = when (tab) {
                NavTab.WEEK -> "回到本周"
                NavTab.MONTH -> "回到本月"
                else -> "回到今天"
            },
            // 用页码加减，不用「当前日期 ± 1 期」再反算页码：
            // 后者多一次换算，onClick 的 lambda 还会缓存组合时的日期。
            //
            // 基准必须是 targetPage（动画的目标页）而不是 currentPage：
            // currentPage 是"离吸附点最近的一页"，动画途中它还没变，
            // 连点两次「下一个」会算出同一个目标，第二次点击被当成重复而丢弃。
            onPrevious = { scrollToPage(pagerState.targetPage - 1) },
            onNext = { scrollToPage(pagerState.targetPage + 1) },
            // 「回到今天/本周/本月」= 滚到今天所在那一页，**并且**把上层的「当前日期」落回今天。
            // 只滚页的话，上层日期会停在「翻到的那一期的代表日」（月视图就是当月 1 号），
            // 再切回日视图就会落到 1 号而不是今天（实测：月视图→11 月→回到本月→日视图 = 10 月 1 日）。
            onBackToToday = {
                scope.launch {
                    try {
                        pagerState.animateScrollToPage(pageOfDate(tab, today))
                    } catch (e: CancellationException) {
                        throw e // 用户中途接管（滑动/再翻页）就不强行重置了
                    } catch (e: Throwable) {
                        Log.w("TodoListScreen", "回到今天失败", e)
                    }
                    currentOnDisplay(today)
                }
            },
        )
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .testTag(TAG_SWIPE_AREA),
        ) { page ->
            content(dateOfPage(tab, page))
        }
    }
}

// ---------------------------------------------------------------------------
// 页 <-> 日期 的换算：绝对基准，纯函数
//
// 之前用「可变锚点 + 页码偏移」做两次换算，锚点还会在翻页时被改写，
// 于是映射随会话漂移，出现「左滑日期不变」「好像翻到头了」这类问题。
// 现在页 0 固定对应 EPOCH（2000-01-01，恰好是周六），
// 页与日期互为反函数，不受任何会话状态影响：
//
//     dateOfPage(tab, pagerState.currentPage)   当前显示哪一期
//     pageOfDate(tab, 某个日期)                  该跳到第几页
//
// 三个粒度分别用 ChronoUnit 的 DAYS / WEEKS / MONTHS 度量；
// 周对齐到周一、月对齐到 1 号，因此 pageOfDate 对同一期内的任意一天返回同一个页码。
// ---------------------------------------------------------------------------

/** 页 0 对应的日期。选 2000-01-01 只是为了远离实际使用范围，避免触到两端。 */
private val PAGE_EPOCH: LocalDate = LocalDate.of(2000, 1, 1)

/**
 * 周视图的页 0 基准：必须是**周一**。
 *
 * 2000-01-01 是周六，直接拿它当基准会让「第 N 週」落在周六上，
 * 与「周对齐到周一」的自洽性冲突，导致 pageOfDate(dateOfPage(p)) != p，
 * 表现为翻页方向漂移、有时像是翻不动。2000-01-03 才是那一周的周一。
 */
private val WEEK_EPOCH: LocalDate = LocalDate.of(2000, 1, 3)

/** 页与日期换算用到的粒度。 */
private fun chronoUnitFor(tab: NavTab): java.time.temporal.ChronoUnit = when (tab) {
    NavTab.WEEK -> java.time.temporal.ChronoUnit.WEEKS
    NavTab.MONTH -> java.time.temporal.ChronoUnit.MONTHS
    else -> java.time.temporal.ChronoUnit.DAYS
}

/** 把日期对齐到它所属那一期的起点：周=周一，月=1 号，日=本身。 */
private fun periodStartOf(tab: NavTab, date: LocalDate): LocalDate = when (tab) {
    NavTab.WEEK -> CalendarLayout.weekStart(date)
    NavTab.MONTH -> date.withDayOfMonth(1)
    else -> date
}

/** 第 [page] 页代表哪一天。 */
internal fun dateOfPage(tab: NavTab, page: Int): LocalDate {
    val steps = (page - MIDDLE).toLong()
    return when (tab) {
        NavTab.WEEK -> WEEK_EPOCH.plusWeeks(steps)
        NavTab.MONTH -> PAGE_EPOCH.plusMonths(steps)
        else -> PAGE_EPOCH.plusDays(steps)
    }
}

/** [date] 落在第几页。 */
internal fun pageOfDate(tab: NavTab, date: LocalDate): Int {
    val aligned = periodStartOf(tab, date)
    return when (tab) {
        // 周用周一基准，月用 1 号基准，日直接按天数差
        NavTab.WEEK -> MIDDLE + java.time.temporal.ChronoUnit.WEEKS.between(WEEK_EPOCH, aligned).toInt()
        else -> MIDDLE + chronoUnitFor(tab).between(PAGE_EPOCH, aligned).toInt()
    }
}

/**
 * 边界自检：这些日期都必须落在合法页号 `0 until PAGE_COUNT` 内。
 *
 * 越界意味着翻页会被 [CalendarPager] 里的 `coerceIn` 夹断——用户看到的正是
 * 「滑到头了、回不到今天」。这类错在编译期没有任何提示，只能靠断言兜住：
 * 调试构建里由 [CalendarPager] 调用，单元测试里直接调用（见 CalendarPagingTest）。
 *
 * @throws IllegalStateException 任一日期越界时抛出，消息里带上越界页码和修法。
 */
internal fun checkPagingBudget(tab: NavTab, vararg dates: LocalDate) {
    dates.forEach { date ->
        val page = pageOfDate(tab, date)
        check(page in 0 until PAGE_COUNT) {
            "$tab 视图的翻页预算不够：$date 在第 $page 页，合法范围 0..${PAGE_COUNT - 1}；" +
                "请调大 MIDDLE / PAGE_COUNT（预算按日视图的天数计算）"
        }
    }
}

/** 日历视图上方的翻页控件：左右按钮 + 回到今天。 */
@Composable
private fun PeriodNavigator(
    label: String,
    /** 「回到今天/本周/本月」按钮的文案，按当前视图给出 */
    backLabel: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onBackToToday: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(
            onClick = onPrevious,
            modifier = Modifier.testTag(TAG_PERIOD_PREV),
        ) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上一个")
        }
        TextButton(
            onClick = onBackToToday,
            modifier = Modifier.testTag(TAG_BACK_TO_TODAY),
        ) { Text(backLabel) }
        IconButton(
            onClick = onNext,
            modifier = Modifier.testTag(TAG_PERIOD_NEXT),
        ) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下一个")
        }
    }
}

/** 删除前的二次确认。 */
@Composable
private fun ConfirmDeleteDialog(
    todoName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除待办") },
        text = { Text("确定要删除「${todoName.ifBlank { "这条待办" }}」吗？删除后无法恢复。") },
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.testTag(TAG_CONFIRM_DELETE)) {
                Text("删除")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.testTag(TAG_CANCEL_DELETE)) {
                Text("取消")
            }
        },
    )
}

/** 重复待办的作用范围选择。 */
@Composable
private fun RecurrenceScopeDialog(
    action: RecurrenceAction,
    onEntireSeries: () -> Unit,
    onThisOccurrence: () -> Unit,
    onDismiss: () -> Unit,
) {
    val verb = if (action == RecurrenceAction.DELETE) "删除" else "修改"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("这是重复待办") },
        text = { Text("你要$verb 这一次，还是$verb 整个重复系列？") },
        confirmButton = {
            TextButton(
                onClick = onThisOccurrence,
                modifier = Modifier.testTag(TAG_SCOPE_THIS),
            ) { Text("仅这一次") }
        },
        dismissButton = {
            TextButton(
                onClick = onEntireSeries,
                modifier = Modifier.testTag(TAG_SCOPE_ALL),
            ) { Text("整个系列") }
        },
    )
}

@Composable
private fun PermissionBanner(
    canScheduleExact: Boolean,
    canPostNotifications: Boolean,
    batteryOptimizationIgnored: Boolean,
    onOpenReminderHealth: () -> Unit,
) {
    val missing = listOf(canScheduleExact, canPostNotifications, batteryOptimizationIgnored)
        .count { !it }
    if (missing == 0) return
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TAG_HEALTH_BANNER),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "有 $missing 项提醒设置未开启，可能到点不提醒",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = onOpenReminderHealth,
                modifier = Modifier.testTag(TAG_HEALTH_BANNER_OPEN),
            ) { Text("去检查") }
        }
    }
}

// ---------------------------------------------------------------------------
// 分页预算
//
// 预算必须**按最细的粒度（日视图）**来定，因为三种视图共用同一套页号空间：
//
//   * 日视图：1 页 = 1 天，[MIDDLE] 前后各 [MIDDLE] 天；
//   * 周视图：1 页 = 1 周，覆盖范围是日视图的 7 倍；
//   * 月视图：1 页 = 1 月，覆盖范围约是日视图的 30 倍。
//
// 之前 MIDDLE = 6000，锚点是 2000-01-01，于是日视图只覆盖到 1983-08 ~ 2016-06，
// 「回到今天」（2026 年）会算出 15000 多页、被 coerceIn 夹到末页 12000，
// 表现就是日视图一打开往回滑几下就「到头了」，怎样都回不到今天。
// 周/月视图当时碰巧还够用，但边界迟早也会撞上，所以三个视图一起修。
//
// 现在按天给 ±40000 天（≈ ±109 年）：日视图 1983 年之前 / 2135 年之后才到头，
// 周/月视图的实际覆盖范围更是各自外扩到 ±769 年 / ±3333 年。
// HorizontalPager 是懒加载的，页数多不影响内存和首帧。
// ---------------------------------------------------------------------------

/** 锚点页：[PAGE_EPOCH] / [WEEK_EPOCH] 对应的页号。 */
internal const val MIDDLE = 40_000

/** 页总数，必须是奇数，保证 [MIDDLE] 两侧对称。 */
internal const val PAGE_COUNT = 80_001

// 测试标签：仪器化测试依赖这些常量定位控件，改名时请同步更新 androidTest。
const val TAG_ADD_FAB = "todo_add_fab"
const val TAG_TODO_ROW = "todo_row"
const val TAG_TODO_CHECKBOX = "todo_checkbox"
const val TAG_NAME_FIELD = "todo_name_field"
const val TAG_LEAD_FIELD = "todo_lead_field"
const val TAG_REMINDER_PREVIEW = "todo_reminder_preview"
const val TAG_QUICK_LEAD_PREFIX = "quick_lead_"
const val TAG_SAVE_BUTTON = "todo_save_button"
const val TAG_TITLE = "app_title"
const val TAG_PERIOD_LABEL = "period_label"
const val TAG_COUNTDOWN = "countdown_text"
const val TAG_TAB_TODO = "tab_todo"
const val TAG_TAB_DAY = "tab_day"
const val TAG_TAB_WEEK = "tab_week"
const val TAG_TAB_MONTH = "tab_month"
const val TAG_SWIPE_AREA = "swipe_area"
const val TAG_PERIOD_PREV = "period_prev"
const val TAG_PERIOD_NEXT = "period_next"
const val TAG_BACK_TO_TODAY = "back_to_today"
const val TAG_SORT_BY_TIME = "sort_by_time"
const val TAG_SORT_BY_DONE = "sort_by_done"
const val TAG_CONFIRM_DELETE = "confirm_delete"
const val TAG_CANCEL_DELETE = "cancel_delete"
const val TAG_SCOPE_THIS = "scope_this"
const val TAG_SCOPE_ALL = "scope_all"

/** 提醒方式与周期的测试标签。 */
const val TAG_MODE_NOTIFICATION = "mode_notification"
const val TAG_MODE_ALARM = "mode_alarm"
const val TAG_RECURRENCE_PREFIX = "recurrence_"
const val TAG_WEEKDAY_PREFIX = "weekday_"
const val TAG_MONTHDAY_PREFIX = "monthday_"
const val TAG_FORM_SCREEN = "todo_form_screen"
const val TAG_FORM_BACK = "todo_form_back"
const val TAG_WEEKDAYS_SUMMARY = "weekdays_summary"
const val TAG_MONTHDAYS_SUMMARY = "monthdays_summary"
const val TAG_RECURRENCE_START = "recurrence_start"
const val TAG_RECURRENCE_COUNT = "recurrence_count"
const val TAG_RECURRENCE_END = "recurrence_end"
const val TAG_RECURRENCE_SYNC = "recurrence_sync"
const val TAG_RECURRENCE_SUMMARY = "recurrence_summary"
const val TAG_SETTINGS_BUTTON = "settings_button"
const val TAG_SETTINGS_SCREEN = "settings_screen"
const val TAG_SETTINGS_BACK = "settings_back"

/** 生成「带 id」的测试标签，用于精确定位某一行。 */
fun tagForTodo(base: String, todoId: Long): String = "$base#$todoId"

/** 保留旧标签常量以免既有测试引用失效。 */
const val TAG_EXACT_SWITCH = "todo_exact_switch"
