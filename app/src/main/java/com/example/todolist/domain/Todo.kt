package com.example.todolist.domain

/**
 * 一条待办事项在内存中的领域表示。
 *
 * @param startAt 起始时间（epoch 毫秒），也是提醒的基准时间
 * @param endAt   终止时间（epoch 毫秒）
 * @param leadMinutes 提前提醒量（分钟），0 表示准点提醒
 * @param mode 提醒方式：通知 / 闹钟式（响铃 + 亮屏 + 全屏页）
 * @param recurrence 周期规则；[Recurrence.NONE] 表示一次性待办
 * @param weekDays 周重复选中的星期几；为空时回落到开始日期所在的星期
 * @param monthDays 月重复选中的几号；为空时回落到开始日期的号数
 * @param recurrenceExceptions 重复规则的例外日期（跳过或被单独改过的场次）
 * @param parentId 由「仅修改本次」生成的独立待办会指向原重复待办的 id，便于溯源
 * @param remindedOccurrenceStart 已经提醒过的那一场的开始时间；用于避免重复提醒
 *   （周期待办不能只用一个布尔标记，必须记住是哪一场提醒过了）
 */
data class Todo(
    val id: Long = 0L,
    val name: String,
    val startAt: Long,
    val endAt: Long,
    val leadMinutes: Int = 0,
    val mode: ReminderMode = ReminderMode.NOTIFICATION,
    val recurrence: Recurrence = Recurrence.NONE,
    val weekDays: WeekDays = emptySet(),
    val monthDays: MonthDays = emptySet(),
    val recurrenceExceptions: List<RecurrenceException> = emptyList(),
    val parentId: Long? = null,
    val isDone: Boolean = false,
    val remindedOccurrenceStart: Long? = null,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
) {
    /** 应该发出提醒的时刻。 */
    val remindAt: Long get() = ReminderTime.remindAt(startAt, leadMinutes)

    /** 是否已经提醒过（兼容一次性待办的旧判断方式）。 */
    val notifiedAt: Long? get() = remindedOccurrenceStart

    /** 相对当前时刻的状态。 */
    fun status(now: Long): TodoStatus = ReminderTime.statusOf(this, now)
}
