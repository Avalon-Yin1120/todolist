package com.example.todolist.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.remember
import java.time.LocalDate
import java.time.YearMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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

/**
 * 表单字段本体，整屏表单与（历史上的）对话框共用同一份实现。
 *
 * 版面按「一张卡一个主题」分成四组：名称 → 重复 → 时间 → 提醒。
 * 重复被提到名称正下方，且用分段按钮做成整屏最重的选择：
 * 用户最常改的就是「这件事要不要重复」，改完周期后日期也就定了，
 * 所以重复范围、以及下面时间卡里能改什么，都由它决定。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TodoFormContent(
    form: DraftFormStateHolder,
    modifier: Modifier = Modifier,
    oneOffEdit: Boolean = false,
) {
    val state by form.state.collectAsStateWithLifecycle()
    val errors by form.errors.collectAsStateWithLifecycle()
    val zone = remember { ZoneId.systemDefault() }
    val recurring = state.recurrence.isRecurring
    // 重复态下日期不再单独选：用它把「日期到底算哪天」明确写出来
    val effectiveStartDate = state.recurrenceStartDate?.let { parseIsoDate(it) }
        ?: state.startAt?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(FORM_CARD_GAP),
    ) {
        // ---------------------------------------------------------------- 名称
        FormCard {
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
        }

        // ---------------------------------------------------------------- 重复
        // 紧随名称：这是本屏最主要的选择，选完它下面才知道还能改什么。
        FormCard(title = "重复") {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                Recurrence.entries.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = state.recurrence == option,
                        onClick = {
                            form.setRecurrence(option)
                            resyncDeadline(form, state)
                        },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = Recurrence.entries.size,
                        ),
                        // 不显示选中打勾图标：四个中文标签才不会被图标挤到换行
                        icon = {},
                        // NONE 显示为「单次」：写「重复」会和卡片标题重复，也不像个选项
                        label = { Text(recurrenceChipLabel(option)) },
                        modifier = Modifier.testTag(TAG_RECURRENCE_PREFIX + option.name),
                    )
                }
            }

            // 周重复：精确到星期几
            if (state.recurrence == Recurrence.WEEKLY) {
                FormLabel("重复于哪些天")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    RecurrenceOptions.WEEK_ORDER.forEach { day ->
                        FilterChip(
                            selected = day in state.weekDays,
                            onClick = {
                                form.toggleWeekDay(day)
                                resyncDeadline(form, state)
                            },
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
                FormLabel("重复于每月哪几天")
                RecurrenceOptions.MONTH_DAYS.chunked(7).forEach { week ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        week.forEach { day ->
                            OutlinedButton(
                                onClick = {
                                    form.toggleMonthDay(day)
                                    resyncDeadline(form, state)
                                },
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
                    text = monthDaysSummary(state.monthDays) + "（29/30/31 遇小月落到月末）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(TAG_MONTHDAYS_SUMMARY),
                )
            }

            // 重复范围：紧接着规则出现，它就是「重复态下的日期来源」
            if (recurring) {
                RecurrenceRangeSection(form = form, state = state)
                Text(
                    text = "重复待办修改或删除时会询问：仅这一次，还是整个系列",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ---------------------------------------------------------------- 时间
        FormCard(title = "时间") {
            DateTimePickerField(
                label = "起始时间",
                value = state.startAt,
                onValueChange = form::setStartAt,
                tagPrefix = "start",
                // 重复态下日期由上面的重复范围决定，这里只留时间
                showDate = !recurring,
            )

            DateTimePickerField(
                label = "终止时间",
                value = state.endAt,
                onValueChange = form::setEndAt,
                tagPrefix = "end",
                showDate = !recurring,
            )

            if (recurring) {
                Text(
                    text = "日期由上面的重复范围决定" + (
                        effectiveStartDate?.let {
                            "（生效起始 ${it.format(DateTimeFormatter.ISO_LOCAL_DATE)}）"
                        } ?: ""
                        ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (errors.containsKey(FormValidator.FIELD_RANGE)) {
                Text(
                    text = messageOf(FormError.END_NOT_AFTER_START),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        // ---------------------------------------------------------------- 提醒
        FormCard(title = "提醒") {
            FormLabel("提前提醒")
            // FlowRow：放不下就换行，「1 天」不会再被裁掉（此前一行 5 个芯片塞不下）
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
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
            FormLabel("提醒方式")
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
        }

        Spacer(Modifier.height(24.dp))
    }
}

/** 卡片之间的间距；与卡片内边距一起构成整套表单的留白节奏。 */
private val FORM_CARD_GAP = 12.dp

/** 表单分组卡片：统一圆角、统一 16dp 内边距，可选一行小标题。 */
@Composable
private fun FormCard(
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 小标题永远排在内容最前面
            title?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            content()
        }
    }
}

