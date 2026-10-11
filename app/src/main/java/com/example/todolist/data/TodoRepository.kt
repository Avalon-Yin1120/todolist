package com.example.todolist.data

import com.example.todolist.data.local.TodoDao
import com.example.todolist.data.local.toDomain
import com.example.todolist.data.local.toEntity
import com.example.todolist.domain.Todo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 待办仓库：只负责持久化与查询，不做校验、不碰提醒调度。
 * 调度编排集中在 SchedulingCoordinator，保证两者可以各自独立测试。
 */
class TodoRepository(private val dao: TodoDao) {

    fun observeAll(): Flow<List<Todo>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun findById(id: Long): Todo? = dao.findById(id)?.toDomain()

    suspend fun findAll(): List<Todo> = dao.findAll().map { it.toDomain() }

    /** 新增，返回自增主键。 */
    suspend fun insert(todo: Todo): Long = dao.insert(todo.toEntity())

    /** 覆盖更新。 */
    suspend fun update(todo: Todo) = dao.update(todo.toEntity())

    suspend fun delete(todo: Todo) = dao.delete(todo.toEntity())

    suspend fun deleteById(id: Long) = dao.deleteById(id)

    suspend fun markNotified(id: Long, at: Long) = dao.markNotified(id, at)
}
