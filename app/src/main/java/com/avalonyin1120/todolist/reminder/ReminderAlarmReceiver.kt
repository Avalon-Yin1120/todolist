package com.avalonyin1120.todolist.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.avalonyin1120.todolist.TodoApp
import com.avalonyin1120.todolist.graphOrNull
import kotlinx.coroutines.launch

/**
 * 精确闹钟的到点回调：把待办 id 与场次开始时间交给提醒处理器。
 *
 * 用 goAsync() 延长广播生命周期，保证「标记已提醒 + 重排下一场」的数据库写入
 * 不被半路掐断（周期待办尤其依赖这两步）。
 */
class ReminderAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val todoId = intent.getLongExtra(AlarmReminderScheduler.EXTRA_TODO_ID, -1L)
        if (todoId <= 0L) return
        val occurrenceStart = intent.getLongExtra(
            AlarmReminderScheduler.EXTRA_OCCURRENCE_START,
            0L,
        )

        val app = context.applicationContext as? TodoApp ?: return
        val handler = app.graphOrNull()?.fireHandler ?: return
        val pending = goAsync()
        TodoApp.scope.launch {
            try {
                handler.fire(todoId, occurrenceStart)
            } finally {
                pending.finish()
            }
        }
    }
}
