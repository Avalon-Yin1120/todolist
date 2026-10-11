package com.example.todolist.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * 日期 + 时间选择字段。
 *
 * 时间戳统一为 epoch 毫秒；选择器内部用「本地时区」解释用户选的年月日与时分，
 * 因此跨时区/跨夏令时都能得到正确的绝对时刻。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimePickerField(
    label: String,
    value: Long?,
    onValueChange: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDateDialog by remember { mutableStateOf(false) }
    var showTimeDialog by remember { mutableStateOf(false) }

    val zone = remember { ZoneId.systemDefault() }
    val local = remember(value) {
        value?.let { Instant.ofEpochMilli(it).atZone(zone) }
    }
    val dateState = rememberDatePickerState(
        initialSelectedDateMillis = local?.toLocalDate()?.utcMidnightMillis() ?: defaultTodayUtc()
    )
    val timeState = rememberTimePickerState(
        initialHour = local?.hour ?: 9,
        initialMinute = local?.minute ?: 0,
        is24Hour = true,
    )

    Column(modifier = modifier) {
        OutlinedTextField(
            value = local?.format(DISPLAY_FORMATTER).orEmpty(),
            onValueChange = { },
            readOnly = true,
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showDateDialog = true },
            trailingIcon = {
                TextButton(onClick = { showTimeDialog = true }) { Text("时间", style = MaterialTheme.typography.labelMedium) }
            },
        )
    }

    if (showDateDialog) {
        DatePickerDialog(
            onDismissRequest = { showDateDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    showDateDialog = false
                    showTimeDialog = true
                }) { Text("下一步") }
            },
            dismissButton = {
                TextButton(onClick = { showDateDialog = false }) { Text("取消") }
            },
        ) {
            DatePicker(state = dateState)
        }
    }

    if (showTimeDialog) {
        Dialog(onDismissRequest = { showTimeDialog = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Text("选择时间", style = MaterialTheme.typography.titleMedium)
                TimePicker(state = timeState)
                TextButton(
                    onClick = {
                        val dateMillis = dateState.selectedDateMillis ?: defaultTodayUtc()
                        onValueChange(combine(dateMillis, timeState.hour, timeState.minute, zone))
                        showTimeDialog = false
                    },
                ) { Text("确定") }
                TextButton(onClick = { showTimeDialog = false }) { Text("取消") }
            }
        }
    }
}

private val DISPLAY_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

/** 把「UTC 午夜的毫秒值 + 本地小时分钟」合成绝对时刻。 */
internal fun combine(dateUtcMillis: Long, hour: Int, minute: Int, zone: ZoneId): Long {
    val date = Instant.ofEpochMilli(dateUtcMillis).atZone(ZoneOffset.UTC).toLocalDate()
    return date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()
}

/** 本地日期的 00:00 表示为「UTC 午夜毫秒值」，供 DatePicker 使用。 */
internal fun LocalDate.utcMidnightMillis(): Long =
    atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun defaultTodayUtc(): Long =
    LocalDate.now(ZoneId.systemDefault()).utcMidnightMillis()
