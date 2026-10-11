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

    /** 标记「哪一场已经提醒过」。 */
    suspend fun markReminded(id: Long, occurrenceStart: Long) = dao.markReminded(id, occurrenceStart)

    /**
     * 与给定时间段重叠的未完成待办（可排除自己）。
     *
     * 用于「新建时该时段已有其它待办」的提示：按需求仍然允许创建，只是提醒用户。
     */
    suspend fun findOverlapping(startAt: Long, endAt: Long, excludeId: Long = 0L): List<Todo> =
        dao.findOverlapping(startAt, endAt)
            .map { it.toDomain() }
            .filter { it.id != excludeId }

    /** 由「仅修改本次」派生的独立待办。 */
    suspend fun findChildren(parentId: Long): List<Todo> =
        dao.findChildren(parentId).map { it.toDomain() }
}
