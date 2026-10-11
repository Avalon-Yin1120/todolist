package com.example.todolist.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoDao {

    /** 全部待办，按开始时间升序；各视图再按需分组。 */
    @Query("SELECT * FROM todos ORDER BY isDone ASC, startAt ASC")
    fun observeAll(): Flow<List<TodoEntity>>

    @Query("SELECT * FROM todos WHERE id = :id")
    suspend fun findById(id: Long): TodoEntity?

    @Query("SELECT * FROM todos")
    suspend fun findAll(): List<TodoEntity>

    /**
     * 与 [startAt, endAt) 有重叠的未完成待办，用于新建前的时间段冲突检测。
     *
     * 重叠判定：已有项开始早于新项结束，且已有项结束晚于新项开始。
     */
    @Query(
        """
        SELECT * FROM todos
        WHERE startAt < :endAt AND endAt > :startAt AND isDone = 0
        ORDER BY startAt ASC
        """
    )
    suspend fun findOverlapping(startAt: Long, endAt: Long): List<TodoEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: TodoEntity): Long

    @Update
    suspend fun update(entity: TodoEntity)

    @Delete
    suspend fun delete(entity: TodoEntity)

    @Query("DELETE FROM todos WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE todos SET remindedOccurrenceStart = :occurrenceStart WHERE id = :id")
    suspend fun markReminded(id: Long, occurrenceStart: Long)

    /** 由「仅修改本次」生成的独立待办，删除整条重复规则时一并清理。 */
    @Query("SELECT * FROM todos WHERE parentId = :parentId")
    suspend fun findChildren(parentId: Long): List<TodoEntity>
}
