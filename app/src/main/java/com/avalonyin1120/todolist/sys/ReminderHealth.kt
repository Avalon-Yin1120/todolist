package com.avalonyin1120.todolist.sys

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

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

    /** 打开本应用的通知设置页。 */
    fun openNotificationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        launchOrAppDetails(context, intent)
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
        launchOrAppDetails(context, intent)
    }

    /** 打开本应用的「应用信息」页——国产 ROM 的自启动/后台管理入口都在它的下一层。 */
    fun openAppDetailsSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        launchOrAppDetails(context, intent)
    }

    /**
     * 打开系统的「电池优化」列表页，让用户本应用选「不优化」。
     *
     * ## 为什么用列表页，而不是那条会弹确认框的 `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
     *
     * 后者要申请 `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 权限，而**Google Play 的内容政策
     * 不允许普通应用申请它**：Android 自带的 lint 就有专门一条检查
     * （`BatteryLife`）把声明该权限判为违反 Play 政策，现实中也确实有应用因此被拒。
     * 本应用要的是「闹钟到点能被唤醒」，用列表页 + 文案引导同样能达到目的，
     * 只是用户要多点一步，换来的是上架环节没有这个风险。
     *
     * 注意：**读取**「是否已豁免」（`PowerManager.isIgnoringBatteryOptimizations`）
     * 不需要任何权限，所以本页的状态判断照旧准确。
     */
    fun openBatteryOptimizationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        launchOrAppDetails(context, intent)
    }

    /**
     * 当前系统是不是常见的「需要额外开自启动」的国产 ROM。
     *
     * 只用来决定是否展示专项引导文案，不做任何跳转（那些页面的 Intent 各版本不一致，
     * 硬跳容易失败或跳到错误页面，所以统一让用户从「应用信息」进去）。
     */
    fun needsOemBackgroundGuide(): Boolean {
        val brand = (Build.BRAND + " " + Build.MANUFACTURER).lowercase()
        return listOf("oppo", "realme", "oneplus", "vivo", "iqoo", "xiaomi", "redmi",
            "huawei", "honor", "meizu", "samsung").any { brand.contains(it) }
    }

    private fun launchOrAppDetails(context: Context, intent: Intent) {
        runCatching { context.startActivity(intent) }.onFailure {
            val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(fallback) }
        }
    }
}
