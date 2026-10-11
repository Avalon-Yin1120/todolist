package com.example.todolist.ui

import com.example.todolist.domain.Clock
import com.example.todolist.domain.FormError
import com.example.todolist.domain.FormValidator
import com.example.todolist.domain.ReminderTime
import com.example.todolist.data.TodoDraft
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 表单草稿；时间字段用可空槽位表达「还没选」。 */
data class DraftFormState(
    val id: Long = 0L,
    val name: String = "",
    val startAt: Long? = null,
    val endAt: Long? = null,
    val leadMinutes: Int = 0,
    val useExactAlarm: Boolean = false,
) {
    /** 提前量的可读描述，例如「提前 30 分钟」。 */
    val leadLabel: String
        get() = when {
            leadMinutes == 0 -> "准点"
            leadMinutes % 1440 == 0 -> "提前 ${leadMinutes / 1440} 天"
            leadMinutes % 60 == 0 -> "提前 ${leadMinutes / 60} 小时"
            else -> "提前 $leadMinutes 分钟"
        }
}

/**
 * 新增 / 编辑表单的状态与校验。
 *
 * 校验结果用 [FormError] 枚举表达（见 FormValidator），不掺任何 Android 资源 id，
 * 因此本类可以在纯 JVM 测试里完整覆盖。
 */
class DraftFormStateHolder(
    private val clock: Clock,
    private val defaultStartAtProvider: (Long) -> Long = ::defaultStartAtOf,
) {

    private val _state = MutableStateFlow(DraftFormState())
    val state: StateFlow<DraftFormState> = _state.asStateFlow()

    private val _errors = MutableStateFlow<Map<String, FormError>>(emptyMap())
    val errors: StateFlow<Map<String, FormError>> = _errors.asStateFlow()

    /** 打开表单：编辑已有项时传入其值，新建时传 null。 */
    fun open(
        id: Long = 0L,
        name: String? = null,
        startAt: Long? = null,
        endAt: Long? = null,
        leadMinutes: Int = 0,
        useExactAlarm: Boolean = false,
    ) {
        val start = startAt ?: defaultStartAtProvider(clock.now())
        _state.value = DraftFormState(
            id = id,
            name = name.orEmpty(),
            startAt = start,
            endAt = endAt ?: (start + DEFAULT_DURATION_MS),
            leadMinutes = leadMinutes,
            useExactAlarm = useExactAlarm,
        )
        _errors.value = emptyMap()
    }

    fun setName(value: String) {
        _state.value = _state.value.copy(name = value)
        clearError(FormValidator.FIELD_NAME)
    }

    /** 改起始时间时若结束时间已不合理，自动顺延一小时，避免用户手动兜底。 */
    fun setStartAt(value: Long) {
        val current = _state.value
        val end = current.endAt
        val newEnd = if (end == null || end <= value) value + DEFAULT_DURATION_MS else end
        _state.value = current.copy(startAt = value, endAt = newEnd)
        clearError(FormValidator.FIELD_RANGE)
    }

    fun setEndAt(value: Long) {
        _state.value = _state.value.copy(endAt = value)
        clearError(FormValidator.FIELD_RANGE)
    }

    fun setLeadMinutes(value: Int) {
        _state.value = _state.value.copy(leadMinutes = value)
        clearError(FormValidator.FIELD_LEAD)
    }

    fun setUseExactAlarm(value: Boolean) {
        _state.value = _state.value.copy(useExactAlarm = value)
    }

    /** 校验当前表单；返回空 map 表示通过。 */
    fun validate(): Map<String, FormError> {
        val current = _state.value
        val result = FormValidator.validate(
            rawName = current.name,
            startAt = current.startAt,
            endAt = current.endAt,
            leadMinutes = current.leadMinutes,
        )
        _errors.value = result
        return result
    }

    /** 校验通过后转成落库草稿。 */
    fun toDraft(): TodoDraft {
        val current = _state.value
        return TodoDraft(
            id = current.id,
            name = ReminderTime.normalizeName(current.name),
            startAt = requireNotNull(current.startAt) { "起始时间未选择" },
            endAt = requireNotNull(current.endAt) { "终止时间未选择" },
            leadMinutes = current.leadMinutes,
            useExactAlarm = current.useExactAlarm,
        )
    }

    private fun clearError(field: String) {
        if (_errors.value.containsKey(field)) {
            _errors.value = _errors.value - field
        }
    }

    companion object {
        const val DEFAULT_DURATION_MS: Long = 60 * 60 * 1000L

        /** 默认起始时间：下一个整点。 */
        fun defaultStartAtOf(now: Long): Long {
            val nextHour = now + DEFAULT_DURATION_MS
            return nextHour - nextHour % DEFAULT_DURATION_MS
        }
    }
}
