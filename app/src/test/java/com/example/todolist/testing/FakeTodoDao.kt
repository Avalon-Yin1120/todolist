package com.example.todolist.testing

import com.example.todolist.data.local.TodoDao
import com.example.todolist.data.local.TodoEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * 内存版 DAO。
 *
 * 用它替代 Room 有两个好处：纯 JVM 就能跑（不需要 Robolectric/Android 运行时），
 * 而且可以精确控制 Flow 的发射时机，测试更稳定。
 */
class FakeTodoDao : TodoDao {

    private val state = MutableStateFlow<Map<Long, TodoEntity>>(emptyMap())
    private var nextId: Long = 1L

    /** 记录写操作次序，便于断言「先取消旧调度再写库」这类顺序要求。 */
    val writeLog = mutableListOf<String>()

    override fun observeAll(): Flow<List<TodoEntity>> =
        state.map { map -> map.values.sortedWith(compareBy({ it.isDone }, { it.startAt })) }

    override suspend fun findById(id: Long): TodoEntity? = state.value[id]

    override suspend fun findAll(): List<TodoEntity> = state.value.values.toList()

    override suspend fun insert(entity: TodoEntity): Long {
        val id = if (entity.id == 0L) nextId++ else entity.id
        state.value = state.value + (id to entity.copy(id = id))
        writeLog += "insert($id)"
        return id
    }

    override suspend fun update(entity: TodoEntity) {
        state.value = state.value + (entity.id to entity)
        writeLog += "update(${entity.id})"
    }

    override suspend fun delete(entity: TodoEntity) {
        state.value = state.value - entity.id
        writeLog += "delete(${entity.id})"
    }

    override suspend fun deleteById(id: Long) {
        state.value = state.value - id
        writeLog += "deleteById($id)"
    }

    override suspend fun markNotified(id: Long, notifiedAt: Long) {
        val entity = state.value[id] ?: return
        state.value = state.value + (id to entity.copy(notifiedAt = notifiedAt))
        writeLog += "markNotified($id)"
    }
}
