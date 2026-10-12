package com.example.todolist

import android.app.Application
import androidx.work.Configuration
import com.example.todolist.data.SettingsStore
import com.example.todolist.data.SharedPreferencesSettingsStore
import com.example.todolist.data.TodoRepository
import com.example.todolist.data.local.TodoDatabase
import com.example.todolist.domain.Clock
import com.example.todolist.domain.SystemClock
import com.example.todolist.notify.NotificationHelper
import com.example.todolist.reminder.AlarmReminderScheduler
import com.example.todolist.reminder.ReminderFireHandler
import com.example.todolist.reminder.ReminderFiringService
import com.example.todolist.reminder.ReminderScheduler
import com.example.todolist.reminder.SchedulingCoordinator
import com.example.todolist.reminder.WorkReminderScheduler
import com.example.todolist.sys.ExactAlarmPermission
import com.example.todolist.sys.SystemExactAlarmPermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 应用级依赖图。
 *
 * 刻意不引入 Hilt：这个 App 的依赖图很小，手写装配根更易读，
 * 也让单元测试能直接替换任意一层（各测试里都用了 Fake）。
 */
open class TodoApp : Application(), Configuration.Provider {

    /**
     * 依赖图。
     *
     * 声明为 lateinit + 私有 setter，onCreate 里装配；
     * 读取统一走 [requireGraph] / [graphOrNull]，避免未初始化时崩溃。
     */
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        installGraph(createGraph())
        graph.notificationHelper.ensureChannel()

        // 启动时对账一次：覆盖「重启后 AlarmManager 被清空」「系统时间被改」等情况。
        // 失败不影响 App 启动（例如数据库被占用），下一次开机/启动会再对账。
        scope.launch {
            runCatching { graph.coordinator.reconcileAll() }
        }
    }

    /** 供测试覆盖。 */
    internal open fun createGraph(): AppGraph = AppGraph(this)

    /** 供测试注入自定义依赖图（例如用 Fake 仓库与 Fake 调度器）。 */
    fun installGraph(custom: AppGraph) {
        graph = custom
    }

    /** 为 WorkManager 提供配置，避免额外的 manifest provider 声明。 */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.WARN)
            .build()

    companion object {
        /** 应用级协程作用域：用于广播接收器里的 goAsync() 任务。 */
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}

/**
 * 读取依赖图；Application 已创建且依赖图已装配时返回它，否则返回 null。
 *
 * 广播接收器与 Worker 在进程可能被冷启动的场景下调用它，
 * 用 try/catch 处理 lateinit 未初始化，避免在这些边界上崩溃。
 */
fun TodoApp.graphOrNull(): AppGraph? = try {
    graph
} catch (_: UninitializedPropertyAccessException) {
    null
}

/** 与 [graphOrNull] 相同，但未初始化时直接抛出带说明的异常。 */
fun TodoApp.requireGraph(): AppGraph = graphOrNull()
    ?: error("应用依赖图尚未初始化（TodoApp.onCreate 未执行）")

/** 具体依赖的装配结果。 */
class AppGraph(application: Application) {

    val clock: Clock = SystemClock()

    private val database: TodoDatabase = TodoDatabase.get(application)

    val repository: TodoRepository = TodoRepository(database.todoDao())

    val notificationHelper: NotificationHelper = NotificationHelper(application)

    val exactAlarmPermission: ExactAlarmPermission = SystemExactAlarmPermission(application)

    /** 后台运行（电池优化白名单）能力，用于引导用户允许后台运行。 */
    val batteryOptimization: com.example.todolist.sys.BatteryOptimizationPermission =
        com.example.todolist.sys.SystemBatteryOptimizationPermission(application)

    /** 用户设置：提醒方式、记住的提前量、是否二次确认等。 */
    val settings: SettingsStore = SharedPreferencesSettingsStore(application)

    /**
     * 精确闹钟通道：闹钟式提醒的主通道。
     *
     * 用 `setAlarmClock`（系统级「用户可见的闹钟」），国产 ROM 的省电策略普遍对它放行。
     */
    private val exactScheduler: ReminderScheduler = AlarmReminderScheduler(application, exact = true)

    /**
     * 非精确闹钟通道：通知方式的主通道，同时是精确通道的保险。
     *
     * 不需要任何特殊权限，且比 WorkManager 可靠——JobScheduler 在国产 ROM 上
     * 会被整体推迟到「用户下次打开 App」，而闹钟即使 Doze 也会被投递。
     */
    private val inexactScheduler: ReminderScheduler =
        AlarmReminderScheduler(application, exact = false)

    /** WorkManager 通道：只作为最后一道保险。 */
    private val workScheduler: ReminderScheduler = WorkReminderScheduler(application)

    val coordinator: SchedulingCoordinator = SchedulingCoordinator(
        repository = repository,
        exactScheduler = exactScheduler,
        inexactScheduler = inexactScheduler,
        workScheduler = workScheduler,
        clock = clock,
        exactAlarmPermission = exactAlarmPermission,
    )

    val fireHandler: ReminderFireHandler = ReminderFiringService(
        coordinator = coordinator,
        onFire = { todo -> notificationHelper.notifyReminder(todo) },
    )
}
