package com.example.todolist.ui

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimePickerField(
    label: String,
    value: Long?,
    onValueChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
    tagPrefix: String = label,
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
            value = local?.format(DISPLAY_FORMATTER).orEmpty(),
            onValueChange = { /* 只读展示 */ },
            readOnly = true,
            singleLine = true,
            label = { Text(label) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("${tagPrefix}_field")
                .clickable {
                    dateOpenCount++
                    showDatePicker = true
                },
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
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
        val datePickerState = remember(dateOpenCount) {
            initialDateMillis(local?.toLocalDate(), zone)
        }
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = datePickerState,
        )
        AlertDialog(
            onDismissRequest = { showDatePicker = false },
            title = { Text("选择日期") },
            text = {
                // 用中文语境渲染日历：Material3 的日期/时间选择器文案取自
                // LocalConfiguration，而不是应用里的硬编码字符串。
                CompositionLocalProvider(LocalConfiguration provides ChineseConfiguration()) {
                    DatePicker(state = pickerState)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val picked = pickerState.selectedDateMillis
                        if (picked != null) {
                            applyDate(picked, local?.hour ?: DEFAULT_HOUR, local?.minute ?: 0)
                        }
                        showDatePicker = false
                    },
                    modifier = Modifier.testTag("date_confirm"),
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDatePicker = false },
                    modifier = Modifier.testTag("date_cancel"),
                ) { Text("取消") }
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
