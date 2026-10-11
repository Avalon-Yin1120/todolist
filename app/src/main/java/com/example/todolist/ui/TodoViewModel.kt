package com.example.todolist.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.todolist.AppGraph
import com.example.todolist.TodoApp
import com.example.todolist.requireGraph
import com.example.todolist.data.TodoDraft
import com.example.todolist.data.TodoRepository
import com.example.todolist.data.toTodo
import com.example.todolist.domain.Clock
import com.example.todolist.domain.FormError
import com.example.todolist.domain.Todo
import com.example.todolist.reminder.SchedulingCoordinator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 编辑表单是否打开。 */
data class EditState(
    val isOpen: Boolean = false,
    val editingId: Long = 0L,
)

/** 一次性提示（降级、权限变化、保存成功）。 */
sealed interface Notice {
    /** 精确闹钟不可用，已自动降级为普通通知。 */
    data object ExactAlarmDegraded : Notice

    /** 精确闹钟权限被撤销。 */
    data object ExactAlarmPermissionRevoked : Notice

    /** 待办已保存。 */
    data object Saved : Notice
}

/** 列表页的集合状态。 */
data class TodoUiState(
    val todos: List<Todo> = emptyList(),
    val editState: EditState = EditState(),
    val canScheduleExact: Boolean = true,
    val notice: Notice? = null,
)

/** 把校验错误枚举翻译成用户可见文案。 */
fun messageOf(error: FormError): String = when (error) {
    FormError.NAME_EMPTY -> "请填写名称"
    FormError.NAME_TOO_LONG -> "名称不能超过 80 个字"
    FormError.END_NOT_AFTER_START -> "终止时间必须晚于起始时间"
    FormError.LEAD_OUT_OF_RANGE -> "提前量需在 0 到 10080 分钟（7 天）之间"
}

/**
 * 列表页 ViewModel。
 *
 * 依赖全部从构造函数注入，因此可以在测试里直接 new 出来并替换任意一层。
 */
class TodoViewModel(
    application: Application,
    private val repository: TodoRepository,
    private val coordinator: SchedulingCoordinator,
    private val clock: Clock,
    private val canScheduleExactAlarms: () -> Boolean,
) : AndroidViewModel(application) {

    /** 表单状态独立于列表状态，避免列表刷新打断用户输入。 */
    val form = DraftFormStateHolder(clock)

    private val editState = MutableStateFlow(EditState())

    private data class PermissionState(val granted: Boolean, val notice: Notice?)

    private val permissionState = MutableStateFlow(
        PermissionState(canScheduleExactAlarms(), null)
    )

    val uiState: StateFlow<TodoUiState> = combine(
        repository.observeAll(),
        editState,
        permissionState,
    ) { todos, edit, permission ->
        TodoUiState(
            todos = todos,
            editState = edit,
            canScheduleExact = permission.granted,
            notice = permission.notice,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, TodoUiState())

    // ---------------------------------------------------------------- 表单开关

    fun openCreateForm() {
        form.open()
        editState.value = EditState(isOpen = true, editingId = 0L)
    }

    fun openEditForm(todo: Todo) {
        form.open(
            id = todo.id,
            name = todo.name,
            startAt = todo.startAt,
            endAt = todo.endAt,
            leadMinutes = todo.leadMinutes,
            useExactAlarm = todo.useExactAlarm,
        )
        editState.value = EditState(isOpen = true, editingId = todo.id)
    }

    fun dismissForm() {
        editState.value = EditState()
    }

    // ---------------------------------------------------------------- 增删改

    /** 保存表单；校验不通过时只更新错误状态。 */
    fun submitForm() {
        val errors = form.validate()
        if (errors.isNotEmpty()) return

        viewModelScope.launch {
            val hadExactPermission = canScheduleExactAlarms()
            saveDraft(form.toDraft())
            val degraded = !hadExactPermission &&
                !form.state.value.useExactAlarm.not()
            setNotice(
                when {
                    degraded && !hadExactPermission -> Notice.ExactAlarmDegraded
                    else -> Notice.Saved
                }
            )
            dismissForm()
        }
    }

    private suspend fun saveDraft(draft: TodoDraft) {
        val now = clock.now()
        if (draft.id == 0L) {
            coordinator.create(draft.toTodo(existing = null, now = now))
        } else {
            val existing = coordinator.findById(draft.id) ?: return
            coordinator.update(draft.toTodo(existing = existing, now = now))
        }
    }

    fun delete(todoId: Long) {
        viewModelScope.launch {
            coordinator.delete(todoId)
            if (editState.value.editingId == todoId) dismissForm()
        }
    }

    fun toggleDone(todo: Todo) {
        viewModelScope.launch { coordinator.setDone(todo.id, !todo.isDone) }
    }

    // ---------------------------------------------------------------- 权限

    /** 从系统设置返回后刷新精确闹钟权限状态。 */
    fun refreshPermissions() {
        val granted = canScheduleExactAlarms()
        val previous = permissionState.value
        val notice = if (previous.granted && !granted) {
            Notice.ExactAlarmPermissionRevoked
        } else {
            previous.notice
        }
        permissionState.value = PermissionState(granted, notice)
    }

    fun consumeNotice() {
        setNotice(null)
    }

    private fun setNotice(notice: Notice?) {
        permissionState.value = permissionState.value.copy(notice = notice)
    }

    companion object {
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
                        clock = graph.clock,
                        canScheduleExactAlarms = {
                            graph.exactAlarmPermission.canScheduleExactAlarms()
                        },
                    ) as T
            }
        }
    }
}
