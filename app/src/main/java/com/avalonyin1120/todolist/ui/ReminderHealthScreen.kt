package com.avalonyin1120.todolist.ui

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.avalonyin1120.todolist.data.SharedPreferencesSettingsStore
import com.avalonyin1120.todolist.notify.NotificationHelper
import com.avalonyin1120.todolist.sys.ReminderHealth

/**
 * 「提醒可靠性检查」页。
 *
 * 存在的理由：真机上「到点不响」几乎全是系统开关问题，而它们在设置里散落在不同页面，
 * 用户根本不知道要找哪些。这里把每一项的状态、为什么需要、去哪里开都摆在一起，
 * 并在缺项时明确告诉用户「关掉这项就会漏提醒」。
 *
 * 国产 ROM（ColorOS / MIUI / EMUI …）的自启动与后台管理没有稳定的标准 Intent，
 * 跳哪一页由 [ReminderHealth] 在运行时用 `resolveActivity` 逐个候选决定；
 * 文案仍然给出文字路径，跳转失败时用户也能自己照着找。
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

    // ---------------------------------------------------------------- 通知样式（静默/锁屏/横幅）
    //
    // 提醒走的是哪条渠道由「通知提示音」这个设置决定（见 NotificationHelper.channelId）。
    // 这个布尔值已经在 SettingsUiState 里了，但取到它需要改 TodoListScreen 的调用处，
    // 而本次改动不允许动那个文件——因此这里就地读设置（默认实现 SharedPreferences）：
    // 改动面最小，也不需要把参数一路传下来。SharedPreferences 是进程内缓存的，
    // 页面重建时读一次的开销可以忽略。
    val soundEnabled = remember(context) {
        SharedPreferencesSettingsStore(context).notificationSound
    }
    val activeChannelId = if (soundEnabled) {
        NotificationHelper.CHANNEL_ID
    } else {
        NotificationHelper.CHANNEL_ID_SILENT
    }
    // 与其它几项一样只在进入本页/重组时读一次：用户去系统设置改完回来，
    // 本页是从设置返回后重建的（TodoRoute 在 ON_RESUME 里刷新权限），因此能看到新状态；
    // 停在原地不动时不会自己变——这与顶部横幅的刷新时机一致，不额外加轮询。
    val channelHealth = remember(context) { ReminderHealth.notificationChannelHealth(context) }
    val activeChannel = channelHealth.firstOrNull { it.id == activeChannelId }
    // 只按「当前实际使用的那条渠道」判定：判别的对象必须是真正会响的那条，
    // 否则用户改了一条不用的渠道，检查页还是显示未开启（或相反），完全对不上。
    // 渠道还没建出来（activeChannel == null）也算未就绪。
    //
    // 「静默」这一项只在**应用自己开着提示音**时才要求：用户在设置里关掉「通知提示音」后，
    // 提醒本来就是走那条「高重要性 + 无声音」的静音渠道，它的 sound 为 null、注定被判成静默。
    // 若不排除这种情况，用户会在自己主动关掉提示音之后，被检查页一直催着「关闭静默通知」——
    // 那是一个他故意造成的、且改不掉的状态（渠道声音创建后应用自己都改不动）。
    val channelOk = activeChannel != null &&
        (!soundEnabled || !activeChannel.silent) &&
        activeChannel.banners && activeChannel.lockscreen

    // 两个条件都要：清单声明了 USE_EXACT_ALARM（Android 13+ 安装即授予）**且**当前确实能用。
    // 只看清单会在 Android 12 / 被用户关掉的机器上说「已自动授予」，与事实不符；
    // 只看 canScheduleExact 又分不出「系统给的」还是「用户自己开的」，文案会退回「去开启」。
    val exactAlarmAlwaysGranted =
        canScheduleExact && ReminderHealth.hasExactAlarmDeclaredInManifest(context)

    val missing = listOf(
        canPostNotifications,
        canScheduleExact,
        batteryOptimizationIgnored,
        // 全屏提醒只在「闹钟式 + Android 14+」下才影响体验
        fullScreenIntentAllowed || !alarmModeInUse,
        // 通知样式整体算**一项**（不是每条渠道各算一项）：横幅数字要让人一眼看懂
        // 「还有几件事要做」，渠道内部的静默/横幅/锁屏在展开区里逐条列
        channelOk,
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

            // 放在「通知权限」之后：权限是「能不能发」，这一项是「发出来长什么样」——
            // 用户反馈里最容易漏的正是后者（只开了「允许通知」，其余全默认）。
            HealthItem(
                ok = channelOk,
                title = "通知样式（静默 / 锁屏 / 横幅）",
                why = buildString {
                    append("只开「允许通知」还不够：通知设置里还要")
                    append("把所有类别的「静默通知」都关掉，并打开「锁屏」与「横幅」通知。")
                    append("否则提醒可能不出声、不飘出来，或者锁屏上什么都看不到。")
                    append("（渠道一旦创建，声音与重要性只能由你在系统设置里改，应用读得到、改不了。）")
                },
                actionLabel = "去设置",
                tag = TAG_HEALTH_CHANNELS,
                onAction = { ReminderHealth.openChannelSettings(context, activeChannelId) },
                secondaryLabel = "打开通知设置（全部类别）",
                onSecondary = {
                    ReminderHealth.openNotificationSettings(context)
                },
                secondaryTag = TAG_HEALTH_NOTIF_SETTINGS,
            ) {
                Text(
                    text = "当前提醒走的是「${activeChannel?.title ?: "渠道尚未创建"}」" +
                        when {
                            activeChannel == null -> "。"
                            // 关掉应用内提示音后，静音渠道被判为静默是**预期**的，
                            // 不能反过来让用户去「关掉静默通知」（那条渠道本来就不出声）
                            !soundEnabled -> "。你已关闭「通知提示音」，因此这里只检查横幅与锁屏。"
                            activeChannel.silent -> "，它现在被判为静默通知，不会出声。"
                            else -> "。各条类别（渠道）的现状："
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                channelHealth.forEach { channel ->
                    ChannelRow(
                        health = channel,
                        highlighted = channel.id == activeChannelId,
                        // 只有「当前在用且应用要求出声」的那条，静默才算是问题
                        expectAudible = soundEnabled && channel.id == activeChannelId,
                        onFix = { ReminderHealth.openChannelSettings(context, channel.id) },
                    )
                }
            }

            HealthItem(
                ok = canScheduleExact,
                title = "精确闹钟",
                why = if (exactAlarmAlwaysGranted) {
                    // USE_EXACT_ALARM 的应用无需用户授权，系统直接给精确闹钟能力
                    "已由系统自动授予（USE_EXACT_ALARM），到点精确触发。"
                } else {
                    "闹钟式提醒靠它才能「准时」响铃并亮屏；没有它只能退化成普通通知，" +
                        "而且系统可能延后几分钟到几十分钟。"
                },
                actionLabel = "去开启",
                tag = TAG_HEALTH_EXACT_ALARM,
                onAction = onRequestExactAlarm,
                // 已由系统授予时主按钮不出现，但仍留一个「系统设置」入口：万一用户/厂商策略
                // 在系统设置里把它关掉了，canScheduleExact 会变回 false，本项自动恢复成
                // 「未开启 + 去开启」——这个兜底入口就是为那种情况留的（也是本项唯一的跳转）。
                secondaryLabel = if (exactAlarmAlwaysGranted) "系统设置" else null,
                onSecondary = if (exactAlarmAlwaysGranted) onRequestExactAlarm else null,
            )

            HealthItem(
                ok = batteryOptimizationIgnored,
                title = "允许后台运行",
                why = "系统的省电策略会把后台应用冻结，闹钟和通知都会因此不响。" +
                    "ColorOS(OPPO/一加/realme) 请走：应用详情 → 耗电管理 → 允许完全后台行为" +
                    "（各厂商叫法不同：小米叫「省电策略 → 无限制」，华为叫「应用启动管理 → 手动管理」）。" +
                    "按钮会优先打开厂商的「耗电管理 / 自启动」页；若本机没有这些页面，" +
                    "则退回本应用的应用详情页，从那里也能进「耗电管理」。",
                actionLabel = "去设置",
                tag = TAG_HEALTH_BATTERY,
                onAction = onRequestIgnoreBatteryOptimization,
                secondaryLabel = "打开应用详情页",
                onSecondary = { ReminderHealth.openAppDetailsSettings(context) },
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

/**
 * 单项检查：状态点 + 标题 + 原因 + 操作。
 *
 * `body` 是可选展开区：用来放「这一项下面还有几条子项要逐条处理」的内容
 * （目前只有通知样式用它列渠道）。
 */
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
    secondaryTag: String? = null,
    body: (@Composable () -> Unit)? = null,
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
            if (body != null) {
                HorizontalDivider(Modifier.padding(vertical = 2.dp))
                body()
            }
            ActionRow(
                // 未开启才把主操作摆出来；已开启时只留「次级入口」（例如系统设置、应用详情页），
                // 这样卡片不会因为按钮太多而看不出「还差什么」。
                showPrimary = !ok && enabled,
                actionLabel = actionLabel,
                onAction = onAction,
                secondaryLabel = secondaryLabel,
                onSecondary = onSecondary,
                secondaryTag = secondaryTag,
            )
        }
    }
}

