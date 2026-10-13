package com.avalonyin1120.todolist.data

import android.content.Context
import android.content.SharedPreferences
import com.avalonyin1120.todolist.domain.WeekWindow

/**
 * 应用设置。
 *
 * 用 SharedPreferences 而不是 DataStore：只有寥寥几个开关，
 * 引入 DataStore 会多一个依赖与异步初始化流程，收益不足。
 *
 * 定义为接口 + 生产/内存两个实现，测试里用内存实现即可断言「记住上次的提前量」。
 *
 * 所有「取值 + 兜底」都写成 `getX(default)` 的形式：即使 SharedPreferences 里
 * 存着非法值（例如手改过、或旧版本写进去的越界小时数），读出来也一定是合法值。
 */
interface SettingsStore {
    /**
     * 提醒方式是否使用闹钟式（响铃 + 亮屏 + 全屏页）。
     *
     * **默认关闭**，也就是默认走通知式。理由有三：
     * * 闹钟式会持续响铃直到用户处理，是「抢占式」的（点亮屏幕 + 弹整屏页面），当默认值太吵；
     * * 它依赖精确闹钟权限，未授权时系统会把提醒推迟，甚至拉不起响铃页；
     * * 通知式不需要任何特殊权限，而且通知上就能直接「完成 / 10 分钟后再提醒」。
     */
    var useAlarmForReminders: Boolean

    /** 上次输入的提前量（分钟），用于新建待办时的智能默认值。 */
    var lastLeadMinutes: Int

    /** 是否在删除前二次确认。 */
    var confirmBeforeDelete: Boolean

    /** 时间段冲突时是否提示（关闭后仍可创建，只是不提示）。 */
    var warnOnOverlap: Boolean

    /**
     * 提醒通知是否播放提示音。
     *
     * 与「闹钟式提醒」是两回事：闹钟式是持续响铃直到处理，通知只是「响一下」。
     * 关掉后通知仍然会弹出（横幅 + 通知栏），只是不出声。
     */
    var notificationSound: Boolean

    /**
     * 周视图纵向显示的起始小时（0..23）。
     *
     * 周视图把 [weekViewStartHour]~[weekViewEndHour] 这一段压进一屏，
     * 所以窗口越窄、每小时占的高度越大。默认从 6 点开始：凌晨那一截
     * 极少有人排待办，白白占掉一屏的四分之一，也让整个时间轴更挤。
     */
    var weekViewStartHour: Int

    /** 周视图纵向显示的结束小时（1..24），必须比起始小时至少晚 [WeekWindow.MIN_HOURS] 小时。 */
    var weekViewEndHour: Int
}

/** 生产实现。 */
class SharedPreferencesSettingsStore(context: Context) : SettingsStore {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override var useAlarmForReminders: Boolean
        get() = prefs.getBoolean(KEY_USE_ALARM, DEFAULT_USE_ALARM)
        set(value) = prefs.edit().putBoolean(KEY_USE_ALARM, value).apply()

    override var lastLeadMinutes: Int
        get() = prefs.getInt(KEY_LAST_LEAD, DEFAULT_LEAD_MINUTES)
        set(value) = prefs.edit().putInt(KEY_LAST_LEAD, value).apply()

    override var confirmBeforeDelete: Boolean
        get() = prefs.getBoolean(KEY_CONFIRM_DELETE, true)
        set(value) = prefs.edit().putBoolean(KEY_CONFIRM_DELETE, value).apply()

    override var warnOnOverlap: Boolean
        get() = prefs.getBoolean(KEY_WARN_OVERLAP, true)
        set(value) = prefs.edit().putBoolean(KEY_WARN_OVERLAP, value).apply()

    override var notificationSound: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATION_SOUND, true)
        set(value) = prefs.edit().putBoolean(KEY_NOTIFICATION_SOUND, value).apply()

    override var weekViewStartHour: Int
        // 存进去的可能是被手改坏的越界值，读出来先夹一遍
        get() = WeekWindow.clampStart(
            prefs.getInt(KEY_WEEK_START_HOUR, WeekWindow.DEFAULT_START_HOUR),
            prefs.getInt(KEY_WEEK_END_HOUR, WeekWindow.DEFAULT_END_HOUR),
        )
        set(value) = prefs.edit().putInt(KEY_WEEK_START_HOUR, value).apply()

    override var weekViewEndHour: Int
        get() = WeekWindow.clampEnd(
            prefs.getInt(KEY_WEEK_END_HOUR, WeekWindow.DEFAULT_END_HOUR),
            prefs.getInt(KEY_WEEK_START_HOUR, WeekWindow.DEFAULT_START_HOUR),
        )
        set(value) = prefs.edit().putInt(KEY_WEEK_END_HOUR, value).apply()

    companion object {
        private const val PREFS_NAME = "todolist_settings"
        private const val KEY_USE_ALARM = "use_alarm_for_reminders"
        private const val KEY_LAST_LEAD = "last_lead_minutes"
        private const val KEY_CONFIRM_DELETE = "confirm_before_delete"
        private const val KEY_WARN_OVERLAP = "warn_on_overlap"
        private const val KEY_NOTIFICATION_SOUND = "notification_sound"
        private const val KEY_WEEK_START_HOUR = "week_view_start_hour"
        private const val KEY_WEEK_END_HOUR = "week_view_end_hour"

        /** 用户从未设置过时的默认提前量。 */
        const val DEFAULT_LEAD_MINUTES = 0

        /** 用户从未设置过时的默认提醒方式：通知式。 */
        const val DEFAULT_USE_ALARM = false
    }
}

/** 内存实现，供单元测试与预览使用。 */
class InMemorySettingsStore(
    override var useAlarmForReminders: Boolean = SharedPreferencesSettingsStore.DEFAULT_USE_ALARM,
    override var lastLeadMinutes: Int = SharedPreferencesSettingsStore.DEFAULT_LEAD_MINUTES,
    override var confirmBeforeDelete: Boolean = true,
    override var warnOnOverlap: Boolean = true,
    override var notificationSound: Boolean = true,
    override var weekViewStartHour: Int = WeekWindow.DEFAULT_START_HOUR,
    override var weekViewEndHour: Int = WeekWindow.DEFAULT_END_HOUR,
) : SettingsStore
