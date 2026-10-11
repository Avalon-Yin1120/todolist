package com.example.todolist.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.todolist.TodoApp
import com.example.todolist.graphOrNull
import kotlinx.coroutines.launch

/**
 * 精确闹钟的到点回调：把待办 id 交给提醒处理器。
 *
 * 用 goAsync() 延长广播生命周期，保证数据库写入（打「已提醒」标记）不被半路掐断。
 */
class ReminderAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val todoId = intent.getLongExtra(AlarmReminderScheduler.EXTRA_TODO_ID, -1L)
        if (todoId <= 0L) return

        val app = context.applicationContext as? TodoApp ?: return
        val handler = app.graphOrNull()?.fireHandler ?: return
        val pending = goAsync()
        TodoApp.scope.launch {
            try {
                handler.fire(todoId)
            } finally {
                pending.finish()
            }
        }
    }
}
