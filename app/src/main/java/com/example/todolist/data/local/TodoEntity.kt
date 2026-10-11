package com.example.todolist.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.todolist.domain.Todo

/** 待办事项持久化表。 */
@Entity(tableName = "todos")
data class TodoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val startAt: Long,
    val endAt: Long,
    val leadMinutes: Int,
    val useExactAlarm: Boolean,
    val isDone: Boolean,
    val notifiedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
)

fun TodoEntity.toDomain(): Todo = Todo(
    id = id,
    name = name,
    startAt = startAt,
    endAt = endAt,
    leadMinutes = leadMinutes,
    useExactAlarm = useExactAlarm,
    isDone = isDone,
    notifiedAt = notifiedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun Todo.toEntity(): TodoEntity = TodoEntity(
    id = id,
    name = name,
    startAt = startAt,
    endAt = endAt,
    leadMinutes = leadMinutes,
    useExactAlarm = useExactAlarm,
    isDone = isDone,
    notifiedAt = notifiedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
