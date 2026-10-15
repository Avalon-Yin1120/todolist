package com.avalonyin1120.todolist.notify

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.avalonyin1120.todolist.R

/**
 * 闹钟铃声的**唯一**持有者。
 *
 * ## 为什么必须只有一个持有者
 *
 * 用户反复反馈「闹钟有时关不掉」。根因不是某一条停止路径写错了，而是**铃声有两个可能的持有者**：
 * 前台服务 [AlarmRingService] 一个、闹钟页 `AlarmActivity` 一个（页面在服务没起来时自己补响）。
 * 于是「停止」这件事变成了「必须先判断现在是谁在响」——只要漏掉一个分支，就有一条路径永远停不下来：
 *
 * | 停止动作 | 旧实现 | 后果 |
 * |---|---|---|
 * | 关掉闹钟页 | 页面停自己的 + 解绑服务（600ms 宽限） | 基本可用 |
 * | 通知里的「完成 / 稍后」 | 只停服务的那条（`muteNow` + `stopService`） | **页面自己响的那条没人停，界面留着就一直在响** |
 * | 页面被划到后台（上滑回桌面） | 页面没销毁 → 什么都不停 | **看不到界面，却一直响，只能等 5 分钟兜底** |
 * | 服务被系统限制没能启动 | `muteNow` 里 `live == null`，`stopService` 无事可做 | 同上 |
 * | 闹钟页拉不起来（后台启动界面被拦） | 响铃通知没有停止按钮且不可划掉 | 只能听着响满 5 分钟 |
 *
 * 现在铃声只在这里，且**任何**停止入口都调 [stop]：吵不吵、谁负责停，都不再需要分支判断。
 * 另外这里自带超时兜底，因此「一直响」在物理上不可能超过 [MAX_RING_MS]——
 * 页面侧原先没有任何超时，这是「关不掉能响到天荒地老」的最后一块拼图。
 *
 * 状态放在 object（同进程单例）里还有一个好处：广播接收器（通知里的操作按钮）
 * 不必持有服务的绑定句柄也能立刻静音。进程被杀时铃声随进程消失，不存在泄漏。
 */
object AlarmRinger {

    /**
     * 当前是否有闹钟在响。
     *
     * 与「有没有真的拿到 [Ringtone] 对象」无关：设备若连一个可用提示音都没有，
     * 闹钟页那套兜底逻辑用的是同一条 URI 链，同样拿不到，因此没有必要让状态退回
     * false 去换一次注定无声的重试。
     */
    @Volatile
    var isRinging: Boolean = false
        private set

    /** 正在响铃的待办 id；-1 表示没有在响。用于识别「同一条待办的重复投递」。 */
    @Volatile
    var ringingTodoId: Long = NO_TODO
        private set

    /** 本次闹钟的「身份」（就是待办的 `updatedAt`）。 */
    @Volatile
    private var ringingAlarmId: Long = NO_TODO

    /** 最近一次被结束的待办 id，与 [recentlyStoppedAlarmId]、[recentlyStoppedAt] 一起挡掉迟到的启动指令。 */
    @Volatile
    private var recentlyStoppedTodoId: Long = NO_TODO

    /** 最近一次被结束的「闹钟身份」；编辑过待办之后它会变，于是不会被抑制窗口误吞。 */
    @Volatile
    private var recentlyStoppedAlarmId: Long = NO_TODO

    @Volatile
    private var recentlyStoppedAt: Long = 0L

    private var ringtone: Ringtone? = null

    /** 内置音频的播放器（优先用它，见 [startPlayer]）。 */
    private var player: MediaPlayer? = null

    private val handler = Handler(Looper.getMainLooper())

    /**
     * 绝对兜底：无论停止路径出了什么岔子，响铃最长 [MAX_RING_MS]。
     *
     * 放在这里而不是只放在服务里，是因为「页面自己补响」这条路径上原先**没有任何超时**。
     */
    private val autoStop = Runnable {
        Log.w(TAG, "响铃超过 ${MAX_RING_MS / 60_000} 分钟仍未处理，自动停止")
        stop()
    }

