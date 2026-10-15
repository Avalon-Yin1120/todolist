package com.avalonyin1120.todolist.ui

import android.content.Context
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.avalonyin1120.todolist.data.SharedPreferencesSettingsStore
import com.avalonyin1120.todolist.sys.ReminderHealth

/**
 * 「提醒可靠性检查」页。
 *
 * 这一页只做一件事：把「为了到点能响，你要去哪里改什么」列成能照着点的清单。
 * 所以它不解释判定机制、不出现 importance / 渠道这类术语、也不摆数字指标——
 * 那些机制说明留在 sys/ReminderHealth.kt 的注释与 README 里
 * （用户反馈：这一页文字太冗余、术语太多）。
 *
 * 清单分两组，分组的理由写在 [ReminderHealth.sections] 里：
 * * 「你的手机：<厂商>」是厂商路径（经验值，各版本可能不同）。这些开关系统读不到真实状态，
 *   **只认用户自己勾的「我已设置好」**，页面不替系统报一个状态；
 * * 「系统权限」是 Android 自己有读取接口的开关，读得到就显示一句人话（已允许 / 未允许）。
 *
 * 计数规则（先看用户确认、再看系统读数）只在 [ReminderHealth.isStepPending] 里实现一次。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderHealthScreen(
    canPostNotifications: Boolean,
    canScheduleExact: Boolean,
    batteryOptimizationIgnored: Boolean,
    fullScreenIntentAllowed: Boolean,
    needsOemBackgroundGuide: Boolean,
    /** 当前提醒方式是否是闹钟式：只有闹钟式才关心精确闹钟与全屏提醒 */
    alarmModeInUse: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onRequestExactAlarm: () -> Unit,
    onRequestIgnoreBatteryOptimization: () -> Unit,
    onBack: () -> Unit,
    /**
     * 打开「提醒诊断日志」。
     *
     * 给默认空实现：日志那一份由另一个改动提供，接线放在调用方（TodoListScreen），
     * 这样本页不必等它就能先独立编译、验证。
     */
    onOpenReminderLog: () -> Unit = {},
) {
    val context = LocalContext.current

    // 用户勾过的项存在设置里（[com.avalonyin1120.todolist.data.SettingsStore.confirmedHealthItems]）。
    // 这里自己读一次而不是从 TodoListScreen 传进来：本次改动不动那个文件，
    // 而且读的是同一份 SharedPreferences（进程内缓存），开销可以忽略。
    val settingsStore = remember(context) { SharedPreferencesSettingsStore(context) }
    var confirmed by remember(context) { mutableStateOf(settingsStore.confirmedHealthItems) }

    val brand = remember { ReminderHealth.currentBrand() }
    val sections = remember(brand, alarmModeInUse) {
        ReminderHealth.sections(brand, alarmModeInUse)
    }
    val states = ReminderHealth.SystemStates(
        notifications = canPostNotifications,
        exactAlarm = canScheduleExact,
        fullScreenIntent = fullScreenIntentAllowed,
        batteryUnrestricted = batteryOptimizationIgnored,
    )
    // 计数不 remember：它只是一次 count，而 remember 漏掉依赖时会把旧数字留在屏幕上
    val pending = ReminderHealth.pendingStepCount(
        steps = sections.flatMap { it.steps },
        states = states,
        confirmed = confirmed,
    )

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
                text = if (pending == 0) {
                    "都设置好了，到点会正常提醒 ✓"
                } else {
                    "还有 $pending 项没设置好，可能到点不提醒："
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (pending == 0) FontWeight.Normal else FontWeight.SemiBold,
                color = if (pending == 0) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
                modifier = Modifier.padding(top = 8.dp),
            )

            // 厂商路径是经验值：系统版本一换，同一处设置的叫法就可能不同，先说清楚，
            // 免得用户照着找不到就以为应用写错了。类原生上这些就是系统标准叫法，
            // 不打这个补丁（needsOemBackgroundGuide 由调用方按品牌给出）。
            if (needsOemBackgroundGuide) {
                Text(
                    text = "路径按常见系统版本整理，你的手机版本不同可能略有出入。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            sections.forEach { section ->
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp),
                )
                section.steps.forEach { step ->
                    StepCard(
                        step = step,
                        pending = ReminderHealth.isStepPending(step, states, confirmed),
                        confirmed = step.id in confirmed,
                        hint = ReminderHealth.statusHint(step, states),
                        onOpenSettings = {
                            openStepSettings(
                                step = step,
                                context = context,
                                canPostNotifications = canPostNotifications,
                                onRequestNotificationPermission = onRequestNotificationPermission,
                                onRequestExactAlarm = onRequestExactAlarm,
                                onRequestIgnoreBatteryOptimization =
                                    onRequestIgnoreBatteryOptimization,
                            )
                        },
                        onToggleConfirm = { checked ->
                            val next = if (checked) {
                                confirmed + step.id
                            } else {
                                confirmed - step.id
                            }
                            // 立刻落盘：点完「我已设置好」用户往往接着按「去设置」跳走，
                            // 本页随时可能被销毁，只留在内存里就丢了。
                            settingsStore.confirmedHealthItems = next
                            confirmed = next
                        },
                    )
                }
            }

            HorizontalDivider(Modifier.padding(top = 4.dp))

            TextButton(
                onClick = onOpenReminderLog,
                modifier = Modifier.testTag(TAG_HEALTH_REMINDER_LOG),
            ) { Text("查看提醒诊断日志") }
            Text(
                text = "到点没响时，用它看当时的记录。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "改完回到本页会自动重新检查。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * 把一条清单的 [ReminderHealth.SettingsTarget] 翻译成一次跳转。
 *
 * 为什么集中在这一处：意图链的兜底（厂商页面找不到就退到应用详情页）只在
 * sys/ReminderHealth.kt 里实现一次，页面不自己拼 Intent；「要不要先向系统申请」
 * 这类判断也只在下面各写一遍，否则同一条跳转早晚会在两处走成两个样子。
 *
 * * 通知权限：系统里还没允许就先弹系统授权框——那才是「允许这个应用发通知」这一步；
 *   已经允许时打开通知管理页（用户要改的锁屏/横幅/静默都在它里面）。
 * * 闹钟和提醒、后台运行：直接用调用方现有的请求入口，它们内部就是同一条候选链，
 *   这样「从系统设置返回时刷新状态」的既有链路完全不变。
 */
private fun openStepSettings(
    step: ReminderHealth.ChecklistStep,
    context: Context,
    canPostNotifications: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onRequestExactAlarm: () -> Unit,
    onRequestIgnoreBatteryOptimization: () -> Unit,
) {
    when (step.target) {
        ReminderHealth.SettingsTarget.NOTIFICATION ->
            if (canPostNotifications) {
                ReminderHealth.openNotificationSettings(context)
            } else {
                onRequestNotificationPermission()
            }

        ReminderHealth.SettingsTarget.EXACT_ALARM -> onRequestExactAlarm()

        ReminderHealth.SettingsTarget.BACKGROUND_POPUP ->
            ReminderHealth.openBackgroundPopupSettings(context)

        ReminderHealth.SettingsTarget.FULL_SCREEN_INTENT ->
            ReminderHealth.openFullScreenIntentSettings(context)

        ReminderHealth.SettingsTarget.BATTERY -> onRequestIgnoreBatteryOptimization()
    }
}

/**
 * 清单里的一条：标题 / 路径 /「去设置」/「我已设置好」。
 *
 * 卡片底色与前面的 ✓、! 只表达「这一条还没完成」；[confirmed] 是用户自己勾的，
 * [hint] 是系统里真读到的那一句人话。两者分开摆，是因为它们可能不一致
 * （用户勾了但系统读数还没变、或系统已经是好的但用户没勾），混成一个状态就会骗人。
 */
@Composable
private fun StepCard(
    step: ReminderHealth.ChecklistStep,
    pending: Boolean,
    confirmed: Boolean,
    hint: String?,
    onOpenSettings: () -> Unit,
    onToggleConfirm: (Boolean) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TAG_HEALTH_STEP_PREFIX + step.id),
        colors = CardDefaults.cardColors(
            containerColor = if (pending) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (pending) "!" else "✓",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (pending) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                )
                Spacer(Modifier.padding(horizontal = 4.dp))
                Text(
                    text = step.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                text = step.path,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // 读不到状态的条 hint 为 null：一个字都不显示，而不是显示一个猜出来的「未开启」
            if (hint != null) {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(TAG_HEALTH_STEP_HINT_PREFIX + step.id),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.testTag(TAG_HEALTH_STEP_SETTINGS_PREFIX + step.id),
                ) { Text("去设置") }
                Spacer(Modifier.weight(1f))
                // 整块可点：勾选框只有十几像素，手指点「我已设置好」这几个字也该生效
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onToggleConfirm(!confirmed) },
                ) {
                    Checkbox(
                        checked = confirmed,
                        onCheckedChange = onToggleConfirm,
                        modifier = Modifier.testTag(TAG_HEALTH_STEP_CONFIRM_PREFIX + step.id),
                    )
                    Text(text = "我已设置好", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

const val TAG_HEALTH_SCREEN = "reminder_health_screen"
const val TAG_HEALTH_BACK = "reminder_health_back"

/**
 * 主界面顶部那条横幅用的两个标签（`ui/TodoListScreen.kt` 里的 `PermissionBanner`）。
 *
 * 横幅不在本文件里，这两个常量留在原处只是为了让那边继续引用同一个名字，
 * 免得同一条横幅出现两个字符串。
 */
const val TAG_HEALTH_BANNER = "health_banner"
const val TAG_HEALTH_BANNER_OPEN = "health_banner_open"

/** 清单里一条的标签前缀，后面接 [ReminderHealth.ChecklistStep.id]。 */
const val TAG_HEALTH_STEP_PREFIX = "health_step_"

/** 「去设置」按钮的标签前缀，后面接 step id。 */
const val TAG_HEALTH_STEP_SETTINGS_PREFIX = "health_step_settings_"

/** 「我已设置好」勾选框的标签前缀，后面接 step id。 */
const val TAG_HEALTH_STEP_CONFIRM_PREFIX = "health_step_confirm_"

/** 「系统里看是：…」那句提示的标签前缀，后面接 step id（读不到状态的条没有这个标签）。 */
const val TAG_HEALTH_STEP_HINT_PREFIX = "health_step_hint_"

/** 「查看提醒诊断日志」按钮。 */
const val TAG_HEALTH_REMINDER_LOG = "health_reminder_log"