/** 卡片内的字段小标题，风格统一，避免各卡片各写一套字号。 */
@Composable
private fun FormLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
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

    /** 用于推算的临时 Todo：带上当前已选的规则与生效起始日。 */
    fun draftTodo(): Todo? {
        val s = start ?: return null
        return Todo(
            name = "",
            startAt = s,
            endAt = state.endAt ?: s,
            recurrence = state.recurrence,
            weekDays = state.weekDays,
            monthDays = state.monthDays,
            recurrenceStartDate = state.recurrenceStartDate,
        )
    }

    val countText = state.recurrenceCount?.toString().orEmpty()
    val endDate = state.recurrenceEndDate?.let { parseIsoDate(it) }
    val effectiveStart = state.recurrenceStartDate?.let { parseIsoDate(it) } ?: startDate

    // 与卡片里其它小标题同一套字号，「生效起始 / 截止日期 / 次数」是一组
    FormLabel("重复范围")

    // 1) 生效起始日期：留空表示从待办自身的起始日生效
    DateFieldButton(
        label = "生效起始",
        value = state.recurrenceStartDate?.let { parseIsoDate(it) },
        hint = "留空则从 ${startDate?.format(SHORT_DATE) ?: "—"} 生效",
        tag = TAG_RECURRENCE_START,
        onPick = { picked ->
            form.setRecurrenceStartDate(picked.format(DateTimeFormatter.ISO_LOCAL_DATE))
            // 生效起始日会改变场次数，如果次数已定就重算截止日期
            val todo = draftTodo()?.copy(
                recurrenceStartDate = picked.format(DateTimeFormatter.ISO_LOCAL_DATE),
            )
            state.recurrenceCount?.let { count ->
                if (todo != null) {
                    form.setRecurrenceEndDate(
                        RecurrenceExpander.endDateForCount(todo, count, zone)
                            ?.format(DateTimeFormatter.ISO_LOCAL_DATE),
                    )
                }
            }
        },
        onClear = { if (state.recurrenceStartDate != null) form.setRecurrenceStartDate(null) },
    )

    // 2) 截止日期：填了就反推次数
    DateFieldButton(
        label = "截止日期",
        value = endDate,
        hint = "留空表示不限",
        tag = TAG_RECURRENCE_END,
        onPick = { picked ->
            val iso = picked.format(DateTimeFormatter.ISO_LOCAL_DATE)
            form.setRecurrenceEndDate(iso)
            // 截止日期 -> 次数：数出到该日期为止总共有几场
            draftTodo()?.let { todo ->
                form.setRecurrenceCount(RecurrenceExpander.countUntil(todo, picked, zone))
            }
        },
        onClear = {
            form.setRecurrenceEndDate(null)
            form.setRecurrenceCount(null)
        },
    )

    // 3) 重复次数：填了就反推截止日期
    OutlinedTextField(
        value = countText,
        onValueChange = { raw ->
            val digits = raw.filter { it.isDigit() }.take(4)
            val count = digits.toIntOrNull()
            form.setRecurrenceCount(count)
            if (count == null) {
                // 清空次数就同时清掉由它算出的截止日期，避免留下自相矛盾的状态
                form.setRecurrenceEndDate(null)
            } else if (count > 0) {
                val todo = draftTodo()
                if (todo != null) {
                    form.setRecurrenceEndDate(
                        RecurrenceExpander.endDateForCount(todo, count, zone)
                            ?.format(DateTimeFormatter.ISO_LOCAL_DATE),
                    )
                }
            }
        },
        label = { Text("重复次数") },
        placeholder = { Text("留空表示不限") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TAG_RECURRENCE_COUNT),
    )

    Text(
        text = buildString {
            append("首次 ")
            append(effectiveStart?.format(SHORT_DATE) ?: "—")
            append("；")
            when {
                state.recurrenceCount != null && endDate != null ->
                    append("共 ${state.recurrenceCount} 次，最后一场 " + endDate.format(SHORT_DATE))
                state.recurrenceCount != null -> append("共 ${state.recurrenceCount} 次")
                endDate != null -> append("截止 " + endDate.format(SHORT_DATE))
                else -> append("不限次数与截止日期")
            }
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag(TAG_RECURRENCE_SUMMARY),
    )
}

private val SHORT_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("M 月 d 日")

