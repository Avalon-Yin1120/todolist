package com.example.todolist.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 设置页（独立界面，不再是弹窗）。
 *
 * 分组顺序按「用户最可能要改的东西」排：提醒 → 权限 → 后台 → 待办行为 → 关于。
 * 精确闹钟这类实现细节放在这里说明，不出现在新增表单里。
 *
 * 「关于」一组的三个入口（关于待办提醒 / 隐私 / 版本号）都做成了可点整行，
 * 而不是只写一段静态文字——用户找「这个 App 到底会不会上传我的数据」时
 * 需要的是能点进去看的地方。
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
    /** 打开「提醒可靠性检查」页 */
    onOpenReminderHealth: () -> Unit = {},
    /** 打开「关于待办提醒」页 */
    onOpenAbout: () -> Unit = {},
    /** 打开「隐私」页 */
    onOpenPrivacy: () -> Unit = {},
    /** 版本与构建信息，用于「版本号」一行与详情弹窗 */
    version: VersionInfo = VersionInfo(),
    /** 当前本机待办数量，版本详情里展示 */
    todoCount: Int = 0,
) {
    var showVersionDetail by remember { mutableStateOf(false) }

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
            SettingSwitch(
                title = "通知提示音",
                subtitle = if (state.notificationSound) {
                    "通知式提醒到达时响一下（闹钟式提醒的铃声不受此开关影响）"
                } else {
                    "通知式提醒已静音：仍会弹出通知，但不出声"
                },
                checked = state.notificationSound,
                tag = TAG_SETTING_NOTIFICATION_SOUND,
                onCheckedChange = { onChange(SettingsChange.NotificationSound(it)) },
            )

            // 一站式检查：通知权限 / 精确闹钟 / 后台运行 / 全屏提醒 / 国产 ROM 自启动
            TextButton(
                onClick = onOpenReminderHealth,
                modifier = Modifier.testTag(TAG_OPEN_REMINDER_HEALTH),
            ) { Text("提醒可靠性检查（到点不响时先看这里）") }

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

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SectionTitle("关于")
            SettingRow(
                title = "关于待办提醒",
                subtitle = "提醒是怎么工作的、有哪些功能、权限都用在哪",
                tag = TAG_OPEN_ABOUT,
                onClick = onOpenAbout,
            )
            SettingRow(
                title = "隐私",
                subtitle = "数据只存在这台手机上，不联网、不上传",
                tag = TAG_OPEN_PRIVACY,
                onClick = onOpenPrivacy,
            )
            SettingRow(
                title = "版本号",
                subtitle = "点击查看构建与存储详情",
                value = version.shortLabel(),
                tag = TAG_VERSION_ROW,
                onClick = { showVersionDetail = true },
            )

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showVersionDetail) {
        VersionDetailDialog(
            version = version,
            todoCount = todoCount,
            onDismiss = { showVersionDetail = false },
        )
    }
}

/**
 * 版本详情。
 *
 * 排查「提醒不响」时经常需要确认用户装的到底是哪个包、哪个版本，
 * 所以把包名、版本号、数据库版本、系统 API 级别一并列出来，方便用户直接截图。
 */
@Composable
private fun VersionDetailDialog(
    version: VersionInfo,
    todoCount: Int,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(TAG_VERSION_DIALOG),
        title = { Text("版本信息") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                InfoLine("应用名称", "待办提醒")
                InfoLine("版本号", version.shortLabel())
                InfoLine("包名", version.packageName)
                InfoLine("构建类型", version.buildTypeLabel)
                InfoLine("数据库版本", "v${version.databaseVersion}")
                InfoLine("最低/目标系统", "Android ${version.minSdk} / ${version.targetSdk}")
                InfoLine("当前设备", "Android ${version.deviceSdk}")
                InfoLine("本机待办", "$todoCount 条")
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag(TAG_VERSION_CLOSE),
            ) { Text("知道了") }
        },
    )
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
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

/** 可点击的整行设置项（用于「关于」这类跳转入口）。 */
@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    tag: String,
    onClick: () -> Unit,
    value: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp)
            .testTag(tag),
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
        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 设置项的快照。 */
data class SettingsUiState(
    val useAlarmForReminders: Boolean = true,
    val confirmBeforeDelete: Boolean = true,
    val warnOnOverlap: Boolean = true,
    val lastLeadMinutes: Int = 0,
    /** 通知提示音开关；关闭后提醒仍会弹出，只是不出声 */
    val notificationSound: Boolean = true,
)

/** 设置项的变更意图。 */
sealed interface SettingsChange {
    data class UseAlarm(val value: Boolean) : SettingsChange
    data class ConfirmDelete(val value: Boolean) : SettingsChange
    data class WarnOverlap(val value: Boolean) : SettingsChange
    data class NotificationSound(val value: Boolean) : SettingsChange
}

const val TAG_SETTING_ALARM = "setting_alarm"
const val TAG_SETTING_CONFIRM_DELETE = "setting_confirm_delete"
const val TAG_SETTING_WARN_OVERLAP = "setting_warn_overlap"
const val TAG_SETTING_NOTIFICATION_SOUND = "setting_notification_sound"
const val TAG_BATTERY_EXEMPT = "battery_exempt"
const val TAG_OPEN_REMINDER_HEALTH = "open_reminder_health"
const val TAG_OPEN_ABOUT = "open_about"
const val TAG_OPEN_PRIVACY = "open_privacy"
const val TAG_VERSION_ROW = "version_row"
const val TAG_VERSION_DIALOG = "version_dialog"
const val TAG_VERSION_CLOSE = "version_close"
