package com.example.todolist.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.todolist.TodoApp
import com.example.todolist.graphOrNull
import com.example.todolist.data.TodoDraft
import com.example.todolist.domain.ReminderMode
import kotlinx.coroutines.launch

/**
 * 仅存在于 **debug 构建** 的测试入口：造一条「马上要提醒」的待办。
 *
 * 为什么需要它：「退到后台/锁屏后提醒到底响不响」是最关键也最容易出问题的场景，
 * 但用 adb 点界面把起始时间调到几十秒后非常费劲（要过日期/时间两个选择器）。
 * 这个接收器走**真实链路**（`SchedulingCoordinator.create`，与界面保存是同一条路），
 * 于是可以干净地复现：造数据 → 退到后台 → 等到点 → 看通知与日志。
 *
 * 用法（`--el` 传 long，`--ei` 传 int，`--ez` 传 boolean）：
 * ```
 * adb shell am broadcast -a com.example.todolist.debug.SEED_TODO \
 *   -n com.example.todolist.debug.DebugSeedReceiver \
 *   -e name 后台提醒验证 --el trigger_in_seconds 60 --ez alarm true
 * ```
 *
 * @param name 名称
 * @param trigger_in_seconds 提醒在多少秒后触发（会反推起始时间，提前量按 0 处理）
 * @param lead_minutes 提前量（默认 0，即准点提醒）
 * @param alarm true=闹钟式，false=通知式（默认 false）
 * @param duration_minutes 持续时长（默认 10 分钟），仅用于列表展示
 */
class DebugSeedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val name = intent.getStringExtra(EXTRA_NAME) ?: "后台提醒验证"
        val triggerInSeconds = intent.getLongExtra(EXTRA_TRIGGER_IN_SECONDS, 60L)
        val leadMinutes = intent.getIntExtra(EXTRA_LEAD_MINUTES, 0)
        val alarm = intent.getBooleanExtra(EXTRA_ALARM, false)
        val durationMinutes = intent.getLongExtra(EXTRA_DURATION_MINUTES, 10L)

        val app = context.applicationContext as? TodoApp
        val graph = app?.graphOrNull()
        if (graph == null) {
            Log.e(TAG, "依赖图未初始化，无法造数据")
            return
        }

        val pending = goAsync()
        TodoApp.scope.launch {
            try {
                val now = graph.clock.now()
                // 提醒时刻 = now + triggerInSeconds，因此起始时间要往前推一个提前量
                val remindAt = now + triggerInSeconds * 1000L
                val startAt = remindAt + leadMinutes * 60_000L
                val draft = TodoDraft(
                    name = name,
                    startAt = startAt,
                    endAt = startAt + durationMinutes * 60_000L,
                    leadMinutes = leadMinutes,
                    mode = if (alarm) ReminderMode.ALARM else ReminderMode.NOTIFICATION,
                )
                val result = graph.coordinator.create(draft)
                Log.i(
                    TAG,
                    "已造数据 id=${result.todoId} name=$name 提醒时刻=$remindAt" +
                        "（${triggerInSeconds}s 后）方式=${if (alarm) "ALARM" else "NOTIFICATION"}" +
                        " 起始=$startAt 降级=${result.degraded}",
                )
                Log.i(TAG, "SEEDED_TODO_ID=${result.todoId}")
            } catch (e: Exception) {
                Log.e(TAG, "造数据失败：${e.javaClass.simpleName} ${e.message}")
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val TAG = "TodoDebugSeed"
        const val ACTION = "com.example.todolist.debug.SEED_TODO"
        const val EXTRA_NAME = "name"
        const val EXTRA_TRIGGER_IN_SECONDS = "trigger_in_seconds"
        const val EXTRA_LEAD_MINUTES = "lead_minutes"
        const val EXTRA_ALARM = "alarm"
        const val EXTRA_DURATION_MINUTES = "duration_minutes"
    }
}
