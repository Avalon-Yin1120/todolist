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
import com.example.todolist.domain.Todo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 通知渠道与提醒通知的落地实现。
 *
 * 所有对系统通知接口的调用都收敛在这里，便于 Robolectric 测试直接断言渠道与通知属性。
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
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.notif_channel_desc)
            enableVibration(true)
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

    /** 发出一次提醒通知。 */
    open fun notifyReminder(todo: Todo) {
        ensureChannel()
        val timeText = formatRange(todo)
        val title = todo.name
        val text = todoText(timeText)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent(todo.id))
            .build()

        // 权限缺失时 NotificationManagerCompat.notify 本身不会抛异常，
        // 但显式短路让调用方与测试都更清楚行为。
        if (!canPostNotifications()) {
            Log.w(TAG, "无法发送提醒：通知权限未开启（todo id=${todo.id}）")
            return
        }
        val notificationId = notificationIdOf(todo.id)
        NotificationManagerCompat.from(context).notify(notificationId, notification)
        // 排查提醒不显示时最有用的一条日志：记录通知 id 与是否真的进入系统。
        Log.i(
            TAG,
            "已发送提醒通知：id=$notificationId todo=${todo.id} name=${todo.name} " +
                "active=${NotificationManagerCompat.from(context).activeNotifications.size}",
        )
    }

    /** 主动取消某条待办的通知（用户删除待办时调用）。 */
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
