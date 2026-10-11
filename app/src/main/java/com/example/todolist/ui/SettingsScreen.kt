package com.example.todolist.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 设置页（独立界面，不再是弹窗）。
 *
 * 用户在这里决定「默认怎么提醒我」，以及一些与打扰程度相关的偏好。
 * 精确闹钟这类实现细节放在这里说明，不出现在新增表单里。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onChange: (SettingsChange) -> Unit,
    onBack: () -> Unit,
    exactAlarmAllowed: Boolean,
    onRequestExactAlarmPermission: () -> Unit,
    /** 是否已允许后台运行（电池优化白名单） */
    batteryOptimizationIgnored: Boolean = true,
    onRequestIgnoreBatteryOptimization: () -> Unit = {},
) {
    Scaffold(
        modifier = Modifier.testTag(TAG_SETTINGS_SCREEN),
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag(TAG_SETTINGS_BACK),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SectionTitle("提醒")
            SettingSwitch(
                title = "默认使用闹钟式提醒",
                subtitle = "新建待办时默认选择「闹钟式」：到点响铃、点亮屏幕并弹出提醒页",
                checked = state.useAlarmForReminders,
                tag = TAG_SETTING_ALARM,
                onCheckedChange = { onChange(SettingsChange.UseAlarm(it)) },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SectionTitle("精确提醒权限")
            Text(
                text = if (exactAlarmAllowed) {
                    "已允许。闹钟式提醒会精确到分钟触发。"
                } else {
                    "未允许。闹钟式提醒会被系统推迟，可能晚几分钟；" +
                        "在系统设置的「闹钟和提醒」里允许本应用即可精确触发。"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!exactAlarmAllowed) {
                TextButton(onClick = onRequestExactAlarmPermission) { Text("去开启") }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SectionTitle("后台运行")
            Text(
                text = if (batteryOptimizationIgnored) {
                    "已允许后台运行，到点提醒不会被系统限制。"
                } else {
                    "系统可能限制本应用在后台运行，导致到点提醒变晚甚至不响。" +
                        "允许后台运行后，闹钟式提醒才能准时响铃并亮屏。"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!batteryOptimizationIgnored) {
                TextButton(
                    onClick = onRequestIgnoreBatteryOptimization,
                    modifier = Modifier.testTag(TAG_BATTERY_EXEMPT),
                ) { Text("允许后台运行") }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SectionTitle("待办行为")
            SettingSwitch(
                title = "删除前二次确认",
                subtitle = "删除待办前弹出确认框，避免误删",
                checked = state.confirmBeforeDelete,
                tag = TAG_SETTING_CONFIRM_DELETE,
                onCheckedChange = { onChange(SettingsChange.ConfirmDelete(it)) },
            )
            SettingSwitch(
                title = "时间段冲突时提示",
                subtitle = "新建的待办与该时段已有待办重叠时给出提示（仍可创建）",
                checked = state.warnOnOverlap,
                tag = TAG_SETTING_WARN_OVERLAP,
                onCheckedChange = { onChange(SettingsChange.WarnOverlap(it)) },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SectionTitle("默认提前提醒")
            Text(
                text = "新建待办时默认沿用你上次填写的提前量：" +
                    if (state.lastLeadMinutes == 0) "准点" else "${state.lastLeadMinutes} 分钟前",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    tag: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(tag),
        )
    }
}

/** 设置项的快照。 */
data class SettingsUiState(
    val useAlarmForReminders: Boolean = true,
    val confirmBeforeDelete: Boolean = true,
    val warnOnOverlap: Boolean = true,
    val lastLeadMinutes: Int = 0,
)

/** 设置项的变更意图。 */
sealed interface SettingsChange {
    data class UseAlarm(val value: Boolean) : SettingsChange
    data class ConfirmDelete(val value: Boolean) : SettingsChange
    data class WarnOverlap(val value: Boolean) : SettingsChange
}

const val TAG_SETTING_ALARM = "setting_alarm"
const val TAG_SETTING_CONFIRM_DELETE = "setting_confirm_delete"
const val TAG_SETTING_WARN_OVERLAP = "setting_warn_overlap"
const val TAG_BATTERY_EXEMPT = "battery_exempt"
