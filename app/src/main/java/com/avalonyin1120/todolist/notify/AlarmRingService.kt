package com.avalonyin1120.todolist.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
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
 * ## 铃声归 [AlarmRinger] 管，本服务只管「活着」
 *
 * 用户反馈过多次「闹钟有时关不掉」。教训是：只要铃声有**两个可能的持有者**
 * （服务一个、闹钟页一个），停止逻辑就必须先判断「现在是谁在响」，漏掉任何一个分支
 * 都会留下一条永远停不下来的路径。现在铃声只在 [AlarmRinger] 里，本服务与闹钟页
 * 都只是「启动者」；停止入口统一走 [stopEverything]，不再有分支。
 *
 * 本服务自己保留两件事：
 * 1. 与闹钟页**绑定**——页面销毁即解绑，服务随之退出（返回键、上滑划掉、被系统回收
 *    这三条路径都不会留下残留的服务）；
 * 2. 响应超时兜底：响铃最长 [AlarmRinger.MAX_RING_MS] 后自己 `stopSelf`，
 *    并把常驻通知一并撤掉。
 */
class AlarmRingService : Service() {

    /** 短时唤醒锁：保证响铃期间 CPU 不休眠，屏幕能及时亮起。 */
    private var wakeLock: android.os.PowerManager.WakeLock? = null

    /** 主线程 Handler：用于「解绑宽限期」与「响铃超时」两个延时任务。 */
    private val handler = Handler(Looper.getMainLooper())

    /** 当前是否有界面（闹钟页）绑在本服务上。 */
    private var clientBound = false

    /** 页面拿到的绑定句柄；本身不再承载「静音」能力（那是 [AlarmRinger] 的职责）。 */
    private val binder = Binder()

    /**
     * 最后一个客户端解绑后，等一小段时间再停铃。
     *
     * 为什么需要宽限期：屏幕旋转等**配置变更**也会走「销毁 + 重建」这条路，
     * 如果解绑就立刻停铃，转一下屏幕闹钟就不响了。宽限期内新页面重新绑定即取消停止。
     */
    private val stopWhenNoClient = Runnable {
        if (!clientBound) {
            Log.i(TAG, "闹钟页已关闭，停止响铃并退出服务")
            AlarmRinger.stop()
            stopSelf()
        }
    }

    /** 响铃超时兜底：只负责服务自身退出（声音由 [AlarmRinger] 自己的超时保证）。 */
    private val autoStop = Runnable {
        Log.w(TAG, "响铃超过 ${AlarmRinger.MAX_RING_MS / 60_000} 分钟仍未处理，退出响铃服务")
        AlarmRinger.stop()
        stopSelf()
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
        val todoId = intent?.getLongExtra(EXTRA_TODO_ID, AlarmRinger.NO_TODO) ?: AlarmRinger.NO_TODO
        val name = intent?.getStringExtra(EXTRA_TODO_NAME).orEmpty()

        // 迟到的启动指令：这条待办的闹钟刚刚才被用户结束（关页面 / 点完成 / 点停止），
        // 但 Android 12+ 对「后台启动前台服务」的限制让这次 onStartCommand 现在才到。
        // 真机实测这种情况能晚十几秒——如果不挡掉，用户会遇到「明明关了，过一会儿又响」。
        if (AlarmRinger.wasRecentlyStopped(todoId)) {
            Log.i(TAG, "这条待办的闹钟刚刚已被结束，忽略迟到的启动：todo=$todoId")
            // startForegroundService 起的服务必须转到前台，否则系统会判「未及时转前台」；
            // 转完立刻退出即可。
            startForeground(NOTIFICATION_ID, buildNotification(todoId, name))
            stopSelf(startId)
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification(todoId, name))
        acquireWakeLock()

        // 开始响铃。同一条待办的重复投递由 AlarmRinger 自己识别，不会打断也不会叠加。
        AlarmRinger.start(this, todoId)

        // 每次投递都把超时重新计一遍
        handler.removeCallbacks(autoStop)
        handler.postDelayed(autoStop, AlarmRinger.MAX_RING_MS)

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
        AlarmRinger.stop()
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
        // 这条通知原先只有「点一下打开闹钟页」这一个出口。真机上闹钟页有可能拉不起来
        // （后台启动界面被厂商限制），于是用户只能听着铃声响最多 5 分钟——
        // 这就是「有时关不掉」里最难受的一种。现在通知自带「停止」按钮。
        val stopIntent = ReminderActionReceiver.stopAlarmIntent(this, todoId)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(name.ifBlank { getString(R.string.notif_channel_name) })
            .setContentText(getString(R.string.alarm_ringing))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .setDeleteIntent(stopIntent)
            .setFullScreenIntent(contentIntent, true)
            .addAction(
                R.drawable.ic_stat_reminder,
                getString(R.string.alarm_action_stop),
                stopIntent,
            )
            .addAction(
                R.drawable.ic_stat_reminder,
                getString(R.string.alarm_action_done),
                ReminderActionReceiver.doneIntent(this, todoId),
            )
            .build()
    }

