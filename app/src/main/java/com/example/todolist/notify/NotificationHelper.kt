package com.example.todolist.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.todolist.MainActivity
import com.example.todolist.R
import com.example.todolist.domain.ReminderMode
import com.example.todolist.domain.Todo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 通知渠道与提醒通知的落地实现。
 *
 * 两种提醒方式都走这里：
 * * [ReminderMode.NOTIFICATION]：普通通知，点击进入应用；
 * * [ReminderMode.ALARM]：高优先级通知 + **全屏 Intent**（系统在锁屏/亮屏时直接弹出
 *   [com.example.todolist.ui.alarm.AlarmActivity]），配合页面内的提示音与常亮，
 *   行为接近系统闹钟。
 *
 * 方法声明为 open 是为了让测试能覆盖「发送」这一步，只验证业务判断。
 */
open class NotificationHelper(private val context: Context) {

    /** 幂等创建渠道；Application 启动时调用一次即可。 */
    open fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notif_channel_name),
            // 闹钟式提醒需要能绕过免打扰并点亮屏幕
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.notif_channel_desc)
            enableVibration(true)
            // 让渠道声音与震动在锁屏上也能触发
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            setBypassDnd(true)
            // 显式指定提示音：只设 IMPORTANCE_HIGH 而不给 sound，
            // 部分 ROM 会建出「静音」渠道，用户反馈过通知没声音
            setSound(
                android.media.RingtoneManager.getDefaultUri(
                    android.media.RingtoneManager.TYPE_NOTIFICATION,
                ),
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
        }
        manager.createNotificationChannel(channel)
    }

    /** 是否可以向用户弹通知（Android 13+ 需要运行时权限）。 */
    open fun canPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** 发出一次提醒。 */
    open fun notifyReminder(todo: Todo) {
        ensureChannel()
        val timeText = formatRange(todo)
        val text = todoText(timeText)

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(todo.name)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent(todo.id))

        if (todo.mode == ReminderMode.ALARM) {
            // 通知本身也带全屏 Intent 作为兜底（锁屏时系统会直接拉起闹钟页），
            // 但真正保证「响铃 + 亮屏 + 弹页」的是 AlarmRingService：
            // 屏幕亮着时系统不会用全屏 Intent 拉起界面，只给一条横幅。
            builder.setFullScreenIntent(fullScreenIntent(todo.id), true)
            builder.setDefaults(NotificationCompat.DEFAULT_ALL)
        }

        val notification = builder.build()

        if (!canPostNotifications()) {
            Log.w(TAG, "无法发送提醒：通知权限未开启（todo id=${todo.id}）")
            return
        }
        val notificationId = notificationIdOf(todo.id)
        NotificationManagerCompat.from(context).notify(notificationId, notification)

        // 闹钟式：额外启动前台服务，确保响铃、亮屏并弹出闹钟页
        if (todo.mode == ReminderMode.ALARM) {
            runCatching { AlarmRingService.start(context, todo.id, todo.name) }
                .onFailure { Log.w(TAG, "启动闹钟服务失败：${it.message}") }
        }
        Log.i(
            TAG,
            "已发送提醒：id=$notificationId todo=${todo.id} mode=${todo.mode} " +
                "name=${todo.name}",
        )
    }

    /** 主动取消某条待办的通知。 */
    open fun cancel(todoId: Long) {
        NotificationManagerCompat.from(context).cancel(notificationIdOf(todoId))
    }

    private fun contentIntent(todoId: Long): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_TODO
            data = android.net.Uri.parse("todolist://todo/$todoId")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            notificationIdOf(todoId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** 全屏 Intent：让系统直接把闹钟页拉到前台（锁屏上也生效）。 */
    private fun fullScreenIntent(todoId: Long): PendingIntent {
        val intent = Intent(context, com.example.todolist.ui.alarm.AlarmActivity::class.java).apply {
            action = com.example.todolist.ui.alarm.AlarmActivity.ACTION_SHOW_ALARM
            data = android.net.Uri.parse("todolist://alarm/$todoId")
            putExtra(com.example.todolist.ui.alarm.AlarmActivity.EXTRA_TODO_ID, todoId)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
        }
        return PendingIntent.getActivity(
            context,
            notificationIdOf(todoId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun todoText(timeRange: String): String =
        context.getString(R.string.notif_content, timeRange)

    private fun formatRange(todo: Todo): String {
        val formatter = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
        return "${formatter.format(Date(todo.startAt))} - ${formatter.format(Date(todo.endAt))}"
    }

    companion object {
        const val CHANNEL_ID = "todo_reminder"
        const val ACTION_OPEN_TODO = "com.example.todolist.action.OPEN_TODO"

        /** 日志标签：按它过滤即可看到提醒发送情况。 */
        const val TAG = "TodoReminder"

        /**
         * 通知 id 需要是 int；取 id 的异或折叠结果，冲突概率可忽略。
         * 单独抽成函数，保证发通知与取消通知用同一个 id。
         */
        fun notificationIdOf(todoId: Long): Int = (todoId xor (todoId ushr 32)).toInt()
    }
}
