package com.avalonyin1120.todolist.notify

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
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.avalonyin1120.todolist.R
import com.avalonyin1120.todolist.ui.alarm.AlarmActivity

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
 *
 * ## 响铃与「闹钟页」严格同生命周期
 *
 * 用户反馈过「有时关闭界面没了，闹钟还在继续响」。根因有两个，这里都堵住了：
 * 1. 闹钟页只 stop 了它自己那条铃声，服务侧的铃声没人管——服务是被 startService 起来的，
 *    页面销毁与它毫无关系。现在页面会**绑定**本服务：最后一个客户端解绑（页面销毁）时
 *    立即停铃并自杀。
 * 2. `onStartCommand` 可能被投递多次（精确闹钟通道与 +3 分钟保险通道先后触发）。
 *    早先的实现每次都 new 一个 [Ringtone] 覆盖字段，前一个就再也没人 stop，
 *    于是「界面关掉了，铃声还在响」。[startRinging] 现在无条件先停掉上一条。
 *
 * 另外保留一条保险：无论发生什么，响铃最长 [MAX_RING_MS] 后自动停止，避免异常路径下
 * 一直响个不停。
 */
class AlarmRingService : Service() {

    private var ringtone: Ringtone? = null

    /** 短时唤醒锁：保证响铃期间 CPU 不休眠，屏幕能及时亮起。 */
    private var wakeLock: android.os.PowerManager.WakeLock? = null

    /** 当前正在响铃的待办；用于识别「同一条待办被重复投递」，避免重复起铃。 */
    private var ringingTodoId = -1L

    /** 主线程 Handler：用于「解绑宽限期」与「响铃超时」两个延时任务。 */
    private val handler = Handler(Looper.getMainLooper())

    /** 当前是否有界面（闹钟页）绑在本服务上。 */
    private var clientBound = false

    private val binder = LocalBinder()

    /**
     * 最后一个客户端解绑后，等一小段时间再停铃。
     *
     * 为什么需要宽限期：屏幕旋转等**配置变更**也会走「销毁 + 重建」这条路，
     * 如果解绑就立刻停铃，转一下屏幕闹钟就不响了。宽限期内新页面重新绑定即取消停止。
     */
    private val stopWhenNoClient = Runnable {
        if (!clientBound) {
            Log.i(TAG, "闹钟页已关闭，停止响铃并退出服务")
            stopRinging()
            stopSelf()
        }
    }

    /** 响铃超时兜底。 */
    private val autoStop = Runnable {
        Log.w(TAG, "响铃超过 ${MAX_RING_MS / 60_000} 分钟仍未处理，自动停止")
        stopRinging()
        stopSelf()
    }

    /** 闹钟页通过它拿到「立刻静音」的能力，不必等服务走完销毁流程。 */
    inner class LocalBinder : Binder() {
        fun stopRingingNow() = stopRinging()

        fun todoId(): Long = ringingTodoId
    }

    override fun onCreate() {
        super.onCreate()
        live = this
    }

    override fun onBind(intent: Intent?): IBinder {
        // 有界面接管了：取消待执行的「无客户端就停铃」
        clientBound = true
        handler.removeCallbacks(stopWhenNoClient)
        return binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        clientBound = false
        handler.postDelayed(stopWhenNoClient, UNBIND_GRACE_MS)
        // 返回 false：下次绑定仍然会调用 onBind，服务可以被重新接管
        return super.onUnbind(intent)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val todoId = intent?.getLongExtra(EXTRA_TODO_ID, -1L) ?: -1L
        val name = intent?.getStringExtra(EXTRA_TODO_NAME).orEmpty()

        startForeground(NOTIFICATION_ID, buildNotification(todoId, name))
        acquireWakeLock()

        // 同一条待办的重复投递不再重新起铃（既不打断也不叠加），换了一条才换铃声
        if (ringingTodoId != todoId || ringtone == null) {
            ringingTodoId = todoId
            startRinging()
        }
        // 每次投递都把超时重新计一遍
        handler.removeCallbacks(autoStop)
        handler.postDelayed(autoStop, MAX_RING_MS)

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
        handler.removeCallbacks(stopWhenNoClient)
        handler.removeCallbacks(autoStop)
        stopRinging()
        releaseWakeLock()
        live = null
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
        // 无条件先停上一条：任何情况下都不能出现「没人持有的铃声」在后台一直响
        stopRinging()

        // 进入「本次闹钟正在响」的状态。
        //
        // 这里与「有没有真的拿到铃声对象」无关：设备若连一个可用提示音都没有，
        // 闹钟页那套兜底铃声用的是同一条 URI 链，同样拿不到，因此没有必要
        // 让状态退回 false 去换一次注定无声的重试。
        isRinging = true

        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        if (uri == null) {
            Log.w(TAG, "系统没有可用的提示音，本次只靠闹钟页与通知")
            return
        }
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
        Log.i(TAG, "闹钟开始响铃：todo=$ringingTodoId")
    }

    private fun stopRinging() {
        val had = ringtone != null
        runCatching { ringtone?.stop() }
        ringtone = null
        isRinging = false
        if (had) Log.i(TAG, "闹钟停止响铃：todo=$ringingTodoId")
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
        /**
         * 当前是否有闹钟在响（供闹钟页判断是否还需要自己补响）。
         *
         * setter 对同模块可见：生产代码只在服务内部写它，单测需要在用例之间把它复位
         * （它是静态状态，不复位会互相污染）。
         */
        @Volatile
        var isRinging: Boolean = false
            internal set

        /**
         * 正在运行的实例。
         *
         * 通知里的「完成 / 稍后提醒」跑在广播接收器里，拿不到闹钟页的绑定句柄；
         * 而 `stopService` 对**已被闹钟页绑定**的服务不会立刻停铃（要等解绑才销毁），
         * 于是会出现「通知里已经点了完成，铃声还在响」。这里留一个同进程引用，
         * 让接收器可以直接把铃声掐掉。onDestroy 里清空，不会泄漏。
         */
        @Volatile
        private var live: AlarmRingService? = null

        /** 立刻静音（不销毁服务）。 */
        fun muteNow() {
            live?.stopRinging()
        }

        const val TAG = "TodoAlarm"
        const val CHANNEL_ID = "todo_alarm_ringing"
        const val EXTRA_TODO_ID = "extra_todo_id"
        const val EXTRA_TODO_NAME = "extra_todo_name"
        private const val NOTIFICATION_ID = 0x5A1A

        /** 唤醒锁最长持有 10 分钟，足够用户处理提醒。 */
        private const val WAKE_LOCK_TIMEOUT_MS = 10 * 60 * 1000L

        /** 响铃最长时长：超过就自动停，避免异常路径下一直响。 */
        private const val MAX_RING_MS = 5 * 60 * 1000L

        /** 解绑后的宽限期：给配置变更（旋转）留出重新绑定的时间。 */
        private const val UNBIND_GRACE_MS = 600L

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

        /** 停止响铃（用户点了「知道了」「标记完成」，或通知里的「完成」）。 */
        fun stop(context: Context) {
            context.stopService(Intent(context, AlarmRingService::class.java))
        }
    }
}
