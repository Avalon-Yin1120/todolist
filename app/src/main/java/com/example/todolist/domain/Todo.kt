package com.example.todolist.domain

/**
 * 一条待办事项在内存中的领域表示。
 *
 * @param startAt 起始时间（epoch 毫秒），也是提醒的基准时间
 * @param endAt   终止时间（epoch 毫秒）
 * @param leadMinutes 提前提醒量（分钟），0 表示准点提醒
 * @param useExactAlarm 是否使用精确闹钟策略；false 时使用普通通知（无权限、可能被系统延后）
 * @param notifiedAt 已发出提醒的时间戳，null 表示尚未提醒
 */
data class Todo(
    val id: Long = 0L,
    val name: String,
    val startAt: Long,
    val endAt: Long,
    val leadMinutes: Int = 0,
    val useExactAlarm: Boolean = false,
    val isDone: Boolean = false,
    val notifiedAt: Long? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
) {
    /** 应该发出提醒的时刻。 */
    val remindAt: Long get() = ReminderTime.remindAt(startAt, leadMinutes)

    /** 相对当前时刻的状态。 */
    fun status(now: Long): TodoStatus = ReminderTime.statusOf(this, now)
}
