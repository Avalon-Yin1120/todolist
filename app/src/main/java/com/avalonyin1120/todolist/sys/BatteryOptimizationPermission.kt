package com.avalonyin1120.todolist.sys

import android.content.Context
import android.os.PowerManager

/**
 * 后台运行（电池优化白名单）能力。
 *
 * 为什么需要：Android 6+ 的 Doze 与各厂商的省电策略会限制后台任务，
 * 表现就是「到点提醒不响、或者晚了很久」。加入白名单后系统不再限制本应用的
 * 后台闹钟与前台服务，闹钟式提醒才能真正准时响铃并亮屏。
 *
 * 与精确闹钟权限是两个独立开关，缺哪个都会让提醒不可靠，因此都要引导用户开启。
 *
 * 注意这里**只读状态 + 打开系统页面**，不申请 `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
 * 权限（原因见 [ReminderHealth.openBatteryOptimizationSettings] 的注释）。
 */
interface BatteryOptimizationPermission {
    /** 是否已豁免电池优化（豁免即允许后台运行）。读取不需要任何权限。 */
    fun isIgnoringBatteryOptimizations(): Boolean

    /** 打开系统的电池优化列表页，由用户手动把本应用设为「不优化」。 */
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
        ReminderHealth.openBatteryOptimizationSettings(context)
    }
}