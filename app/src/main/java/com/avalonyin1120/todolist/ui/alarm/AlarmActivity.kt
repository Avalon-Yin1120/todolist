package com.avalonyin1120.todolist.ui.alarm

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.avalonyin1120.todolist.TodoApp
import com.avalonyin1120.todolist.graphOrNull
import com.avalonyin1120.todolist.notify.AlarmRinger
import com.avalonyin1120.todolist.notify.AlarmRingService
import com.avalonyin1120.todolist.ui.theme.TodolistTheme
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 闹钟式提醒页。
 *
 * 由通知的全屏 Intent 拉起，因此能越过锁屏直接显示（`setShowWhenLocked` +
 * `setTurnScreenOn`，与系统闹钟一致）。页面内：
 * * 显示待办名称与时间；
 * * 循环播放提醒音（用系统闹钟铃声，用户可在系统设置里改）；
 * * 保持屏幕常亮；
 * * 提供「知道了 / 稍后提醒 / 标记完成」三个动作。
 *
 * ## 「关掉界面就静音」到底覆盖了哪些动作
 *
 * 用户反馈过「界面没了，闹钟还在响」，也反馈过「有时关不掉」。页面能想到的关闭方式
 * 至少有五种，每一种都必须走到同一个出口（[AlarmRinger] 静音 + 退出服务 + 撤通知）：
 *
 * | 用户动作 | 回调 | 处理 |
 * |---|---|---|
 * | 点「知道了 / 稍后 / 完成」 | [dismissAlarm] | 立即静音并关闭 |
 * | 返回键 | `onDestroy` | [endAlarm] |
 * | 上滑回桌面 / 切到别的应用 | `onUserLeaveHint` | [endAlarm] |
 * | 从最近任务里划掉 | `onDestroy` | [endAlarm] |
 * | 通知里点「完成 / 稍后 / 停止」 | 广播接收器 | [AlarmRingService.stopEverything] |
 *
 * `onStop` 上还有一道**延时看门狗**：页面停止可见超过 [LEAVE_GRACE_MS] 就结束本次闹钟。
 * 屏幕亮着而闹钟页不可见，只可能是用户已经在用别的应用——真有 ROM 吞掉了
 * `onUserLeaveHint`，也兜得住。
 *
 * ## 「用户收到了」的四种表达
 *
 * 除了页面上的三个按钮，用户还可能用硬件键表态。用户的反馈很明确：**按锁屏键或音量键
 * 都算已收到，铃声应当停**。
 *
 * * **音量键**：会被投递到 Activity（闹钟页上音量键调的本来就是闹钟音量），
 *   在 [onKeyDown] 里直接结束，并且吞掉事件——不要变成「只是把音量调小、铃还在响」。
 * * **电源键（锁屏键）**：系统不会把这个键交给应用，只能观察结果——页面 `onStop` 之后
 *   发现屏幕已经熄灭，就按「用户按了锁屏键」处理。
 * * 早期版本刻意相反（熄屏后继续响，模仿系统闹钟），结果被用户当成「关不掉」，
 *   这一版按用户的要求改掉。
 */
class AlarmActivity : ComponentActivity() {

    /** 当前展示的待办；`onNewIntent` 时会被改写（新闹钟复用同一个实例）。 */
    private var todoId by mutableLongStateOf(AlarmRinger.NO_TODO)

    /** 页面是否可见。看门狗靠它判断「是否真的离开了」。 */
    private var visible = false

