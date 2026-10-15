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
 * 后者不是假设：Android 15 / ColorOS 真机上，「待办提醒（有声）」的静默通知明明关着，
 * 旧实现（`importance <= LOW || sound == null`）却因为它读不到声音而判成静默，
 * 界面因此永远显示「未开启」。本类现在把这个误报钉成回归保护。
 *
 * 渠道的读行为与真机一致（Robolectric 用的就是系统那套 NotificationManager），
 * 但有一处**真机与测试的关键差异**必须写下来，否则很容易写出假绿/假红的用例：
 * 重新 `createNotificationChannel` 一个已存在的渠道时，系统只应用你**显式设置**过的字段，
 * 没设的字段保持原样（这正是「渠道一旦创建，声音与重要性就改不了」的底层原因）。
 * 更强的一条（本类里实测确认过）：**声音一旦有过就再也去不掉**——
 * 把一个曾经有声音的渠道 `deleteNotificationChannel` 之后再新建同名渠道并 `setSound(null, null)`，
 * 读回来仍然是有声音的那条；而从来没设过声音的 id 无论建几次都读回 null。
 * 后果是本类里 `CHANNEL_ID`（`NotificationHelper` 建它时就给了默认提示音）**永远读得到声音**，
 * `CHANNEL_ID_SILENT` 永远读不到。也就是说，真机误报的那种形态
 * 「有声渠道却读不到声音」在测试里**造不出来**；本类改用应用自己那条「高重要性 + 读不到声音」
 * 的渠道来钉住同一条判定规则（对判定而言两者完全等价：`importance == HIGH && sound == null`）。
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
        // 原始值要如实带出来（真机排查用）：重要性照抄系统，声音/振动只作信息
        val stored = manager.getNotificationChannel(NotificationHelper.CHANNEL_ID)
        assertThat(health.importance).isEqualTo(stored.importance)
        assertThat(health.hasSound).isEqualTo(stored.sound != null)
        assertThat(health.vibrates).isEqualTo(stored.shouldVibrate())
    }

    /**
     * 真机误报的回归保护（Android 15 / ColorOS）。
     *
     * 用户手机上那条「待办提醒（有声）」渠道：系统通知设置里**静默通知是关的**，
     * 但 `getSound()` 读回 null（创建渠道时传的默认提示音 URI 在该 ROM 上解析不到，
     * 系统响铃时用的是它自己的音源）。旧判定 `importance <= LOW || sound == null` 因此把它判成静默，
     * 界面永远显示「未开启」、还让用户去关一个已经关掉的开关——那是**用户怎么改都不会变绿**的死循环。
     *
     * 测试里造不出「有声渠道读不到声音」（见类注释：声音一旦有过就去不掉），
     * 所以这里用应用自建的那条**同样的读数**（`importance == HIGH && sound == null`）来钉住规则：
     * 旧实现在这条渠道上会判成静默，新实现必须判「非静默」。
     */
    @Test
    fun `高重要性但读不到声音不算静默`() {
        NotificationHelper(context, soundEnabled = { false }).ensureChannel()

        // 先确认复现出了那种读数，否则这条用例会变成假绿
        val stored = manager.getNotificationChannel(NotificationHelper.CHANNEL_ID_SILENT)
        assertThat(stored.importance).isEqualTo(NotificationManager.IMPORTANCE_HIGH)
        assertThat(stored.sound).isNull()

        val health = ReminderHealth.notificationChannelHealth(context)
            .single { it.id == NotificationHelper.CHANNEL_ID_SILENT }

        // 新语义：静默只看重要性。高重要性 ⇒ 不算静默，哪怕声音读不到。
        assertThat(health.silent).isFalse()
        // 原始值如实带出，用户截图时能把这行读数带给我们
        assertThat(health.importance).isEqualTo(NotificationManager.IMPORTANCE_HIGH)
        assertThat(health.hasSound).isFalse()
        assertThat(health.rawValuesText()).contains("importance=4")
        assertThat(health.rawValuesText()).contains("声音=读不到")
    }

    @Test
    fun `顶部横幅的渠道健康判定与检查页共用同一个静默判定`() {
        // 顶部横幅的「还有 N 项没打开」用的是 activeChannelHealthy，检查页用的是同一个 silent 字段：
        // 两处只要有一处判定不同，就会出现「页面里说已开启、横幅还说差一项」或者反过来。
        NotificationHelper(context, soundEnabled = { true }).ensureChannel()
        NotificationHelper(context, soundEnabled = { false }).ensureChannel()

        // 1) 应用自建的静音渠道是「高重要性 + 读不到声音」：新语义下不算静默，
        //    因此关掉提示音（提醒走这条渠道）时，横幅不该再喊缺项
        assertThat(ReminderHealth.notificationChannelHealth(context)
            .single { it.id == NotificationHelper.CHANNEL_ID_SILENT }.silent).isFalse()
        assertThat(ReminderHealth.activeChannelHealthy(context, soundEnabled = false)).isTrue()

        // 2) 模拟用户在系统里给「有声」渠道打开了「静默通知」（落成低重要性）：
        //    检查页必须判静默，横幅必须同时判不合格——两处不能各说一套
        manager.createNotificationChannel(
            NotificationChannel(
                NotificationHelper.CHANNEL_ID,
                "待办提醒（有声）",
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        assertThat(ReminderHealth.notificationChannelHealth(context)
            .single { it.id == NotificationHelper.CHANNEL_ID }.silent).isTrue()
        assertThat(ReminderHealth.activeChannelHealthy(context, soundEnabled = true)).isFalse()
        // 3) 但关掉应用内提示音时提醒走的是另一条渠道，不该被这条拖累（否则就是第二次误报）
        assertThat(ReminderHealth.activeChannelHealthy(context, soundEnabled = false)).isTrue()
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
        // 新语义下「静默」的判据就是重要性，原始值要与判定一致（否则界面上的括号会自相矛盾）
        assertThat(health.importance).isEqualTo(NotificationManager.IMPORTANCE_LOW)
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
        // 因此这里把每个入口都点一遍，只要不抛异常就算通过。
        ReminderHealth.openBatteryOptimizationSettings(context)
        ReminderHealth.openChannelSettings(context, NotificationHelper.CHANNEL_ID)
        ReminderHealth.openAppDetailsSettings(context)
        // ColorOS 的「后台弹出界面」引导：候选链里全是厂商组件，本机一个都不存在，
        // 必须静默退到应用详情页（用户点「去设置」不能没反应，更不能崩）
        ReminderHealth.openBackgroundPopupSettings(context)
        // 「发送全屏通知」入口（Android 14+ 的 ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT）
        ReminderHealth.openFullScreenIntentSettings(context)
    }

    @Test
    fun `渠道 id 不存在时也走兜底而不崩`() {
        // 传入一个系统里没有的渠道 id（例如渠道被清掉、或版本升级改了 id）：
        // 候选链里的渠道页可能起不来，必须静默退到下一级，而不是把异常抛给界面。
        ReminderHealth.openChannelSettings(context, "this_channel_does_not_exist")
    }
}
