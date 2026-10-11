package com.example.todolist.sys

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/**
 * 后台运行（电池优化白名单）能力。
 *
 * 为什么需要：Android 6+ 的 Doze 与各厂商的省电策略会限制后台任务，
 * 表现就是「到点提醒不响、或者晚了很久」。加入白名单后系统不再限制本应用的
 * 后台闹钟与前台服务，闹钟式提醒才能真正准时响铃并亮屏。
 *
 * 与精确闹钟权限是两个独立开关，缺哪个都会让提醒不可靠，因此都要引导用户开启。
 */
interface BatteryOptimizationPermission {
    /** 是否已豁免电池优化（豁免即允许后台运行）。 */
    fun isIgnoringBatteryOptimizations(): Boolean

    /** 打开系统对话框请求豁免。 */
    fun requestIgnore()
}

class SystemBatteryOptimizationPermission(private val context: Context) :
    BatteryOptimizationPermission {

    override fun isIgnoringBatteryOptimizations(): Boolean {
        val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            ?: return false
        return power.isIgnoringBatteryOptimizations(context.packageName)
    }

    override fun requestIgnore() {
        // ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS 会弹系统确认框；
        // 个别 ROM 上没有这个界面时退回到电池优化设置列表。
        val direct = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(direct) }.onFailure {
            val list = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(list) }
        }
    }
}