/** 解析 yyyy-MM-dd，失败返回 null（数据异常时不该崩）。 */
private fun parseIsoDate(text: String): LocalDate? =
    runCatching { LocalDate.parse(text, DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull()

/**
 * 一个「点开选日期」的只读字段。
 *
 * 比裸的 OutlinedTextField 更明确：既显示当前值，也给出留空时的默认语义，
 * 并提供清除按钮把该字段恢复成「不限」。
 */
@Composable
private fun DateFieldButton(
    label: String,
    value: LocalDate?,
    hint: String,
    tag: String,
    onPick: (LocalDate) -> Unit,
    onClear: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = { open = true },
            modifier = Modifier
                .weight(1f)
                .testTag(tag),
        ) {
            Text("$label：${value?.format(SHORT_DATE) ?: "未设置"}")
        }
        if (value != null) {
            TextButton(onClick = onClear) { Text("清除") }
        }
    }
    Text(
        text = hint,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    if (open) {
        SimpleDatePickerDialog(
            initial = value ?: LocalDate.now(),
            onDismiss = { open = false },
            onConfirm = { picked ->
                onPick(picked)
                open = false
            },
        )
    }
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

/**
 * 简单的日期选择对话框：自绘日历 + 年/月/日三个输入框。
 *
 * 与时间字段用的那个是同一套交互（见 DateTimePickerField 里的 DatePickerDialog），
 * 这里独立一份是为了不让表单依赖时间字段的实现细节。
 */
@Composable
private fun SimpleDatePickerDialog(
    initial: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    var yearText by remember { mutableStateOf(initial.year.toString()) }
    var monthText by remember { mutableStateOf(initial.monthValue.toString()) }
    var dayText by remember { mutableStateOf(initial.dayOfMonth.toString()) }
    var shownMonth by remember { mutableStateOf(YearMonth.from(initial)) }
    val parsed = DateFormats.parseDateParts(yearText, monthText, dayText)

    AlertDialog(
        onDismissRequest = onDismiss,
        // 不设 title：日历自己带月份标题
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // 自绘日历：Material3 的 DatePicker 最小宽度大于对话框可用宽度，右列会被裁
                CompactCalendar(
                    month = shownMonth,
                    selected = parsed ?: initial,
                    today = LocalDate.now(),
                    onSelect = { picked ->
                        yearText = picked.year.toString()
                        monthText = picked.monthValue.toString()
                        dayText = picked.dayOfMonth.toString()
                        shownMonth = YearMonth.from(picked)
                    },
                    onMonthChange = { shownMonth = it },
                )
                Text("手动输入", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = yearText,
                        onValueChange = { yearText = it.filter { c -> c.isDigit() }.take(4) },
                        label = { Text("年") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(1.4f)
                            .testTag("simple_date_year"),
                    )
                    OutlinedTextField(
                        value = monthText,
                        onValueChange = { monthText = it.filter { c -> c.isDigit() }.take(2) },
                        label = { Text("月") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("simple_date_month"),
                    )
                    OutlinedTextField(
                        value = dayText,
                        onValueChange = { dayText = it.filter { c -> c.isDigit() }.take(2) },
                        label = { Text("日") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("simple_date_day"),
                    )
                }
                Text(
                    text = parsed?.let { "将设置为 " + DateFormats.fullDate(it) }
                        ?: "请填写合法的年月日（例如 2026 / 1 / 1）",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (parsed == null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { parsed?.let(onConfirm) },
                enabled = parsed != null,
                modifier = Modifier.testTag("simple_date_confirm"),
            ) { Text("确定") }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("simple_date_cancel"),
            ) { Text("取消") }
        },
    )
}
/**
 * 规则或「重复于哪些天」变化后，重新推算截止日期。
 *
 * 次数与截止日期是联动的：次数已经定了的时候，改了每周哪几天 / 每月哪几号，
 * 最后一场落在哪天就变了。不重算的话界面上会留着旧截止日期，与实际规则不符。
 *
 * 从 [DraftFormStateHolder.state] 取最新值而不是用传入的旧 state：
 * toggleWeekDay 刚写进去，闭包捕获的 state 还是变更前那一份。
 */
private fun resyncDeadline(form: DraftFormStateHolder, @Suppress("UNUSED_PARAMETER") stale: DraftFormState) {
    form.setRecurrenceEndDate(deadlineIsoForCurrentRule(form.state.value, ZoneId.systemDefault()))
}

/**
 * 按当前规则与次数算出截止日期（yyyy-MM-dd）；不该算时返回 null。
 *
 * 抽成纯函数是为了能直接单测：改「重复于哪些天 / 几号」或改周期规则后，
 * 已定的次数对应哪一天就变了，界面上必须跟着更新——这段逻辑曾经缺失，
 * 导致用户看到过期的截止日期。
 */
internal fun deadlineIsoForCurrentRule(state: DraftFormState, zone: ZoneId): String? {
    val count = state.recurrenceCount ?: return null
    val start = state.startAt ?: return null
    if (!state.recurrence.isRecurring || count <= 0) return null
    val todo = Todo(
        name = "",
        startAt = start,
        endAt = state.endAt ?: start,
        recurrence = state.recurrence,
        weekDays = state.weekDays,
        monthDays = state.monthDays,
        recurrenceStartDate = state.recurrenceStartDate,
    )
    return RecurrenceExpander.endDateForCount(todo, count, zone)
        ?.format(DateTimeFormatter.ISO_LOCAL_DATE)
}