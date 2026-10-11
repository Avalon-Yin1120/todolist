package com.example.todolist.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.todolist.BuildConfig
import com.example.todolist.domain.ListOrdering
import com.example.todolist.domain.RecurrenceExpander
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

    // 返回键：设置页/表单页按返回应回到主界面，而不是直接退出应用
    BackHandler(enabled = screen != Screen.MAIN) {
        when (screen) {
            Screen.FORM -> viewModel.dismissForm()
            Screen.SETTINGS -> viewModel.closeSettings()
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
                CalendarPager(
                    tab = uiState.tab,
                    selectedDate = uiState.selectedDate,
                    // 翻页只改日期，**不要**用 onSelectDate：那个语义是「点某天跳到日视图」，
                    // 用它会让「翻一页」变成「切到日视图」。
                    onPageDate = { date ->
                        when (uiState.tab) {
                            NavTab.WEEK -> onShiftDate(
                                java.time.temporal.ChronoUnit.DAYS
                                    .between(uiState.selectedDate, date),
                            )
                            NavTab.MONTH -> onShiftMonth(
                                java.time.temporal.ChronoUnit.MONTHS.between(
                                    java.time.YearMonth.from(uiState.selectedDate),
                                    java.time.YearMonth.from(date),
                                ),
                            )
                            else -> onShiftDate(
                                java.time.temporal.ChronoUnit.DAYS
                                    .between(uiState.selectedDate, date),
                            )
                        }
                    },
                    onSwipe = { delta ->
                        shiftDirection = if (delta > 0) 1 else -1
                        when (uiState.tab) {
                            NavTab.DAY -> onShiftDate(delta.toLong())
                            NavTab.WEEK -> onShiftDate(delta * 7L)
                            else -> onShiftMonth(delta.toLong())
                        }
                    },
                    // 「回到今天」保持当前视图：在周视图就回到本周、月视图回到本月。
                    // 这里直接按视图把日期挪到目标那一页（等价于翻页），
                    // 不用 onSelectDate——那个会跳到日视图。
                    onBackToToday = {
                        val today = CalendarLayout.toLocalDate(now, zone)
                        when (uiState.tab) {
                            NavTab.WEEK -> onShiftDate(
                                java.time.temporal.ChronoUnit.DAYS
                                    .between(uiState.selectedDate, today),
                            )
                            NavTab.MONTH -> onShiftMonth(
                                java.time.temporal.ChronoUnit.MONTHS.between(
                                    java.time.YearMonth.from(uiState.selectedDate),
                                    java.time.YearMonth.from(today),
                                ),
                            )
                            else -> onShiftDate(
                                java.time.temporal.ChronoUnit.DAYS
                                    .between(uiState.selectedDate, today),
                            )
                        }
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
    selectedDate: LocalDate,
    /** 翻页后把新日期同步给上层（只改日期，不切视图） */
    onPageDate: (LocalDate) -> Unit,
    onSwipe: (Int) -> Unit,
    onBackToToday: () -> Unit,
    /** 点某一天；这个动作的语义是「跳到日视图」 */
    onPickDate: (LocalDate) -> Unit,
    content: @Composable (LocalDate) -> Unit,
) {
    /**
     * 锚点必须与「一页」对齐，页码与日期才严格互逆
     * （即 pageOf(dateOf(page)) == page，不会出现「设置日期触发滚动、滚动又改日期」的回环）。
     * 周视图对齐到周一、月视图对齐到 1 号；锚点只在切换视图时取一次。
     */
    val anchor = remember(tab) {
        when (tab) {
            NavTab.WEEK -> CalendarLayout.weekStart(selectedDate)
            NavTab.MONTH -> selectedDate.withDayOfMonth(1)
            else -> selectedDate
        }
    }
    val pagerState = rememberPagerState(initialPage = MIDDLE, pageCount = { PAGE_COUNT })

    fun dateOf(page: Int): LocalDate = when (tab) {
        NavTab.WEEK -> anchor.plusWeeks((page - MIDDLE).toLong())
        NavTab.MONTH -> anchor.plusMonths((page - MIDDLE).toLong())
        else -> anchor.plusDays((page - MIDDLE).toLong())
    }

    fun pageOf(date: LocalDate): Int = when (tab) {
        NavTab.WEEK -> MIDDLE + java.time.temporal.ChronoUnit.WEEKS.between(
            anchor,
            CalendarLayout.weekStart(date),
        ).toInt()
        NavTab.MONTH -> MIDDLE + java.time.temporal.ChronoUnit.MONTHS.between(
            java.time.YearMonth.from(anchor),
            java.time.YearMonth.from(date),
        ).toInt()
        else -> MIDDLE + java.time.temporal.ChronoUnit.DAYS.between(anchor, date).toInt()
    }

    // 外部改了日期（点周/月视图里的某天、回到今天）：平滑滚到对应页
    LaunchedEffect(tab, selectedDate) {
        val target = pageOf(selectedDate).coerceIn(0, PAGE_COUNT - 1)
        if (target != pagerState.currentPage) {
            pagerState.animateScrollToPage(target)
        }
    }

    // 用户滑停后把日期同步回上层，顶部标题与倒计时随之更新。
    //
    // loadedDate 用 rememberUpdatedState 跟随最新值：如果直接在 collect 里读 selectedDate，
    // 闭包会捕获 effect 启动时的旧值，于是「前进一页再后退回来」时会误判为没变化、
    // 不再回调，表现为标题停在原处不动。
    val loadedDate by rememberUpdatedState(selectedDate)
    LaunchedEffect(tab, anchor) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            val date = dateOf(page)
            if (date != loadedDate) onPageDate(date)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        PeriodNavigator(
            label = DateFormats.shortDate(dateOf(pagerState.currentPage)),
            onPrevious = { onSwipe(-1) },
            onNext = { onSwipe(1) },
            onBackToToday = onBackToToday,
        )
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .testTag(TAG_SWIPE_AREA),
        ) { page ->
            content(dateOf(page))
        }
    }
}

/** 日历视图上方的翻页控件：左右按钮 + 回到今天。 */
@Composable
private fun PeriodNavigator(
    label: String,
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
        ) { Text("回到今天") }
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

/** 分页总数与锚点页：给足 ±100 年，让用户不可能滑到边界。 */
private const val MIDDLE = 6000
private const val PAGE_COUNT = 12001

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