    private fun ensureChannel() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return
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
         * 当前是否有闹钟在响。
         *
         * 只是 [AlarmRinger.isRinging] 的别名：状态由铃声持有者持有，这里不另存一份，
         * 避免出现两个真值来源。生产代码只通过 [stopEverything] 等动作改变它，
         * 读它的是单测（断言「该停的时候真的停了」）。
         */
        val isRinging: Boolean get() = AlarmRinger.isRinging

        const val TAG = "TodoAlarm"
        const val CHANNEL_ID = "todo_alarm_ringing"
        const val EXTRA_TODO_ID = "extra_todo_id"
        const val EXTRA_TODO_NAME = "extra_todo_name"
        private const val NOTIFICATION_ID = 0x5A1A

        /** 唤醒锁最长持有 10 分钟，足够用户处理提醒。 */
        private const val WAKE_LOCK_TIMEOUT_MS = 10 * 60 * 1000L

        /** 解绑后的宽限期：给配置变更（旋转）留出重新绑定的时间。 */
        private const val UNBIND_GRACE_MS = 600L

        /** 启动闹钟服务。 */
        fun start(context: Context, todoId: Long, name: String) {
            val intent = Intent(context, AlarmRingService::class.java).apply {
                putExtra(EXTRA_TODO_ID, todoId)
                putExtra(EXTRA_TODO_NAME, name)
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * 结束本次闹钟：静音 + 关掉闹钟页 + 撤掉常驻通知 + 退出服务。
         *
         * 「完成 / 稍后提醒 / 停止 / 关页面」四个入口都走这里。以前它们各写一半，
         * 于是出现「通知里点了完成，页面上还在响」。
         *
         * @param todoId 闹钟页或通知所属的待办；传 [AlarmRinger.NO_TODO] 表示「不管是谁在响，
         *   一律停掉」（响铃通知上的「停止」走这条）。若传了有效 id 而正在响的却是**另一条**
         *   待办，则只撤本待办的通知，不碰对方的铃声、通知与服务。
         */
        fun stopEverything(context: Context, todoId: Long = AlarmRinger.NO_TODO) {
            val manager = NotificationManagerCompat.from(context)
            // 本条待办自己的提醒通知总是要撤的，与响铃状态无关
            if (todoId > 0L) manager.cancel(NotificationHelper.notificationIdOf(todoId))

            if (AlarmRinger.isRingingForOtherThan(todoId)) {
                Log.i(TAG, "正在响的是另一条待办（todo=${AlarmRinger.ringingTodoId}），只撤本待办的通知")
                return
            }

            AlarmRinger.stop()
            AlarmActivity.finishLive()
            manager.cancel(NOTIFICATION_ID)
            // 若闹钟页仍绑着服务，stopService 不会立刻销毁它；上面的静音与关页面已经把
            // 用户可见的部分收干净了，剩下的销毁由解绑完成。
            runCatching { context.stopService(Intent(context, AlarmRingService::class.java)) }
        }

        /** 立即静音（不销毁服务、不关页面）。 */
        fun muteNow() {
            AlarmRinger.stop()
        }

        /** 停止响铃（用户点了「知道了」「标记完成」，或通知里的「完成」）。 */
        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, AlarmRingService::class.java)) }
        }
    }
}
