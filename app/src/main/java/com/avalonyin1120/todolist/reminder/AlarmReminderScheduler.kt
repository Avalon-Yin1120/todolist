package com.avalonyin1120.todolist.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import com.avalonyin1120.todolist.domain.ReminderPlan

/**
 * 基于 AlarmManager 的提醒通道。
 *
 * ## 两种强度，都走精确闹钟
 *
 * 应用在清单里声明了 `USE_EXACT_ALARM`（Android 13+），因此绝大多数设备上
 * 「精确闹钟」是自动授予的；[AlarmStyle] 决定的是**用哪一种精确闹钟**：
 *
 * * [AlarmStyle.ALARM_CLOCK]：闹钟式提醒。用 **`setAlarmClock`**——系统把它当作
 *   「用户可见的闹钟」，Doze 下必定唤醒，状态栏会出现闹钟图标；国产 ROM
 *   （ColorOS / MIUI / EMUI）的省电策略普遍对「闹钟」放行，而普通精确闹钟会被冻结。
 * * [AlarmStyle.EXACT]：通知方式的主通道。用 `setExactAndAllowWhileIdle`——精确到点，
 *   但不占用「闹钟」语义（状态栏不出现闹钟图标，也不会把音量当成闹钟音量）。
 *
 * 两条通道都保留「没有精确闹钟权限」时的兜底：退到 `setAndAllowWhileIdle`（不需要任何权限），
 * 宁可晚一点也不能崩、不能什么都不做。旧版本只有「精确 / 非精确」两档，通知方式走的是非精确通道，
 * 因此本条改动让通知方式也从「近似」变成了「精确」——这正是用户反馈「杀进程后不响」时
 * 能做的最实在的一件事（精确闹钟在 Doze / 被冻结后仍会被投递）。
 *
 * 两条通道用不同的 data URI 与 requestCode，互不覆盖、互不取消。
 */
class AlarmReminderScheduler(
    private val context: Context,
    private val style: AlarmStyle,
) : ReminderScheduler {

    private val alarmManager: AlarmManager? =
        context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    override fun schedule(plan: ReminderPlan) {
        val manager = alarmManager ?: return
        val pendingIntent = buildPendingIntent(plan.todoId, plan.occurrenceStart)
        val canScheduleExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            manager.canScheduleExactAlarms()

        if (!canScheduleExact) {
            Log.w(TAG, "没有精确闹钟权限，退化为非精确调度：todo=${plan.todoId}")
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, plan.triggerAt, pendingIntent)
            return
        }
        try {
            when (style) {
                AlarmStyle.ALARM_CLOCK -> manager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(plan.triggerAt, showIntent(plan.todoId)),
                    pendingIntent,
                )

                AlarmStyle.EXACT -> manager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    plan.triggerAt,
                    pendingIntent,
                )
            }
        } catch (_: SecurityException) {
            // setAlarmClock / setExactAndAllowWhileIdle 都要求精确闹钟权限。
            // 上面的检查与真正调用之间权限可能被收回（例如用户在系统设置里关掉），
            // 这时退到非精确而不是崩掉或静默失败。
            Log.w(TAG, "精确调度被拒，退化为非精确调度：todo=${plan.todoId}")
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
            data = Uri.parse("todolist://reminder/${channelKey()}/$todoId")
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
        val intent = Intent(context, com.avalonyin1120.todolist.ui.alarm.AlarmActivity::class.java).apply {
            action = com.avalonyin1120.todolist.ui.alarm.AlarmActivity.ACTION_SHOW_ALARM
            data = Uri.parse("todolist://alarm/$todoId")
            putExtra(com.avalonyin1120.todolist.ui.alarm.AlarmActivity.EXTRA_TODO_ID, todoId)
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

    /**
     * 通道标识，只用来区分两条通道，**不代表精确与否**。
     *
     * 名字沿用早期「按是否精确分成 exact / inexact 两条通道」时的取值：
     * 改名的代价是升级后**旧版本登记的闹钟再也没人取消**（PendingIntent 靠 data + requestCode
     * 匹配），会留下幽灵提醒。所以这里宁可让名字与现在的语义不一致，也不动它。
     */
    private fun channelKey(): String = when (style) {
        AlarmStyle.ALARM_CLOCK -> "exact"
        AlarmStyle.EXACT -> "inexact"
    }

    /** PendingIntent 的 requestCode：与 [channelKey] 一起保证两条通道互不覆盖。 */
    private fun requestCodeOf(todoId: Long): Int {
        val base = baseRequestCodeOf(todoId)
        return when (style) {
            AlarmStyle.ALARM_CLOCK -> base
            AlarmStyle.EXACT -> base xor 0x4000_0000
        }
    }

    companion object {
        const val TAG = "TodoReminder"

        const val ACTION_FIRE = "com.avalonyin1120.todolist.action.FIRE_REMINDER"
        const val EXTRA_TODO_ID = "extra_todo_id"
        const val EXTRA_OCCURRENCE_START = "extra_occurrence_start"

        /** requestCode 需要是稳定的 int；用 id 低位即可。 */
        fun baseRequestCodeOf(todoId: Long): Int = (todoId xor (todoId ushr 32)).toInt()
    }
}

/**
 * 精确闹钟的两种强度。
 *
 * 分成两档而不是「精确 / 非精确」两档，是因为精确闹钟权限现在由 `USE_EXACT_ALARM` 自动授予，
 * 真正的区别变成了「要不要让系统按闹钟对待它」（状态栏图标、音量通道、厂商放行策略）。
 */
enum class AlarmStyle {
    /** 闹钟式：`setAlarmClock`。 */
    ALARM_CLOCK,

    /** 通知式：`setExactAndAllowWhileIdle`。 */
    EXACT,
}
