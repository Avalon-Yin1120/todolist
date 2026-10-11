package com.example.todolist.domain

/**
 * 提醒相关的纯业务规则。
 *
 * 这里刻意不引入任何 Android API，好处是：提醒时间与状态判定的全部边界情况
 * 都能用最快、最稳定的纯 JVM 单元测试覆盖（见 ReminderTimeTest）。
 */
object ReminderTime {

    /** 提前量的合法上限：7 天。 */
    const val MAX_LEAD_MINUTES: Int = 7 * 24 * 60

    /** 名称长度上限。 */
    const val MAX_NAME_LENGTH: Int = 80

    private const val MILLIS_PER_MINUTE: Long = 60_000L

    /** 计算提醒时刻：起始时间往前推 leadMinutes 分钟。 */
    fun remindAt(startAt: Long, leadMinutes: Int): Long =
        startAt - leadMinutes.toLong() * MILLIS_PER_MINUTE

    /** 根据当前时间判定展示状态。 */
    fun statusOf(todo: Todo, now: Long): TodoStatus = when {
        todo.isDone -> TodoStatus.DONE
        now < todo.startAt -> TodoStatus.UPCOMING
        now < todo.endAt -> TodoStatus.RUNNING
        else -> TodoStatus.ENDED
    }

    /**
     * 判断某条待办此刻是否还需要排定提醒。
     *
     * 返回 false 的三种情况：
     * 1. 已完成；
     * 2. 已经提醒过；
     * 3. 提醒时刻已经过去（例如刚装上 App、数据是从备份恢复的）；
     * 这种情况下直接补发通知会打扰用户，因此跳过。
     */
    fun shouldSchedule(todo: Todo, now: Long): Boolean = when {
        todo.isDone -> false
        todo.notifiedAt != null -> false
        remindAt(todo.startAt, todo.leadMinutes) <= now -> false
        else -> true
    }

    /** 校验提前量是否在合法区间。 */
    fun isValidLead(leadMinutes: Int): Boolean = leadMinutes in 0..MAX_LEAD_MINUTES

    /** 校验起止时间是否合理。 */
    fun isValidRange(startAt: Long, endAt: Long): Boolean = endAt > startAt

    /** 校验名称。 */
    fun isValidName(rawName: String): Boolean {
        val trimmed = rawName.trim()
        return trimmed.isNotEmpty() && trimmed.length <= MAX_NAME_LENGTH
    }

    /** 归一化名称：去首尾空白。 */
    fun normalizeName(rawName: String): String = rawName.trim()
}
