package com.example.todolist.data

import com.example.todolist.domain.Clock
import com.example.todolist.domain.ReminderTime
import com.example.todolist.domain.Todo

/**
 * 表单输入到落库之间的中间态。
 * 校验在 ViewModel 里做，这里只承载「用户填了什么」。
 */
data class TodoDraft(
    val id: Long = 0L,
    val name: String,
    val startAt: Long,
    val endAt: Long,
    val leadMinutes: Int,
    val useExactAlarm: Boolean,
)

/** 把草稿与已有记录合成一条可落库的实体。 */
fun TodoDraft.toTodo(existing: Todo?, now: Long): Todo {
    val name = ReminderTime.normalizeName(name)
    return if (existing == null) {
        Todo(
            name = name,
            startAt = startAt,
            endAt = endAt,
            leadMinutes = leadMinutes,
            useExactAlarm = useExactAlarm,
            isDone = false,
            notifiedAt = null,
            createdAt = now,
            updatedAt = now,
        )
    } else {
        // 提醒时刻变了就把「已提醒」标记清掉，否则编辑后不会再响。
        val scheduleChanged = existing.startAt != startAt ||
            existing.leadMinutes != leadMinutes ||
            existing.useExactAlarm != useExactAlarm
        existing.copy(
            name = name,
            startAt = startAt,
            endAt = endAt,
            leadMinutes = leadMinutes,
            useExactAlarm = useExactAlarm,
            notifiedAt = if (scheduleChanged) null else existing.notifiedAt,
            updatedAt = now,
        )
    }
}

/** 用于日志与状态文案的简单描述。 */
fun Todo.describe(clock: Clock): String =
    "Todo(id=$id, name=$name, start=${startAt}, end=${endAt}, lead=$leadMinutes, " +
        "exact=$useExactAlarm, done=$isDone, notified=$notifiedAt, now=${clock.now()})"
