package com.example.todolist.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.todolist.R
import com.example.todolist.ui.alarm.AlarmActivity

/**
 * 闹钟式提醒的前台服务。
 *
 * 为什么需要它：只靠通知的全屏 Intent 并不可靠——系统只在「屏幕关闭或锁屏」时才
 * 把全屏 Intent 当成闹钟直接拉起；屏幕亮着时它只是一条横幅通知，于是用户看到的效果
 * 和普通通知没有区别，也不会响、不会亮屏。
 *
 * 这里改为前台服务主动播放闹钟铃声，并直接 startActivity 拉起闹钟页
 * （配合 AlarmActivity 的 setTurnScreenOn / setShowWhenLocked 点亮屏幕）。
 * 前台服务需要一条常驻通知，否则系统会在几秒内杀掉它。
 */
class AlarmRingService : Service() {

    private var ringtone: Ringtone? = null

    /** 短时唤醒锁：保证响铃期间 CPU 不休眠，屏幕能及时亮起。 */
    private var wakeLock: android.os.PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val todoId = intent?.getLongExtra(EXTRA_TODO_ID, -1L) ?: -1L
        val name = intent?.getStringExtra(EXTRA_TODO_NAME).orEmpty()

        startForeground(NOTIFICATION_ID, buildNotification(todoId, name))
        acquireWakeLock()
        isRinging = true
        startRinging()

        // 直接拉起闹钟页：不受「全屏 Intent 只在锁屏生效」的限制
        runCatching {
            startActivity(
                Intent(this, AlarmActivity::class.java).apply {
                    action = AlarmActivity.ACTION_SHOW_ALARM
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS,
                    )
                    putExtra(AlarmActivity.EXTRA_TODO_ID, todoId)
                },
            )
        }.onFailure { Log.w(TAG, "拉起闹钟页失败：${it.message}") }

        // 被系统杀掉后不自动重启：到点没响属于漏提醒，重启反而会立刻又响一次
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        isRinging = false
        stopRinging()
        releaseWakeLock()
        super.onDestroy()
    }

    // ---------------------------------------------------------------- 唤醒锁

    private fun acquireWakeLock() {
        runCatching {
            val manager = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            wakeLock = manager.newWakeLock(
                // 屏幕 + CPU 都保持，闹钟页才能立刻可见；
                // 用超时兜底，避免异常路径下一直占着唤醒锁耗电
                android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                    android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "todolist:alarm",
            ).apply { acquire(WAKE_LOCK_TIMEOUT_MS) }
        }.onFailure { Log.w(TAG, "获取唤醒锁失败：${it.message}") }
    }

    private fun releaseWakeLock() {
        runCatching {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        }
        wakeLock = null
    }

    // ---------------------------------------------------------------- 铃声

    private fun startRinging() {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: return
        ringtone = RingtoneManager.getRingtone(applicationContext, uri)?.apply {
            // USAGE_ALARM 让声音走闹钟音量通道，静音模式下也能响——这正是闹钟该有的行为
            audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                isLooping = true
            }
            runCatching { play() }
                .onFailure { Log.w(TAG, "播放闹钟铃声失败：${it.message}") }
        }
        Log.i(TAG, "闹钟开始响铃")
    }

    private fun stopRinging() {
        runCatching { ringtone?.stop() }
        ringtone = null
    }

    // ---------------------------------------------------------------- 常驻通知

    private fun buildNotification(todoId: Long, name: String): Notification {
        ensureChannel()
        val contentIntent = PendingIntent.getActivity(
            this,
            todoId.toInt(),
            Intent(this, AlarmActivity::class.java).apply {
                action = AlarmActivity.ACTION_SHOW_ALARM
                putExtra(AlarmActivity.EXTRA_TODO_ID, todoId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(name.ifBlank { getString(R.string.notif_channel_name) })
            .setContentText(getString(R.string.alarm_ringing))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .setFullScreenIntent(contentIntent, true)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.alarm_channel_name),
            // 前台服务通知必须能显示，用 LOW 避免它自己再响一次
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.alarm_channel_desc)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        /** 当前是否有闹钟在响（供闹钟页判断是否还需要自己补响）。 */
        @Volatile
        var isRinging: Boolean = false
            private set

        const val TAG = "TodoAlarm"
        const val CHANNEL_ID = "todo_alarm_ringing"
        const val EXTRA_TODO_ID = "extra_todo_id"
        const val EXTRA_TODO_NAME = "extra_todo_name"
        private const val NOTIFICATION_ID = 0x5A1A

        /** 唤醒锁最长持有 10 分钟，足够用户处理提醒。 */
        private const val WAKE_LOCK_TIMEOUT_MS = 10 * 60 * 1000L

        /** 启动闹钟服务。 */
        fun start(context: Context, todoId: Long, name: String) {
            val intent = Intent(context, AlarmRingService::class.java).apply {
                putExtra(EXTRA_TODO_ID, todoId)
                putExtra(EXTRA_TODO_NAME, name)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /** 停止响铃（用户点了「知道了」或「标记完成」）。 */
        fun stop(context: Context) {
            context.stopService(Intent(context, AlarmRingService::class.java))
        }
    }
}
