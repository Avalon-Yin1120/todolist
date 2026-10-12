package com.example.todolist.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.todolist.sys.ReminderHealth

/**
 * 「提醒可靠性检查」页。
 *
 * 存在的理由：真机上「到点不响」几乎全是系统开关问题，而它们在设置里散落在不同页面，
 * 用户根本不知道要找哪些。这里把每一项的状态、为什么需要、去哪里开都摆在一起，
 * 并在缺项时明确告诉用户「关掉这项就会漏提醒」。
 *
 * 国产 ROM（ColorOS / MIUI / EMUI …）的自启动与后台管理没有稳定的标准 Intent，
 * 硬跳容易跳到错误页面，因此这一项给出文字路径 + 一个跳「应用信息」的按钮。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderHealthScreen(
    canPostNotifications: Boolean,
    canScheduleExact: Boolean,
    batteryOptimizationIgnored: Boolean,
    fullScreenIntentAllowed: Boolean,
    needsOemBackgroundGuide: Boolean,
    /** 当前提醒方式是否是闹钟式：只有闹钟式才关心全屏提醒 */
    alarmModeInUse: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onRequestExactAlarm: () -> Unit,
    onRequestIgnoreBatteryOptimization: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val missing = listOf(
        canPostNotifications,
        canScheduleExact,
        batteryOptimizationIgnored,
        // 全屏提醒只在「闹钟式 + Android 14+」下才影响体验
        fullScreenIntentAllowed || !alarmModeInUse,
    ).count { !it }

    Scaffold(
        modifier = Modifier.testTag(TAG_HEALTH_SCREEN),
        topBar = {
            TopAppBar(
                title = { Text("提醒可靠性检查") },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag(TAG_HEALTH_BACK),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = if (missing == 0) {
                    "所有关键开关都已打开，到点会正常提醒 ✓"
                } else {
                    "还有 $missing 项没打开，缺任意一项都可能「到点了什么都不响」："
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (missing == 0) FontWeight.Normal else FontWeight.SemiBold,
                color = if (missing == 0) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
                modifier = Modifier.padding(top = 8.dp),
            )

            HealthItem(
                ok = canPostNotifications,
                title = "通知权限",
                why = "没有它，系统会直接丢弃提醒通知，界面里也不会出现任何提示。",
                actionLabel = if (canPostNotifications) "打开系统通知设置" else "授予通知权限",
                tag = TAG_HEALTH_NOTIFICATION,
                onAction = {
                    if (canPostNotifications) {
                        ReminderHealth.openNotificationSettings(context)
                    } else {
                        onRequestNotificationPermission()
                    }
                },
                secondaryLabel = "系统通知设置",
                onSecondary = { ReminderHealth.openNotificationSettings(context) },
            )

            HealthItem(
                ok = canScheduleExact,
                title = "精确闹钟",
                why = "闹钟式提醒靠它才能「准时」响铃并亮屏；没有它只能退化成普通通知，" +
                    "而且系统可能延后几分钟到几十分钟。",
                actionLabel = "去开启",
                tag = TAG_HEALTH_EXACT_ALARM,
                onAction = onRequestExactAlarm,
            )

            HealthItem(
                ok = batteryOptimizationIgnored,
                title = "允许后台运行",
                why = "系统的省电策略会把后台应用冻结，闹钟和通知都会因此不响。" +
                    "加入电池优化白名单后，到点才能被唤醒。",
                actionLabel = "去允许",
                tag = TAG_HEALTH_BATTERY,
                onAction = onRequestIgnoreBatteryOptimization,
            )

            HealthItem(
                ok = fullScreenIntentAllowed || !alarmModeInUse,
                title = "全屏提醒（Android 14+）",
                why = if (!alarmModeInUse) {
                    "当前用的是通知方式，不需要这一项；改用闹钟式后会需要它。"
                } else {
                    "Android 14 起，系统可能把闹钟的整屏提醒降级成一条横幅通知，" +
                        "也就是「不弹页面、只在状态栏出现」。"
                },
                actionLabel = "去允许",
                tag = TAG_HEALTH_FULL_SCREEN,
                enabled = alarmModeInUse,
                onAction = { ReminderHealth.openFullScreenIntentSettings(context) },
            )

            if (needsOemBackgroundGuide) {
                OemGuideCard(onOpenAppDetails = { ReminderHealth.openAppDetailsSettings(context) })
            }

            Text(
                text = "提醒的可靠性由系统决定，本应用只能把能开的开关都打开。" +
                    "设置完成后回到本页会自动重新检查。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** 单项检查：状态点 + 标题 + 原因 + 操作。 */
@Composable
private fun HealthItem(
    ok: Boolean,
    title: String,
    why: String,
    actionLabel: String,
    tag: String,
    onAction: () -> Unit,
    enabled: Boolean = true,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(tag),
        colors = CardDefaults.cardColors(
            containerColor = if (ok) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.errorContainer
            },
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (ok) "✓" else "!",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (ok) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
                Spacer(Modifier.padding(horizontal = 4.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.padding(horizontal = 4.dp))
                Text(
                    text = if (ok) "已开启" else "未开启",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = why,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!ok && enabled) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onAction) { Text(actionLabel) }
                    if (secondaryLabel != null && onSecondary != null) {
                        TextButton(onClick = onSecondary) { Text(secondaryLabel) }
                    }
                }
            } else if (ok && secondaryLabel != null && onSecondary != null) {
                TextButton(onClick = onSecondary) { Text(secondaryLabel) }
            }
        }
    }
}

/** 国产 ROM 专项：自启动 / 后台管理 / 锁屏显示。 */
@Composable
private fun OemGuideCard(onOpenAppDetails: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TAG_HEALTH_OEM),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "国产 ROM 需要额外设置（自启动 / 后台管理）",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "OPPO / 一加 / realme（ColorOS）：设置 → 应用管理 → 本应用 → " +
                    "① 允许自启动 ② 电池 → 允许后台活动（不优化）" +
                    "③ 权限管理 → 通知 → 允许打扰/锁屏显示。\n" +
                    "小米（MIUI/澎湃）：设置 → 应用设置 → 本应用 → 自启动 + 省电策略“无限制”。\n" +
                    "华为 / 荣耀：设置 → 应用启动管理 → 本应用 → 手动管理，三个开关全开。\n" +
                    "vivo（OriginOS）：设置 → 电池 → 后台高耗电 → 允许本应用。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = onOpenAppDetails,
                modifier = Modifier.testTag(TAG_HEALTH_OEM_SETTINGS),
            ) { Text("打开本应用的应用信息页") }
        }
    }
}

const val TAG_HEALTH_SCREEN = "reminder_health_screen"
const val TAG_HEALTH_BACK = "reminder_health_back"
const val TAG_HEALTH_NOTIFICATION = "health_notification"
const val TAG_HEALTH_EXACT_ALARM = "health_exact_alarm"
const val TAG_HEALTH_BATTERY = "health_battery"
const val TAG_HEALTH_FULL_SCREEN = "health_full_screen"
const val TAG_HEALTH_OEM = "health_oem"
const val TAG_HEALTH_OEM_SETTINGS = "health_oem_settings"
const val TAG_HEALTH_BANNER = "health_banner"
const val TAG_HEALTH_BANNER_OPEN = "health_banner_open"