    /**
     * 屏幕熄灭（按锁屏键）的监听。
     *
     * 电源键不会被投递给应用，但 `ACTION_SCREEN_OFF` 可以：它只是**不允许在清单里静态注册**，
     * 运行时注册照旧能收到。这是「按锁屏键 = 我收到了」唯一可靠的抓手。
     *
     * 为什么还要配合服务侧的唤醒锁收短（见 `AlarmRingService.WAKE_LOCK_TIMEOUT_MS`）：
     * 只要那段 `SCREEN_BRIGHT_WAKE_LOCK` 还握着，按电源键**屏幕根本不会灭**，
     * 这个广播也就永远不会来——两个改动是一件事的两半。
     */
    private val screenOffReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != Intent.ACTION_SCREEN_OFF) return
            Log.i(TAG, "屏幕已熄灭（锁屏键），结束本次闹钟")
            endAlarm()
        }
    }
    private var screenOffRegistered = false

    private var serviceBound = false

    private val handler = Handler(Looper.getMainLooper())

    /** 见类注释：`onStop` 后「看不见页面」的延时看门狗。 */
    private val leaveWatchdog = Runnable {
        if (visible || isChangingConfigurations || isFinishing) return@Runnable
        Log.i(TAG, "闹钟页已不可见，结束本次闹钟")
        endAlarm()
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) = Unit
        override fun onServiceDisconnected(name: ComponentName?) = Unit
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 锁屏上直接显示并点亮屏幕
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            val keyguard = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
            keyguard?.requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        todoId = intent.getLongExtra(EXTRA_TODO_ID, AlarmRinger.NO_TODO)

        // 与响铃服务绑定（服务不存在时由系统按需创建）：
        // 页面一销毁就解绑，服务侧靠解绑事件退出——「关门即静音」的最后一道保险。
        serviceBound = runCatching {
            bindService(
                Intent(this, AlarmRingService::class.java),
                serviceConnection,
                Context.BIND_AUTO_CREATE,
            )
        }.onFailure { Log.w(TAG, "绑定响铃服务失败：${it.message}") }.getOrDefault(false)

        // 让响铃与「本页展示的这条待办」对齐，然后由 [AlarmRinger] 去重：
        // * 服务没起来（例如只由通知的全屏 Intent 拉起）→ 本页自己补响；
        // * 服务已经在响同一条 → 空操作，不会打断；
        // * 正在响的是**上一条**待办而服务对新闹钟还没起来（实测出现过：前台服务的启动
        //   被系统推迟了十几秒）→ 本页接管这条铃声。
        // 最后那种情况如果不处理，页面标题是新闹钟、响的却是旧闹钟，用户一关页面就变成
        // 「界面关了还在响」——正是最难查的一类。
        AlarmRinger.start(this, todoId)

        live = WeakReference(this)

        setContent {
            TodolistTheme {
                AlarmScreen(
                    todoId = todoId,
                    onDismiss = { dismissAlarm() },
                    onSnooze = { minutes ->
                        snooze(todoId, minutes)
                        dismissAlarm()
                    },
                    onComplete = {
                        complete(todoId)
                        dismissAlarm()
                    },
                )
            }
        }
    }

    /**
     * 新闹钟复用了本实例（`singleInstance` + `CLEAR_TOP`）。
     *
     * 不处理它就会出现「页面标题还是上一条待办，响的却是新闹钟」——
     * 用户看到的是「关掉的界面，铃声却还在响」的另一种版本。
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        todoId = intent.getLongExtra(EXTRA_TODO_ID, AlarmRinger.NO_TODO)
        // 本页现在展示新闹钟了，铃声也归它
        AlarmRinger.start(this, todoId)
    }

    override fun onStart() {
        super.onStart()
        visible = true
        handler.removeCallbacks(leaveWatchdog)
        // 只在页面可见期间听「熄屏」：不可见时闹钟本来就该结束了，不必再监听
        if (!screenOffRegistered) {
            screenOffRegistered = runCatching {
                registerReceiver(screenOffReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
            }.isSuccess
        }
    }

    override fun onStop() {
        super.onStop()
        visible = false
        // 配置变更（旋转）会走「销毁 + 重建」，此刻不能停铃
        if (isChangingConfigurations) return
        // 按了电源键（锁屏键）→ 屏幕熄灭。用户的反馈很明确：这时候铃声就该停，
        // 「按锁屏键」本身就是一种「我收到了」。所以这里不再像早期版本那样区分
        // 「屏幕亮着才认为用户走了」——那个区分会让熄屏后铃声一直响到超时。
        if (!isScreenOn()) {
            Log.i(TAG, "屏幕已熄灭（锁屏键），结束本次闹钟")
            endAlarm()
            return
        }
        handler.postDelayed(leaveWatchdog, LEAVE_GRACE_MS)
    }

    /**
     * 音量键也算「用户收到了」。
     *
     * 电源键不会被投递给应用（系统直接熄屏），只能靠上面的熄屏检测；
     * 音量键是会到 Activity 的，而且闹钟页上音量键调的本来就是闹钟音量——
     * 用户按它是想让它安静下来，那就直接结束，不要只把音量调小、铃还在响。
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            Log.i(TAG, "按了音量键，视为已收到提醒，结束本次闹钟")
            endAlarm()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    /** 上滑回桌面、切到别的应用、从最近任务进入其它应用都会走这里。 */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        Log.i(TAG, "用户主动离开闹钟页，结束本次闹钟")
        endAlarm()
    }

    /** 用户就地处理完提醒：停掉铃声与服务，再关闭页面。 */
    private fun dismissAlarm() {
        AlarmRingService.stopEverything(this)
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacks(leaveWatchdog)
        if (screenOffRegistered) {
            runCatching { unregisterReceiver(screenOffReceiver) }
            screenOffRegistered = false
        }
        if (live?.get() === this) live = null
        if (!isChangingConfigurations) {
            endAlarm()
        }
        if (serviceBound) {
            // 解绑即「告诉服务：界面没了」。服务的 onUnbind 会停铃并自杀，
            // 因此返回键、上滑划掉、系统回收这三条路径都不会留下还在响的闹钟。
            runCatching { unbindService(serviceConnection) }
                .onFailure { Log.w(TAG, "解绑响铃服务失败：${it.message}") }
            serviceBound = false
        }
        super.onDestroy()
    }

    /**
     * 结束本次闹钟：静音、关页面、撤通知、退出服务。可重复调用。
     *
     * 这里**无条件**停掉铃声（不传 todoId）：页面显示哪条就保证响的是哪条
     * （见 `onCreate` 里无条件的 `AlarmRinger.start`），所以「用户关掉了这张界面」
     * 就等于「这次闹钟结束了」。反过来，如果在这里做「正在响的是不是本页这条」的判断，
     * 一旦两者不一致（服务对新闹钟的启动被系统推迟过），页面关了铃声还在响——
     * 那就又回到了用户反复反馈的那个现象。
     */
    private fun endAlarm() {
        AlarmRingService.stopEverything(this)
        if (!isFinishing) finish()
    }

    private fun isScreenOn(): Boolean = runCatching {
        (getSystemService(Context.POWER_SERVICE) as android.os.PowerManager).isInteractive
    }.getOrDefault(true)

    /** 稍后提醒：为这条待办临时排一个「几分钟后」的提醒。 */
    private fun snooze(todoId: Long, minutes: Int) {
        val graph = (application as? TodoApp)?.graphOrNull() ?: return
        TodoApp.scope.launch {
            val todo = graph.coordinator.findById(todoId) ?: return@launch
            val now = System.currentTimeMillis()
            val target = now + minutes * 60_000L
            // 用同样的提醒方式再排一次：把提醒时刻显式表达为「目标时间」
            graph.coordinator.exactScheduler.schedule(
                com.avalonyin1120.todolist.domain.ReminderPlan(
                    todoId = todo.id,
                    triggerAt = target,
                    strategy = com.avalonyin1120.todolist.domain.ReminderStrategy.EXACT_ALARM,
                    occurrenceStart = todo.startAt,
                )
            )
        }
    }

    private fun complete(todoId: Long) {
        val graph = (application as? TodoApp)?.graphOrNull() ?: return
        TodoApp.scope.launch {
            graph.coordinator.setDone(todoId, done = true)
            graph.notificationHelper.cancel(todoId)
        }
    }

    companion object {
        const val ACTION_SHOW_ALARM = "com.avalonyin1120.todolist.action.SHOW_ALARM"
        const val EXTRA_TODO_ID = "extra_todo_id"

        /** 日志标签，与 [AlarmRingService.TAG] 配合过滤「闹钟起停」全过程。 */
        const val TAG = "TodoAlarm"

        /**
         * `onStop` 后的宽限期。
         *
         * 取值权衡：太短会误伤「熄屏后又亮屏回到闹钟页」「被系统弹窗短暂盖住」；
         * 太长则真的「页面不见了还在响」。15 秒足够覆盖前者，又远短于用户能忍受的响铃时长。
         */
        private const val LEAVE_GRACE_MS = 15_000L

        /**
         * 当前显示的闹钟页。
         *
         * 通知里的「完成 / 稍后 / 停止」跑在广播接收器里，拿不到页面句柄；
         * 若只静音而不关页面，用户看到的就是「点了停止，界面还留着并在响」。
         */
        @Volatile
        private var live: WeakReference<AlarmActivity>? = null

        /** 关掉正在显示的闹钟页（没有则空操作）。可在任意线程调用。 */
        fun finishLive() {
            val activity = live?.get() ?: return
            // 调用方通常在后台线程（TodoApp.scope 跑在 Dispatchers.Default）
            Handler(Looper.getMainLooper()).post {
                runCatching { if (!activity.isFinishing) activity.finish() }
            }
        }
    }
}

