package com.example.todolist.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.todolist.TodoApp
import com.example.todolist.graphOrNull
import com.example.todolist.notify.AlarmRingService
import kotlinx.coroutines.launch

/**
 * 仅存在于 **debug 构建** 的测试入口：立刻触发一次闹钟式提醒。
 *
 * 为什么需要它：`AlarmRingService` 与 `AlarmActivity` 都不导出（发布版必须如此），
 * 用 adb 无法直接触发，于是「应用在后台/锁屏时闹钟到底响不响」这种最关键的场景
 * 没法在设备上验证。这个接收器在 debug 清单里声明为 exported，
 * 让 adb 能模拟「到点了」的那一刻，从而实测后台启动前台服务是否被系统拒绝。
 *
 * 用法：
 * ```
 * adb shell am broadcast -a com.example.todolist.debug.RING_ALARM \
 *   --el extra_todo_id 1 -n com.example.todolist.debug.DebugAlarmReceiver
 * ```
 *
 * release 构建里没有这个类，也没有对应的清单条目。
 */
class DebugAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val todoId = intent.getLongExtra(EXTRA_TODO_ID, -1L)
        Log.i(TAG, "收到调试触发：todo=$todoId")

        val app = context.applicationContext as? TodoApp
        val graph = app?.graphOrNull()
        if (graph == null) {
            Log.e(TAG, "依赖图未初始化，无法触发")
            return
        }

        val pending = goAsync()
        TodoApp.scope.launch {
            try {
                val todo = graph.coordinator.findById(todoId)
                if (todo == null) {
                    Log.e(TAG, "找不到待办：id=$todoId")
                    return@launch
                }
                Log.i(TAG, "走真实链路发提醒：name=${todo.name} mode=${todo.mode}")
                graph.notificationHelper.notifyReminder(todo)
            } catch (e: Exception) {
                Log.e(TAG, "触发失败：${e.javaClass.simpleName} ${e.message}")
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val TAG = "TodoDebugAlarm"
        const val ACTION = "com.example.todolist.debug.RING_ALARM"
        const val EXTRA_TODO_ID = "extra_todo_id"

        /** 供 adb 直接拉起响铃服务（用来单独验证前台服务能否启动）。 */
        fun startServiceDirectly(context: Context, todoId: Long, name: String) {
            AlarmRingService.start(context, todoId, name)
        }
    }
}
