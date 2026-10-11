package com.example.todolist.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.todolist.domain.ListOrdering
import com.example.todolist.domain.RecurrenceExpander
import com.example.todolist.domain.ReminderTime
import com.example.todolist.domain.Todo
import java.time.LocalDate
import java.time.ZoneId

/** 列表页入口：把 ViewModel 状态接到纯展示组件上。 */
@Composable
fun TodoRoute(
    viewModel: TodoViewModel,
    onRequestExactAlarmPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
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

    AnimatedContent(
        targetState = screen,
        transitionSpec = {
            if (targetState == Screen.SETTINGS) {
                slideInHorizontally(tween(260)) { it / 3 } + fadeIn(tween(260)) togetherWith
                    fadeOut(tween(180))
            } else {
                fadeIn(tween(220)) togetherWith slideOutHorizontally(tween(260)) { it / 3 } + fadeOut(tween(180))
            }
        },
        label = "screen",
    ) { target ->
        if (target == Screen.SETTINGS) {
            SettingsScreen(
                state = settings,
                onChange = viewModel::changeSetting,
                onBack = viewModel::closeSettings,
                exactAlarmAllowed = uiState.canScheduleExact,
                onRequestExactAlarmPermission = onRequestExactAlarmPermission,
            )
            return@AnimatedContent
        }

        TodoListScreen(
            uiState = uiState,
            now = now,
            zone = zone,
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
            onShiftDate = viewModel::shiftDate,
            onShiftMonth = viewModel::shiftMonth,
            onSetGrouping = viewModel::setGrouping,
            onOpenSettings = viewModel::openSettings,
            onRequestExactAlarmPermission = onRequestExactAlarmPermission,
            onRequestNotificationPermission = onRequestNotificationPermission,
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
    onShiftDate: (Long) -> Unit,
    onShiftMonth: (Long) -> Unit,
    onSetGrouping: (ListGrouping) -> Unit,
    onOpenSettings: () -> Unit,
    onRequestExactAlarmPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    form: DraftFormStateHolder,
    onDismissForm: () -> Unit,
    onSubmitForm: () -> Unit,
) {
    // 滑动方向：+1 表示往「后一天/后一周/后一月」翻，用于决定切换动画方向
    var shiftDirection by remember { mutableIntStateOf(0) }

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
                onRequestExactAlarmPermission = onRequestExactAlarmPermission,
                onRequestNotificationPermission = onRequestNotificationPermission,
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
                // 日历类视图：带左右滑动 + 切换动画
                val periodKey: Any = when (uiState.tab) {
                    NavTab.DAY -> uiState.selectedDate
                    NavTab.WEEK -> CalendarLayout.weekStart(uiState.selectedDate)
                    NavTab.MONTH -> java.time.YearMonth.from(uiState.selectedDate)
                    NavTab.TODO -> uiState.selectedDate
                }

                SwipeablePager(
                    onSwipeLeft = {
                        shiftDirection = 1
                        when (uiState.tab) {
                            NavTab.DAY -> onShiftDate(1)
                            NavTab.WEEK -> onShiftDate(7)
                            else -> onShiftMonth(1)
                        }
                    },
                    onSwipeRight = {
                        shiftDirection = -1
                        when (uiState.tab) {
                            NavTab.DAY -> onShiftDate(-1)
                            NavTab.WEEK -> onShiftDate(-7)
                            else -> onShiftMonth(-1)
                        }
                    },
                ) {
                    AnimatedContent(
                        targetState = periodKey,
                        transitionSpec = {
                            val direction = if (shiftDirection >= 0) 1 else -1
                            (
                                slideInHorizontally(tween(280)) { width -> direction * width } +
                                    fadeIn(tween(200))
                                ) togetherWith (
                                slideOutHorizontally(tween(280)) { width -> -direction * width } +
                                    fadeOut(tween(200))
                                )
                        },
                        label = "period",
                    ) { _ ->
                        when (uiState.tab) {
                            NavTab.DAY -> DailyView(
                                todos = uiState.todos,
                                date = uiState.selectedDate,
                                now = now,
                                zone = zone,
                                onToggleDone = onToggleDone,
                                onEdit = onEdit,
                                onDelete = onDelete,
                            )
                            NavTab.WEEK -> WeeklyView(
                                todos = uiState.todos,
                                anchor = uiState.selectedDate,
                                zone = zone,
                                onPickDate = onSelectDate,
                            )
                            else -> MonthlyView(
                                todos = uiState.todos,
                                anchor = uiState.selectedDate,
                                zone = zone,
                                today = CalendarLayout.toLocalDate(now, zone),
                                onPickDate = onSelectDate,
                            )
                        }
                    }
                }
            }
        }

        if (uiState.editState.isOpen) {
            TodoEditSheet(
                form = form,
                isEditing = uiState.editState.editingId != 0L,
                onDismiss = onDismissForm,
                onSubmit = onSubmitForm,
            )
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

/** 横向滑动切换日期/周/月。 */
@Composable
private fun SwipeablePager(
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                var accumulated = 0f
                detectHorizontalDragGestures(
                    onDragEnd = {
                        when {
                            accumulated < -SWIPE_THRESHOLD -> onSwipeLeft()
                            accumulated > SWIPE_THRESHOLD -> onSwipeRight()
                        }
                        accumulated = 0f
                    },
                    onHorizontalDrag = { _, delta -> accumulated += delta },
                )
            }
            .testTag(TAG_SWIPE_AREA),
    ) {
        content()
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
    onRequestExactAlarmPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
) {
    if (canScheduleExact) return
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "精确闹钟权限未开启，闹钟式提醒会退化为普通通知",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRequestExactAlarmPermission) { Text("去开启") }
            TextButton(onClick = onRequestNotificationPermission) { Text("通知权限") }
        }
    }
}

private const val SWIPE_THRESHOLD = 120f

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
const val TAG_SETTINGS_BUTTON = "settings_button"
const val TAG_SETTINGS_SCREEN = "settings_screen"
const val TAG_SETTINGS_BACK = "settings_back"

/** 生成「带 id」的测试标签，用于精确定位某一行。 */
fun tagForTodo(base: String, todoId: Long): String = "$base#$todoId"

/** 保留旧标签常量以免既有测试引用失效。 */
const val TAG_EXACT_SWITCH = "todo_exact_switch"
