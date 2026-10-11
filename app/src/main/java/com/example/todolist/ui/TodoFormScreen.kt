package com.example.todolist.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.remember
import java.time.LocalDate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.todolist.domain.FormError
import com.example.todolist.domain.FormValidator
import com.example.todolist.domain.Recurrence
import com.example.todolist.domain.RecurrenceExpander
import com.example.todolist.domain.RecurrenceOptions
import com.example.todolist.domain.ReminderMode
import com.example.todolist.domain.ReminderTime
import com.example.todolist.domain.Todo
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 新增 / 编辑待办的整屏表单。
 *
 * 早期版本用对话框承载这个表单，但字段较多（名称、两个时间、提前量、提醒方式、周期），
 * 对话框为控制高度把滚动区以外的内容完全排除在组合之外：用户必须反复滚动才能看到
 * 后面几项，自动化测试也拿不到那些节点。整屏表单把可用高度交还给内容，
 * 填写体验与可测性同时改善。
 *
 * 表单状态放在 [DraftFormStateHolder] 里而不是 composable 的 remember 中，
 * 这样旋转屏幕、列表刷新都不会丢用户已填的内容。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoFormScreen(
    form: DraftFormStateHolder,
    isEditing: Boolean,
    /** 是否来自「仅修改重复待办的这一次」 */
    oneOffEdit: Boolean = false,
    onBack: () -> Unit,
    onSubmit: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.testTag(TAG_FORM_SCREEN),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when {
                            oneOffEdit -> "修改这一次"
                            isEditing -> "编辑待办"
                            else -> "新增待办"
                        },
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag(TAG_FORM_BACK),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(
                        onClick = onSubmit,
                        modifier = Modifier.testTag(TAG_SAVE_BUTTON),
                    ) { Text("保存", fontWeight = FontWeight.SemiBold) }
                },
            )
        },
    ) { padding ->
        TodoFormContent(
            form = form,
            oneOffEdit = oneOffEdit,
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding),
        )
    }
}

