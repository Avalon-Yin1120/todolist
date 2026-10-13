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
    /**
     * 是否已豁免电池优化（豁免即允许后台运行）。读取不需要任何权限。
     *
     * 注意它只反映 AOSP 的那套白名单。ColorOS 上真正决定「后台能不能跑」的是
     * 耗电管理里的「允许完全后台行为」，那一处**没有公开的读取接口**，
     * 因此查询结果可能在 ColorOS 上偏乐观——界面文案不要因此就说死「已允许」，
     * 而是把用户引到 [requestIgnore] 打开的那一页自己确认。
     */
    fun isIgnoringBatteryOptimizations(): Boolean

    /**
     * 打开「允许后台运行」相关的设置页，由用户手动允许。
     *
     * 具体跳到哪一页由 [ReminderHealth.openBatteryOptimizationSettings] 的
     * 候选意图链在运行时决定（ColorOS 的耗电管理 → 自启动 → 系统电池优化列表 → 应用详情页）。
     */
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