    /**
     * 开始响铃（内部会先停掉上一条，任何情况下都不会出现两条无人持有的铃声）。
     *
     * @param todoId 本次闹钟对应的待办；同一条待办被重复投递时**不打断**当前铃声，
     *   避免「精确通道 + 3 分钟保险通道」先后触发导致响一声又从头开始。
     */
    fun start(context: Context, todoId: Long, alarmId: Long = NO_TODO) {
        // 迟到的启动：这条待办的闹钟刚刚才被用户结束（详见 [wasRecentlyStopped]）
        if (wasRecentlyStopped(todoId, alarmId)) {
            Log.i(TAG, "这条待办的闹钟刚刚已被结束，不再起铃：todo=$todoId")
            ReminderLog.add(ReminderLog.CAT_RING, "不再起铃：这次闹钟刚被用户关掉（todo=$todoId）")
            return
        }
        if (isRinging && ringingTodoId == todoId) {
            Log.i(TAG, "同一条待办的重复投递，继续沿用当前铃声：todo=$todoId")
            return
        }
        // 无条件先停上一条：任何情况下都不能出现「没人持有的铃声」在后台一直响
        stop()

        ringingTodoId = todoId
        ringingAlarmId = alarmId
        isRinging = true
        handler.removeCallbacks(autoStop)
        handler.postDelayed(autoStop, MAX_RING_MS)

        startPlayer(context)
    }

