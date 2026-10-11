package com.example.todolist.reminder

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.todolist.domain.ReminderPlan
import java.util.concurrent.TimeUnit

/**
 * 普通通知策略：交给 WorkManager 做延迟任务。
 *
 * 优点是完全不需要特殊权限；代价是系统会把任务与其他后台工作批处理，
 * 实际触发时间可能延后（Doze / 省电模式下更明显）。这一点在 UI 与 README 里都写明。
 */
class WorkReminderScheduler(private val context: Context) : ReminderScheduler {

    private val workManager: WorkManager get() = WorkManager.getInstance(context)

    override fun schedule(plan: ReminderPlan) {
        val delayMillis = (plan.triggerAt - System.currentTimeMillis()).coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(
                Data.Builder()
                    .putLong(ReminderWorker.KEY_TODO_ID, plan.todoId)
                    .build()
            )
            .addTag(tagOf(plan.todoId))
            .build()
        workManager.enqueueUniqueWork(uniqueNameOf(plan.todoId), ExistingWorkPolicy.REPLACE, request)
    }

    override fun cancel(todoId: Long) {
        workManager.cancelUniqueWork(uniqueNameOf(todoId))
    }

    companion object {
        fun uniqueNameOf(todoId: Long): String = "reminder-$todoId"

        fun tagOf(todoId: Long): String = "reminder-tag-$todoId"
    }
}
