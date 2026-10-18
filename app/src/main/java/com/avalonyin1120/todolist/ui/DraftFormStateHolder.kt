package com.avalonyin1120.todolist.ui

import com.avalonyin1120.todolist.domain.Clock
import com.avalonyin1120.todolist.domain.FormError
import com.avalonyin1120.todolist.domain.FormValidator
import com.avalonyin1120.todolist.domain.MonthDays
import com.avalonyin1120.todolist.domain.NaturalLanguage
import com.avalonyin1120.todolist.domain.Recurrence
import com.avalonyin1120.todolist.domain.ReminderMode
import com.avalonyin1120.todolist.domain.ReminderTime
import com.avalonyin1120.todolist.domain.WeekDays
import com.avalonyin1120.todolist.data.TodoDraft
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
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
    /**
     * 提醒方式：通知 / 闹钟式 / 不通知。
     *
     * 选「不通知」时 [leadMinutes] / [leadInput] 原样留着不清空：用户只是这一条不想被提醒，
     * 切回通知或闹钟式时先前填过的提前量必须还在，否则每次切档都要重填一遍。
     */
    val mode: ReminderMode = ReminderMode.NOTIFICATION,
    /** 周期规则。 */
    val recurrence: Recurrence = Recurrence.NONE,
    /** 周重复选中的星期几。 */
    val weekDays: WeekDays = emptySet(),
    /** 月重复选中的几号。 */
    val monthDays: MonthDays = emptySet(),
    /** 重复生效起始日期 yyyy-MM-dd；为空表示从待办起始日生效。 */
    val recurrenceStartDate: String? = null,
    /** 重复截止日期 yyyy-MM-dd；为空表示不限。 */
    val recurrenceEndDate: String? = null,
    /** 重复总次数；为空表示不限。 */
    val recurrenceCount: Int? = null,
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
    /** 表单里按哪个时区解释日期；与界面一致。 */
    private val zone: ZoneId = ZoneId.systemDefault(),
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
        weekDays: WeekDays = emptySet(),
        monthDays: MonthDays = emptySet(),
        recurrenceStartDate: String? = null,
        recurrenceEndDate: String? = null,
        recurrenceCount: Int? = null,
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
            // 没指定具体日子时，用起始时间所在的星期/号数作为默认，避免出现「每周但没选星期」
            weekDays = weekDays.ifEmpty {
                if (recurrence == Recurrence.WEEKLY) {
                    setOf(Instant.ofEpochMilli(start).atZone(zone).dayOfWeek)
                } else {
                    emptySet()
                }
            },
            recurrenceStartDate = recurrenceStartDate,
            recurrenceEndDate = recurrenceEndDate,
            recurrenceCount = recurrenceCount,
            monthDays = monthDays.ifEmpty {
                if (recurrence == Recurrence.MONTHLY) {
                    setOf(Instant.ofEpochMilli(start).atZone(zone).dayOfMonth)
                } else {
                    emptySet()
                }
            },
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

    /**
     * 切换提醒方式。
     *
     * 只改方式，绝不动提前量：[setLeadInput] / [setLeadMinutes] 填过的值保留在状态里，
     * 用户从「不通知」切回来时输入框还是原样（清空的话每次切档都得重填）。
     */
    fun setMode(value: ReminderMode) {
        _state.value = _state.value.copy(mode = value)
    }

    /**
     * 切换周期规则。
     *
     * 切到周重复/月重复时，如果还没选具体日子，就用起始时间所在的星期/号数兜底，
     * 否则用户会看到「每周」却没有任何一天被选中。
     */
    fun setRecurrence(value: Recurrence) {
        val current = _state.value
        val start = current.startAt
        _state.value = current.copy(
            recurrence = value,
            weekDays = when {
                value != Recurrence.WEEKLY -> current.weekDays
                current.weekDays.isNotEmpty() -> current.weekDays
                start != null -> setOf(Instant.ofEpochMilli(start).atZone(zone).dayOfWeek)
                else -> current.weekDays
            },
            monthDays = when {
                value != Recurrence.MONTHLY -> current.monthDays
                current.monthDays.isNotEmpty() -> current.monthDays
                start != null -> setOf(Instant.ofEpochMilli(start).atZone(zone).dayOfMonth)
                else -> current.monthDays
            },
        )
    }

    /** 设置重复总次数；null 表示不限。 */
    fun setRecurrenceCount(value: Int?) {
        _state.value = _state.value.copy(recurrenceCount = value?.takeIf { it > 0 })
    }

    /** 设置重复生效起始日期（yyyy-MM-dd）；null 表示从待办起始日生效。 */
    fun setRecurrenceStartDate(value: String?) {
        _state.value = _state.value.copy(recurrenceStartDate = value?.takeIf { it.isNotBlank() })
    }

    /** 设置重复截止日期（yyyy-MM-dd）；null 表示不限。 */
    /**
     * 把「一句话解析」的结果回填进当前表单。
     *
     * 只覆盖解析出来的东西（名称 / 起止时间 / 重复 / 提前量），其余保持原样：
     * 用户的意图是「补全我这句描述」，不是「把我已经填好的表清空重来」。
     * 尤其是 [current].id / mode / 重复次数与截止日期都不动。
     */
    fun applyParsed(parsed: NaturalLanguage.Parsed) {
        val current = _state.value
        val start = parsed.startAt
        _state.value = current.copy(
            name = parsed.name.take(80),
            startAt = start,
            endAt = start + parsed.durationMinutes * 60_000L,
            leadMinutes = parsed.leadMinutes.coerceIn(0, ReminderTime.MAX_LEAD_MINUTES),
            leadInput = parsed.leadMinutes
                .coerceIn(0, ReminderTime.MAX_LEAD_MINUTES)
                .toEditableLeadInput(),
            recurrence = parsed.recurrence,
            weekDays = parsed.weekDays,
            monthDays = parsed.monthDays,
        )
    }
    fun setRecurrenceEndDate(value: String?) {
        _state.value = _state.value.copy(recurrenceEndDate = value?.takeIf { it.isNotBlank() })
    }

    /** 勾选/取消某个星期。 */
    fun toggleWeekDay(day: DayOfWeek) {
        val current = _state.value.weekDays
        _state.value = _state.value.copy(
            weekDays = if (day in current) current - day else current + day,
        )
    }

    /** 勾选/取消某个号数。 */
    fun toggleMonthDay(day: Int) {
        val current = _state.value.monthDays
        _state.value = _state.value.copy(
            monthDays = if (day in current) current - day else current + day,
        )
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
            // 「不通知」时界面把提前量整块藏起来了，用户没有地方去修正一个看不见的错误，
            // 因此这一档直接不校验提前量（详见 FormValidator.validate 的 validateLead）。
            validateLead = current.mode.needsLeadTime,
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
            // 「不通知」落库时把提前量写成 0，而不是照抄用户上一档填的值：
            // 这一档的提前量校验是跳过的，那个值可能已经非法（例如 99999），
            // 原样写进库只会留下一条永远用不上的脏数据；0 与「准点」同义、也无副作用。
            // 注意这只影响落库，表单状态里的值没动，切回另外两档仍然看得见。
            leadMinutes = if (current.mode.needsLeadTime) current.leadMinutes else 0,
            mode = current.mode,
            recurrence = current.recurrence,
            weekDays = current.weekDays,
            monthDays = current.monthDays,
            recurrenceStartDate = current.recurrenceStartDate,
            recurrenceEndDate = current.recurrenceEndDate,
            recurrenceCount = current.recurrenceCount,
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
