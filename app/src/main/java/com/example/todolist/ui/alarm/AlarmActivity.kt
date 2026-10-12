package com.example.todolist.ui.alarm

import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.todolist.TodoApp
import com.example.todolist.graphOrNull
import com.example.todolist.notify.AlarmRingService
import com.example.todolist.notify.NotificationHelper
import com.example.todolist.ui.theme.TodolistTheme
import kotlinx.coroutines.launch
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
 * ## 响铃与页面同生命周期
 *
 * 页面会**绑定** [AlarmRingService]：页面被销毁（返回键、上滑划掉、系统回收）时系统自动解绑，
 * 服务侧随即停铃。这是为了修掉「界面没了，铃声还在响」——只靠 `finish()` 里调一次
 * `stopService` 是不够的，那条路径在返回键 / 划掉 / 被系统回收时根本不会执行。
 */
class AlarmActivity : ComponentActivity() {

    private var ringtone: Ringtone? = null

    /** 响铃服务的绑定句柄：拿到它可以「立刻静音」，不必等服务走完销毁流程。 */
    private var ringBinder: AlarmRingService.LocalBinder? = null
    private var serviceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            ringBinder = service as? AlarmRingService.LocalBinder
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            ringBinder = null
        }
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

        val todoId = intent.getLongExtra(EXTRA_TODO_ID, -1L)
        // 与响铃服务绑定（服务不存在时由系统按需创建）：
        // 页面一销毁就解绑，服务侧靠解绑事件停铃——这是「关门即静音」的关键一步。
        serviceBound = runCatching {
            bindService(
                Intent(this, AlarmRingService::class.java),
                serviceConnection,
                Context.BIND_AUTO_CREATE,
            )
        }.onFailure { Log.w(TAG, "绑定响铃服务失败：${it.message}") }.getOrDefault(false)

        // 正常情况下由 AlarmRingService 在响铃；只有被通知的全屏 Intent 直接拉起时
        // （锁屏场景）才由本页自己补响，避免两处同时响。
        if (!AlarmRingService.isRinging) {
            // 服务没在响（例如由通知的全屏 Intent 直接拉起），本页自己补响
            startRinging()
        }

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

    /** 用户处理完提醒：停掉铃声与服务，再关闭页面。 */
    private fun dismissAlarm() {
        // 先立刻静音（经绑定句柄直达服务，不用等服务走完销毁流程），再无绑定则停服务
        ringBinder?.stopRingingNow()
        AlarmRingService.stop(this)
        finish()
    }

    override fun onDestroy() {
        // 本页自己补响的那条铃声
        stopRinging()
        // 解绑即「告诉服务：界面没了」。服务的 onUnbind 会停铃并自杀，
        // 因此返回键、上滑划掉、系统回收这三条路径都不会留下还在响的闹钟。
        if (serviceBound) {
            runCatching { unbindService(serviceConnection) }
                .onFailure { Log.w(TAG, "解绑响铃服务失败：${it.message}") }
            serviceBound = false
        }
        ringBinder = null
        super.onDestroy()
    }

    // ---------------------------------------------------------------- 铃声

    private fun startRinging() {
        // 输入来源为闹钟时（正是本场景）应遵循铃声设置而不是静音
        if (getSystemService(AudioManager::class.java)
                ?.ringerMode == AudioManager.RINGER_MODE_SILENT
        ) {
            // 静音模式下不强行出声，但仍保持亮屏与页面显示
            return
        }
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: return
        ringtone = RingtoneManager.getRingtone(applicationContext, uri)?.apply {
            audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                isLooping = true
            }
            play()
        }
    }

    private fun stopRinging() {
        runCatching {
            ringtone?.stop()
        }
        ringtone = null
    }

    /** 稍后提醒：为这条待办临时排一个「几分钟后」的提醒。 */
    private fun snooze(todoId: Long, minutes: Int) {
        val graph = (application as? TodoApp)?.graphOrNull() ?: return
        TodoApp.scope.launch {
            val todo = graph.coordinator.findById(todoId) ?: return@launch
            val now = System.currentTimeMillis()
            val target = now + minutes * 60_000L
            // 用同样的提醒方式再排一次：把提醒时刻显式表达为「目标时间」
            graph.coordinator.exactScheduler.schedule(
                com.example.todolist.domain.ReminderPlan(
                    todoId = todo.id,
                    triggerAt = target,
                    strategy = com.example.todolist.domain.ReminderStrategy.EXACT_ALARM,
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
        const val ACTION_SHOW_ALARM = "com.example.todolist.action.SHOW_ALARM"
        const val EXTRA_TODO_ID = "extra_todo_id"

        /** 日志标签，与 [AlarmRingService.TAG] 配合过滤「闹钟起停」全过程。 */
        const val TAG = "TodoAlarm"
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
