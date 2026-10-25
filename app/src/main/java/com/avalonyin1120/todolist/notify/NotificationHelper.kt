package com.avalonyin1120.todolist.notify

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
import com.avalonyin1120.todolist.MainActivity
import com.avalonyin1120.todolist.R
import com.avalonyin1120.todolist.domain.ReminderMode
import com.avalonyin1120.todolist.domain.Todo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 通知渠道与提醒通知的落地实现。
 *
 * 两种提醒方式都走这里：
 * * [ReminderMode.NOTIFICATION]：普通通知，点击进入应用；
 * * [ReminderMode.ALARM]：高优先级通知 + **全屏 Intent**（系统在锁屏/亮屏时直接弹出
 *   [com.avalonyin1120.todolist.ui.alarm.AlarmActivity]），配合页面内的提示音与常亮，
 *   行为接近系统闹钟。
 *
 * 方法声明为 open 是为了让测试能覆盖「发送」这一步，只验证业务判断。
 *
 * ## 为什么有两个渠道
 *
 * 通知渠道一旦创建，**声音就改不了了**——用户手机上早已存在旧渠道，代码里再
 * `setSound` 也不会让它出声（这正是「通知没有声音」的根因）。因此这里用两个固定
 * 渠道（有声 / 静音）按设置二选一，而不是去改一个已存在渠道的声音。
 * 同时把更早版本建出来的旧渠道删掉，避免系统通知设置里堆一串同名项。
 */
