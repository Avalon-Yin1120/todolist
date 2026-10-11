package com.example.todolist.data

import android.content.Context
import android.content.SharedPreferences

/**
 * 应用设置。
 *
 * 用 SharedPreferences 而不是 DataStore：只有寥寥几个开关，
 * 引入 DataStore 会多一个依赖与异步初始化流程，收益不足。
 *
 * 定义为接口 + 生产/内存两个实现，测试里用内存实现即可断言「记住上次的提前量」。
 */
interface SettingsStore {
    /** 提醒方式是否使用闹钟式（响铃 + 亮屏 + 全屏页）。 */
    var useAlarmForReminders: Boolean

    /** 上次输入的提前量（分钟），用于新建待办时的智能默认值。 */
    var lastLeadMinutes: Int

    /** 是否在删除前二次确认。 */
    var confirmBeforeDelete: Boolean

    /** 时间段冲突时是否提示（关闭后仍可创建，只是不提示）。 */
    var warnOnOverlap: Boolean
}

/** 生产实现。 */
class SharedPreferencesSettingsStore(context: Context) : SettingsStore {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override var useAlarmForReminders: Boolean
        get() = prefs.getBoolean(KEY_USE_ALARM, true)
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

    companion object {
        private const val PREFS_NAME = "todolist_settings"
        private const val KEY_USE_ALARM = "use_alarm_for_reminders"
        private const val KEY_LAST_LEAD = "last_lead_minutes"
        private const val KEY_CONFIRM_DELETE = "confirm_before_delete"
        private const val KEY_WARN_OVERLAP = "warn_on_overlap"

        /** 用户从未设置过时的默认提前量。 */
        const val DEFAULT_LEAD_MINUTES = 0
    }
}

/** 内存实现，供单元测试与预览使用。 */
class InMemorySettingsStore(
    override var useAlarmForReminders: Boolean = true,
    override var lastLeadMinutes: Int = SharedPreferencesSettingsStore.DEFAULT_LEAD_MINUTES,
    override var confirmBeforeDelete: Boolean = true,
    override var warnOnOverlap: Boolean = true,
) : SettingsStore
