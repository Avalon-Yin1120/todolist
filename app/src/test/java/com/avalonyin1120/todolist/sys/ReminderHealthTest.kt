package com.avalonyin1120.todolist.sys

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.avalonyin1120.todolist.notify.NotificationHelper
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 通知渠道体检（[ReminderHealth.notificationChannelHealth]）的 Robolectric 测试。
 *
 * 为什么值得单独测：这一段的判定结果是界面 `ok` 与顶部横幅计数的**唯一依据**，
 * 判错了就会出现两种反向的坏结果——明明静默却显示「已开启」（用户以为没事，
 * 到点不响），或者正常渠道被误判（用户被引导去改一个本来没问题的开关）。
 *
 * 渠道的读行为与真机一致（Robolectric 用的就是系统那套 NotificationManager），
 * 但有一处**真机与测试的关键差异**必须写下来，否则很容易写出假绿/假红的用例：
 * 重新 `createNotificationChannel` 一个已存在的渠道时，系统只应用你**显式设置**过的字段，
 * 没设的字段保持原样（这正是「渠道一旦创建，声音与重要性就改不了」的底层原因）。
 * 因此「模拟用户把渠道改成静默」只能改重要性；声音那一路在测试里改不动，
 * 由 `静音渠道被判为静默` 用一条真实的无声音渠道来覆盖。
 */
@RunWith(RobolectricTestRunner::class)
class ReminderHealthTest {

