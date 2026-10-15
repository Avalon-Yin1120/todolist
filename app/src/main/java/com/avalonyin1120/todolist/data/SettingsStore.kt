package com.avalonyin1120.todolist.data

import android.content.Context
import android.content.SharedPreferences
import com.avalonyin1120.todolist.domain.ReminderMode
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
     * 新建待办时表单里**预先选中**的提醒方式（通知 / 闹钟式 / 不通知）。
     *
     * 三档都允许当默认值，因为这三档本来就是同一份「提醒方式」的取值；
     * 只暴露一个布尔（闹钟式 / 非闹钟式）时，「默认不通知」这种合理诉求根本表达不出来。
     *
     * **默认是 [ReminderMode.NOTIFICATION]**（与 v17 起的既有行为一致），理由有三：
     * * 闹钟式会持续响铃直到用户处理，是「抢占式」的（点亮屏幕 + 弹整屏页面），当默认值太吵；
     * * 它依赖精确闹钟权限，未授权时系统会把提醒推迟，甚至拉不起响铃页；
     * * 通知式不需要任何特殊权限，而且通知上就能直接「完成 / 10 分钟后再提醒」。
     *
     * 而当默认值是「不通知」时，新建出来的待办到点什么都不做——这是用户主动选的，
     * 不是我们替他决定「这条不用提醒」，因此不做任何拦截。
     */
    var defaultReminderMode: ReminderMode

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

    /**
     * 用户在「提醒可靠性检查」页勾过的「我已设置好」项 id 集合。
     *
     * 为什么需要它：那一页有一半开关**系统读不到真实状态**（后台弹出界面、耗电管理，
     * 以及通知渠道的锁屏/横幅/静默细节——渠道读数在真机上被证伪过，见 sys/ReminderHealth.kt）。
     * 以前只能反复显示「需人工确认」，用户照着改完回来页面还是老样子、计数也清不掉。
     * 把用户的确认记在本地，检查页的计数与提示才能真的归零（用户确认优先于系统读数）。
     *
     * 默认**空集**：老版本从没写过这个键，读出来就是「什么都没确认」，与升级前的行为一致。
     * 契约：id **只增不改**（sys/ReminderHealth.kt 里的 STEP_xxx 是持久化键，改名等于丢掉勾选）。
     */
    var confirmedHealthItems: Set<String>
}

/** 生产实现。 */
class SharedPreferencesSettingsStore(context: Context) : SettingsStore {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override var defaultReminderMode: ReminderMode
        get() {
            // 新键在，直接用。存的是枚举名；认不出来（手改坏、或被降级安装的旧版本写脏）
            // 就走下面的兼容分支，绝不让非法值漏进界面。
            val stored = prefs.getString(KEY_DEFAULT_MODE, null)
            val parsed = stored?.let { name -> ReminderMode.entries.firstOrNull { it.name == name } }
            if (parsed != null) return parsed

            // 新键不在，但**旧键在**：这是「升级安装」的老用户。
            // 旧版本只存过一个布尔（闹钟式 / 通知式），升级后那个键不会自己变成枚举，
            // 不在这里翻译一次，用户之前选的「闹钟式」就会被悄悄改回通知式——
            // 而「到点不响铃」恰恰是本应用最不能出的问题。
            if (prefs.contains(KEY_USE_ALARM)) {
                return if (prefs.getBoolean(KEY_USE_ALARM, DEFAULT_USE_ALARM)) {
                    ReminderMode.ALARM
                } else {
                    ReminderMode.NOTIFICATION
                }
            }

            // 两个键都没有：全新安装，用产品默认值。
            return DEFAULT_REMINDER_MODE
        }
        set(value) = prefs.edit().putString(KEY_DEFAULT_MODE, value.name).apply()

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

    override var confirmedHealthItems: Set<String>
        // 必须交出去**另一份**集合：getStringSet 返回的是 SharedPreferences 内部持有的那个实例，
        // 调用方一旦就地修改它，改动既绕过 commit/apply（不落盘），又会污染内存里的那份值。
        get() = prefs.getStringSet(KEY_CONFIRMED_HEALTH_ITEMS, null)?.toSet() ?: emptySet()
        // 写入同样先落成一份快照：putStringSet 之后调用方若再改自己那个集合，落盘内容会跟着变。
        set(value) = prefs.edit().putStringSet(KEY_CONFIRMED_HEALTH_ITEMS, value.toSet()).apply()

    companion object {
        private const val PREFS_NAME = "todolist_settings"

        /**
         * **旧键（只读，不再写入）**：v18 之前存的是「是否用闹钟式」的布尔。
         *
         * 保留这个名字只为了在 [defaultReminderMode] 的 getter 里读出来做一次性翻译，
         * 让升级安装的老用户保住自己的选择（true → ALARM，false → NOTIFICATION）。
         * 等确认没有用户会从 v17 及更早版本升级上来之后，这个键与那段兼容分支可以一起删。
         */
        private const val KEY_USE_ALARM = "use_alarm_for_reminders"

        /** 新键：存 [ReminderMode] 的枚举名（与数据库存枚举名同一套做法）。 */
        private const val KEY_DEFAULT_MODE = "default_reminder_mode"
        private const val KEY_LAST_LEAD = "last_lead_minutes"
        private const val KEY_CONFIRM_DELETE = "confirm_before_delete"
        private const val KEY_WARN_OVERLAP = "warn_on_overlap"
        private const val KEY_NOTIFICATION_SOUND = "notification_sound"
        private const val KEY_WEEK_START_HOUR = "week_view_start_hour"
        private const val KEY_WEEK_END_HOUR = "week_view_end_hour"

        /**
         * 「提醒可靠性检查」页里用户勾过的项 id。
         *
         * 与其它键不同，这个键**从第一次写入起就必须保持不变**（见 [SettingsStore.confirmedHealthItems]）：
         * 它存的 id 来自 sys/ReminderHealth.kt 里的 STEP_xxx，改名等于把用户的勾选全部作废。
         */
        private const val KEY_CONFIRMED_HEALTH_ITEMS = "confirmed_health_items"

        /** 用户从未设置过时的默认提前量。 */
        const val DEFAULT_LEAD_MINUTES = 0

        /** 旧布尔键缺失时的兜底；只为兼容分支服务，值仍是「非闹钟式」。 */
        private const val DEFAULT_USE_ALARM = false

        /** 用户从未设置过时的默认提醒方式：通知式。 */
        val DEFAULT_REMINDER_MODE: ReminderMode = ReminderMode.NOTIFICATION
    }
}

/** 内存实现，供单元测试与预览使用。 */
class InMemorySettingsStore(
    override var defaultReminderMode: ReminderMode =
        SharedPreferencesSettingsStore.DEFAULT_REMINDER_MODE,
    override var lastLeadMinutes: Int = SharedPreferencesSettingsStore.DEFAULT_LEAD_MINUTES,
    override var confirmBeforeDelete: Boolean = true,
    override var warnOnOverlap: Boolean = true,
    override var notificationSound: Boolean = true,
    override var weekViewStartHour: Int = WeekWindow.DEFAULT_START_HOUR,
    override var weekViewEndHour: Int = WeekWindow.DEFAULT_END_HOUR,
    /** 见 [SettingsStore.confirmedHealthItems]；默认「什么都没确认」，与老用户升级上来的状态一致。 */
    override var confirmedHealthItems: Set<String> = emptySet(),
) : SettingsStore
