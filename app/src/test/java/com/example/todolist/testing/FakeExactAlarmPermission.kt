package com.example.todolist.testing

import com.example.todolist.sys.ExactAlarmPermission

/** 可切换的精确闹钟权限，用来验证降级路径。 */
class FakeExactAlarmPermission(var granted: Boolean = true) : ExactAlarmPermission {
    override fun canScheduleExactAlarms(): Boolean = granted
}
