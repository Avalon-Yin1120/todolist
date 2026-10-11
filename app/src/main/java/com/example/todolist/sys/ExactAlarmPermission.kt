package com.example.todolist.sys

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * 精确闹钟权限（Android 12+ 的「闹钟和提醒」特殊权限）。
 *
 * 之所以单独成类：调度器需要问权限、UI 需要引导授权，两处都要它，
 * 而测试里需要能假装「没有权限」以验证降级路径。
 */
interface ExactAlarmPermission {
    fun canScheduleExactAlarms(): Boolean
}

/** 生产实现：API 31 以下恒为 true（那时没有这个权限概念）。 */
class SystemExactAlarmPermission(private val context: Context) : ExactAlarmPermission {

    override fun canScheduleExactAlarms(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
        return manager.canScheduleExactAlarms()
    }

    /** 跳转到系统设置让用户允许本应用使用精确闹钟。 */
    fun requestIntent(): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        return Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }
}
