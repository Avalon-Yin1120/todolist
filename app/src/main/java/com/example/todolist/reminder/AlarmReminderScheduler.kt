package com.example.todolist.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.example.todolist.domain.ReminderPlan

/**
 * 基于 AlarmManager 的提醒通道。同一条通道有两种强度：
 *
 * * [exact] = true：闹钟式提醒。优先用 **`setAlarmClock`**——系统把它当作「用户可见的闹钟」，
 *   Doze 下必定唤醒，状态栏还会出现闹钟图标；国产 ROM（ColorOS / MIUI / EMUI）的省电策略
 *   普遍对「闹钟」放行，而普通精确闹钟会被冻结。权限被拒或调用失败时退到
 *   `setExactAndAllowWhileIdle`，再退到 `setAndAllowWhileIdle`。
 * * [exact] = false：通知方式的主通道。用 `setAndAllowWhileIdle`，**不需要任何特殊权限**，
 *   而且比 JobScheduler（WorkManager）可靠得多：JobScheduler 在国产 ROM 上经常被整体推迟到
 *   「用户下次打开 App」，而闹钟即使设备处于 Doze 也会被投递（同一应用约 9 分钟节流一次）。
 *
 * 两条通道用不同的 data URI 与 requestCode，互不覆盖、互不取消。
 */
class AlarmReminderScheduler(
    private val context: Context,
    private val exact: Boolean = true,
) : ReminderScheduler {

    private val alarmManager: AlarmManager? =
        context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    override fun schedule(plan: ReminderPlan) {
        val manager = alarmManager ?: return
        val pendingIntent = buildPendingIntent(plan.todoId, plan.occurrenceStart)
        try {
            when {
                !exact -> manager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    plan.triggerAt,
                    pendingIntent,
                )

                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    !manager.canScheduleExactAlarms() -> {
                    // 理论到不了这里（协调器已降级），但保留兜底以免抛 SecurityException。
                    manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, plan.triggerAt, pendingIntent)
                }

                else -> manager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(plan.triggerAt, showIntent(plan.todoId)),
                    pendingIntent,
                )
            }
        } catch (_: SecurityException) {
            // setAlarmClock / setExactAndAllowWhileIdle 都要求精确闹钟权限，被拒时退到非精确。
            runCatching {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, plan.triggerAt, pendingIntent)
            }
        }
    }

    override fun cancel(todoId: Long) {
        val manager = alarmManager ?: return
        // 取消时场次时间无关紧要：PendingIntent 的匹配靠 requestCode 与 data（都只含 todoId）。
        manager.cancel(buildPendingIntent(todoId, 0L))
    }

    private fun buildPendingIntent(todoId: Long, occurrenceStart: Long): PendingIntent {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ACTION_FIRE
            // data 唯一化，保证不同待办、不同通道各自得到不同的 PendingIntent。
            data = Uri.parse("todolist://reminder/${channelName()}/$todoId")
            putExtra(EXTRA_TODO_ID, todoId)
            putExtra(EXTRA_OCCURRENCE_START, occurrenceStart)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCodeOf(todoId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * 「点系统闹钟图标」时打开的界面。
     *
     * `setAlarmClock` 需要一个用户可见的入口，缺了它部分 ROM 会拒绝登记这个闹钟；
     * 指向闹钟页而不是主页，用户点图标就能直接处理这条提醒。
     */
    private fun showIntent(todoId: Long): PendingIntent {
        val intent = Intent(context, com.example.todolist.ui.alarm.AlarmActivity::class.java).apply {
            action = com.example.todolist.ui.alarm.AlarmActivity.ACTION_SHOW_ALARM
            data = Uri.parse("todolist://alarm/$todoId")
            putExtra(com.example.todolist.ui.alarm.AlarmActivity.EXTRA_TODO_ID, todoId)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS,
            )
        }
        return PendingIntent.getActivity(
            context,
            requestCodeOf(todoId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun channelName(): String = if (exact) "exact" else "inexact"

    /** PendingIntent 的 requestCode：与 [channelName] 一起保证两条通道互不覆盖。 */
    private fun requestCodeOf(todoId: Long): Int {
        val base = baseRequestCodeOf(todoId)
        return if (exact) base else base xor 0x4000_0000
    }

    companion object {
        const val ACTION_FIRE = "com.example.todolist.action.FIRE_REMINDER"
        const val EXTRA_TODO_ID = "extra_todo_id"
        const val EXTRA_OCCURRENCE_START = "extra_occurrence_start"

        /** requestCode 需要是稳定的 int；用 id 低位即可。 */
        fun baseRequestCodeOf(todoId: Long): Int = (todoId xor (todoId ushr 32)).toInt()
    }
}
