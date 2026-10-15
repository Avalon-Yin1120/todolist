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
     * 需要用户在设置里显式允许。用户在系统里看到的那个开关叫**「发送全屏通知」**
     * （见 [openFullScreenIntentSettings]），界面文案沿用这个名字，用户才好照着找。
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

    /**
     * 打开系统的「发送全屏通知」页（Android 14+）。
     *
     * 「发送全屏通知」就是用户在系统里看到的那个开关名（AOSP 的
     * `ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT` 对应的页面），关掉它锁屏就不会弹整屏提醒页。
     * 因此界面文案用这个名字，而不是「全屏提醒」这种我们自己的叫法——用户要照着名字在设置里找。
     *
     * 候选链：那一个 Action（通用入口）→ 本应用的通知设置页（Android 14+ 上同一个开关
     * 也出现在那里；个别 ROM 会把这个 Action 吞掉或指向别的页）→ 兜底应用详情页。
     */
    fun openFullScreenIntentSettings(context: Context) {
        if (Build.VERSION.SDK_INT < 34) {
            openNotificationSettings(context)
            return
        }
        val candidates = listOf(
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            } to "本应用的「发送全屏通知」页（Android 14+ 通用入口）",
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            } to "本应用的通知设置页（同一个「发送全屏通知」开关也在里面）",
        )
        launchFirstAvailable(context, candidates)
    }

    /**
     * 打开「权限管理 / 其他权限 / 特殊应用权限」那一处设置——ColorOS 的「后台弹出界面」就在它下面。
     *
     * ## 为什么必须有这一项（真机上真正卡住「闹钟页弹不出来」的开关）
     *
     * Android 10+ 起系统本身就禁止后台应用直接拉起界面（实测日志
     * `Background activity launch blocked!`），厂商在此基础上又加了一道**用户可见的开关**：
     * ColorOS 叫「后台弹出界面」，MIUI 也在「权限管理 → 其他权限 → 后台弹出界面」。
     * 不允许时，应用即使准时被唤醒、服务也起来了，**界面依然弹不出来**——
     * 于是用户看到的现象正是「到点了什么都没有」。
     *
     * 注意它与 [canUseFullScreenIntent] 是两件事，别混：那个管的是 Android 14+ 的全屏 Intent
     * 会不会被系统降级（用户侧的名字是「发送全屏通知」），这个管的是厂商允不允许后台拉界面。
     * 两个都缺时表现相似，所以检查页分成两项，各给各的入口与路径。
     *
     * ## 为什么这一项「不判定状态」（不谎报）
     *
     * 上面那个厂商开关**没有任何公开 API 能读**：`AppOpsManager` 里那几条 OEM op 名字各版本不同，
     * 读不到就只会得到一个恒定值。据此显示「已开启/未开启」等于凭空猜一个状态——
     * 要么永远红着（又是一次本 bug 同类的误报），要么永远绿着（用户以为没事，到点不弹）。
     * 所以这一项做成**始终可点的引导**：不判定、不计入缺项数，只给入口 + 文字路径，
     * 由用户自己在系统里核对（见 `ReminderHealthScreen` 里 `primaryAlwaysVisible` 的用法）。
     *
     * ## 候选链与可信度
     *
     * OPPO / 小米 / 华为都没有为这些二级页提供公开 Action，只能按社区常见类名逐个试；
     * 这些组件名**未在真机验证过**（我们只有模拟器），而且同一个页面在不同 ColorOS 版本上
     * 归属的包都不一样（`com.coloros.safecenter` / `com.oplus.safecenter` /
     * `com.coloros.oppoguardelf`）。因此这里只作候选、逐个 `resolveActivity`，
     * 全都不存在时由 [launchFirstAvailable] 退到应用详情页（用户从那里照样能走到「权限管理」）。
     * 正因为跳偏的概率不低，界面文案同时给出文字路径。
     */
    fun openBackgroundPopupSettings(context: Context) {
        launchFirstAvailable(context, backgroundPopupCandidates(context))
    }

    /**
     * 「后台弹出界面 / 其他权限 / 特殊应用权限」的候选页面，按「最贴近用户要改的东西」排序。
     *
     * 排序理由：ColorOS 的「后台弹出界面」在「权限管理 → 其他权限（新版本叫特殊应用权限）」里，
     * 所以先试「特殊应用权限」二级页，再试「权限管理」页（点进去还要再点一层，但至少到了正确的地方）。
     * 小米/华为各留一条社区里最常见的权限管理页；一条都不中时由
     * [launchFirstAvailable] 兜到应用详情页。
     *
     * **下面所有组件名都是推测**（来自社区常见做法与各 ROM 的公开 dump，未在真机实测）：
     * 类名会随 ROM 版本变化，同名类在不同版本上甚至可能是不同的页面。这正是必须先 resolve 的原因。
     */
    private fun backgroundPopupCandidates(context: Context): List<Pair<Intent, String>> {
        val list = buildList {
            // ---- ColorOS / OPPO / 一加 / realme：特殊应用权限（后台弹出界面在这里）----
            add(
                componentIntent(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.permission.special.SpecialAppPermissionActivity",
                ) to "ColorOS「特殊应用权限」页（后台弹出界面所在处）",
            )
            add(
                componentIntent(
                    "com.oplus.safecenter",
                    "com.oplus.safecenter.permission.special.SpecialAppPermissionActivity",
                ) to "ColorOS 12+「特殊应用权限」页（oplus 包）",
            )
            // ---- ColorOS：权限管理总入口（后台弹出界面在它的「其他权限」下）----
            add(
                componentIntent(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.permission.app.PermissionManagerActivity",
                ) to "ColorOS「权限管理」页（新版本类名）",
            )
            add(
                componentIntent(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.permission.PermissionManagerActivity",
                ) to "ColorOS「权限管理」页（旧版本类名）",
            )
            add(
                componentIntent(
                    "com.oplus.safecenter",
                    "com.oplus.safecenter.permission.PermissionManagerActivity",
                ) to "ColorOS 12+「权限管理」页（oplus 包）",
            )
            add(
                componentIntent(
                    "com.coloros.oppoguardelf",
                    "com.coloros.oppoguardelf.permission.PermissionManagerActivity",
                ) to "ColorOS「权限管理」页（手机管家包）",
            )
            // ---- 小米 MIUI / 澎湃：权限管理页，其他权限（含后台弹出界面）就在同一页里 ----
            // 这两个 Activity 需要 extra 指明是哪个应用，否则打开的是空的权限列表
            add(
                componentIntent(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.permissions.PermissionsEditorActivity",
                ).apply { putExtra("extra_pkgname", context.packageName) } to
                    "MIUI「权限管理」页（应用中权限，其他权限在其下）",
            )
            add(
                componentIntent(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.permissions.AppPermissionsEditorActivity",
                ).apply { putExtra("extra_package_name", context.packageName) } to
                    "MIUI「应用权限管理」页（另一个已知类名）",
            )
            // ---- 华为 / 荣耀：权限管理总入口 ----
            add(
                componentIntent(
                    "com.huawei.systemmanager",
                    "com.huawei.permissionmanager.ui.MainActivity",
                ) to "EMUI / MagicOS「权限管理」页",
            )
        }
        // 同一个组件可能被上面写重复（不同版本落在不同包），去重后再逐个试，避免白跳两次
        return list.distinctBy { it.first.component?.flattenToString() ?: it.first.action }
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
     * 前三个字段是**判定**，对应系统通知设置里用户要动的那三处：静默通知、横幅、锁屏；
     * 后三个是**原始值**，只作信息展示（真机排查用），不参与任何合格判断。
     * 两者的区别是本次修 bug 的核心：把一个读不准的原始值（[hasSound]）当合格条件，
     * 就会出现「系统里明明关掉了静默，应用却永远说它是静默」这种误报。
     */
    data class ChannelHealth(
        val id: String,
        val title: String,
        /** 被判为「静默通知」：不会响也不会震。**只看重要性**，理由见 [notificationChannelHealth]。 */
        val silent: Boolean,
        /** 会弹出横幅（heads-up，即屏幕顶部那条飘出来的通知）。 */
        val banners: Boolean,
        /** 锁屏上可见。 */
        val lockscreen: Boolean,
        /**
         * 系统读回来的原始重要性。
         *
         * 数值含义（[NotificationManager] 里的常量）：0=IMPORTANCE_NONE（这条渠道被系统整个关掉）、
         * 1=MIN、2=LOW、3=DEFAULT、4=HIGH、5=MAX。用户看不懂数字是正常的——
         * 它摆出来是给排查用的（用户截图时能带上真实读数）。
         */
        val importance: Int,
        /**
         * 有没有读到提示音 URI。**仅作信息，不作合格条件**。
         *
         * 这一条在真机（Android 15 / ColorOS）上被证伪过：用户系统里已经关掉了「静默通知」，
         * 这里却读回 null。旧实现据此判成静默，于是「通知样式」这一项永远显示「未开启」。
         */
        val hasSound: Boolean,
        /** 渠道是否开了振动。**仅作信息**：关掉振动不等于静默（横幅、下拉、锁屏都还在）。 */
        val vibrates: Boolean,
    ) {
        /**
         * 真机排查用的一行原始值，格式固定：
         * `importance=4 / 声音=读不到 / 振动=开 / 锁屏=可见`。
         *
         * 为什么还要留着它：这类误判只有拿到用户手机上的真实读数才能定位。此前我们手上
         * 只有「它说未开启」这一句话，既分不清是 ROM 读不准还是我们的判定写错了。
         * 格式固定（而不是随场景变化）是为了让两次读数能直接对比。
         *
         * **它已经不出现在检查页上了**：那一页改成「给路径 + 用户自己确认」，
         * 半懂不懂的原始值摆在那里只会让用户更困惑（用户反馈：字太多、术语太多）。
         * 现在它留给排查与诊断记录用，界面侧由「查看提醒诊断日志」承接。
         */
        fun rawValuesText(): String = listOf(
            "importance=$importance",
            "声音=" + if (hasSound) "有" else "读不到",
            "振动=" + if (vibrates) "开" else "关",
            "锁屏=" + if (lockscreen) "可见" else "不可见",
        ).joinToString(" / ")
    }

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
     * ## 谁在用这些判定
     *
     * 顶部横幅的「还有 N 项没打开」（[activeChannelHealthy]）与真机排查（[ChannelHealth.rawValuesText]）。
     * 检查页**不再展示**这些读数：那些开关一旦建出来应用就改不动，只能用户去系统里改，
     * 因此那一页改成「给一行路径 + 用户自己勾『我已设置好』」（见下面的清单部分），
     * 读不准的值不再摆到用户面前。
     *
     * ## 静默判定为什么改成「只看重要性」（Android 15 / ColorOS 真机误报的结论）
     *
     * 旧实现是 `importance <= IMPORTANCE_LOW || sound == null`，在用户真机上被证伪：
     * 系统通知设置里那条「待办提醒（有声）」的**静默通知明明是关掉的**（该出声），
     * 应用却判它静默，于是「通知样式」永远显示「未开启」，还劝用户去关一个已经关掉的开关——
     * 用户怎么改都不会变绿。误报只可能来自 `sound == null` 这一支，也就是那台 ROM 上
     * `getSound()` 读回了 null。两个已知成因（没有真机无法进一步区分，但**两个都不该当成「静默」的证据**）：
     *
     * * **(a) 渠道有声音，但 `getSound()` 读成 null。** 渠道创建时传的是
     *   `RingtoneManager.getDefaultUri(TYPE_NOTIFICATION)`，部分 ROM 上这个默认提示音 URI
     *   解析不到（或用户从没设过默认铃声），框架里那条渠道的 sound 就落成 null；
     *   而系统在响铃时用**它自己的**默认音源，于是「实际会响，API 读不到」。
     *   更极端的是厂商把铃声记在自己的字段里，AOSP 这一侧永远是 null。
     * * **(b) 系统那个「静默」开关在 Android 13+ 上映射为低重要性，但本 ROM 读回来不是 LOW。**
     *   ColorOS 自己记着静默标志，未必通过 `importance` 回传；因此「重要性不是 LOW」
     *   也不能反过来说明它有声——但更不能据此说它静默。
     *
     * 结论：`sound == null` 在真机上**既不是静默的充分条件，也不是必要条件**，只能当噪声。
     * 于是判定收敛成一条：`importance <= IMPORTANCE_LOW` 才算静默——低重要性一定不会出声、
     * 不会有横幅，这是系统保证的；高重要性不保证有声，但我们**不能**拿一个读不准的声音字段
     * 去判它静默。两个方向的错法代价并不对称，这才是取舍的依据：
     * 误报静默 → 用户被指去改一个本来正常的开关，改完页面还是红的（渠道创建后应用改不动，
     * 只能用户改，而用户那边已经是对的，于是**永远无法消除**）；漏报静默 → 最坏只是这一条不出声，
     * 而「声音=读不到」这个原始值还能在排查时拿到（[ChannelHealth.rawValuesText]）。
     * 另外，应用自己那条静音渠道**不需要**靠这个判定来识别：它是应用自己建的，
     * 谁在用完全由「通知提示音」设置决定（见 [activeChannelHealthy]）。
     *
     * ## 三个判定的依据
     *
     * * **静默**：`importance <= IMPORTANCE_LOW`（理由见上）。它只表示「系统把这条渠道当静默」，
     *   至于声音 URI 与振动开关，只作为 [ChannelHealth.rawValuesText] 里的信息展示。
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
                importance = channel.importance,
                hasSound = channel.sound != null,
                // 纯信息字段，读不到就按「关」显示：不让一个排查用的值把整条渠道的体检搞崩
                // （个别 ROM / 测试环境下这个方法可能没实现）。
                vibrates = runCatching { channel.shouldVibrate() }.getOrDefault(false),
            )
        }
    }

    /**
     * 是否算「静默通知」。**只看重要性**——理由（真机误报的根因与代价不对称）写在
     * [notificationChannelHealth] 的注释里，这里刻意不再引入 `sound == null` 这一支：
     * 它在 ColorOS 上被证伪，加回去就等于把那个「永远显示未开启」的 bug 一起加回来。
     *
     * 抽成扩展函数只为让判定条件集中在一处，便于与注释一一对应，也让测试能直接钉住语义。
     */
    private fun NotificationChannel.isSilentForUser(): Boolean =
        importance <= NotificationManager.IMPORTANCE_LOW

    /**
     * 当前实际使用的那条渠道能不能用（给顶部横幅的计数用）。
     *
     * 与检查页的区别：渠道**还没创建**时这里返回 true。横幅是「有 N 项要你去开」的提醒，
     * 而「渠道还没建」是应用自己没走到那一步（首次发通知时才建），不是用户能处理的事；
     * 检查页则把它如实算成「未就绪」，因为那一页是让人逐项核对的。
     *
     * 「静默」这一项判的是 [ChannelHealth.silent]，也就是**只看重要性**（见
     * [notificationChannelHealth]）：横幅计数与检查页共用同一个字段，因此 ColorOS 上那次误报
     * 在两个地方会同时消失——不会再出现「检查页说静默、顶部横幅也一直喊还差一项」的连锁误报。
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

    // -----------------------------------------------------------------------
    // 「照着做」的设置清单（页面主体）
    // -----------------------------------------------------------------------
    // 为什么整份清单放在这一层，而不是写死在 Composable 里：
    // * 这一页的业务逻辑只有两件事——给用户看哪几条、其中几条还没完成——放在这里就能用普通
    //   单元测试钉住（OPPO 那四条原文、「以用户确认为准」的计数），不必去跑 Compose 测试；
    // * 跳转目标是枚举而不是 Intent：页面只把它翻译成一次 openXxx 调用，厂商页面不存在时的
    //   兜底仍然只有 [launchFirstAvailable] 一处，界面里不再自己拼一套跳转。
    //
    // 文案总原则（用户反馈：这一页字太多、术语太多）：短句、动词开头、不说原理、
    // 不出现 importance / 渠道 / 数字指标。机制说明留在本文件的注释与 README 里，
    // 不摆到用户面前——用户要的是「点哪里、改成什么」，不是判定规则。

    /** 手机厂商分组：决定给用户看哪一套「去哪里改」的路径。 */
    enum class PhoneBrand { OPPO, XIAOMI, HUAWEI, VIVO, OTHER }

    /**
     * 按品牌/厂商名判分组。
     *
     * 只用来选展示哪一组路径，**不参与任何跳转决策**：跳哪一页仍由各 `openXxx` 的候选链
     * 在运行时 `resolveActivity` 决定。因此这里判错的代价只是用户看到另一组路径
     * （同一件事各厂商叫法不同，而且路径本身也照实标了「各版本可能不同」）。
     *
     * 写成接收两个字符串的纯函数（而不是直接读 [Build]）：单元测试里改不了 Build 的静态字段，
     * 参数化之后每个分组才能被测到。
     */
    fun brandOf(brand: String, manufacturer: String): PhoneBrand {
        val text = (brand + " " + manufacturer).lowercase()
        return when {
            listOf("oppo", "realme", "oneplus").any { text.contains(it) } -> PhoneBrand.OPPO
            listOf("xiaomi", "redmi", "poco").any { text.contains(it) } -> PhoneBrand.XIAOMI
            listOf("huawei", "honor").any { text.contains(it) } -> PhoneBrand.HUAWEI
            listOf("vivo", "iqoo").any { text.contains(it) } -> PhoneBrand.VIVO
            else -> PhoneBrand.OTHER
        }
    }

    /** 当前真机的分组：页面顶部那行「你的手机：…」由它决定。 */
    fun currentBrand(): PhoneBrand = brandOf(Build.BRAND.orEmpty(), Build.MANUFACTURER.orEmpty())

    /** 分组在界面上的名字，用户要一眼认出该看哪一组。 */
    fun brandLabel(brand: PhoneBrand): String = when (brand) {
        PhoneBrand.OPPO -> "OPPO / 一加 / realme（ColorOS）"
        PhoneBrand.XIAOMI -> "小米 / 红米（MIUI / 澎湃 OS）"
        PhoneBrand.HUAWEI -> "华为 / 荣耀"
        PhoneBrand.VIVO -> "vivo / iQOO（OriginOS）"
        PhoneBrand.OTHER -> "其他安卓手机（原生 / 三星 / 魅族等）"
    }

    /**
     * 系统里**读得到**的那几个开关。
     *
     * 只有这几项参与「是不是已经好了」的自动判定：它们都有明确的公开读取接口，读回来是什么
     * 就是什么。厂商自己的开关（后台弹出界面、耗电管理）与通知渠道的静默/横幅/锁屏细节
     * 都不在这里——那些读不到，或者在真机上被证伪过（见 [ChannelHealth] 的注释）。
     */
    enum class AutoCheck { NOTIFICATIONS, EXACT_ALARM, FULL_SCREEN_INTENT, BATTERY }

    /**
     * 「去设置」要打开的那一处。
     *
     * 取值刻意只覆盖本文件里已有的候选意图链：直达不了时由 [launchFirstAvailable] 兜到
     * 应用详情页，所以这里不需要再单列一个「应用详情」目标。
     */
    enum class SettingsTarget {
        NOTIFICATION,
        EXACT_ALARM,
        BACKGROUND_POPUP,
        FULL_SCREEN_INTENT,
        BATTERY,
    }

    /** 页面拿到的四项系统状态（调用方从系统读好传进来，这里只做判断与展示）。 */
    data class SystemStates(
        val notifications: Boolean,
        val exactAlarm: Boolean,
        val fullScreenIntent: Boolean,
        val batteryUnrestricted: Boolean,
    ) {
        fun isOn(check: AutoCheck): Boolean = when (check) {
            AutoCheck.NOTIFICATIONS -> notifications
            AutoCheck.EXACT_ALARM -> exactAlarm
            AutoCheck.FULL_SCREEN_INTENT -> fullScreenIntent
            AutoCheck.BATTERY -> batteryUnrestricted
        }
    }

    /**
     * 清单里的一条：一句标题 + 一行路径 + 一个「去设置」+ 一个「我已设置好」。
     *
     * @param id 持久化用的稳定 id（存进设置里的「已确认项」集合）。**发布后不能再改**：
     *   改了等于把用户之前勾的全部丢掉。
     * @param autoCheck 能自动读到的那个开关；有值且读回「已允许」就算这一条完成。
     *   **读不到状态的一律为 null**——那种情况只认用户勾选，页面不替系统报一个状态。
     * @param hintCheck 这一条里另有一个读得到的开关，但**不参与计数**，只出一句人话提示。
     *   目前只有 OPPO 的「后台弹出界面和发送全屏通知」用它：前半句读不到、后半句读得到，
     *   混在一条里时不能拿后半句当整条的结论（拿它当结论就是在替读不到的那半句报状态）。
     */
    data class ChecklistStep(
        val id: String,
        val title: String,
        val path: String,
        val target: SettingsTarget,
        val autoCheck: AutoCheck? = null,
        val hintCheck: AutoCheck? = null,
    )

    /** 清单的一组：界面上用它当小标题，下面依次是 [steps]。 */
    data class ChecklistSection(val title: String, val steps: List<ChecklistStep>)

    // 下面这些 id 是持久化契约（用户勾选就存它们）：只能新增，不能改名，否则用户的勾选会丢
    const val STEP_LOCKSCREEN_BANNER = "lockscreen_banner"
    const val STEP_SILENT_OFF = "silent_off"
    const val STEP_BACKGROUND_POPUP = "background_popup"
    const val STEP_BATTERY = "battery"
    const val STEP_ALLOW_NOTIFICATIONS = "allow_notifications"
    const val STEP_EXACT_ALARM = "exact_alarm"
    const val STEP_FULL_SCREEN_INTENT = "full_screen_intent"

    /**
     * 页面主体：两组清单。
     *
     * 顺序是「先厂商路径、后系统权限」：用户来这一页就是为了照着厂商路径改那几个开关；
     * 系统权限那几条能自动检测、多数时候本来就是好的，放后面不占视线。
     */
    fun sections(brand: PhoneBrand, alarmModeInUse: Boolean): List<ChecklistSection> = listOf(
        ChecklistSection("你的手机：" + brandLabel(brand), oemSteps(brand)),
        ChecklistSection("系统权限", systemSteps(brand, alarmModeInUse)),
    )

    /**
     * 展平后的全部条目。
     *
     * 页面用 [sections] 渲染、用这一份计数，测试也用它断言「看见了哪几条」：
     * 两者必须是同一批数据，否则会出现「看见 6 条、计数只算 4 条」这种对不上的情况。
     */
    fun steps(brand: PhoneBrand, alarmModeInUse: Boolean): List<ChecklistStep> =
        sections(brand, alarmModeInUse).flatMap { it.steps }

    /**
     * 厂商路径那一组。
     *
     * 这些路径是**经验值**（按各 ROM 常见版本的叫法整理的），不同版本可能不一样，页面顶部
     * 照实标注了这一点——宁可让用户按路径自己找，也不把一条「看起来很精确」的路径说成保证。
     * OPPO 那四条是用户在真机上逐条核对过的原文，一字不改。
     *
     * 全部 `autoCheck = null`：这一组的开关要么没有公开 API（后台弹出界面、耗电管理），
     * 要么读回来的值在真机上被证伪过（渠道的静默/横幅/锁屏细节），所以一律只认用户勾选；
     * 其中确实读得到的那个（电池优化、发送全屏通知）降级成 [ChecklistStep.hintCheck] 的一句提示。
     */
    private fun oemSteps(brand: PhoneBrand): List<ChecklistStep> = when (brand) {
        // OPPO / 一加 / realme：用户给的四条原文（标题即为原话，路径只把「/」换成箭头）
        PhoneBrand.OPPO -> listOf(
            step(
                STEP_LOCKSCREEN_BANNER,
                "打开锁屏和横幅通知",
                "应用详情 → 通知管理 → 锁屏和横幅打勾",
                SettingsTarget.NOTIFICATION,
            ),
            step(
                STEP_SILENT_OFF,
                "关掉静默通知",
                "应用详情 → 通知管理 → 待办提醒（有声）和闹钟式提醒 → 静默通知（关掉）",
                SettingsTarget.NOTIFICATION,
            ),
            step(
                STEP_BACKGROUND_POPUP,
                "允许后台弹出界面和发送全屏通知",
                "应用详情 → 权限管理 → 其他权限 / 特殊应用权限 → 后台弹出界面 和 发送全屏通知（允许）",
                SettingsTarget.BACKGROUND_POPUP,
                hintCheck = AutoCheck.FULL_SCREEN_INTENT,
            ),
            step(
                STEP_BATTERY,
                "允许完全后台行为",
                "应用详情 → 耗电管理 → 完全允许后台行为",
                SettingsTarget.BATTERY,
                // 只作提示、不参与计数：这里真正管事的是 ColorOS 的「完全允许后台行为」，
                // 而读得到的是 AOSP 那套电池优化白名单，两者不是一回事（见本文件里
                // openBatteryOptimizationSettings 的注释），拿它当结论会偏乐观。
                hintCheck = AutoCheck.BATTERY,
            ),
        )

        // 小米 / 红米（MIUI / 澎湃 OS）
        PhoneBrand.XIAOMI -> listOf(
            step(
                STEP_LOCKSCREEN_BANNER,
                "打开锁屏和横幅通知",
                "应用详情 → 通知管理 → 锁屏通知 和 横幅通知（打开）",
                SettingsTarget.NOTIFICATION,
            ),
            step(
                STEP_SILENT_OFF,
                "关掉静默通知",
                "应用详情 → 通知管理 → 待办提醒（有声）→ 静默通知（关掉）",
                SettingsTarget.NOTIFICATION,
            ),
            step(
                STEP_BACKGROUND_POPUP,
                "允许后台弹出界面",
                "应用详情 → 权限管理 → 其他权限 → 后台弹出界面（允许）",
                SettingsTarget.BACKGROUND_POPUP,
            ),
            step(
                STEP_BATTERY,
                "允许后台运行和自启动",
                "应用详情 → 省电策略 → 无限制；自启动 → 允许",
                SettingsTarget.BATTERY,
                hintCheck = AutoCheck.BATTERY,
            ),
        )

        // 华为 / 荣耀（EMUI / MagicOS）
        PhoneBrand.HUAWEI -> listOf(
            step(
                STEP_LOCKSCREEN_BANNER,
                "打开锁屏和横幅通知",
                "应用详情 → 通知管理 → 锁屏通知 和 横幅通知（打开）",
                SettingsTarget.NOTIFICATION,
            ),
            step(
                STEP_SILENT_OFF,
                "关掉静默通知",
                "应用详情 → 通知管理 → 待办提醒（有声）→ 静默通知（关掉）",
                SettingsTarget.NOTIFICATION,
            ),
            step(
                STEP_BACKGROUND_POPUP,
                "允许后台弹出界面",
                "应用详情 → 权限管理 → 其他权限 → 后台弹出界面（允许）",
                SettingsTarget.BACKGROUND_POPUP,
            ),
            step(
                STEP_BATTERY,
                "允许后台运行",
                "手机管家 → 应用启动管理 → 本应用 → 手动管理（自启动、关联启动、后台活动都允许）",
                SettingsTarget.BATTERY,
                hintCheck = AutoCheck.BATTERY,
            ),
        )

        // vivo / iQOO（OriginOS）
        PhoneBrand.VIVO -> listOf(
            step(
                STEP_LOCKSCREEN_BANNER,
                "打开锁屏和横幅通知",
                "应用详情 → 通知管理 → 锁屏通知 和 横幅通知（打开）",
                SettingsTarget.NOTIFICATION,
            ),
            step(
                STEP_SILENT_OFF,
                "关掉静默通知",
                "应用详情 → 通知管理 → 待办提醒（有声）→ 静默通知（关掉）",
                SettingsTarget.NOTIFICATION,
            ),
            step(
                STEP_BACKGROUND_POPUP,
                "允许后台弹出界面",
                "应用详情 → 权限管理 → 其他权限 → 后台弹出界面（允许）",
                SettingsTarget.BACKGROUND_POPUP,
            ),
            step(
                STEP_BATTERY,
                "允许后台高耗电",
                "应用详情 → 电池 → 后台高耗电（允许）",
                SettingsTarget.BATTERY,
                hintCheck = AutoCheck.BATTERY,
            ),
        )

        // 类原生 / 三星 / 魅族等：没有厂商自己那套「后台弹出界面」，少一条，其余叫法照系统原文
        PhoneBrand.OTHER -> listOf(
            step(
                STEP_LOCKSCREEN_BANNER,
                "打开锁屏和横幅通知",
                "应用详情 → 通知管理 → 锁屏通知 和 横幅通知（打开）",
                SettingsTarget.NOTIFICATION,
            ),
            step(
                STEP_SILENT_OFF,
                "关掉静默通知",
                "应用详情 → 通知管理 → 待办提醒（有声）→ 静默通知（关掉）",
                SettingsTarget.NOTIFICATION,
            ),
            step(
                STEP_BATTERY,
                "允许后台运行",
                "应用详情 → 电池 → 不受限制（三星：设置 → 电池 → 后台使用限制 → 允许后台活动）",
                SettingsTarget.BATTERY,
                hintCheck = AutoCheck.BATTERY,
            ),
        )
    }

    /**
     * 「系统权限」那一组：全是 Android 自己有明确读取接口的开关，读得到就不用用户操心。
     *
     * 为什么能自动检测的只留这几条、而且还单独成一组：这三条的状态是确定的（已允许/未允许），
     * 用户看完可以直接跳过；和厂商路径混排会让「哪些不用管」看不出来。
     *
     * 两个只在需要时出现的条目：
     * * 「闹钟和提醒」与「发送全屏通知」都只为**闹钟式提醒**服务。通知式提醒既不要求精确闹钟、
     *   也不弹整屏页面，这时候把它们列出来等于让用户去开一个用不到的开关，而且计数会永远差一项。
     * * OPPO 那一组的第 3 条已经把「发送全屏通知」写进路径了，这里不再重复列一条（重复就是冗余）。
     */
    private fun systemSteps(brand: PhoneBrand, alarmModeInUse: Boolean): List<ChecklistStep> = buildList {
        add(
            step(
                STEP_ALLOW_NOTIFICATIONS,
                "允许这个应用发通知",
                "应用详情 → 通知管理 → 允许通知（打开）",
                SettingsTarget.NOTIFICATION,
                autoCheck = AutoCheck.NOTIFICATIONS,
            ),
        )
        if (!alarmModeInUse) return@buildList
        add(
            step(
                STEP_EXACT_ALARM,
                "允许「闹钟和提醒」",
                "应用详情 → 权限管理 → 其他权限 / 特殊应用权限 → 闹钟和提醒（允许）",
                SettingsTarget.EXACT_ALARM,
                autoCheck = AutoCheck.EXACT_ALARM,
            ),
        )
        if (brand != PhoneBrand.OPPO) {
            add(
                step(
                    STEP_FULL_SCREEN_INTENT,
                    "允许「发送全屏通知」",
                    "应用详情 → 通知管理 → 发送全屏通知（允许）",
                    SettingsTarget.FULL_SCREEN_INTENT,
                    autoCheck = AutoCheck.FULL_SCREEN_INTENT,
                ),
            )
        }
    }

    /** 免去每条都写一遍参数名；[ChecklistStep] 的默认值已经说明了哪些可以省。 */
    private fun step(
        id: String,
        title: String,
        path: String,
        target: SettingsTarget,
        autoCheck: AutoCheck? = null,
        hintCheck: AutoCheck? = null,
    ) = ChecklistStep(id, title, path, target, autoCheck, hintCheck)

    /**
     * 这一条还差着吗。判定顺序与用户约定的一致：**先看用户确认，再看系统读数**。
     *
     * * 用户勾了「我已设置好」→ 无论系统读出什么，都不再算未完成（也就不会再被提示）；
     * * 没勾、且这一条有可读的开关、读数确实是「未允许」→ 算一条未完成；
     * * 没勾、且这一条读不到状态（[ChecklistStep.autoCheck] 为 null）→ 也算未完成，等用户确认。
     *   这不是「谎报它没开」，而是「还没确认」：页面上一句状态都不显示（见 [statusHint]），
     *   只说「这一条请你照着路径核对一下」。
     */
    fun isStepPending(step: ChecklistStep, states: SystemStates, confirmed: Set<String>): Boolean {
        if (step.id in confirmed) return false
        val check = step.autoCheck ?: return true
        return !states.isOn(check)
    }

    /** 顶部那句「还有 N 项没设置好」里的数字。 */
    fun pendingStepCount(
        steps: List<ChecklistStep>,
        states: SystemStates,
        confirmed: Set<String>,
    ): Int = steps.count { isStepPending(it, states, confirmed) }

    /** 可读开关在用户眼里的名字：提示里要点明是哪一个，免得和整条的结论混淆。 */
    fun autoCheckLabel(check: AutoCheck): String = when (check) {
        AutoCheck.NOTIFICATIONS -> "通知权限"
        AutoCheck.EXACT_ALARM -> "闹钟和提醒"
        AutoCheck.FULL_SCREEN_INTENT -> "发送全屏通知"
        AutoCheck.BATTERY -> "电池优化"
    }

    /**
     * 这一条能给出的「一句人话」状态提示；**读不到状态的条返回 null**（一个字都不显示）。
     *
     * 两种来源：[ChecklistStep.autoCheck]（也参与计数）与 [ChecklistStep.hintCheck]（只提示）。
     * 后者要带上开关名字，因为同一行里还有读不到的东西，光说「系统里看是未允许」会被当成整条的结论。
     */
    fun statusHint(step: ChecklistStep, states: SystemStates): String? {
        val check = step.hintCheck ?: step.autoCheck ?: return null
        val state = if (states.isOn(check)) "已允许" else "未允许"
        return if (step.hintCheck != null) {
            "${autoCheckLabel(check)} 系统里看是：$state"
        } else {
            "系统里看是：$state"
        }
    }
}
