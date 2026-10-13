package com.avalonyin1120.todolist.reminder

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.avalonyin1120.todolist.TodoApp
import com.avalonyin1120.todolist.graphOrNull
import kotlinx.coroutines.runBlocking

/**
 * 非精确提醒的落地执行者：WorkManager 延迟结束后读取待办并发出通知。
 *
 * 依赖从应用依赖图取，测试可整体替换为 Fake。
 */
class ReminderWorker(
    context: Context,
    params: WorkerParameters,
) : Worker(context, params) {

    override fun doWork(): Result {
        val todoId = inputData.getLong(KEY_TODO_ID, -1L)
        if (todoId <= 0L) return Result.failure()
        val occurrenceStart = inputData.getLong(KEY_OCCURRENCE_START, 0L)

        val app = applicationContext as? TodoApp ?: return Result.retry()
        val handler = app.graphOrNull()?.fireHandler ?: return Result.retry()
        return try {
            runBlocking { handler.fire(todoId, occurrenceStart) }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val KEY_TODO_ID = "todo_id"
        const val KEY_OCCURRENCE_START = "occurrence_start"
    }
}