/** 一行操作按钮；两个都可空，全空时不占高度。 */
@Composable
private fun ActionRow(
    showPrimary: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
    secondaryLabel: String?,
    onSecondary: (() -> Unit)?,
    secondaryTag: String?,
) {
    if (!showPrimary && (secondaryLabel == null || onSecondary == null)) return
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (showPrimary) {
            TextButton(onClick = onAction) { Text(actionLabel) }
        }
        if (secondaryLabel != null && onSecondary != null) {
            TextButton(
                onClick = onSecondary,
                modifier = if (secondaryTag != null) Modifier.testTag(secondaryTag) else Modifier,
            ) { Text(secondaryLabel) }
        }
    }
}

/**
 * 一条通知渠道的状态行。
 *
 * 为什么要把三条状态各自写出来、再各配一个「去设置」：系统通知设置里
 * 「静默通知」「横幅」「锁屏」是**逐渠道**的开关，只给一个总入口的话，
 * 用户点进去还要自己找是哪一条；这里直接把要改的那条渠道摊开。
 */
@Composable
private fun ChannelRow(
    health: ReminderHealth.ChannelHealth,
    /** 是不是当前实际使用的那条渠道 */
    highlighted: Boolean,
    /** 这条渠道**本该出声**吗——只有这种情况下「静默」才是需要用户去关掉的问题 */
    expectAudible: Boolean,
    onFix: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TAG_HEALTH_CHANNEL_ROW_PREFIX + health.id),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = health.title + if (highlighted) "（当前使用）" else "",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Normal,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = buildString {
                    append(
                        if (health.silent) {
                            // 「静默」只有在「它本该出声」时才是问题，否则如实说明
                            if (expectAudible) "✗ 静默通知（要关掉）" else "· 静默（按当前设置）"
                        } else {
                            "✓ 非静默"
                        },
                    )
                    append("　")
                    append(if (health.banners) "✓ 有横幅" else "✗ 无横幅（要打开）")
                    append("　")
                    append(if (health.lockscreen) "✓ 锁屏可见" else "✗ 锁屏不可见（要打开）")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = onFix,
                modifier = Modifier.testTag(TAG_HEALTH_CHANNEL_FIX_PREFIX + health.id),
            ) { Text("去设置") }
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
                    "① 允许自启动 ② 耗电管理 → 允许完全后台行为（不优化）" +
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
            ) { Text("打开本应用的应用详情页") }
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

/** 通知样式（静默 / 锁屏 / 横幅）整项的标签。 */
const val TAG_HEALTH_CHANNELS = "health_channels"

/** 每条渠道状态行的标签前缀，后面接 channelId。 */
const val TAG_HEALTH_CHANNEL_ROW_PREFIX = "health_channel_row_"

/** 每条渠道「去设置」按钮的标签前缀，后面接 channelId。 */
const val TAG_HEALTH_CHANNEL_FIX_PREFIX = "health_channel_fix_"

/** 「打开通知设置（全部类别）」的标签。 */
const val TAG_HEALTH_NOTIF_SETTINGS = "health_notif_settings"
