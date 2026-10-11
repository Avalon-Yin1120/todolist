package com.example.todolist.ui

import com.example.todolist.domain.Clock
import com.example.todolist.domain.FormError
import com.example.todolist.domain.FormValidator
import com.example.todolist.domain.Recurrence
import com.example.todolist.domain.ReminderMode
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
    /**
     * 提前量输入框里的原始文本。
     *
     * 与 [leadMinutes] 分开保存是必要的：用户想把 0 删掉时输入框必须允许为空，
     * 而 0 本身是合法业务值。如果只留一个 Int，「删空」就只能立刻回填 0，体验很差。
     * 空字符串在语义上等同于 0。
     */
    val leadInput: String = "",
    /** 用户输入的提前量非法（非空但解析失败或超出范围）。 */
    val leadInputInvalid: Boolean = false,
    /** 提醒方式：通知 / 闹钟式。 */
    val mode: ReminderMode = ReminderMode.NOTIFICATION,
    /** 周期规则。 */
    val recurrence: Recurrence = Recurrence.NONE,
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
    /**
     * 用户此前输入过的提前量，作为新建待办时的默认值。
     * 没填过则回落到 [FALLBACK_LEAD_MINUTES]。
     */
    private val rememberedLeadMinutes: () -> Int = { FALLBACK_LEAD_MINUTES },
    /** 用户此前选择的提醒方式，作为新建待办时的默认值。 */
    private val defaultMode: () -> ReminderMode = { ReminderMode.NOTIFICATION },
) {

    private val _state = MutableStateFlow(DraftFormState())
    val state: StateFlow<DraftFormState> = _state.asStateFlow()

    private val _errors = MutableStateFlow<Map<String, FormError>>(emptyMap())
    val errors: StateFlow<Map<String, FormError>> = _errors.asStateFlow()

    /**
     * 打开表单。
     *
     * @param leadMinutes 传 null 表示「用记住的默认提前量」；编辑已有项时传具体值。
     * @param mode 传 null 表示「用设置里的默认提醒方式」。
     */
    fun open(
        id: Long = 0L,
        name: String? = null,
        startAt: Long? = null,
        endAt: Long? = null,
        leadMinutes: Int? = null,
        mode: ReminderMode? = null,
        recurrence: Recurrence = Recurrence.NONE,
    ) {
        val start = startAt ?: defaultStartAtProvider(clock.now())
        val lead = leadMinutes ?: rememberedLeadMinutes().coerceIn(0, ReminderTime.MAX_LEAD_MINUTES)
        _state.value = DraftFormState(
            id = id,
            name = name.orEmpty(),
            startAt = start,
            endAt = endAt ?: (start + DEFAULT_DURATION_MS),
            leadMinutes = lead,
            leadInput = lead.toEditableLeadInput(),
            mode = mode ?: defaultMode(),
            recurrence = recurrence,
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

    /**
     * 处理提前量输入框的原始文本。
     *
     * 空文本是允许的：语义上等于「准点」（0），但输入框保持为空，
     * 这样用户能把 0 删掉再输入别的数字。非空但无法解析时标记为非法输入。
     */
    fun setLeadInput(raw: String) {
        val digits = raw.filter { it.isDigit() }.take(MAX_LEAD_DIGITS)
        val parsed = digits.toIntOrNull()
        val invalid = digits.isNotEmpty() && (parsed == null || !ReminderTime.isValidLead(parsed))
        _state.value = _state.value.copy(
            leadInput = digits,
            // 空输入按 0 处理，但不动输入框文本
            leadMinutes = parsed ?: 0,
            leadInputInvalid = invalid,
        )
        clearError(FormValidator.FIELD_LEAD)
    }

    /** 由快捷按钮设置提前量：同时更新语义值与输入框文本。 */
    fun setLeadMinutes(value: Int) {
        _state.value = _state.value.copy(
            leadMinutes = value,
            leadInput = value.toEditableLeadInput(),
            leadInputInvalid = false,
        )
        clearError(FormValidator.FIELD_LEAD)
    }

    fun setMode(value: ReminderMode) {
        _state.value = _state.value.copy(mode = value)
    }

    fun setRecurrence(value: Recurrence) {
        _state.value = _state.value.copy(recurrence = value)
    }

    /** 校验当前表单；返回空 map 表示通过。 */
    fun validate(): Map<String, FormError> {
        val current = _state.value
        val result = FormValidator.validate(
            rawName = current.name,
            startAt = current.startAt,
            endAt = current.endAt,
            leadMinutes = current.leadMinutes,
            leadInputInvalid = current.leadInputInvalid,
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
            mode = current.mode,
            recurrence = current.recurrence,
        )
    }

    private fun clearError(field: String) {
        if (_errors.value.containsKey(field)) {
            _errors.value = _errors.value - field
        }
    }

    companion object {
        const val DEFAULT_DURATION_MS: Long = 60 * 60 * 1000L

        /** 用户从未设置过时给出的默认提前量。 */
        const val FALLBACK_LEAD_MINUTES: Int = 0

        private const val MAX_LEAD_DIGITS = 5

        /** 0 显示为空串，避免出现「删不掉的 0」。 */
        fun Int.toEditableLeadInput(): String = if (this == 0) "" else toString()

        /** 默认起始时间：下一个整点。 */
        fun defaultStartAtOf(now: Long): Long {
            val nextHour = now + DEFAULT_DURATION_MS
            return nextHour - nextHour % DEFAULT_DURATION_MS
        }
    }
}
