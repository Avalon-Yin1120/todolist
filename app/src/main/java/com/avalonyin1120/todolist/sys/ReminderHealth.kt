package com.avalonyin1120.todolist.sys

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.avalonyin1120.todolist.notify.NotificationHelper

/**
 * 提醒可靠性相关的「系统开关」查询与跳转。
 *
 * 为什么单独成类：真机上「到点不响」几乎都不是代码问题，而是下面这几个开关没开——
 * 尤其在 ColorOS / MIUI / EMUI 这类省电激进的 ROM 上，缺一个就可能整天不提醒。
 * 这些开关分散在系统设置的不同页面，集中在这里便于引导用户逐个打开。
 */
object ReminderHealth {

    /** 是否能弹通知（Android 13+ 需要运行时权限，且未被用户在系统里关掉）。 */
    fun canPostNotifications(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /**
     * 闹钟式提醒能否「弹出整屏提醒页」。
     *
     * Android 14 起，非通话/闹钟类应用的全屏通知可能被系统降级为普通横幅，
     * 需要用户在设置里显式允许。
     */
    fun canUseFullScreenIntent(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 34) return true
        val manager = context.getSystemService(NotificationManager::class.java) ?: return true
        return manager.canUseFullScreenIntent()
    }

    /**
     * 本应用是不是已经在清单里声明了 `USE_EXACT_ALARM`。
     *
     * 声明它的应用会被系统**直接授予**精确闹钟能力：`AlarmManager.canScheduleExactAlarms()`
     * 恒为 true，系统集成不再弹「闹钟和提醒」授权页，也不会把提醒降级成非精确闹钟。
     * 判断方式只能是查清单里有没有这条权限——运行时的
     * [android.app.AlarmManager.canScheduleExactAlarms] 在两种来源（用户授予 / 清单声明）
     * 下返回的都是 true，分不出来，而界面文案需要区分（「系统自动授予」还是「你去开一下」）。
     */
    fun hasExactAlarmDeclaredInManifest(context: Context): Boolean = runCatching {
        context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions
            ?.contains(android.Manifest.permission.USE_EXACT_ALARM) == true
    }.getOrDefault(false)

