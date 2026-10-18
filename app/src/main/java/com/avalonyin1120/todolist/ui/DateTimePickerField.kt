package com.avalonyin1120.todolist.ui

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 日期 + 时间选择字段。
 *
 * 交互设计（针对此前「点击只聚焦、日期根本调不了」的问题）：
 * * 文本框只读，点击整块即打开**日期**选择器；
 * * 下方提供两个独立按钮：「日期」「时间」各自打开对应选择器，互不干扰；
 * * 两个选择器都有明确标题；时间选择器把当前选中的时刻用大字号显示，
 *   并提供 ±5/±15 分钟快捷键，避免小屏上圆盘难以点准；确认键是整行主按钮。
 *
 * 每次打开对话框都重新初始化 picker 状态（用打开次数作 remember key），
 * 因此总是从当前值开始，不会出现「对话框里还是上一次的值」。
 * 时间戳统一为 epoch 毫秒，内部按本地时区解释年月日与时分，跨时区/夏令时都正确。
 *
 * [showDate] = false 的「只选时间」模式：日期由调用方（例如重复范围）决定，
 * 这里只显示与修改时分——`_date_button` 不再生成，文本框只渲染 `HH:mm`。
 * 底层时间戳仍然带着原来的日期，只改时间不会把日期挪到别的天。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimePickerField(
    label: String,
    value: Long?,
    onValueChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
    tagPrefix: String = label,
    /** 字段为空时，日期选择器默认落在哪一天 */
    defaultDate: Long = System.currentTimeMillis(),
    /** false = 只选时间：不显示「日期」按钮，文本框也只显示 HH:mm。 */
    showDate: Boolean = true,
) {
    val zone = remember { ZoneId.systemDefault() }
    val local = value?.let { Instant.ofEpochMilli(it).atZone(zone) }

    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    // 这两个计数器只为让 picker 状态在每次打开时重建，不参与展示。
    var dateOpenCount by remember { mutableIntStateOf(0) }
    var timeOpenCount by remember { mutableIntStateOf(0) }

    /** 选完日期：保留原来的时分。 */
    fun applyDate(utcMidnight: Long, hour: Int, minute: Int) {
        onValueChange(combineDateTime(utcMidnight, hour, minute, zone))
    }

    /** 选完时间：保留原来的日期。 */
    fun applyTime(hour: Int, minute: Int) {
        val date = local?.toLocalDate() ?: LocalDate.now(zone)
        onValueChange(combineDateTime(date.utcMidnightMillis(), hour, minute, zone))
    }

    Column(modifier = modifier) {
        OutlinedTextField(
            // 只选时间时不再显示日期：那部分由重复范围决定，显示出来只会让人以为能改
            value = local
                ?.format(if (showDate) DISPLAY_FORMATTER else TIME_FORMATTER)
                .orEmpty(),
            onValueChange = { /* 只读展示 */ },
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("${tagPrefix}_field")
                .clickable {
                    // 点整块打开的是当前唯一能改的那个选择器
                    if (showDate) {
                        dateOpenCount++
                        showDatePicker = true
                    } else {
                        timeOpenCount++
                        showTimePicker = true
                    }
                },
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (showDate) {
                OutlinedButton(
                    onClick = {
                        dateOpenCount++
                        showDatePicker = true
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("${tagPrefix}_date_button"),
                ) {
                    Text("日期 ${local?.format(DATE_FORMATTER) ?: "未设置"}")
                }
            }
            OutlinedButton(
                onClick = {
                    timeOpenCount++
                    showTimePicker = true
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("${tagPrefix}_time_button"),
            ) {
                Text("时间 ${local?.format(TIME_FORMATTER) ?: "未设置"}")
            }
        }
    }

    if (showDatePicker) {
        // key = 打开次数：每次打开都从当前字段值重新初始化，而不是沿用上次的选中状态
        val initial = remember(dateOpenCount) {
            local?.toLocalDate() ?: Instant.ofEpochMilli(defaultDate).atZone(zone).toLocalDate()
        }
        DatePickerDialog(
            initial = initial,
            onDismiss = { showDatePicker = false },
            onConfirm = { date ->
                val utcMidnight = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                applyDate(utcMidnight, local?.hour ?: DEFAULT_HOUR, local?.minute ?: 0)
                showDatePicker = false
            },
        )
    }

    if (showTimePicker) {
        val timeState = rememberTimePickerState(
            initialHour = local?.hour ?: DEFAULT_HOUR,
            initialMinute = local?.minute ?: 0,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = {
                Column {
                    Text("选择时间")
                    Text(
                        text = "%02d:%02d".format(timeState.hour, timeState.minute),
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .testTag("time_preview"),
                    )
                }
            },
            text = {
                CompositionLocalProvider(LocalConfiguration provides ChineseConfiguration()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        TimePicker(state = timeState)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SHORTCUT_MINUTES.forEach { delta ->
                                OutlinedButton(
                                    onClick = {
                                        val total = timeState.hour * 60 + timeState.minute + delta
                                        val normalized = ((total % 1440) + 1440) % 1440
                                        timeState.hour = normalized / 60
                                        timeState.minute = normalized % 60
                                    },
                                    // 收紧内边距，避免「-15」这类标签被挤成两行
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(
                                        text = shortcutLabel(delta),
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        applyTime(timeState.hour, timeState.minute)
                        showTimePicker = false
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("time_confirm"),
                ) { Text("确定", style = MaterialTheme.typography.titleMedium) }
            },
            dismissButton = {
                TextButton(
                    onClick = { showTimePicker = false },
                    modifier = Modifier.testTag("time_cancel"),
                ) { Text("取消") }
            },
        )
    }
}

private const val DEFAULT_HOUR = 9

/** 时间选择器的快捷调整（分钟）。配合下方按钮用于快速微调。 */
private val SHORTCUT_MINUTES = listOf(-15, 0, 15, 60)

private fun shortcutLabel(delta: Int): String = when {
    delta == 0 -> "准点"
    delta == 60 -> "+1时"
    delta > 0 -> "+$delta"
    else -> "$delta"
}

private val DISPLAY_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd")
private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private fun initialDateMillis(date: LocalDate?, zone: ZoneId): Long =
    (date ?: LocalDate.now(zone)).utcMidnightMillis()

/**
 * 把「UTC 午夜毫秒值 + 本地小时分钟」合成绝对时刻。
 *
 * DatePicker 用 UTC 午夜表示某一天，因此先按 UTC 还原年月日，再用本地时区组合时分。
 */
internal fun combineDateTime(utcMidnightMillis: Long, hour: Int, minute: Int, zone: ZoneId): Long {
    val date = Instant.ofEpochMilli(utcMidnightMillis).atZone(ZoneOffset.UTC).toLocalDate()
    return date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
}

/** 本地日期的 00:00 表示为「UTC 午夜毫秒值」，供 DatePicker 使用。 */
internal fun LocalDate.utcMidnightMillis(): Long =
    atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

/**
 * 构造一份中文的 [Configuration]，用于让 Material3 的日期/时间选择器显示中文。
 *
 * 选择器内部的月份名、星期名等文案来自 `LocalConfiguration`，应用里无法直接改；
 * 在对话框范围内覆盖它即可，不影响系统其他部分。
 */
@Composable
private fun ChineseConfiguration(): Configuration {
    val base = LocalConfiguration.current
    return remember(base) {
        Configuration(base).apply {
            setLocale(Locale.SIMPLIFIED_CHINESE)
        }
    }
}


/**
 * 日期选择对话框：日历 + 年月日三个独立输入框。
 *
 * 为什么不用 Material3 自带的文字输入模式：它严格按 Locale 短日期格式解析，
 * 中文环境下要求 2026/01/01 这种补零写法，用户输入 2026/1/1 会被判为非法，
 * 报错也不说明到底要什么格式。改成三个数字框后年月日各填各的，不再有格式歧义。
 *
 * 日历部分用自绘的 [CompactCalendar]：Material3 的 DatePicker 最小宽度大于
 * 对话框可用宽度，最右一列会被裁掉，用户已反馈过两次。
 */
@Composable
private fun DatePickerDialog(
    initial: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    var yearText by remember { mutableStateOf(initial.year.toString()) }
    var monthText by remember { mutableStateOf(initial.monthValue.toString()) }
    var dayText by remember { mutableStateOf(initial.dayOfMonth.toString()) }
    // 日历当前展示的月份，与三个输入框双向联动
    var shownMonth by remember { mutableStateOf(YearMonth.from(initial)) }

    val parsed = DateFormats.parseDateParts(yearText, monthText, dayText)

    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // 自绘日历：不再用 Material3 的 DatePicker（它最小宽度大于对话框可用宽度，
                // 最右一列会被裁掉）。点某天会同步刷新下面三个输入框。
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
                    // 同 CompactCalendar 的约定：箭头给「位移」，横滑给「绝对月份」。
                    // 以前箭头回调的是 month.plusMonths(1)（组合时捕获的月），连按会丢按。
                    onShiftMonth = { delta -> shownMonth = shownMonth.plusMonths(delta.toLong()) },
                    onSwipeMonth = { shownMonth = it },
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
                            .testTag("date_year"),
                    )
                    OutlinedTextField(
                        value = monthText,
                        onValueChange = { monthText = it.filter { c -> c.isDigit() }.take(2) },
                        label = { Text("月") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("date_month"),
                    )
                    OutlinedTextField(
                        value = dayText,
                        onValueChange = { dayText = it.filter { c -> c.isDigit() }.take(2) },
                        label = { Text("日") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("date_day"),
                    )
                }
                Text(
                    text = if (parsed == null) {
                        "请填写合法的年月日（例如 2026 / 1 / 1）"
                    } else {
                        "将设置为 " + DateFormats.fullDate(parsed)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (parsed == null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.testTag("date_preview"),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { parsed?.let(onConfirm) },
                enabled = parsed != null,
                modifier = Modifier.testTag("date_confirm"),
            ) { Text("确定") }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("date_cancel"),
            ) { Text("取消") }
        },
    )
}