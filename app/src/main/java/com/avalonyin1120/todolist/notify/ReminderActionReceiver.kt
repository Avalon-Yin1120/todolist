package com.avalonyin1120.todolist.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import com.avalonyin1120.todolist.TodoApp
import com.avalonyin1120.todolist.graphOrNull
import kotlinx.coroutines.launch

/**
 * 提醒通知上的操作按钮：
 * * **完成**：直接标记完成并撤掉调度（不用打开 App）；
 * * **10 分钟后再提醒**：稍后再响一次；
 * * **停止**：只结束正在响的闹钟，不改待办状态（挂在响铃时的常驻通知上）。
 *
 * 为什么需要它：真机上「后台启动前台服务 / 后台拉起界面」会被系统限制，
 * 尤其在没有精确闹钟权限（拿不到临时白名单）时，到点往往**只剩一条通知**。
 * 这种情况下通知就是唯一的交互面，必须能就地完成、稍后提醒或直接停止响铃。
 *
 * 都走广播（不需要启动界面），因此不受后台启动限制。
 */
class ReminderActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // 「停止」不依赖待办是否存在（闹钟可能是在待办被删掉后仍在响的），
        // 因此放在 todoId 校验之前。
        if (intent.action == ACTION_STOP_ALARM) {
            Log.i(TAG, "通知里停止响铃")
            AlarmRingService.stopEverything(context)
            return
        }

        val todoId = intent.getLongExtra(EXTRA_TODO_ID, -1L)
        if (todoId <= 0L) return

        val app = context.applicationContext as? TodoApp ?: return
        val graph = app.graphOrNull() ?: return

        val pending = goAsync()
        TodoApp.scope.launch {
            try {
                when (intent.action) {
                    ACTION_DONE -> {
                        Log.i(TAG, "通知里直接完成：todo=$todoId")
                        graph.coordinator.setDone(todoId, true)
                        graph.notificationHelper.cancel(todoId)
                        // 闹钟式可能正在响铃：静音、关掉闹钟页、撤通知、停服务一次做完。
                        // 以前这里只静音「服务侧」的铃声并 stopService，而闹钟页若自己
                        // 补响着铃声（服务没起来时就是它），那条铃声没人停——用户点了
                        // 「完成」，界面留着就一直在响。
                        AlarmRingService.stopEverything(context, todoId)
                    }

                    ACTION_SNOOZE -> {
                        Log.i(TAG, "稍后提醒 $SNOOZE_MINUTES 分钟：todo=$todoId")
                        scheduleSnooze(context, todoId)
                        graph.notificationHelper.cancel(todoId)
                        AlarmRingService.stopEverything(context, todoId)
                    }

                    ACTION_SNOOZE_FIRE -> {
                        val todo = graph.coordinator.findById(todoId)
                        if (todo == null || todo.isDone) {
                            Log.i(TAG, "稍后提醒到点，但待办已完成或不存在：todo=$todoId")
                            return@launch
                        }
                        Log.i(TAG, "稍后提醒到点，重新发送：todo=$todoId")
                        graph.notificationHelper.notifyReminder(todo)
                    }

                    else -> Unit
                }
            } catch (e: Exception) {
                Log.e(TAG, "通知操作失败：${e.javaClass.simpleName} ${e.message}")
            } finally {
                pending.finish()
            }
        }
    }

    /**
     * 排一次「稍后提醒」。
     *
     * 用非精确闹钟而不是精确闹钟：稍后提醒对精度不敏感，而且这样**不需要任何权限**，
     * 在没有精确闹钟权限的真机上同样可用。data 与 requestCode 都与正式提醒错开，
     * 避免互相覆盖。
     */
    private fun scheduleSnooze(context: Context, todoId: Long) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, ReminderActionReceiver::class.java).apply {
            action = ACTION_SNOOZE_FIRE
            data = Uri.parse("todolist://snooze/$todoId")
            putExtra(EXTRA_TODO_ID, todoId)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            snoozeRequestCodeOf(todoId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val at = System.currentTimeMillis() + SNOOZE_MINUTES * 60_000L
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pendingIntent)
            } else {
                manager.set(AlarmManager.RTC_WAKEUP, at, pendingIntent)
            }
        }.onFailure { Log.e(TAG, "排稍后提醒失败：${it.javaClass.simpleName} ${it.message}") }
    }

    companion object {
        const val TAG = "TodoReminder"

        /** 「完成」。 */
        const val ACTION_DONE = "com.avalonyin1120.todolist.action.REMINDER_DONE"

        /** 「稍后提醒」。 */
        const val ACTION_SNOOZE = "com.avalonyin1120.todolist.action.REMINDER_SNOOZE"

        /** 稍后提醒到点（内部使用）。 */
        const val ACTION_SNOOZE_FIRE = "com.avalonyin1120.todolist.action.REMINDER_SNOOZE_FIRE"

        /** 停止正在响的闹钟（响铃时常驻通知上的「停止」，以及上滑划掉该通知）。 */
        const val ACTION_STOP_ALARM = "com.avalonyin1120.todolist.action.STOP_ALARM_RING"

        const val EXTRA_TODO_ID = "extra_todo_id"

        /** 稍后提醒的间隔（分钟）。 */
        const val SNOOZE_MINUTES = 10L

        /** 稍后提醒的 requestCode：与正式提醒错开，避免互相覆盖。 */
        fun snoozeRequestCodeOf(todoId: Long): Int =
            (todoId xor (todoId ushr 32)).toInt() xor 0x2000_0000

        /** 构造「完成」动作的 PendingIntent。 */
        fun doneIntent(context: Context, todoId: Long): PendingIntent =
            actionIntent(context, todoId, ACTION_DONE)

        /** 构造「稍后提醒」动作的 PendingIntent。 */
        fun snoozeIntent(context: Context, todoId: Long): PendingIntent =
            actionIntent(context, todoId, ACTION_SNOOZE)

        /**
         * 构造「停止响铃」动作的 PendingIntent。
         *
         * 待办可能已被删除（此时闹钟仍在响），因此允许 todoId 无效：
         * 这条路径只做「静音 + 关页面 + 撤通知 + 停服务」，不查数据库。
         */
        fun stopAlarmIntent(context: Context, todoId: Long = -1L): PendingIntent =
            actionIntent(context, todoId, ACTION_STOP_ALARM)

        private fun actionIntent(context: Context, todoId: Long, action: String): PendingIntent {
            val intent = Intent(context, ReminderActionReceiver::class.java).apply {
                this.action = action
                data = Uri.parse("todolist://reminder-action/$action/$todoId")
                putExtra(EXTRA_TODO_ID, todoId)
            }
            return PendingIntent.getBroadcast(
                context,
                NotificationHelper.notificationIdOf(todoId) xor action.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
