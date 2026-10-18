package com.avalonyin1120.todolist.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.avalonyin1120.todolist.domain.CardColorMode
import com.avalonyin1120.todolist.domain.Personalization
import com.avalonyin1120.todolist.domain.ReminderMode
import com.avalonyin1120.todolist.domain.TimeBucket

/**
 * 「个性化」页。
 *
 * 三件事：卡片按什么上色（以及具体颜色）、表单里「提前提醒」那排快捷按钮有哪些、
 * **恢复默认**（二次确认）。
 *
 * 为什么颜色只给固定色板而不是取色器：取色器能挑出「浅底配浅字」这种看不清的组合，
 * 而这里浅底是由强调色算出来的，选什么都不会糊。这是刻意的限制，不是偷懒。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PersonalizationScreen(
    state: SettingsUiState,
    onChange: (SettingsChange) -> Unit,
    onBack: () -> Unit,
) {
    val personalization = state.personalization
    var confirmReset by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.testTag(TAG_PERSONALIZATION_SCREEN),
        topBar = {
            TopAppBar(
                title = { Text("个性化", fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    TextButton(onClick = onBack, modifier = Modifier.testTag(TAG_PERSONALIZATION_BACK)) {
                        Text("返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ------------------------------------------------------------ 配色依据
            SettingsCard(title = "卡片颜色") {
                Text(
                    text = "选一种依据，卡片底色会跟着它变。已完成一律是灰色（做完了就该一眼看出来）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CardColorMode.entries.forEach { mode ->
                        FilterChip(
                            selected = personalization.colorMode == mode,
                            onClick = { onChange(SettingsChange.CardColorMode(mode)) },
                            label = { Text(mode.label) },
                            modifier = Modifier.testTag("$TAG_COLOR_MODE_PREFIX${mode.name}"),
                        )
                    }
                }
            }

            // 完成态是独立维度：不管按状态还是按方式上色，「已完成」都用这一行选的颜色
            SettingsCard(title = "已完成") {
                Text(
                    text = "无论上面选哪种依据，「已完成」的待办都用这个颜色。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ColorRow(
                    label = "已完成",
                    selected = personalization.doneColor,
                    onPick = { argb -> onChange(SettingsChange.DoneColor(argb)) },
                    tagPrefix = TAG_DONE_COLOR_PREFIX,
                )
            }
            when (personalization.colorMode) {
                CardColorMode.STATUS -> SettingsCard(title = "按状态配色") {
                    TimeBucket.entries.forEach { bucket ->
                        ColorRow(
                            label = bucketLabelOf(bucket),
                            selected = personalization.statusColor(bucket),
                            onPick = { argb -> onChange(SettingsChange.StatusColor(bucket, argb)) },
                            tagPrefix = "$TAG_STATUS_COLOR_PREFIX${bucket.name}_",
                        )
                    }
                }

                CardColorMode.REMINDER -> SettingsCard(title = "按提醒方式配色") {
                    ReminderMode.entries.forEach { mode ->
                        ColorRow(
                            label = reminderModeLabelOf(mode),
                            selected = personalization.reminderColor(mode),
                            onPick = { argb -> onChange(SettingsChange.ReminderColor(mode, argb)) },
                            tagPrefix = "$TAG_REMINDER_COLOR_PREFIX${mode.name}_",
                        )
                    }
                }

                CardColorMode.MONO -> SettingsCard(title = "统一颜色") {
                    Text(
                        text = "所有待办用同一种颜色，靠左侧色条与状态文字区分。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ------------------------------------------------------------ 提前提醒按钮
            SettingsCard(title = "「提前提醒」的快捷按钮") {
                Text(
                    text = "最多 ${Personalization.MAX_PRESETS} 个。勾上的会出现在新建待办的表单里，" +
                        "输入框仍然可以填任意分钟数。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Personalization.MINUTE_CHOICES.forEach { minutes ->
                        FilterChip(
                            selected = minutes in personalization.leadPresets,
                            onClick = { onChange(SettingsChange.ToggleLeadPreset(minutes)) },
                            label = { Text(if (minutes in personalization.leadPresets) "✓ " + leadPresetLabel(minutes) else leadPresetLabel(minutes)) },
                            modifier = Modifier.testTag("$TAG_LEAD_PRESET_PREFIX$minutes"),
                        )
                    }
                }
            }

            // ------------------------------------------------------------ 一句话新建
            SettingsCard(title = "一句话新建待办") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("启用「一句话」输入框", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = "在新增待办页顶部输入「明天下午三点开会，提前 20 分钟」，自动填好时间与重复。关掉后该输入框整块消失。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = state.naturalLanguageEnabled,
                        onCheckedChange = { onChange(SettingsChange.NaturalLanguage(it)) },
                        modifier = Modifier.testTag(TAG_NATURAL_LANGUAGE_SWITCH),
                    )
                }
            }

            // ------------------------------------------------------------ 恢复默认
            SettingsCard(title = "恢复默认") {
                Text(
                    text = "把上面的配色、快捷按钮与「一句话新建」都恢复成出厂设置。" +
                        "待办数据不受影响；此操作不能撤销。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = { confirmReset = true },
                    enabled = !personalization.isDefault || !state.naturalLanguageEnabled,
                    modifier = Modifier.testTag(TAG_RESET_PERSONALIZATION),
                ) { Text("恢复默认设置") }
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("恢复默认？") },
            text = {
                Text(
                    "配色、快捷按钮与「一句话新建」都会恢复出厂设置，待办本身不受影响。" +
                        "这一步不能撤销。"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmReset = false
                        onChange(SettingsChange.ResetPersonalization)
                        onChange(SettingsChange.NaturalLanguage(true))
                    },
                    modifier = Modifier.testTag(TAG_RESET_CONFIRM),
                ) { Text("恢复默认") }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }, modifier = Modifier.testTag(TAG_RESET_CANCEL)) {
                    Text("取消")
                }
            },
        )
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

/** 一行「名称 + 一排色点」。选中项画一个外圈。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorRow(
    label: String,
    selected: Long,
    onPick: (Long) -> Unit,
    tagPrefix: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Personalization.PALETTE_CHOICES.forEach { argb ->
                val color = Color(argb)
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(color)
                        .then(
                            if (argb == selected) {
                                Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                            } else {
                                Modifier
                            }
                        )
                        .clickable { onPick(argb) }
                        .testTag("$tagPrefix${Personalization.hex(argb)}"),
                )
            }
        }
    }
}

private fun bucketLabelOf(bucket: TimeBucket): String = when (bucket) {
    TimeBucket.FUTURE -> "未来（还没到提醒时间）"
    TimeBucket.SOON -> "即将（提醒已响、还没开始）"
    TimeBucket.ONGOING -> "进行中"
    TimeBucket.PAST -> "已过去"
}

private fun reminderModeLabelOf(mode: ReminderMode): String = when (mode) {
    ReminderMode.NOTIFICATION -> "通知"
    ReminderMode.ALARM -> "闹钟式"
    ReminderMode.NONE -> "不通知"
}

private fun leadPresetLabel(minutes: Int): String = when (minutes) {
    0 -> "准点"
    60 -> "1 小时"
    120 -> "2 小时"
    1440 -> "1 天"
    else -> "$minutes 分钟"
}


const val TAG_PERSONALIZATION_SCREEN = "personalization_screen"
const val TAG_PERSONALIZATION_BACK = "personalization_back"
const val TAG_COLOR_MODE_PREFIX = "color_mode_"
const val TAG_STATUS_COLOR_PREFIX = "status_color_"
const val TAG_REMINDER_COLOR_PREFIX = "reminder_color_"
const val TAG_LEAD_PRESET_PREFIX = "lead_preset_"
const val TAG_NATURAL_LANGUAGE_SWITCH = "natural_language_switch"
const val TAG_RESET_PERSONALIZATION = "reset_personalization"
const val TAG_RESET_CONFIRM = "reset_personalization_confirm"
const val TAG_RESET_CANCEL = "reset_personalization_cancel"
const val TAG_DONE_COLOR_PREFIX = "done_color_"
const val TAG_CUSTOM_LEAD_FIELD = "custom_lead_field"
const val TAG_CUSTOM_LEAD_ADD = "custom_lead_add"