package com.avalonyin1120.todolist.notify

import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.TimeUnit

/**
 * 响铃服务的生命周期测试。
 *
 * 回归背景：用户报「有时关闭界面没了，闹钟还在继续响」。根因是响铃只由前台服务持有，
 * 而服务与闹钟页之间没有任何绑定关系——页面销毁、返回键、上滑划掉都不会停铃。
 * 现在页面会绑定服务，解绑即停铃，这里把这条契约固定下来。
 *
 * 铃声本身归 [AlarmRinger]，因此用例只需断言它即可；服务侧关注的是
 * 「解绑 → 退出」「超时 → 退出」「停止入口是否把该收的都收了」。
 */
@RunWith(RobolectricTestRunner::class)
class AlarmRingServiceTest {

    @Before
    fun setUp() {
        // 静态状态（是否在响、抑制窗口）会跨用例残留，必须显式复位
        AlarmRinger.resetForTest()
    }

    @After
    fun tearDown() {
        AlarmRinger.resetForTest()
    }

    private fun startIntent(todoId: Long) = Intent().apply {
        putExtra(AlarmRingService.EXTRA_TODO_ID, todoId)
        putExtra(AlarmRingService.EXTRA_TODO_NAME, "小组会议")
    }

    private fun started(): AlarmRingService {
        val service = Robolectric.buildService(AlarmRingService::class.java).create().get()
        service.onStartCommand(startIntent(7L), 0, 1)
        return service
    }

    @Test
    fun `收到启动指令后处于响铃状态`() {
        started()

        assertThat(AlarmRingService.isRinging).isTrue()
    }

    @Test
    fun `销毁服务后停止响铃`() {
        val controller = Robolectric.buildService(AlarmRingService::class.java).create()
        controller.get().onStartCommand(startIntent(7L), 0, 1)
        assertThat(AlarmRingService.isRinging).isTrue()

        controller.destroy()

        assertThat(AlarmRingService.isRinging).isFalse()
    }

    @Test
    fun `闹钟页解绑后停止响铃`() {
        val service = started()
        service.onBind(Intent())
        assertThat(AlarmRingService.isRinging).isTrue()

        // 页面销毁 → 系统解绑 → 宽限期结束 → 必须已经静音
        service.onUnbind(Intent())
        shadowOf(Looper.getMainLooper()).idleFor(2, TimeUnit.SECONDS)

        assertThat(AlarmRingService.isRinging).isFalse()
    }

    @Test
    fun `宽限期内重新绑定则继续响铃`() {
        val service = started()
        service.onBind(Intent())
        service.onUnbind(Intent())
        // 模拟屏幕旋转：旧页面解绑后，新页面马上又绑上来
        shadowOf(Looper.getMainLooper()).idleFor(100, TimeUnit.MILLISECONDS)
        service.onBind(Intent())
        shadowOf(Looper.getMainLooper()).idleFor(2, TimeUnit.SECONDS)

        assertThat(AlarmRingService.isRinging).isTrue()
    }

    @Test
    fun `立刻静音不必等解绑`() {
        started()
        assertThat(AlarmRingService.isRinging).isTrue()

        // 通知里的「完成 / 稍后提醒」走这条路径：广播接收器拿不到绑定句柄，
        // 只能靠同进程的静态入口把铃声掐掉
        AlarmRingService.muteNow()

        assertThat(AlarmRingService.isRinging).isFalse()
    }

    @Test
    fun `同一条待办被重复投递不会重复起铃`() {
        val service = started()
        // 精确通道与 +3 分钟保险通道先后触发时会出现这种重复投递
        service.onStartCommand(startIntent(7L), 0, 1)

        // 旧实现每次都 new 一个 Ringtone 覆盖字段，前一个再也没人 stop，
        // 于是「界面关掉了铃声还在响」。这里断言重复投递后仍是一个可被停掉的状态：
        // 解绑一次就必须彻底静音，不会剩下第二条没人持有的铃声。
        service.onBind(Intent())
        service.onUnbind(Intent())
        shadowOf(Looper.getMainLooper()).idleFor(2, TimeUnit.SECONDS)

        assertThat(AlarmRingService.isRinging).isFalse()
    }

    @Test
    fun `解绑后服务会自行退出`() {
        val service = started()
        service.onBind(Intent())

        service.onUnbind(Intent())
        shadowOf(Looper.getMainLooper()).idleFor(2, TimeUnit.SECONDS)

        assertThat(org.robolectric.Shadows.shadowOf(service).isStoppedBySelf).isTrue()
    }

    @Test
    fun `响铃通知自带停止与完成按钮`() {
        val service = started()

        // 真机上闹钟页有可能拉不起来（厂商限制后台启动界面）。这时通知就是唯一的交互面，
        // 没有按钮就等于「只能听着铃声响满 5 分钟」——这正是「关不掉」最难受的一种。
        val notification = org.robolectric.Shadows.shadowOf(service).lastForegroundNotification
        assertThat(notification).isNotNull()
        val titles = notification!!.actions.map { it.title.toString() }
        assertThat(titles).containsExactly("停止", "完成")
    }

    @Test
    fun `停止全部会立刻静音`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        started()
        assertThat(AlarmRingService.isRinging).isTrue()

        AlarmRingService.stopEverything(context, 7L)

        assertThat(AlarmRingService.isRinging).isFalse()
    }

    @Test
    fun `停止全部不会误停另一条待办的铃声`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        started()
        // 这一瞬间换成另一条待办在响（例如用户刚点开新闹钟的通知，又去点了旧通知的「完成」）
        AlarmRinger.start(context, 99L)

        AlarmRingService.stopEverything(context, 7L)

        // 只撤 7 的通知，不许把 99 的铃声、通知与服务一起收掉
        assertThat(AlarmRinger.isRinging).isTrue()
        assertThat(AlarmRinger.ringingTodoId).isEqualTo(99L)
    }

    @Test
    fun `不传待办 id 时无条件停掉正在响的铃声`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        started()

        // 响铃通知上的「停止」按钮，以及闹钟页退出，都走这条：不关心响的是哪条
        AlarmRingService.stopEverything(context)

        assertThat(AlarmRinger.isRinging).isFalse()
    }

    @Test
    fun `迟到的启动不会重新响铃并会自行退出`() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        started()
        AlarmRingService.stopEverything(context, 7L)
        assertThat(AlarmRingService.isRinging).isFalse()

        // 真机实测：Android 12+ 的「后台启动前台服务」限制会让 onStartCommand 晚到十几秒，
        // 那时用户已经关掉闹钟页了。这次迟到投递必须什么都不做。
        val late = Robolectric.buildService(AlarmRingService::class.java).create().get()
        late.onStartCommand(startIntent(7L), 0, 1)

        assertThat(AlarmRingService.isRinging).isFalse()
        assertThat(org.robolectric.Shadows.shadowOf(late).isStoppedBySelf).isTrue()
    }
}