    /**
     * 真正发声。
     *
     * ## 为什么用自己的音频文件，而不是系统闹钟铃声
     *
     * 用户真机反馈「到点只弹通知、不响铃」。查下去发现两条依赖系统铃声的路都靠不住：
     * 通知渠道的 `sound` 在部分 ROM 上创建后读回来就是 null（渠道建好又改不了），
     * `RingtoneManager.getDefaultUri(TYPE_ALARM)` 也可能取到 null 或取到本应用没权限播放的音源。
     * 所以这里**优先播放打包在应用里的音频**（`res/raw/alarm_beep.wav`），完全不依赖系统提示音；
     * 只有连它都放不出来时才退回系统闹钟铃声，并把这个降级**明确记进日志**（真机上就靠日志定性）。
     *
     * 播放器用 [MediaPlayer] 而不是 [Ringtone]：能拿到 `isPlaying` 与错误回调，
     * 「到底有没有出声」才可诊断——这正是「闹钟不响」最需要的那条证据。
     */
    private fun startPlayer(context: Context) {
        val app = context.applicationContext
        val attrs = AudioAttributes.Builder()
            // USAGE_ALARM 让声音走闹钟音量通道，静音模式下也能响——这正是闹钟该有的行为
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val bundled = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(attrs)
                setDataSource(app, Uri.parse("android.resource://${app.packageName}/${R.raw.alarm_beep}"))
                isLooping = true
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "内置闹钟音频播放出错：what=$what extra=$extra")
                    true
                }
                prepare()
                start()
            }
        }.onFailure {
            Log.e(TAG, "内置闹钟音频播放失败（${it.javaClass.simpleName}: ${it.message}），改用系统闹钟铃声")
        }.getOrNull()

        if (bundled != null && bundled.isPlaying) {
            player = bundled
            Log.i(TAG, "闹钟开始响铃（内置音频）：todo=$ringingTodoId")
            ReminderLog.add(ReminderLog.CAT_RING, "用内置音频开始响铃（todo=$ringingTodoId）")
            return
        }
        runCatching { bundled?.release() }

        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        if (uri == null) {
            Log.e(TAG, "内置音频与系统提示音都不可用，本次只靠闹钟页与通知：todo=$ringingTodoId")
            ReminderLog.add(ReminderLog.CAT_RING, "⚠ 内置音频与系统提示音都不可用，本次没有声音")
            return
        }
        ringtone = RingtoneManager.getRingtone(app, uri)?.apply {
            audioAttributes = attrs
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                isLooping = true
            }
            runCatching { play() }
                .onFailure { Log.e(TAG, "播放系统闹钟铃声失败：${it.message}") }
        }
        Log.i(TAG, "闹钟开始响铃（系统铃声兜底）：todo=$ringingTodoId")
        ReminderLog.add(ReminderLog.CAT_RING, "内置音频不可用，退回系统闹钟铃声（todo=$ringingTodoId）")
    }

    /** 立即静音。可重复调用；没有在响时是空操作。 */
    fun stop() {
        handler.removeCallbacks(autoStop)
        val had = ringtone != null || player != null
        runCatching { ringtone?.stop() }
        runCatching { player?.stop() }
        runCatching { player?.release() }
        ringtone = null
        player = null
        val wasRinging = isRinging
        val todo = ringingTodoId
        val alarm = ringingAlarmId
        isRinging = false
        ringingTodoId = NO_TODO
        ringingAlarmId = NO_TODO
        if (todo != NO_TODO || alarm != NO_TODO) {
            // 记下「这条待办的这一次闹钟刚刚被用户结束了」，用来挡掉迟到的启动指令。
            // 记「本次闹钟身份」而不是只记待办：用户编辑过提醒时间之后，
            // 下一次响铃是**新的一次**，不能被这段抑制窗口吞掉（用户真机上遇到的「编辑后不响」）。
            recentlyStoppedTodoId = todo
            recentlyStoppedAlarmId = alarm
            recentlyStoppedAt = SystemClock.elapsedRealtime()
        }
        if (had || wasRinging) {
            Log.i(TAG, "闹钟停止响铃：todo=$todo")
            ReminderLog.add(ReminderLog.CAT_STOP, "停止响铃（todo=$todo）")
        }
    }

    /**
     * 这次启动是不是「刚刚被结束的那一次闹钟」。
     *
     * ## 为什么需要它（实测踩到的坑）
     *
     * `AlarmRingService` 是靠 `startForegroundService` 启动的，而 Android 12+ 会限制
     * 「后台启动前台服务」：真机实测出现过**服务晚了十几秒才被投递**的情况
     * （屏幕亮起、闹钟页都显示完了，`onStartCommand` 才到）。于是会有这样一条时序：
     * 用户已经关掉闹钟页 → 迟到的那次 `onStartCommand` 到达 → 铃声**又响起来**，
     * 而这时已经没有页面了。用户看到的就是「明明关了，过一会儿又响了」。
     *
     * ## 为什么必须连「闹钟身份」一起比（用户真机上遇到的反例）
     *
     * 一开始只比待办 id，结果出现另一种 bug：用户关掉闹钟 → 去编辑这条待办的提醒时间
     * → 到点**只弹通知、不响铃**——因为那次响铃落在抑制窗口里，被当成迟到的重复投递吞掉了。
     * 所以现在比的是 `(todoId, alarmId)`，而 `alarmId` 取待办的 `updatedAt`：
     * 只要用户编辑过，它就是新的，抑制不成立；没有编辑的迟到投递则照样被挡掉。
     */
    fun wasRecentlyStopped(todoId: Long, alarmId: Long = NO_TODO): Boolean {
        if (todoId == NO_TODO || todoId != recentlyStoppedTodoId) return false
        if (alarmId != recentlyStoppedAlarmId) return false
        // 用单调时钟算间隔，并显式排除「负数」：elapsedRealtime 在真机上只增不减，
        // 出现负值只可能来自时钟被重置（测试沙箱就是这种情况）。那种情况下按「已过期」处理，
        // 否则一条来自「未来」的旧记录会让这条待办永远不响。
        val elapsed = SystemClock.elapsedRealtime() - recentlyStoppedAt
        return elapsed in 0 until SUPPRESS_AFTER_STOP_MS
    }

    /**
     * 当前响铃是否属于别的待办（不是 [todoId]）。
     *
     * 判据只有一处用户：[AlarmRingService.stopEverything] 用它决定「这条通知的
     * 完成/停止要不要连带收拾铃声」——用户可能在**另一条**待办的闹钟正响时，
     * 回头去点旧通知上的按钮，那种情况下不该把新闹钟一起掐掉。
     *
     * 闹钟页不参与这个判断：页面显示哪条就保证响的是哪条（见 `AlarmActivity.onCreate`
     * 里无条件的 [start]），所以它退出时一律停掉。
     */
    fun isRingingForOtherThan(todoId: Long): Boolean =
        todoId != NO_TODO && isRinging && ringingTodoId != todoId && ringingTodoId != NO_TODO

    /**
     * 把状态恢复到「从未响过」（**仅供单测**）。
     *
     * 状态是 object 里的静态字段，用例之间会互相污染；单测需要显式的复位入口，
     * 而不是靠推进虚拟时钟去「等到窗口过期」——那会让用例依赖 Robolectric 的时钟语义。
     */
    internal fun resetForTest() {
        stop()
        recentlyStoppedTodoId = NO_TODO
        recentlyStoppedAlarmId = NO_TODO
        recentlyStoppedAt = 0L
    }

    const val TAG = "TodoAlarm"

    /** 响铃最长时长：超过就自动停，避免异常路径下一直响。 */
    const val MAX_RING_MS = 5 * 60 * 1000L

    /**
     * 「刚刚被结束」的抑制窗口。
     *
     * 60 秒足够覆盖实测到的那次十几秒的迟到投递，又远小于最短的真实重复提醒间隔
     * （通知里的「稍后提醒」是 10 分钟），因此不会吞掉下一次正常提醒。
     */
    const val SUPPRESS_AFTER_STOP_MS = 60_000L

    /** 「没有待办」的哨兵值。 */
    const val NO_TODO = -1L
}
