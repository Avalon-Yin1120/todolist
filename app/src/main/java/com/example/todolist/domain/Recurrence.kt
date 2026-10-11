package com.example.todolist.domain

/**
 * 提醒方式。
 *
 * 用枚举取代此前「是否精确闹钟」的布尔开关：用户看见的是「要它怎么提醒我」，
 * 而不是「用哪种系统 API」——精确/非精确是实现细节，放进设置页说明。
 */
enum class ReminderMode {
    /** 发一条通知。 */
    NOTIFICATION,

    /** 像闹钟一样：响铃 + 亮屏 + 全屏提醒页（需要精确闹钟权限）。 */
    ALARM,
}

/** 周期规则。 */
enum class Recurrence {
    NONE,
    DAILY,
    WEEKLY,
    MONTHLY,
    ;

    val isRecurring: Boolean get() = this != NONE
}

/**
 * 重复待办的「异常日期」。
 *
 * 修改或删除某一次重复待办时，不会改动整条规则，而是把这个日期记进异常列表，
 * 于是「仅本次」的修改/删除不会影响其它场次。
 *
 * @param date 以本地日期表示的某一天，格式 yyyy-MM-dd
 */
data class RecurrenceException(
    val date: String,
    val kind: RecurrenceExceptionKind,
)

enum class RecurrenceExceptionKind {
    /** 这一次被删除（跳过）。 */
    SKIPPED,

    /** 这一次被单独改过，具体内容存在 override 待办里。 */
    OVERRIDDEN,
}