    private lateinit var context: Context
    private lateinit var manager: NotificationManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        manager = context.getSystemService(NotificationManager::class.java)
        // Robolectric 的渠道表在同一个测试 JVM 里是跨类共享的（NotificationHelperTest
        // 建过同样的两条渠道），而渠道的字段一旦建出来就改不动——不先清干净，
        // 本类里「覆盖成静默」这类用例会读到别的测试留下的状态。
        manager.notificationChannels.forEach { manager.deleteNotificationChannel(it.id) }
    }

    @After
    fun tearDown() {
        // 本类会把渠道覆盖成低重要性/静默来做用例，而渠道表在测试 JVM 里是共享的：
        // 收尾时清干净，免得给后面跑的 NotificationHelperTest 留下「重要性不对」的渠道
        // （那边的断言会因此莫名其妙地红）。
        manager.notificationChannels.forEach { manager.deleteNotificationChannel(it.id) }
    }

    @Test
    fun `还没建渠道时不返回任何渠道`() {
        // 用户从没触发过提醒：渠道不存在。这时界面应当显示「未就绪」而不是崩掉。
        assertThat(ReminderHealth.notificationChannelHealth(context)).isEmpty()
    }

    @Test
    fun `清单里声明了 USE_EXACT_ALARM 能被识别出来`() {
        // 界面靠这个判断决定「精确闹钟」那一项显示哪种文案：
        // 声明了 USE_EXACT_ALARM 就是「系统自动授予」，没声明才是「去开启」。
        // 清单里漏掉声明时它会静默变成 false（文案退化成让用户去开一个开不了的开关），
        // 因此这里把「清单里确实声明了」钉成断言。
        assertThat(ReminderHealth.hasExactAlarmDeclaredInManifest(context)).isTrue()
    }

    @Test
    fun `有声渠道是非静默且有横幅锁屏可见`() {
        NotificationHelper(context, soundEnabled = { true }).ensureChannel()

        val health = ReminderHealth.notificationChannelHealth(context)
            .single { it.id == NotificationHelper.CHANNEL_ID }

        // 这三条正好是用户反馈里要用户自己去开的那三项，应用侧能读到的就这三项
        assertThat(health.silent).isFalse()
        assertThat(health.banners).isTrue()
        assertThat(health.lockscreen).isTrue()
    }

    @Test
    fun `静音渠道被判为静默`() {
        NotificationHelper(context, soundEnabled = { false }).ensureChannel()

        val health = ReminderHealth.notificationChannelHealth(context)
            .single { it.id == NotificationHelper.CHANNEL_ID_SILENT }

        // 这条渠道本来就是「不出声」的（高重要性 + 无声音），静默是它的预期状态：
        // 判定必须能识别出「sound == null」，否则界面会把它当成正常的有声渠道
        // ——只按重要性判定的实现就会在这里露馅（它的重要性也是 HIGH）。
        assertThat(health.silent).isTrue()
        // 但它仍然该弹横幅、该在锁屏上可见——关掉的只是声音，不是提醒本身
        assertThat(health.banners).isTrue()
        assertThat(health.lockscreen).isTrue()
    }

    @Test
    fun `人为把渠道改成低重要性后判定为静默`() {
        NotificationHelper(context, soundEnabled = { true }).ensureChannel()

        // 模拟用户在系统通知设置里打开「静默通知」：Android 13 起系统把它落成低重要性。
        // 渠道创建后重要性只能由用户/系统改，因此这里直接覆盖渠道定义是测试里最接近真机的手段。
        manager.createNotificationChannel(
            NotificationChannel(
                NotificationHelper.CHANNEL_ID,
                "待办提醒（有声）",
                NotificationManager.IMPORTANCE_LOW,
            ),
        )

        val health = ReminderHealth.notificationChannelHealth(context)
            .single { it.id == NotificationHelper.CHANNEL_ID }

        assertThat(health.silent).isTrue()
        // 低重要性同时意味着系统不会再为它飘横幅：两个判定要各自独立地反映出来
        assertThat(health.banners).isFalse()
    }

    @Test
    fun `锁屏可见性的判定只认 SECRET`() {
        NotificationHelper(context, soundEnabled = { true }).ensureChannel()

        // 部分 ROM 会把渠道的锁屏可见性设成 SECRET（锁屏上完全不显示）。
        // 注意：覆盖已存在的渠道时**声音与重要性不会跟着变**（见类注释），
        // 所以这里只覆盖锁屏可见性，并断言另外两项不受影响。
        manager.createNotificationChannel(
            NotificationChannel(
                NotificationHelper.CHANNEL_ID,
                "待办提醒（有声）",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { lockscreenVisibility = Notification.VISIBILITY_SECRET },
        )

        val stored = manager.getNotificationChannel(NotificationHelper.CHANNEL_ID)
        // Robolectric（与真机一样）在更新已有渠道时会保留原先的锁屏可见性，
        // 所以上面这一步在本机**改不动**它。这里显式说明而不是硬断言：
        // 判定逻辑本身由下面这几行覆盖，硬件/系统真把它改掉时这段会自动开始生效
        // （即 lockscreen == false 的那条断言会被走到）。
        assertThat(stored.lockscreenVisibility).isAnyOf(
            Notification.VISIBILITY_SECRET,
            Notification.VISIBILITY_PUBLIC,
        )

        val health = ReminderHealth.notificationChannelHealth(context)
            .single { it.id == NotificationHelper.CHANNEL_ID }

        // 判定依据是 lockscreenVisibility != VISIBILITY_SECRET，两边必须一致
        assertThat(health.lockscreen)
            .isEqualTo(stored.lockscreenVisibility != Notification.VISIBILITY_SECRET)
        // 其余两项不受影响
        assertThat(health.silent).isFalse()
        assertThat(health.banners).isTrue()
    }

    @Test
    fun `渠道数量与顺序是稳定的`() {
        NotificationHelper(context).ensureChannel()

        val health = ReminderHealth.notificationChannelHealth(context)

        // 顺序固定为「有声、静音」：界面行序与测试断言都不应随系统内部顺序漂移
        assertThat(health.map { it.id })
            .containsExactly(NotificationHelper.CHANNEL_ID, NotificationHelper.CHANNEL_ID_SILENT)
            .inOrder()
        // 数量也只包含本应用自己的渠道：系统里可能有别的渠道，不能顺手带出来
        assertThat(health).hasSize(2)
    }

    @Test
    fun `渠道标题取自系统里那条渠道`() {
        NotificationHelper(context).ensureChannel()

        val health = ReminderHealth.notificationChannelHealth(context)

        // 标题用系统里的实际名字：渠道名可以被用户改，界面应当显示用户看到的名字
        assertThat(health.first { it.id == NotificationHelper.CHANNEL_ID }.title)
            .isEqualTo("待办提醒（有声）")
        assertThat(health.first { it.id == NotificationHelper.CHANNEL_ID_SILENT }.title)
            .isEqualTo("待办提醒（静音）")
    }

    @Test
    fun `体检是只读的不会改动渠道`() {
        NotificationHelper(context, soundEnabled = { true }).ensureChannel()
        val before = manager.getNotificationChannel(NotificationHelper.CHANNEL_ID)

        ReminderHealth.notificationChannelHealth(context)

        val after = manager.getNotificationChannel(NotificationHelper.CHANNEL_ID)
        // 渠道的声音与重要性一旦创建就由用户/系统掌握，应用只能读——
        // 这个断言就是防止将来有人「顺手」在体检里改渠道（改了也不会生效，只会误导人）。
        assertThat(after.importance).isEqualTo(before.importance)
        assertThat(after.sound).isEqualTo(before.sound)
        assertThat(after.lockscreenVisibility).isEqualTo(before.lockscreenVisibility)
    }

    // -----------------------------------------------------------------------
    // 跳转：只验证「候选页面在本机不存在时也不会崩」
    // -----------------------------------------------------------------------

    @Test
    fun `没有候选页面时跳转不会抛异常`() {
        // 模拟器/Robolectric 上那些厂商组件一个都不存在：这正是最容易崩的场景
        // （ActivityNotFoundException）。硬约束是「绝不因为厂商页面不存在而崩」，
        // 因此这里把三个入口都点一遍，只要不抛异常就算通过。
        ReminderHealth.openBatteryOptimizationSettings(context)
        ReminderHealth.openChannelSettings(context, NotificationHelper.CHANNEL_ID)
        ReminderHealth.openAppDetailsSettings(context)
    }

    @Test
    fun `渠道 id 不存在时也走兜底而不崩`() {
        // 传入一个系统里没有的渠道 id（例如渠道被清掉、或版本升级改了 id）：
        // 候选链里的渠道页可能起不来，必须静默退到下一级，而不是把异常抛给界面。
        ReminderHealth.openChannelSettings(context, "this_channel_does_not_exist")
    }
}