@Composable
private fun AlarmScreen(
    todoId: Long,
    onDismiss: () -> Unit,
    onSnooze: (Int) -> Unit,
    onComplete: () -> Unit,
) {
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext
    var title by remember { mutableStateOf("待办提醒") }
    var timeRange by remember { mutableStateOf("") }

    // 读取待办内容用于展示；读不到就显示兜底文案
    androidx.compose.runtime.LaunchedEffect(todoId) {
        val graph = (app as? TodoApp)?.graphOrNull() ?: return@LaunchedEffect
        graph.coordinator.findById(todoId)?.let { todo ->
            title = todo.name
            val formatter = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
            timeRange = "${formatter.format(Date(todo.startAt))} - " +
                formatter.format(Date(todo.endAt))
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("待办提醒", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.testTag(TAG_ALARM_TITLE),
            )
            if (timeRange.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(timeRange, style = MaterialTheme.typography.bodyLarge)
            }

            Spacer(Modifier.height(48.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag(TAG_ALARM_DISMISS),
            ) { Text("知道了") }

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = { onSnooze(10) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag(TAG_ALARM_SNOOZE),
                ) { Text("10 分钟后") }
                OutlinedButton(
                    onClick = onComplete,
                    modifier = Modifier
                        .weight(1f)
                        .testTag(TAG_ALARM_COMPLETE),
                ) { Text("标记完成") }
            }
        }
    }
}

const val TAG_ALARM_TITLE = "alarm_title"
const val TAG_ALARM_DISMISS = "alarm_dismiss"
const val TAG_ALARM_SNOOZE = "alarm_snooze"
const val TAG_ALARM_COMPLETE = "alarm_complete"