    /** 打开本应用的通知设置页。 */
    fun openNotificationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        launchFirstAvailable(context, listOf(intent to "本应用的通知设置页"))
    }

    /**
     * 打开**某一条**通知渠道（类别）的设置页。
     *
     * 为什么需要：只打开「通知设置」总览页还不够——用户要改的「静默通知 / 横幅 / 锁屏」
     * 都是**逐渠道**的开关，总览页里得再点进类别才能看到。能直接落到那一条渠道，
     * 用户少点一层，也就更可能真的改对。
     *
     * 层层兜底：渠道页 → 通知设置总览页 → 应用详情页。渠道 id 是本应用自己创建的，
     * 理论上一定存在；但系统在某些 ROM 上可能不认这个 extra，因此仍要 resolve 一遍。
     */
    fun openChannelSettings(context: Context, channelId: String) {
        val candidates = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                add(
                    Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        putExtra(Settings.EXTRA_CHANNEL_ID, channelId)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    } to "本应用某条通知渠道的设置页（channelId=$channelId）",
                )
            }
            add(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                } to "本应用的通知设置总览页（全部类别）",
            )
        }
        launchFirstAvailable(context, candidates)
    }

    /** 打开「全屏提醒」权限页（Android 14+）。 */
    fun openFullScreenIntentSettings(context: Context) {
        if (Build.VERSION.SDK_INT < 34) {
            openNotificationSettings(context)
            return
        }
        val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        launchFirstAvailable(context, listOf(intent to "本应用的全屏提醒权限页"))
    }

    /** 打开本应用的「应用信息」页——国产 ROM 的自启动/后台管理入口都在它的下一层。 */
    fun openAppDetailsSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        launchFirstAvailable(context, listOf(intent to "本应用的应用详情页"))
    }

    /**
     * 打开「允许后台运行 / 耗电管理」那一处设置。
     *
     * ## 为什么不能只跳 `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`
     *
     * 那只是 AOSP 的「电池优化」列表页。用户反馈在 ColorOS 上跳过去「界面不对」——
     * ColorOS 真正管事的是「耗电管理」里的**允许完全后台行为**（以及后台冻结/省电策略），
     * 它在 `com.coloros.oppoguardelf` / `com.oplus.battery` 下，和 AOSP 的电池优化
     * 列表页不是同一处；有的版本上该页面甚至不存在。
     *
     * 因此这里改成**候选意图链**：按「最贴近用户要改的东西」排序逐个试，
     * 用 [PackageManager.resolveActivity] 先问「这个组件在不在」，
     * 在才跳；全都不在就退到本应用的应用详情页（用户从那里也能进「耗电管理」）。
     *
     * ## 候选的来源与可信度（重要）
     *
     * 这些厂商组件名来自社区常见做法与各 ROM 的公开 dump，**组件名随版本变化**，
     * 因此这里仅作候选、并**不声称已实测**（我们只有模拟器，没有 OPPO 真机）。
     * 正因为拿不准，才必须逐个 resolve，且必须有兜底——跳错页面比跳不过去更糟，
     * 而崩掉是最糟的，三者都不能发生。
     *
     * ## 为什么不用那条会弹确认框的 `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
     *
     * 它要申请 `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 权限，而**Google Play 的内容政策
     * 不允许普通应用申请它**：Android 自带的 lint 就有专门一条检查（`BatteryLife`）
     * 把声明该权限判为违反 Play 政策，现实中也确实有应用因此被拒。
     * 本应用要的是「闹钟到点能被唤醒」，用页面引导同样能达到目的，
     * 只是用户要多点一步，换来的是上架环节没有这个风险。
     *
     * 注意：**读取**「是否已豁免」（`PowerManager.isIgnoringBatteryOptimizations`）
     * 不需要任何权限，所以状态判断照旧准确；但它只反映 AOSP 的白名单，
     * ColorOS 的「完全允许后台行为」是否生效，最终还得靠用户到那一页确认。
     */
    fun openBatteryOptimizationSettings(context: Context) {
        launchFirstAvailable(context, batteryOptimizationCandidates(context))
    }

    /**
     * 当前系统是不是常见的「需要额外开自启动」的国产 ROM。
     *
     * 只用来决定是否展示专项引导文案，不做任何跳转决策：
     * 跳哪一页由 [openBatteryOptimizationSettings] 的候选链在运行时 resolve 决定，
     * 这里判错的代价只是多显示/少显示一张说明卡片。
     */
    fun needsOemBackgroundGuide(): Boolean {
        val brand = (Build.BRAND + " " + Build.MANUFACTURER).lowercase()
        return listOf("oppo", "realme", "oneplus", "vivo", "iqoo", "xiaomi", "redmi",
            "huawei", "honor", "meizu", "samsung").any { brand.contains(it) }
    }

    // -----------------------------------------------------------------------
    // 通知渠道（类别）体检
    // -----------------------------------------------------------------------

    /**
     * 单条通知渠道的体检结果。
     *
     * 三个字段对应系统通知设置里用户要动的那三处：静默通知、横幅、锁屏。
     */
    data class ChannelHealth(
        val id: String,
        val title: String,
        /** 被判为「静默通知」：不会响也不会震。 */
        val silent: Boolean,
        /** 会弹出横幅（heads-up，即屏幕顶部那条飘出来的通知）。 */
        val banners: Boolean,
        /** 锁屏上可见。 */
        val lockscreen: Boolean,
    )

    /**
     * 读取本应用自己那几个通知渠道的状态。**只读，不修改任何渠道**。
     *
     * ## 为什么是只读（本项目已经踩过的坑）
     *
     * 通知渠道一旦创建，**声音、重要性（含「静默」）就由用户与系统掌握，应用只能读**：
     * 再调 `createNotificationChannel` 传新的 `setSound`/重要性并不会改变已有渠道，
     * 只会改得动名字和描述。所以「通知没声音」「被判成静默」这类问题，
     * 应用侧唯一的办法是引导用户自己去系统设置里改——本函数就是给那个引导提供依据。
     *
     * 也正因为渠道一旦建出来就改不动，界面必须把**每一条**渠道的真实状态列出来。
     *
     * ## 三个判定的依据
     *
     * * **静默**：`importance <= IMPORTANCE_LOW`。Android 13 起系统把「静默通知」这个
     *   开关映射成低重要性（并去掉声音），所以低重要性就等于用户看到的那条「静默」；
     *   另外只要该渠道 `sound == null`，无论重要性多高，它实际上也不会出声——
     *   本应用的静音渠道正是这种（高重要性 + 无声音，「不出声但仍然弹横幅」），
     *   对用户来说它同样是「静默通知」，因此两个条件取或。
     * * **横幅**：`importance >= IMPORTANCE_HIGH`。heads-up 只由高重要性触发，
     *   `IMPORTANCE_DEFAULT` 只进通知栏、不飘出来。
     * * **锁屏**：`lockscreenVisibility != VISIBILITY_SECRET`。`SECRET` 是「锁屏上完全不显示」，
     *   其余（PUBLIC / PRIVATE / 未设置→系统默认）都至少有某种形式的锁屏可见。
     *
     * 顺序与数量都是稳定的：固定按 [channelIdList] 的顺序返回存在的渠道，
     * 这样界面行序、测试断言都不会随系统内部顺序漂移。
     */
    fun notificationChannelHealth(context: Context): List<ChannelHealth> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return emptyList()
        val manager = context.getSystemService(NotificationManager::class.java)
            ?: return emptyList()
        return channelIdList().mapNotNull { id ->
            // 渠道可能还没建（用户从没走到发通知那一步），或系统查询失败：
            // 这一项直接不列出来，交给 NotificationHelper.ensureChannel() 去补建。
            val channel = runCatching { manager.getNotificationChannel(id) }.getOrNull()
                ?: return@mapNotNull null
            ChannelHealth(
                id = id,
                title = channel.name?.toString()?.takeIf { it.isNotBlank() } ?: id,
                silent = channel.isSilentForUser(),
                banners = channel.importance >= NotificationManager.IMPORTANCE_HIGH,
                lockscreen = channel.lockscreenVisibility != Notification.VISIBILITY_SECRET,
            )
        }
    }

    /**
     * 是否算「静默通知」。
     *
     * 抽成扩展函数只为让上面那段的判定条件集中在一处，便于与注释一一对应。
     */
    private fun NotificationChannel.isSilentForUser(): Boolean =
        importance <= NotificationManager.IMPORTANCE_LOW || sound == null

    /**
     * 当前实际使用的那条渠道能不能用（给顶部横幅的计数用）。
     *
     * 与检查页的区别：渠道**还没创建**时这里返回 true。横幅是「有 N 项要你去开」的提醒，
     * 而「渠道还没建」是应用自己没走到那一步（首次发通知时才建），不是用户能处理的事；
     * 检查页则把它如实算成「未就绪」，因为那一页是让人逐项核对的。
     *
     * @param soundEnabled 应用内「通知提示音」开关。关掉时提醒本来就该走那条静音渠道，
     *   此时不能再要求「不是静默」——那是一个用户故意造成、而且应用改不了的状态。
     */
    fun activeChannelHealthy(context: Context, soundEnabled: Boolean): Boolean {
        val activeId = if (soundEnabled) NotificationHelper.CHANNEL_ID else NotificationHelper.CHANNEL_ID_SILENT
        val health = notificationChannelHealth(context).firstOrNull { it.id == activeId } ?: return true
        return (!soundEnabled || !health.silent) && health.banners && health.lockscreen
    }

    /** 本应用自己的渠道 id，取自 [NotificationHelper]，顺序即界面与测试的稳定顺序。 */
    private fun channelIdList(): List<String> =
        listOf(NotificationHelper.CHANNEL_ID, NotificationHelper.CHANNEL_ID_SILENT)

    // -----------------------------------------------------------------------
    // 跳转基础设施
    // -----------------------------------------------------------------------

    /**
     * 候选意图及其说明（说明只用于日志与排查，不展示给用户）。
     */
    private data class Candidate(val intent: Intent, val what: String)

    /**
     * 依次 resolve + 启动候选意图，直到有一个真的跳成功；全失败则退到应用详情页。
     *
     * 三条硬约束（厂商页面千奇百怪，缺一条就会出问题）：
     * 1. **先 `resolveActivity` 再 `startActivity`**：组件名写错/该版本没有这个页面时，
     *    直接 start 会抛 `ActivityNotFoundException`；
     * 2. **`resolveActivity` 也可能抛**（个别 ROM 的 packageManager 会对畸形 Intent
     *    抛出异常），所以它本身也包在 `runCatching` 里；
     * 3. **最后一定有兜底**：应用详情页所有 Android 版本都有，绝不会「点了没反应」，
     *    用户至少能自己往下点。
     */
    private fun launchFirstAvailable(context: Context, candidates: List<Pair<Intent, String>>) {
        for ((intent, what) in candidates) {
            if (!isResolvable(context, intent)) continue
            val started = runCatching { context.startActivity(intent) }.isSuccess
            if (started) return
            // 能 resolve 却起不来（极少见，例如被系统策略拦下）：继续试下一个候选
        }
        launchAppDetails(context)
    }

    /** 这个 Intent 在当前设备上有没有能处理它的 Activity。 */
    private fun isResolvable(context: Context, intent: Intent): Boolean = runCatching {
        context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY) != null
    }.getOrDefault(false)

    /** 最终兜底：应用详情页。写成独立函数是为了它自己绝不会再走候选链，避免递归。 */
    private fun launchAppDetails(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    /**
     * 显式组件意图（厂商页面都靠这条路进来）。
     *
     * `component` 写成 `包名/类名` 的绝对形式，不写相对形式（`.xxx`）：
     * 相对形式会在**我们自己**的包名下解析，那是完全另一个类。
     */
    private fun componentIntent(packageName: String, className: String): Intent =
        Intent().apply {
            component = ComponentName(packageName, className)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    /**
     * 「允许后台运行」的候选页面，**按「最贴近用户要改的东西」排序**。
     *
     * 前两条是用户反馈里那条真实路径（ColorOS：应用详情 → 耗电管理 → 允许完全后台行为），
     * 只能靠试；中间是国产 ROM 的自启动管理（ColorOS 把自启动单独放在手机管家里）；
     * 然后是 AOSP 的电池优化列表；最后由 [launchFirstAvailable] 兜到应用详情页。
     *
     * 全部厂商组件都**未经真机实测**（我们只有模拟器），仅作候选：
     * 组件名会随 ColorOS 版本变化，同名类在不同版本上也可能是不同的页面。
     * 这正是必须先 resolve 的原因。
     */
    private fun batteryOptimizationCandidates(context: Context): List<Pair<Intent, String>> {
        val list = buildList {
            // ---- ColorOS / OPPO / 一加 / realme：耗电管理、后台冻结、省电策略 ----
            // oppoguardelf 是 ColorOS 的「手机管家/安全」组件，耗电管理与后台冻结都在它下面；
            // 类名各版本不一致，且部分版本把这些页面藏进了系统设置的二级页，故只能逐个试。
            add(
                componentIntent("com.coloros.oppoguardelf", "com.coloros.powermanager.PowerUsageModelActivity") to
                    "ColorOS 耗电管理（后台耗电/省电策略）",
            )
            add(
                componentIntent("com.coloros.oppoguardelf", "com.coloros.oppoguardelf.power.PowerUsageModelActivity") to
                    "ColorOS 耗电管理（另一个已知类名）",
            )
            add(
                componentIntent("com.coloros.oppoguardelf", "com.coloros.oppoguardelf.PowerUsageModelActivity") to
                    "ColorOS 耗电管理（包根目录下的类名）",
            )
            add(
                componentIntent("com.coloros.oppoguardelf", "com.coloros.oppoguardelf.power.PowerConsumptionActivity") to
                    "ColorOS 耗电排行/耗电详情",
            )
            add(
                componentIntent("com.coloros.oppoguardelf", "com.coloros.oppoguardelf.freeze.AppFreezeActivity") to
                    "ColorOS 后台冻结（应用速冻）",
            )
            add(
                componentIntent("com.coloros.oppoguardelf", "com.coloros.oppoguardelf.startup.StartupAppListActivity") to
                    "ColorOS 应用自启动管理（手机管家下）",
            )
            // oplus 是 ColorOS 12 之后的电池服务（一加并入后统一到 oplus 前缀）
            add(
                componentIntent("com.oplus.battery", "com.oplus.powermanager.fuelgaue.PowerUsageModelActivity") to
                    "ColorOS 12+ 耗电管理（oplus 前缀）",
            )
            add(
                componentIntent("com.oplus.battery", "com.oplus.powermanager.fuelgaue.PowerConsumptionActivity") to
                    "ColorOS 12+ 耗电排行",
            )
            add(
                componentIntent("com.oplus.battery", "com.oplus.battery.BatteryActivity") to
                    "ColorOS 12+ 电池设置页",
            )
            // ---- ColorOS：自启动管理（安全中心）----
            add(
                componentIntent("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity") to
                    "ColorOS 自启动管理（安全中心）",
            )
            add(
                componentIntent("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity") to
                    "ColorOS 自启动管理（旧版本路径）",
            )
            // 这条是 OPPO 上「允许后台运行」动作的显式入口：类名就叫 AllowBackgroundRun，
            // 但它在各版本上属于哪个包也不固定，因此两个常见包名都试一遍。
            add(
                componentIntent("com.oplus.battery", "com.oplus.battery.AllowBackgroundRunActivity") to
                    "ColorOS「允许后台运行」入口（oplus 包）",
            )
            add(
                componentIntent("com.coloros.oppoguardelf", "com.coloros.oppoguardelf.AllowBackgroundRunActivity") to
                    "ColorOS「允许后台运行」入口（oppoguardelf 包）",
            )
            // ---- 通用兜底一：本应用的「应用详情」页 ----
            //
            // 刻意排在系统电池优化列表页**之前**：用户明确要求跳到
            // 「应用详情 / 待办管理 / 耗电管理 / 完全允许后台行为」这条路径，
            // 因为 ColorOS / MIUI 这类 ROM 把耗电管理藏进了应用详情，而
            // `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` 在它们上面要么没有这个页面、
            // 要么指向一个跟提醒无关的列表。应用详情页在所有设备上都存在，是可靠的落点；
            // 类原生系统上从它进去也只多一步（电池 → 不受限制）。
            add(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                } to "本应用的应用详情页（各厂商耗电管理的入口都在它里面）",
            )
            // ---- 通用兜底二：AOSP 电池优化列表页 ----
            add(
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                } to "系统电池优化列表页（AOSP）",
            )
        }
        // 同一个组件可能被上面写重复（不同版本落在不同包），去重后再逐个试，避免白跳两次
        return list.distinctBy { it.first.component?.flattenToString() ?: it.first.action }
    }
}
