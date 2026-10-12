package com.example.todolist.notify

import android.content.Intent
import android.os.Looper
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
 */
@RunWith(RobolectricTestRunner::class)
class AlarmRingServiceTest {

    @Before
    fun setUp() {
        AlarmRingService.isRinging = false
    }

    @After
    fun tearDown() {
        AlarmRingService.isRinging = false
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
}
