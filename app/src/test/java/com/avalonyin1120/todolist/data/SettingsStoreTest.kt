package com.avalonyin1120.todolist.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.avalonyin1120.todolist.domain.ReminderMode
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 设置存储：[SharedPreferencesSettingsStore] 与 [InMemorySettingsStore]。
 *
 * 重点在「新建待办时默认选择哪一档提醒」这一项，因为它是从**旧版本的布尔开关**
 * 换过来的：只测「写进去能读回来」会漏掉升级安装的那条路径——老用户的
 * SharedPreferences 里只有 `use_alarm_for_reminders`，没有新的枚举键。
 *
 * 用 Robolectric 而不是纯 JVM：只有它能真给出一个可用的 Context/SharedPreferences，
 * 而这正是迁移逻辑真正跑起来的地方（内存实现会把问题藏起来）。
 */
@RunWith(RobolectricTestRunner::class)
class SettingsStoreTest {

    private lateinit var context: Context

    /**
     * 与 `SharedPreferencesSettingsStore` 里的 PREFS_NAME 必须一致。
     *
     * 那边的常量是 private，这里只能重写一份。名字对不上时，
     * 「清空设置」就会默默清到另一个文件上，测试结果将取决于执行顺序——
     * 所以下面每个用例都先清空再断言，而不是依赖「一开始是干净的」。
     */
    private val prefsName = "todolist_settings"

    /** 旧版本的键名（v18 之前只有这个布尔）。 */
    private val keyUseAlarm = "use_alarm_for_reminders"

    /** 新键：存枚举名。 */
    private val keyDefaultMode = "default_reminder_mode"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // 清干净：Robolectric 在同一个测试 JVM 里共享 SharedPreferences，
        // 上一条用例写下的值会留到这一条（同一个类里也一样）。
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun prefs() = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    private fun store() = SharedPreferencesSettingsStore(context)

    // ------------------------------------------------------------ 全新安装

    @Test
    fun `什么都没存过时默认是通知式`() {
        // 与 v17 起的既有行为一致：闹钟式会持续响铃并抢占屏幕，不该当默认值
        assertThat(store().defaultReminderMode).isEqualTo(ReminderMode.NOTIFICATION)
        assertThat(SharedPreferencesSettingsStore.DEFAULT_REMINDER_MODE)
            .isEqualTo(ReminderMode.NOTIFICATION)
    }

    @Test
    fun `全新安装时其它设置项不受影响`() {
        // 这一段是「顺手确认没把邻居改坏」：同一批 getter 挤在一个文件里，
        // 改动其中一项时最容易出的事就是复制粘贴时连默认值一起抄错。
        val s = store()

        assertThat(s.lastLeadMinutes).isEqualTo(SharedPreferencesSettingsStore.DEFAULT_LEAD_MINUTES)
        assertThat(s.confirmBeforeDelete).isTrue()
        assertThat(s.warnOnOverlap).isTrue()
        assertThat(s.notificationSound).isTrue()
    }

    // ------------------------------------------------------------ 三档落库

    @Test
    fun `三档默认方式都能落库并原样读回`() {
        // 「不通知」也必须能当默认值：一个布尔表达不了它，这正是把开关换成三选一的理由
        ReminderMode.entries.forEach { mode ->
            store().defaultReminderMode = mode

            assertThat(store().defaultReminderMode).isEqualTo(mode)
            // 存的是枚举名（与数据库里存 mode 同一套做法），
            // 这样以后往枚举里加档位也不会把已存的值读错档
            assertThat(prefs().getString(keyDefaultMode, null)).isEqualTo(mode.name)
        }
    }

    @Test
    fun `新键存了无法识别的值时回落到默认档而不是崩`() {
        // 手改过、或被降级安装的旧版本写脏：读设置绝不该抛异常，
        // 界面拿到的必须永远是一个合法档位
        prefs().edit().putString(keyDefaultMode, "SOMETHING_ELSE").commit()

        assertThat(store().defaultReminderMode).isEqualTo(ReminderMode.NOTIFICATION)
    }

    // ------------------------------------------------------------ 老用户迁移

    @Test
    fun `老用户存过闹钟式则迁移成闹钟式`() {
        // 升级安装：只有旧键，没有新键
        prefs().edit().putBoolean(keyUseAlarm, true).commit()

        assertThat(store().defaultReminderMode).isEqualTo(ReminderMode.ALARM)
    }

    @Test
    fun `老用户存过非闹钟式则迁移成通知式`() {
        prefs().edit().putBoolean(keyUseAlarm, false).commit()

        assertThat(store().defaultReminderMode).isEqualTo(ReminderMode.NOTIFICATION)
    }

    @Test
    fun `已经存过新键的用户不会再被旧键覆盖`() {
        // 用户升级后手动选了「不通知」，此时旧键还留在文件里（我们只读不删）。
        // 如果 getter 先看旧键，用户的选择会在下次启动时被悄悄改回通知式。
        prefs().edit()
            .putBoolean(keyUseAlarm, true)
            .putString(keyDefaultMode, ReminderMode.NONE.name)
            .commit()

        assertThat(store().defaultReminderMode).isEqualTo(ReminderMode.NONE)
    }

    @Test
    fun `迁移之后写一次就以新键为准`() {
        prefs().edit().putBoolean(keyUseAlarm, true).commit()
        assertThat(store().defaultReminderMode).isEqualTo(ReminderMode.ALARM)

        store().defaultReminderMode = ReminderMode.NONE

        assertThat(store().defaultReminderMode).isEqualTo(ReminderMode.NONE)
        // 旧键仍在（只读不删），但已被新键压住
        assertThat(prefs().contains(keyUseAlarm)).isTrue()
    }

    // ------------------------------------------------------------ 内存实现

    @Test
    fun `内存实现三档都能读回`() {
        ReminderMode.entries.forEach { mode ->
            val s = InMemorySettingsStore(defaultReminderMode = mode)

            assertThat(s.defaultReminderMode).isEqualTo(mode)
        }
    }

    @Test
    fun `内存实现默认是通知式`() {
        assertThat(InMemorySettingsStore().defaultReminderMode)
            .isEqualTo(ReminderMode.NOTIFICATION)
    }
}
