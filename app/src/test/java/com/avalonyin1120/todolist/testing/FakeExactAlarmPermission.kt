package com.avalonyin1120.todolist.testing

import com.avalonyin1120.todolist.sys.ExactAlarmPermission

/** 可切换的精确闹钟权限，用来验证降级路径。 */
class FakeExactAlarmPermission(var granted: Boolean = true) : ExactAlarmPermission {
    override fun canScheduleExactAlarms(): Boolean = granted
}