open class NotificationHelper(
    private val context: Context,
    /** 通知是否出声；由设置页的开关决定，默认有声。 */
    private val soundEnabled: () -> Boolean = { true },
) {

    /** 幂等创建渠道；Application 启动时调用一次即可。 */
    open fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        // 历史渠道一律删掉：渠道的「声音」创建后不可改，留着只会变成迷惑项
        // （它们要么根本没声音，要么是用户手机上那条永远静音的 v2）。
        LEGACY_CHANNEL_IDS.forEach { runCatching { manager.deleteNotificationChannel(it) } }

        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(soundChannel())
        }
        if (manager.getNotificationChannel(CHANNEL_ID_SILENT) == null) {
            manager.createNotificationChannel(silentChannel())
        }
        // 响铃时常驻通知那条渠道也在这里建：用户反馈「系统设置的类别里，闹钟式提醒
        // 只有响过一遍闹钟后才出现」——那是本应用的行为（原来只在响铃服务里建），
        // 于是用户出事之前看不到、也改不了它。放到应用启动时建，类别列表里一开始就有。
        AlarmRingService.ensureAlarmRingChannel(context)
        Log.i(
            TAG,
            "通知渠道就绪：$CHANNEL_ID 声音=${manager.getNotificationChannel(CHANNEL_ID)?.sound}" +
                " 重要性=${manager.getNotificationChannel(CHANNEL_ID)?.importance}",
        )
    }

    /**
     * 有声渠道用的提示音。
     *
     * 优先系统默认提示音；**取不到就退回打包在应用里的音频**。
     *
     * 这条兜底是用户真机反馈换来的：部分 ROM 上 `RingtoneManager.getDefaultUri(TYPE_NOTIFICATION)`
     * 返回 null，于是渠道被建成「没有声音」——而渠道声音**创建后不可更改**，
     * 那条渠道就永远是静音的，用户看到的现象是「到点只弹通知、不响」。
     * 用自带的音频就不依赖系统提示音是否存在了。
     */
    private fun notificationSoundUri(): android.net.Uri =
        android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_NOTIFICATION)
            ?: android.net.Uri.parse("android.resource://${context.packageName}/${R.raw.alarm_beep}")

    /** 有声渠道：显式给提示音，避免部分 ROM 建出「静音」渠道。 */
    private fun soundChannel(): NotificationChannel =
        NotificationChannel(
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
            setSound(
                notificationSoundUri(),
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
        }

    /**
     * 静音渠道。
     *
     * 仍然保持 [NotificationManager.IMPORTANCE_HIGH]：用户关掉的是「声音」，
     * 不是「提醒」本身——横幅、下拉通知、锁屏显示都还要照常。
     * 不给 sound 也不开振动，系统因此不会出声。
     */
    private fun silentChannel(): NotificationChannel =
        NotificationChannel(
            CHANNEL_ID_SILENT,
            context.getString(R.string.notif_channel_name_silent),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.notif_channel_desc_silent)
            enableVibration(false)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            setSound(null, null)
        }

    /** 当前应该用哪个渠道。 */
    open fun channelId(): String = if (soundEnabled()) CHANNEL_ID else CHANNEL_ID_SILENT

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

        val builder = NotificationCompat.Builder(context, channelId())
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(todo.name)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(contentIntent(todo.id))
            // 两个就地操作：真机上后台拉起界面可能被系统拦掉（尤其没有精确闹钟权限时），
            // 这时通知就是唯一的交互面，必须能直接完成或稍后提醒。
            .addAction(
                R.drawable.ic_stat_reminder,
                "完成",
                ReminderActionReceiver.doneIntent(context, todo.id),
            )
            .addAction(
                R.drawable.ic_stat_reminder,
                "10 分钟后再提醒",
                ReminderActionReceiver.snoozeIntent(context, todo.id),
            )

        if (todo.mode == ReminderMode.ALARM) {
            // 通知本身也带全屏 Intent 作为兜底（锁屏时系统会直接拉起闹钟页），
            // 但真正保证「响铃 + 亮屏 + 弹页」的是 AlarmRingService：
            // 屏幕亮着时系统不会用全屏 Intent 拉起界面，只给一条横幅。
            builder.setFullScreenIntent(fullScreenIntent(todo), true)
            builder.setDefaults(NotificationCompat.DEFAULT_ALL)
        }

        val notification = builder.build()

        val notificationId = notificationIdOf(todo.id)
        val channel = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.getSystemService(NotificationManager::class.java)
                ?.getNotificationChannel(channelId())
        } else {
            null
        }
        ReminderLog.add(
            ReminderLog.CAT_NOTIFY,
            "发提醒：${todo.name}｜方式=${todo.mode}｜渠道=${channelId()}" +
                "｜渠道声音=${if (channel?.sound == null) "无" else "有"}" +
                "｜重要性=${channel?.importance ?: "?"}｜通知权限=${canPostNotifications()}",
        )
        if (canPostNotifications()) {
            // 为什么要 try/catch：`canPostNotifications()` 与这次 notify 之间不是原子的。
            // Android 13+ 用户可以在系统设置里「暂停通知」（REVOKE_POST_NOTIFICATIONS_WITH_IMPORT），
            // 那一刻 notify 会抛 SecurityException；不接住的话整条提醒路径直接崩在
            // 「检查时还有权限」的窗口里。lint 的 MissingPermission 检查也正因此报错——
            // 它看不进 canPostNotifications() 内部（那是个 open fun，测试里会被替换）。
            try {
                NotificationManagerCompat.from(context).notify(notificationId, notification)
                Log.i(
                    TAG,
                    "已发送提醒：id=$notificationId todo=${todo.id} mode=${todo.mode} " +
                        "name=${todo.name}",
                )
            } catch (e: SecurityException) {
                Log.w(TAG, "发通知被系统拒绝（权限在检查之后被撤销）：todo=${todo.id}", e)
                ReminderLog.add(
                    ReminderLog.CAT_NOTIFY,
                    "通知没发出去：系统在检查之后拒绝了通知权限（todo=${todo.id}）",
                )
            }
        } else {
            // 不开日志就会「什么都没发生」：把原因明确记下来
            Log.w(TAG, "通知权限未开启，无法发出通知：todo=${todo.id}")
            ReminderLog.add(ReminderLog.CAT_NOTIFY, "通知没发出去：没有通知权限（todo=${todo.id}）")
        }

        // 闹钟式：额外启动前台服务，确保响铃、亮屏并弹出闹钟页。
        // 注意这一步与通知权限无关，必须独立执行——否则没给通知权限就等于完全不提醒。
        if (todo.mode == ReminderMode.ALARM) {
            startRingService(todo)
        }
    }

    /**
     * 启动闹钟响铃服务。
     *
     * Android 12+ 严格限制「后台启动前台服务」：被拒绝时异常原先只是被 runCatching
     * 吞成一条 WARN，用户侧的表现就是「到点了什么都没发生」。
     * 这里把失败原因明确记下来，并尝试直接拉起闹钟页作为兜底——
     * 该页自带 setShowWhenLocked / setTurnScreenOn，被允许时同样能亮屏并响铃。
     */
    private fun startRingService(todo: Todo) {
        try {
            AlarmRingService.start(context, todo.id, todo.name, todo.updatedAt)
            Log.i(TAG, "已请求启动闹钟响铃服务：todo=${todo.id}")
            ReminderLog.add(ReminderLog.CAT_RING, "请求启动响铃服务：todo=${todo.id}")
        } catch (e: Exception) {
            Log.e(
                TAG,
                "启动闹钟响铃服务失败（Android 12+ 可能限制后台启动前台服务）：" +
                    "${e.javaClass.simpleName} ${e.message}",
            )
            ReminderLog.add(
                ReminderLog.CAT_RING,
                "响铃服务启动失败（${e.javaClass.simpleName}）→ 改试直接拉起闹钟页",
            )
            val alarmIntent = Intent(
                context,
                com.avalonyin1120.todolist.ui.alarm.AlarmActivity::class.java,
            ).apply {
                action = com.avalonyin1120.todolist.ui.alarm.AlarmActivity.ACTION_SHOW_ALARM
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS,
                )
                putExtra(com.avalonyin1120.todolist.ui.alarm.AlarmActivity.EXTRA_TODO_ID, todo.id)
                putExtra(com.avalonyin1120.todolist.ui.alarm.AlarmActivity.EXTRA_ALARM_ID, todo.updatedAt)
            }
            runCatching { context.startActivity(alarmIntent) }
                .onSuccess { Log.i(TAG, "已改为直接拉起闹钟页：todo=${todo.id}") }
                .onFailure {
                    Log.e(TAG, "直接拉起闹钟页也失败：${it.javaClass.simpleName} ${it.message}")
                }
        }
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
    private fun fullScreenIntent(todo: Todo): PendingIntent {
        val todoId = todo.id
        val intent = Intent(context, com.avalonyin1120.todolist.ui.alarm.AlarmActivity::class.java).apply {
            action = com.avalonyin1120.todolist.ui.alarm.AlarmActivity.ACTION_SHOW_ALARM
            data = android.net.Uri.parse("todolist://alarm/$todoId")
            putExtra(com.avalonyin1120.todolist.ui.alarm.AlarmActivity.EXTRA_TODO_ID, todoId)
            // 闹钟身份（updatedAt）：让「迟到投递」与「编辑后的新闹钟」可区分，见 AlarmRinger
            putExtra(com.avalonyin1120.todolist.ui.alarm.AlarmActivity.EXTRA_ALARM_ID, todo.updatedAt)
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
        /**
         * 有声提醒渠道。
         *
         * 注意 id 里的 `v3`：这个后缀是**被迫**加上的，因为通知渠道的声音**创建后不可更改**。
         * 历史上有两代渠道都因此废掉：
         * * `todo_reminder`（最早）：建的时候就没给声音，永远是静音；
         * * `todo_reminder_v2`：虽然显式给了声音，但真机反馈「到点只弹通知、不响」——
         *   查下去是部分 ROM 上 `RingtoneManager.getDefaultUri(TYPE_NOTIFICATION)` 返回 null，
         *   渠道被建成了「无声音」；而且它一旦这么建出来，应用再也改不动。
         *
         * v3 用 [notificationSoundUri]：系统提示音取不到就退回自带音频，因此不会再建出无声渠道。
         * 旧的两代渠道在 [ensureChannel] 里一并删除，免得系统设置里堆一串同名项。
         */
        const val CHANNEL_ID = "todo_reminder_v3"

        /** 静音提醒渠道：用户关掉「通知提示音」后走这条。 */
        const val CHANNEL_ID_SILENT = "todo_reminder_silent_v3"

        /** 历史版本留下的残留渠道，启动时清掉（声音改不了，只能弃用换新 id）。 */
        val LEGACY_CHANNEL_IDS = listOf("todo_reminder", "todo_reminder_v2", "todo_reminder_silent_v2")

        const val ACTION_OPEN_TODO = "com.avalonyin1120.todolist.action.OPEN_TODO"

        /** 日志标签：按它过滤即可看到提醒发送情况。 */
        const val TAG = "TodoReminder"

        /**
         * 通知 id 需要是 int；取 id 的异或折叠结果，冲突概率可忽略。
         * 单独抽成函数，保证发通知与取消通知用同一个 id。
         */
        fun notificationIdOf(todoId: Long): Int = (todoId xor (todoId ushr 32)).toInt()
    }
}
