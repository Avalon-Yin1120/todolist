package com.example.todolist.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.todolist.domain.Recurrence
import com.example.todolist.domain.RecurrenceException
import com.example.todolist.domain.RecurrenceExceptionKind
import com.example.todolist.domain.ReminderMode
import com.example.todolist.domain.Todo

/** 待办事项持久化表。 */
@Entity(tableName = "todos")
data class TodoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val startAt: Long,
    val endAt: Long,
    val leadMinutes: Int,
    /** 提醒方式：存枚举名，避免以后加值导致序号错位。 */
    val reminderMode: String,
    /** 周期规则：同样存枚举名。 */
    val recurrence: String,
    /**
     * 例外日期，形如 `2026-10-12:SKIPPED,2026-10-19:SKIPPED`。
     * 用单个字段序列化而非建关联表：例外数量少，一次解析更简单。
     */
    val recurrenceExceptions: String,
    /** 由「仅修改本次」生成的独立待办会指向原重复待办 id。 */
    val parentId: Long?,
    val isDone: Boolean,
    /** 已经提醒过的那一场的开始时间；周期待办必须记录是哪一场。 */
    val remindedOccurrenceStart: Long?,
    val createdAt: Long,
    val updatedAt: Long,
)

fun TodoEntity.toDomain(): Todo = Todo(
    id = id,
    name = name,
    startAt = startAt,
    endAt = endAt,
    leadMinutes = leadMinutes,
    mode = enumValueOrDefault(reminderMode, ReminderMode.NOTIFICATION),
    recurrence = enumValueOrDefault(recurrence, Recurrence.NONE),
    recurrenceExceptions = parseExceptions(recurrenceExceptions),
    parentId = parentId,
    isDone = isDone,
    remindedOccurrenceStart = remindedOccurrenceStart,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun Todo.toEntity(): TodoEntity = TodoEntity(
    id = id,
    name = name,
    startAt = startAt,
    endAt = endAt,
    leadMinutes = leadMinutes,
    reminderMode = mode.name,
    recurrence = recurrence.name,
    recurrenceExceptions = serializeExceptions(recurrenceExceptions),
    parentId = parentId,
    isDone = isDone,
    remindedOccurrenceStart = remindedOccurrenceStart,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

/** 解析失败时回落到默认值，绝不让一条脏数据把整个列表打崩。 */
private inline fun <reified T : Enum<T>> enumValueOrDefault(raw: String, fallback: T): T =
    enumValues<T>().firstOrNull { it.name == raw } ?: fallback

private const val EXCEPTION_ENTRY_SEPARATOR = ","
private const val EXCEPTION_FIELD_SEPARATOR = ":"

internal fun serializeExceptions(exceptions: List<RecurrenceException>): String =
    exceptions.joinToString(EXCEPTION_ENTRY_SEPARATOR) {
        "${it.date}$EXCEPTION_FIELD_SEPARATOR${it.kind.name}"
    }

internal fun parseExceptions(raw: String): List<RecurrenceException> {
    if (raw.isBlank()) return emptyList()
    return raw.split(EXCEPTION_ENTRY_SEPARATOR).mapNotNull { entry ->
        val parts = entry.split(EXCEPTION_FIELD_SEPARATOR)
        if (parts.size != 2 || parts[0].isBlank()) return@mapNotNull null
        val kind = RecurrenceExceptionKind.entries.firstOrNull { it.name == parts[1] }
            ?: return@mapNotNull null
        RecurrenceException(parts[0], kind)
    }
}
