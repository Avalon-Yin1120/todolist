package com.avalonyin1120.todolist.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import com.avalonyin1120.todolist.domain.Personalization
import com.avalonyin1120.todolist.domain.ReminderMode
import com.avalonyin1120.todolist.domain.TimeBucket
import com.avalonyin1120.todolist.domain.WeekWindow

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
    /** 打开「个性化」页 */
    onOpenPersonalization: () -> Unit = {},
    /** 版本与构建信息，用于「版本号」一行与详情弹窗 */
    version: VersionInfo = VersionInfo(),
    /** 当前本机待办数量，版本详情里展示 */
    todoCount: Int = 0,
) {
    var showVersionDetail by remember { mutableStateOf(false) }
    // 正在挑选的小时（周视图时段）；null 表示没有弹窗
    var hourPicker by remember { mutableStateOf<HourPicker?>(null) }

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
            DefaultModePicker(
                selected = state.defaultReminderMode,
                onSelect = { onChange(SettingsChange.DefaultMode(it)) },
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
                        "点「去设置」会打开本应用的「应用详情」页（厂商 ROM 的耗电管理入口都在它里面）：" +
                        "在里面把耗电管理设为「允许完全后台行为」，再顺手打开「允许自启动」。"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!batteryOptimizationIgnored) {
                TextButton(
                    onClick = onRequestIgnoreBatteryOptimization,
                    modifier = Modifier.testTag(TAG_BATTERY_EXEMPT),
                ) { Text("去设置") }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            SectionTitle("周视图显示时段")
            Text(
                text = "周视图只画这一段，超出的时间不显示；窗口越窄，每小时占的高度越大、" +
                    "色块越好认。默认为 ${WeekWindow.DEFAULT_START_HOUR}:00 – " +
                    "${WeekWindow.DEFAULT_END_HOUR}:00。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SettingRow(
                title = "起始时间",
                subtitle = "周视图最上面是几点",
                tag = TAG_WEEK_START_ROW,
                value = "%02d:00".format(state.weekViewStartHour),
                onClick = { hourPicker = HourPicker.WeekStart },
            )
            SettingRow(
                title = "结束时间",
                subtitle = "周视图最下面是几点",
                tag = TAG_WEEK_END_ROW,
                value = "%02d:00".format(state.weekViewEndHour),
                onClick = { hourPicker = HourPicker.WeekEnd },
            )
            TextButton(
                onClick = { onChange(SettingsChange.ResetWeekWindow) },
                modifier = Modifier.testTag(TAG_WEEK_RESET),
            ) { Text("恢复默认（06:00 – 22:00）") }

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

            SectionTitle("个性化")
            SettingRow(
                title = "颜色与快捷按钮",
                subtitle = "待办卡片的配色依据、" + "「提前提醒」的快捷按钮，" + "以及「一句话新建」开关",
                tag = TAG_OPEN_PERSONALIZATION,
                onClick = onOpenPersonalization,
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

    hourPicker?.let { picker ->
        HourPickerDialog(
            picker = picker,
            // 候选项按「另一个端点」算出来，因此选不出非法组合（间隔至少 2 小时）
            options = when (picker) {
                HourPicker.WeekStart -> WeekWindow.startOptions(state.weekViewEndHour)
                HourPicker.WeekEnd -> WeekWindow.endOptions(state.weekViewStartHour)
            },
            onPick = { hour ->
                when (picker) {
                    HourPicker.WeekStart -> onChange(SettingsChange.WeekStartHour(hour))
                    HourPicker.WeekEnd -> onChange(SettingsChange.WeekEndHour(hour))
                }
                hourPicker = null
            },
            onDismiss = { hourPicker = null },
        )
    }
}

/** 设置页里正在挑选的小时属于哪个端点。 */
private enum class HourPicker { WeekStart, WeekEnd }

/**
 * 小时选择弹窗。
 *
 * 用「候选项列表」而不是时间圆盘：这里只关心整点，圆盘反而难点准；
 * 候选项由调用方按区间生成，用户无论如何都选不出「结束早于开始」。
 */
@Composable
private fun HourPickerDialog(
    picker: HourPicker,
    options: IntRange,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(TAG_HOUR_PICKER_DIALOG),
        title = {
            Text(if (picker == HourPicker.WeekStart) "周视图起始时间" else "周视图结束时间")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                options.forEach { hour ->
                    Text(
                        text = "%02d:00".format(hour),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(hour) }
                            .padding(vertical = 10.dp)
                            .testTag(TAG_HOUR_OPTION_PREFIX + hour),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag(TAG_HOUR_PICKER_CANCEL),
            ) { Text("取消") }
        },
    )
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

/**
 * 「新建待办时默认选择」的三档选择器。
 *
 * 用和新增表单里「提醒方式」完全一样的 FilterChip 三连（同样的顺序、同样的措辞）：
 * 设置页选的这一项，就是用户在表单里会看到的选中态，两处长得一样才一眼对得上。
 * 一个开关只能表达「闹钟式 / 非闹钟式」，而「默认不通知」是真实存在的诉求。
 */
@Composable
private fun DefaultModePicker(
    selected: ReminderMode,
    onSelect: (ReminderMode) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text("新建待办时默认选择", style = MaterialTheme.typography.bodyLarge)
        Text(
            text = "新建待办时会预先选中这一项，之后每条待办都能单独改。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 直接按枚举声明顺序排（NOTIFICATION / ALARM / NONE），与表单里三连的顺序一致，
            // 避免两处肌肉记忆打架。枚举顺序本身是存储契约的一部分（见 ReminderMode 的注释），
            // 不会为了排版好看被人调换，所以这里可以放心用它。
            ReminderMode.entries.forEach { mode ->
                FilterChip(
                    selected = mode == selected,
                    onClick = { onSelect(mode) },
                    label = { Text(defaultModeLabel(mode)) },
                    modifier = Modifier
                        .weight(1f)
                        // 标签按枚举名生成（TAG_DEFAULT_MODE_ALARM 等），
                        // 以后加档位不用手写新常量，测试也能直接按枚举名找控件
                        .testTag(TAG_DEFAULT_MODE_PREFIX + mode.name),
                )
            }
        }
    }
}

/**
 * 三档的中文名。
 *
 * 与表单里的 FilterChip 文案必须一致（那边是写死的三个字面量，这里按枚举取），
 * 改文案时两处一起改——否则设置页说「闹钟式」、表单里写别的名字，用户会以为是两个功能。
 */
private fun defaultModeLabel(mode: ReminderMode): String = when (mode) {
    ReminderMode.NOTIFICATION -> "通知"
    ReminderMode.ALARM -> "闹钟式"
    ReminderMode.NONE -> "不通知"
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
    /**
     * 用户在「提醒可靠性检查」页亲手勾过的「我已设置好」项（step id 集合）。
     *
     * 放进界面状态是因为顶部的「有 N 项未开启」必须与检查页同源：
     * 那些读不到状态的开关只能靠用户确认，勾过之后横幅就该消失，
     * 否则用户明明按引导做完了，主界面还一直挂着一句「未开启」。
     */
    val confirmedHealthItems: Set<String> = emptySet(),
    /** 新建待办时预先选中的提醒方式（通知 / 闹钟式 / 不通知）。 */
    val defaultReminderMode: ReminderMode = ReminderMode.NOTIFICATION,
    val confirmBeforeDelete: Boolean = true,
    val warnOnOverlap: Boolean = true,
    val lastLeadMinutes: Int = 0,
    /** 通知提示音开关；关闭后提醒仍会弹出，只是不出声 */
    val notificationSound: Boolean = true,
    /** 周视图纵向显示的起始小时 */
    val weekViewStartHour: Int = WeekWindow.DEFAULT_START_HOUR,
    /** 周视图纵向显示的结束小时 */
    val weekViewEndHour: Int = WeekWindow.DEFAULT_END_HOUR,
    /** 个性化：卡片配色依据、各类颜色、提前提醒快捷按钮。 */
    val personalization: Personalization = Personalization(),
    /** 「一句话新建待办」是否启用（设置里可关）。 */
    val naturalLanguageEnabled: Boolean = true,
) {
    /** 时间轴窗口文案，例如 `06:00 – 22:00`。 */
    fun weekWindowLabel(): String =
        "%02d:00 – %02d:00".format(weekViewStartHour, weekViewEndHour)
}

/** 设置项的变更意图。 */
sealed interface SettingsChange {
    /** 新建待办的默认提醒方式（三档：通知 / 闹钟式 / 不通知）。 */
    data class DefaultMode(val value: ReminderMode) : SettingsChange
    data class ConfirmDelete(val value: Boolean) : SettingsChange
    data class WarnOverlap(val value: Boolean) : SettingsChange
    data class NotificationSound(val value: Boolean) : SettingsChange

    /** 周视图起始小时；越界组合由 ViewModel 统一夹取。 */
    data class WeekStartHour(val value: Int) : SettingsChange

    /** 周视图结束小时。 */
    data class WeekEndHour(val value: Int) : SettingsChange

    /** 恢复周视图时段的默认值（06:00 – 22:00）。 */
    data object ResetWeekWindow : SettingsChange

    // ---------------------------------------------------------------- 个性化

    /** 卡片配色的依据：按状态 / 按提醒方式 / 统一颜色。 */
    data class CardColorMode(
        val value: com.avalonyin1120.todolist.domain.CardColorMode,
    ) : SettingsChange

    /** 某个状态的强调色（ARGB）。 */
    data class StatusColor(val bucket: TimeBucket, val argb: Long) : SettingsChange

    /** 某种提醒方式的强调色（ARGB）。 */
    data class ReminderColor(val mode: ReminderMode, val argb: Long) : SettingsChange

    /** 勾选 / 取消表单里某个「提前提醒」快捷按钮。 */
    data class ToggleLeadPreset(val minutes: Int) : SettingsChange

    /** 个性化整体恢复默认（界面上还要二次确认）。 */
    data object ResetPersonalization : SettingsChange

    /** 「一句话新建待办」开关。 */
    data class NaturalLanguage(val value: Boolean) : SettingsChange
}

/**
 * 「新建待办时默认选择」三档选择器的测试标签前缀。
 *
 * 拼上 [ReminderMode.name] 才是完整标签，例如 `TAG_DEFAULT_MODE_ALARM`。
 * 让标签由枚举名生成而不是写三个常量：选档控件与档位一一对应，
 * 以后补档位时不会出现「有按钮、没标签」。
 */
const val TAG_DEFAULT_MODE_PREFIX = "setting_default_mode_"
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
const val TAG_WEEK_START_ROW = "week_start_row"
const val TAG_WEEK_END_ROW = "week_end_row"
const val TAG_WEEK_RESET = "week_reset"
const val TAG_HOUR_PICKER_DIALOG = "hour_picker_dialog"
const val TAG_HOUR_PICKER_CANCEL = "hour_picker_cancel"
const val TAG_HOUR_OPTION_PREFIX = "hour_option_"
const val TAG_OPEN_PERSONALIZATION = "open_personalization"