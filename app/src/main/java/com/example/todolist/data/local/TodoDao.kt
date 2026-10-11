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

    /** 列表按起始时间升序，最近的未完成项排在最前。 */
    @Query("SELECT * FROM todos ORDER BY isDone ASC, startAt ASC")
    fun observeAll(): Flow<List<TodoEntity>>

    @Query("SELECT * FROM todos WHERE id = :id")
    suspend fun findById(id: Long): TodoEntity?

    @Query("SELECT * FROM todos")
    suspend fun findAll(): List<TodoEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: TodoEntity): Long

    @Update
    suspend fun update(entity: TodoEntity)

    @Delete
    suspend fun delete(entity: TodoEntity)

    @Query("DELETE FROM todos WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE todos SET notifiedAt = :notifiedAt WHERE id = :id")
    suspend fun markNotified(id: Long, notifiedAt: Long)
}
