package com.example.todolist.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.todolist.domain.ReminderPlan

/**
 * 精确闹钟实现：`setExactAndAllowWhileIdle`，Doze 下也能按时唤醒。
 *
 * 调用方（SchedulingCoordinator）负责在无权限时降级，本类只做「有权限就该做到的事」。
 */
class AlarmReminderScheduler(private val context: Context) : ReminderScheduler {

    private val alarmManager: AlarmManager? =
        context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    override fun schedule(plan: ReminderPlan) {
        val manager = alarmManager ?: return
        val pendingIntent = buildPendingIntent(plan.todoId)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                !manager.canScheduleExactAlarms()
            ) {
                // 理论到不了这里（协调器已降级），但保留兜底以免抛 SecurityException。
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, plan.triggerAt, pendingIntent)
            } else {
                manager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    plan.triggerAt,
                    pendingIntent,
                )
            }
        } catch (_: SecurityException) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, plan.triggerAt, pendingIntent)
        }
    }

    override fun cancel(todoId: Long) {
        val manager = alarmManager ?: return
        manager.cancel(buildPendingIntent(todoId))
    }

    private fun buildPendingIntent(todoId: Long): PendingIntent {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ACTION_FIRE
            // data 唯一化，保证不同待办得到不同的 PendingIntent。
            data = android.net.Uri.parse("todolist://reminder/$todoId")
            putExtra(EXTRA_TODO_ID, todoId)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCodeOf(todoId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val ACTION_FIRE = "com.example.todolist.action.FIRE_REMINDER"
        const val EXTRA_TODO_ID = "extra_todo_id"

        /** requestCode 需要是稳定的 int；用 id 低位即可。 */
        fun requestCodeOf(todoId: Long): Int = (todoId xor (todoId ushr 32)).toInt()
    }
}
