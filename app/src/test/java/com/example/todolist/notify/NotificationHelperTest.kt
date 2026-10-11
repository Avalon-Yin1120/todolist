package com.example.todolist.notify

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.example.todolist.testing.TestData
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
        assertThat(channel.name.toString()).isEqualTo("待办提醒")
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