/** 表单字段本体，整屏表单与（历史上的）对话框共用同一份实现。 */
@Composable
fun TodoFormContent(
    form: DraftFormStateHolder,
    modifier: Modifier = Modifier,
    oneOffEdit: Boolean = false,
) {
    val state by form.state.collectAsStateWithLifecycle()
    val errors by form.errors.collectAsStateWithLifecycle()

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = state.name,
            onValueChange = form::setName,
            label = { Text("名称") },
            placeholder = { Text("例如：小组会议") },
            singleLine = true,
            isError = errors.containsKey(FormValidator.FIELD_NAME),
            supportingText = {
                errors[FormValidator.FIELD_NAME]?.let { Text(messageOf(it)) }
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TAG_NAME_FIELD),
        )

        DateTimePickerField(
            label = "起始时间",
            value = state.startAt,
            onValueChange = form::setStartAt,
            tagPrefix = "start",
        )

        DateTimePickerField(
            label = "终止时间",
            value = state.endAt,
            onValueChange = form::setEndAt,
            tagPrefix = "end",
        )

        if (errors.containsKey(FormValidator.FIELD_RANGE)) {
            Text(
                text = messageOf(FormError.END_NOT_AFTER_START),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Text("提前提醒", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QUICK_LEADS.forEach { minutes ->
                FilterChip(
                    selected = state.leadMinutes == minutes,
                    onClick = { form.setLeadMinutes(minutes) },
                    label = { Text(quickLeadLabel(minutes)) },
                    modifier = Modifier.testTag("$TAG_QUICK_LEAD_PREFIX$minutes"),
                )
            }
        }
        OutlinedTextField(
            // 用单独保存的原始文本，这样 0 可以被删掉（删空即视为准点）
            value = state.leadInput,
            onValueChange = form::setLeadInput,
            label = { Text("提前量（分钟）") },
            placeholder = { Text("留空表示准点提醒") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            isError = errors.containsKey(FormValidator.FIELD_LEAD),
            supportingText = {
                errors[FormValidator.FIELD_LEAD]?.let { Text(messageOf(it)) }
                    ?: Text("0 或留空表示准点提醒")
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TAG_LEAD_FIELD),
        )
        Text(
            text = "提醒时间：" + reminderPreview(state),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(TAG_REMINDER_PREVIEW),
        )

        // 提醒方式：用户选「怎么提醒我」，不涉及「用哪种系统 API」
        Text("提醒方式", style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = state.mode == ReminderMode.NOTIFICATION,
                onClick = { form.setMode(ReminderMode.NOTIFICATION) },
                label = { Text("通知") },
                modifier = Modifier
                    .weight(1f)
                    .testTag(TAG_MODE_NOTIFICATION),
            )
            FilterChip(
                selected = state.mode == ReminderMode.ALARM,
                onClick = { form.setMode(ReminderMode.ALARM) },
                label = { Text("闹钟式") },
                modifier = Modifier
                    .weight(1f)
                    .testTag(TAG_MODE_ALARM),
            )
        }
        Text(
            text = if (state.mode == ReminderMode.ALARM) {
                "到点响铃、点亮屏幕并弹出提醒页（需要精确闹钟权限）"
            } else {
                "只发一条通知，安静不打扰"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // 周期
        Text("重复", style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Recurrence.entries.forEach { option ->
                FilterChip(
                    selected = state.recurrence == option,
                    onClick = { form.setRecurrence(option) },
                    // NONE 显示为「单次」：按钮标题写「重复」会和整行标题重复，也不像个选项
                    label = { Text(recurrenceChipLabel(option)) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag(TAG_RECURRENCE_PREFIX + option.name),
                )
            }
        }

        // 周重复：精确到星期几
        if (state.recurrence == Recurrence.WEEKLY) {
            Text(
                text = "重复于哪些天",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                RecurrenceOptions.WEEK_ORDER.forEach { day ->
                    FilterChip(
                        selected = day in state.weekDays,
                        onClick = { form.toggleWeekDay(day) },
                        label = { Text(RecurrenceOptions.shortLabel(day)) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag(TAG_WEEKDAY_PREFIX + day.name),
                    )
                }
            }
            Text(
                text = "已选：" + weekDaysSummary(state.weekDays),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(TAG_WEEKDAYS_SUMMARY),
            )
        }

        // 月重复：精确到几号
        if (state.recurrence == Recurrence.MONTHLY) {
            Text(
                text = "重复于每月哪几天",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            RecurrenceOptions.MONTH_DAYS.chunked(7).forEach { week ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    week.forEach { day ->
                        OutlinedButton(
                            onClick = { form.toggleMonthDay(day) },
                            contentPadding = PaddingValues(0.dp),
                            colors = if (day in state.monthDays) {
                                ButtonDefaults.outlinedButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                )
                            } else {
                                ButtonDefaults.outlinedButtonColors()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag(TAG_MONTHDAY_PREFIX + day),
                        ) { Text("$day", style = MaterialTheme.typography.labelMedium) }
                    }
                    // 末行补齐占位，避免按钮被拉伸
                    repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Text(
                text = monthDaysSummary(state.monthDays) +
                    "（选 29/30/31 时，没有该日期的月份落到当月最后一天）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag(TAG_MONTHDAYS_SUMMARY),
            )
        }

        if (state.recurrence.isRecurring) {
            RecurrenceRangeSection(form = form, state = state)
            Text(
                text = "重复待办修改或删除时会询问：仅这一次，还是整个系列",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

/**
 * 重复的起止范围：截止日期与重复次数。
 *
 * 两者联动——改任意一个，另一个立刻跟着算出来，避免出现
 * 「填了 10 次又填了截止到明年」这种自相矛盾的输入。
 */
@Composable
private fun RecurrenceRangeSection(
    form: DraftFormStateHolder,
    state: DraftFormState,
) {
    val zone = remember { ZoneId.systemDefault() }
    val start = state.startAt
    val startDate = start?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }

    /** 用于推算的临时 Todo：只要有规则、起始时间与选中的日子即可。 */
    fun draftTodo(): Todo? {
        val s = start ?: return null
        return Todo(
            name = "",
            startAt = s,
            endAt = state.endAt ?: s,
            recurrence = state.recurrence,
            weekDays = state.weekDays,
            monthDays = state.monthDays,
        )
    }

    val countText = state.recurrenceCount?.toString().orEmpty()
    val endText = state.recurrenceEndDate.orEmpty()

    Text(
        text = "重复范围",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = countText,
            onValueChange = { raw ->
                val digits = raw.filter { it.isDigit() }.take(4)
                val count = digits.toIntOrNull()
                form.setRecurrenceCount(count)
                // 次数 -> 截止日期：联动算出最后一场的日期
                val todo = draftTodo()
                if (count != null && count > 0 && todo != null) {
                    val end = RecurrenceExpander.endDateForCount(todo, count, zone)
                    form.setRecurrenceEndDate(end?.format(DateTimeFormatter.ISO_LOCAL_DATE))
                }
            },
            label = { Text("重复次数") },
            placeholder = { Text("不限") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .weight(1f)
                .testTag(TAG_RECURRENCE_COUNT),
        )
        OutlinedTextField(
            value = endText,
            onValueChange = { raw -> /* 只读，用右侧按钮改 */ },
            readOnly = true,
            label = { Text("截止日期") },
            placeholder = { Text("不限") },
            singleLine = true,
            modifier = Modifier
                .weight(1.2f)
                .testTag(TAG_RECURRENCE_END),
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = {
                // 截止日期 -> 次数：按当前规则数出到该日期为止总共有几场
                val todo = draftTodo() ?: return@OutlinedButton
                val end = state.recurrenceEndDate?.let {
                    runCatching { LocalDate.parse(it, DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull()
                } ?: startDate?.plusMonths(1) ?: return@OutlinedButton
                form.setRecurrenceCount(RecurrenceExpander.countUntil(todo, end, zone))
            },
            modifier = Modifier
                .weight(1f)
                .testTag(TAG_RECURRENCE_SYNC),
        ) { Text("按截止日期算次数") }

        OutlinedButton(
            onClick = { form.setRecurrenceEndDate(null) },
            modifier = Modifier.weight(1f),
            enabled = endText.isNotEmpty(),
        ) { Text("清除截止") }
    }
    Text(
        text = buildString {
            append("起始 ")
            append(startDate?.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) ?: "—")
            append("，")
            append(
                when {
                    state.recurrenceCount != null && endText.isNotEmpty() ->
                        "共 ${state.recurrenceCount} 次，最后一场 $endText"
                    state.recurrenceCount != null -> "共 ${state.recurrenceCount} 次"
                    endText.isNotEmpty() -> "截止 $endText"
                    else -> "不限次数与截止日期"
                },
            )
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag(TAG_RECURRENCE_SUMMARY),
    )
}

/** 周期按钮的文案。 */
private fun recurrenceChipLabel(option: Recurrence): String = when (option) {
    Recurrence.NONE -> "单次"
    Recurrence.DAILY -> "每天"
    Recurrence.WEEKLY -> "每周"
    Recurrence.MONTHLY -> "每月"
}

private val QUICK_LEADS = listOf(0, 5, 30, 60, 1440)

private fun quickLeadLabel(minutes: Int): String = when (minutes) {
    0 -> "准点"
    5 -> "5 分钟"
    30 -> "30 分钟"
    60 -> "1 小时"
    1440 -> "1 天"
    else -> "$minutes 分钟"
}

/** 表单里显示的提醒时刻预览。 */
private fun reminderPreview(state: DraftFormState): String {
    val start = state.startAt ?: return "—"
    val zone = ZoneId.systemDefault()
    val remindAt = ReminderTime.remindAt(start, state.leadMinutes)
    val zoned = Instant.ofEpochMilli(remindAt).atZone(zone)
    val suffix = when (state.mode) {
        ReminderMode.ALARM -> "（闹钟式）"
        ReminderMode.NOTIFICATION -> "（通知）"
    }
    val repeat = if (state.recurrence.isRecurring) {
        // 复用列表里的同一套描述逻辑，保证表单预览与列表徽章一致
        val preview = Todo(
            name = "",
            startAt = start,
            endAt = state.endAt ?: start,
            recurrence = state.recurrence,
            weekDays = state.weekDays,
            monthDays = state.monthDays,
        )
        "·" + RecurrenceExpander.describe(preview, zone)
    } else {
        ""
    }
    return zoned.format(PREVIEW_TIME_FORMATTER) + suffix + repeat
}

private val PREVIEW_TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MM-dd HH:mm")

/** 周重复选中项的汇总文案。 */
private fun weekDaysSummary(days: Set<DayOfWeek>): String =
    if (days.isEmpty()) {
        "未选择"
    } else {
        RecurrenceOptions.WEEK_ORDER
            .filter { it in days }
            .joinToString("、") { RecurrenceOptions.fullLabel(it) }
    }

/** 月重复选中项的汇总文案。 */
private fun monthDaysSummary(days: Set<Int>): String =
    if (days.isEmpty()) "未选择" else "已选：" + days.sorted().joinToString("、") + " 号"
