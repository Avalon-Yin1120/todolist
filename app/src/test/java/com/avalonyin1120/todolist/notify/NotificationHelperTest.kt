package com.avalonyin1120.todolist.notify

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.avalonyin1120.todolist.testing.TestData
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/**
 * 通知渠道与通知内容的 Robolectric 测试。
 *
 * 真机上的通知栏行为由仪器化测试与 README 里的 adb 步骤覆盖。
 */
@RunWith(RobolectricTestRunner::class)
class NotificationHelperTest {

    private lateinit var context: Context
    private lateinit var helper: NotificationHelper
    private lateinit var manager: NotificationManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        helper = NotificationHelper(context)
        manager = context.getSystemService(NotificationManager::class.java)
        grantNotificationPermission()
    }

    @Test
    fun `创建渠道后可以查询到且重要性为高`() {
        helper.ensureChannel()

        val channel = manager.getNotificationChannel(NotificationHelper.CHANNEL_ID)
        assertThat(channel).isNotNull()
        assertThat(channel.importance).isEqualTo(NotificationManager.IMPORTANCE_HIGH)
        assertThat(channel.name.toString()).isEqualTo("待办提醒（有声）")
        // 渠道必须有声音：早先版本建出的渠道没声音，而渠道一旦创建就改不了，
        // 这正是「通知不响」的根因，因此这里把「有声」当成硬约束断言下来。
        assertThat(channel.sound).isNotNull()
    }

    @Test
    fun `静音渠道存在且没有声音`() {
        helper.ensureChannel()

        val silent = manager.getNotificationChannel(NotificationHelper.CHANNEL_ID_SILENT)
        assertThat(silent).isNotNull()
        // 重要性仍为高：用户关掉的是声音，不是提醒本身
        assertThat(silent.importance).isEqualTo(NotificationManager.IMPORTANCE_HIGH)
        assertThat(silent.sound).isNull()
    }

    @Test
    fun `关掉提示音后走静音渠道`() {
        val silentHelper = NotificationHelper(context, soundEnabled = { false })
        silentHelper.ensureChannel()

        assertThat(silentHelper.channelId()).isEqualTo(NotificationHelper.CHANNEL_ID_SILENT)

        silentHelper.notifyReminder(TestData.todo(id = 21L))

        val posted = shadowOf(manager).getNotification(NotificationHelper.notificationIdOf(21L))
        assertThat(posted).isNotNull()
        assertThat(posted.channelId).isEqualTo(NotificationHelper.CHANNEL_ID_SILENT)
    }

    @Test
    fun `开着提示音时走有声渠道`() {
        val soundHelper = NotificationHelper(context, soundEnabled = { true })
        soundHelper.ensureChannel()
        soundHelper.notifyReminder(TestData.todo(id = 22L))

        val posted = shadowOf(manager).getNotification(NotificationHelper.notificationIdOf(22L))
        assertThat(posted).isNotNull()
        assertThat(posted.channelId).isEqualTo(NotificationHelper.CHANNEL_ID)
    }

    @Test
    fun `提示音开关是运行时生效的`() {
        var sound = false
        val dynamic = NotificationHelper(context, soundEnabled = { sound })
        dynamic.ensureChannel()

        assertThat(dynamic.channelId()).isEqualTo(NotificationHelper.CHANNEL_ID_SILENT)
        // 开关是取值函数而不是构造时的快照：改完立刻生效，不需要重启应用
        sound = true
        assertThat(dynamic.channelId()).isEqualTo(NotificationHelper.CHANNEL_ID)
    }

    @Test
    fun `建渠道时会清掉旧版本留下的残留渠道`() {
        // 模拟从老版本升级上来：旧渠道已经存在于系统里
        manager.createNotificationChannel(
            android.app.NotificationChannel(
                NotificationHelper.LEGACY_CHANNEL_ID,
                "待办提醒",
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
        assertThat(manager.getNotificationChannel(NotificationHelper.LEGACY_CHANNEL_ID)).isNotNull()

        helper.ensureChannel()

        assertThat(manager.getNotificationChannel(NotificationHelper.LEGACY_CHANNEL_ID)).isNull()
    }

    @Test
    fun `重复创建渠道是幂等的`() {
        helper.ensureChannel()
        helper.ensureChannel()

        val channels = shadowOf(manager).notificationChannels
        assertThat(channels.count { it.id == NotificationHelper.CHANNEL_ID }).isEqualTo(1)
    }

    @Test
    fun `通知 id 由待办 id 稳定推导`() {
        assertThat(NotificationHelper.notificationIdOf(7L))
            .isEqualTo(NotificationHelper.notificationIdOf(7L))
        assertThat(NotificationHelper.notificationIdOf(7L))
            .isNotEqualTo(NotificationHelper.notificationIdOf(8L))
        // 大 id 折叠后仍是合法 int，不应抛异常
        assertThat(NotificationHelper.notificationIdOf(Long.MAX_VALUE)).isNotNull()
    }

    @Test
    fun `发通知后能查到该通知且标题内容正确`() {
        helper.ensureChannel()
        val todo = TestData.todo(id = 3L, name = "小组会议")

        helper.notifyReminder(todo)

        val id = NotificationHelper.notificationIdOf(3L)
        val posted = shadowOf(manager).getNotification(id)
        assertThat(posted).isNotNull()
        assertThat(posted.extras.getString("android.title")).isEqualTo("小组会议")
        assertThat(posted.extras.getString("android.text").orEmpty()).contains("时间：")
    }

    @Test
    fun `通知带上了点击跳转的 PendingIntent`() {
        helper.ensureChannel()
        helper.notifyReminder(TestData.todo(id = 11L))

        val posted = shadowOf(manager).getNotification(NotificationHelper.notificationIdOf(11L))
        assertThat(posted).isNotNull()
        assertThat(posted.contentIntent).isNotNull()
    }

    @Test
    fun `提醒通知带完成与稍后提醒两个就地操作`() {
        helper.ensureChannel()
        helper.notifyReminder(TestData.todo(id = 12L))

        val posted = shadowOf(manager).getNotification(NotificationHelper.notificationIdOf(12L))
        assertThat(posted).isNotNull()
        // 真机上后台拉起界面可能被系统拦掉，通知是唯一的交互面，这两个操作必须存在
        assertThat(posted.actions).hasLength(2)
        assertThat(posted.actions[0].title.toString()).isEqualTo("完成")
        assertThat(posted.actions[1].title.toString()).isEqualTo("10 分钟后再提醒")
        assertThat(posted.actions[0].actionIntent).isNotNull()
        assertThat(posted.actions[1].actionIntent).isNotNull()
    }

    @Test
    fun `取消后通知消失`() {
        helper.ensureChannel()
        helper.notifyReminder(TestData.todo(id = 5L))
        val id = NotificationHelper.notificationIdOf(5L)
        assertThat(shadowOf(manager).getNotification(id)).isNotNull()

        helper.cancel(5L)

        assertThat(shadowOf(manager).getNotification(id)).isNull()
    }

    @Test
    fun `没有通知权限时能正确识别`() {
        // Robolectric 默认授予，这里显式撤销后验证 detect 逻辑
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>())
                .denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        }
        assertThat(helper.canPostNotifications()).isFalse()
    }

    private fun grantNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>())
                .grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
