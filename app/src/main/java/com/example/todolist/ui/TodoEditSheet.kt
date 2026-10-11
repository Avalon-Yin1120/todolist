package com.example.todolist.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.todolist.domain.FormError
import com.example.todolist.domain.FormValidator
import com.example.todolist.domain.ReminderTime

/**
 * 新增 / 编辑待办的表单。
 *
 * 表单状态放在 [DraftFormStateHolder] 里而不是 composable 的 remember 中，
 * 这样旋转屏幕、列表刷新都不会丢用户已填的内容。
 */
@Composable
fun TodoEditSheet(
    form: DraftFormStateHolder,
    isEditing: Boolean,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit,
) {
    val state by form.state.collectAsStateWithLifecycle()
    val errors by form.errors.collectAsStateWithLifecycle()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) "编辑待办" else "新增待办") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
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
                        AssistChip(
                            onClick = { form.setLeadMinutes(minutes) },
                            label = { Text(quickLeadLabel(minutes)) },
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
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("使用精确闹钟", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = "关闭时使用普通通知，系统可能延后几分钟",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = state.useExactAlarm,
                        // 只切换策略，不在这里弹系统设置：切换开关同时弹窗会打断用户操作，
                        // 授权时机交给界面顶部的降级横幅（「去开启」按钮）。
                        onCheckedChange = form::setUseExactAlarm,
                        modifier = Modifier.testTag(TAG_EXACT_SWITCH),
                    )
                }
                Spacer(Modifier.height(4.dp))
            }
        },
        confirmButton = {
            TextButton(
                onClick = onSubmit,
                modifier = Modifier.testTag(TAG_SAVE_BUTTON),
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
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
    val remindAt = ReminderTime.remindAt(start, state.leadMinutes)
    val formatter = java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm")
    val zoned = java.time.Instant.ofEpochMilli(remindAt).atZone(java.time.ZoneId.systemDefault())
    val suffix = if (state.useExactAlarm) "（精确闹钟）" else "（普通通知）"
    return zoned.format(formatter) + suffix
}
