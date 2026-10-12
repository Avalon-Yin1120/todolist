package com.example.todolist.sys

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
