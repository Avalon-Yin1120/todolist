package com.example.todolist.ui

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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.todolist.domain.ReminderTime
import com.example.todolist.domain.Todo
import com.example.todolist.domain.TodoStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 列表页入口：把 ViewModel 状态接到纯展示组件上。
 */
@Composable
fun TodoRoute(
    viewModel: TodoViewModel,
    onRequestExactAlarmPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.notice) {
        val notice = uiState.notice ?: return@LaunchedEffect
        val message = when (notice) {
            Notice.ExactAlarmDegraded -> "精确闹钟不可用，已改为普通通知（可能延后）"
            Notice.ExactAlarmPermissionRevoked -> "精确闹钟权限已关闭，后续提醒会退化为普通通知"
            Notice.Saved -> "已保存"
        }
        snackbarHostState.showSnackbar(message)
        viewModel.consumeNotice()
    }

    TodoListScreen(
        uiState = uiState,
        snackbarHostState = snackbarHostState,
        onAdd = viewModel::openCreateForm,
        onToggleDone = viewModel::toggleDone,
        onDelete = viewModel::delete,
        onEdit = viewModel::openEditForm,
        onRequestExactAlarmPermission = onRequestExactAlarmPermission,
        onRequestNotificationPermission = onRequestNotificationPermission,
        form = viewModel.form,
        onDismissForm = viewModel::dismissForm,
        onSubmitForm = viewModel::submitForm,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoListScreen(
    uiState: TodoUiState,
    snackbarHostState: SnackbarHostState,
    onAdd: () -> Unit,
    onToggleDone: (Todo) -> Unit,
    onDelete: (Long) -> Unit,
    onEdit: (Todo) -> Unit,
    onRequestExactAlarmPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    form: DraftFormStateHolder,
    onDismissForm: () -> Unit,
    onSubmitForm: () -> Unit,
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("待办提醒") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAdd,
                modifier = Modifier.testTag(TAG_ADD_FAB),
            ) {
                Icon(Icons.Filled.Add, contentDescription = "新增待办")
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

            val now = remember(uiState.todos) { System.currentTimeMillis() }
            if (uiState.todos.isEmpty()) {
                EmptyState()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(uiState.todos, key = { it.id }) { todo ->
                        TodoCard(
                            todo = todo,
                            now = now,
                            onToggleDone = { onToggleDone(todo) },
                            onEdit = { onEdit(todo) },
                            onDelete = { onDelete(todo.id) },
                        )
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
    }
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
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "精确闹钟权限未开启，精确提醒会退化为普通通知",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRequestExactAlarmPermission) { Text("去开启") }
            TextButton(onClick = onRequestNotificationPermission) { Text("通知权限") }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("还没有待办事项", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "点击右下角 + 添加第一条待办",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TodoCard(
    todo: Todo,
    now: Long,
    onToggleDone: (Todo) -> Unit,
    onEdit: (Todo) -> Unit,
    onDelete: (Todo) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (todo.isDone) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .padding(start = 4.dp, top = 8.dp, end = 4.dp, bottom = 8.dp)
                .testTag(tagForTodo(TAG_TODO_ROW, todo.id)),
            verticalAlignment = Alignment.Top,
        ) {
            Checkbox(
                checked = todo.isDone,
                onCheckedChange = { onToggleDone(todo) },
                // 带 id 的标签：测试可以精确定位「某一行的勾选框」，
                // 而不是依赖列表顺序（列表会随完成状态重排）。
                modifier = Modifier.testTag(tagForTodo(TAG_TODO_CHECKBOX, todo.id)),
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = todo.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (todo.isDone) TextDecoration.LineThrough else null,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${formatTime(todo.startAt)} → ${formatTime(todo.endAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val status = todo.status(now)
                    StatusChip(status.label())
                    if (!todo.isDone) {
                        StatusChip(reminderLabel(todo))
                    }
                }
            }

            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "更多操作")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("编辑") },
                        onClick = {
                            menuExpanded = false
                            onEdit(todo)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(if (todo.isDone) "取消完成" else "标记完成") },
                        onClick = {
                            menuExpanded = false
                            onToggleDone(todo)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("删除") },
                        onClick = {
                            menuExpanded = false
                            onDelete(todo)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusChip(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

private fun TodoStatus.label(): String = when (this) {
    TodoStatus.UPCOMING -> "未开始"
    TodoStatus.RUNNING -> "进行中"
    TodoStatus.ENDED -> "已结束"
    TodoStatus.DONE -> "已完成"
}

private fun reminderLabel(todo: Todo): String = when {
    todo.notifiedAt != null -> "已提醒"
    todo.leadMinutes == 0 -> if (todo.useExactAlarm) "准点·精确" else "准点·通知"
    todo.useExactAlarm -> "提前 ${leadText(todo.leadMinutes)}·精确"
    else -> "提前 ${leadText(todo.leadMinutes)}·通知"
}

private fun leadText(minutes: Int): String = when {
    minutes % 1440 == 0 -> "${minutes / 1440} 天"
    minutes % 60 == 0 -> "${minutes / 60} 小时"
    else -> "$minutes 分钟"
}

private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")

private fun formatTime(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(TIME_FORMATTER)

/** 供测试与预览使用：距离提醒还有多久（用于将来扩展倒计时显示）。 */
fun timeUntilReminder(todo: Todo, now: Long): Long =
    (ReminderTime.remindAt(todo.startAt, todo.leadMinutes) - now).coerceAtLeast(0L)

// 测试标签：仪器化测试依赖这些常量定位控件，改名字时请同步更新 androidTest。
const val TAG_ADD_FAB = "todo_add_fab"
const val TAG_TODO_ROW = "todo_row"
const val TAG_TODO_CHECKBOX = "todo_checkbox"
const val TAG_NAME_FIELD = "todo_name_field"
const val TAG_EXACT_SWITCH = "todo_exact_switch"
const val TAG_SAVE_BUTTON = "todo_save_button"

/** 生成「带待办 id」的测试标签，用于精确定位某一行。 */
fun tagForTodo(base: String, todoId: Long): String = "$base#$todoId"
