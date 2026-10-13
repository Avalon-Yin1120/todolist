package com.avalonyin1120.todolist.notify

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.TimeUnit

/**
 * 铃声持有者的行为契约。
 *
 * 回归背景：用户多次反馈「闹钟有时关不掉」。旧实现里铃声有两个可能的持有者
 * （前台服务一个、闹钟页一个），任何停止入口都必须先判断「现在是谁在响」，
 * 漏掉一个分支就留下一条停不下来的路径。现在铃声只在这里，这些用例把
 * 「谁停谁」「重复投递怎么办」「没人处理时最长响多久」固定下来。
 */
@RunWith(RobolectricTestRunner::class)
class AlarmRingerTest {

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before
    fun setUp() {
        // 静态状态（是否在响、抑制窗口）会跨用例残留，必须显式复位
        AlarmRinger.resetForTest()
    }

    @After
    fun tearDown() {
        AlarmRinger.resetForTest()
    }

    @Test
    fun `开始响铃后状态与待办一致`() {
        AlarmRinger.start(context, 7L)

        assertThat(AlarmRinger.isRinging).isTrue()
        assertThat(AlarmRinger.ringingTodoId).isEqualTo(7L)
    }

    @Test
    fun `换一条待办会替换响铃目标`() {
        AlarmRinger.start(context, 7L)

        AlarmRinger.start(context, 8L)

        // 不能出现「两条铃声同时在响」：换目标就是替换，永远只有一个持有者
        assertThat(AlarmRinger.isRinging).isTrue()
        assertThat(AlarmRinger.ringingTodoId).isEqualTo(8L)
    }

    @Test
    fun `同一条待办被重复投递不打断`() {
        AlarmRinger.start(context, 7L)

        // 精确通道与 +3 分钟保险通道先后触发时会出现这种重复投递；
        // 打断再重放会变成「响一声又从头开始」
        AlarmRinger.start(context, 7L)

        assertThat(AlarmRinger.isRinging).isTrue()
        assertThat(AlarmRinger.ringingTodoId).isEqualTo(7L)
    }

    @Test
    fun `能判断出正在响的是另一条待办`() {
        AlarmRinger.start(context, 7L)
        AlarmRinger.start(context, 8L)

        // 「停止入口」靠它决定要不要连带收拾铃声：用户可能在 8 号闹钟正响时，
        // 回头去点 7 号那条旧通知上的「完成」，这时不能把 8 号一起掐掉
        assertThat(AlarmRinger.isRingingForOtherThan(7L)).isTrue()
        assertThat(AlarmRinger.isRingingForOtherThan(8L)).isFalse()
        // 哨兵值表示「不知道是谁」，一律不算「别人的铃声」
        assertThat(AlarmRinger.isRingingForOtherThan(AlarmRinger.NO_TODO)).isFalse()
    }

    @Test
    fun `没有在响时不会把任何待办当成别人的铃声`() {
        AlarmRinger.stop()

        assertThat(AlarmRinger.isRingingForOtherThan(7L)).isFalse()
    }

    @Test
    fun `刚被结束的待办不会因为迟到的启动又响起来`() {
        AlarmRinger.start(context, 7L)
        AlarmRinger.stop()
        assertThat(AlarmRinger.wasRecentlyStopped(7L)).isTrue()

        // 真机实测：Android 12+ 的后台启动前台服务限制会让 onStartCommand 晚到十几秒，
        // 那时用户早就关掉闹钟页了——不挡掉就会出现「明明关了，过一会儿又响」
        AlarmRinger.start(context, 7L)

        assertThat(AlarmRinger.isRinging).isFalse()
    }

    @Test
    fun `抑制窗口过后可以再次响铃`() {
        AlarmRinger.start(context, 7L)
        AlarmRinger.stop()

        // 真实的下一次提醒至少是「稍后提醒」的 10 分钟，60 秒窗口不会误伤
        shadowOf(Looper.getMainLooper())
            .idleFor(AlarmRinger.SUPPRESS_AFTER_STOP_MS + 1_000L, TimeUnit.MILLISECONDS)

        assertThat(AlarmRinger.wasRecentlyStopped(7L)).isFalse()
        AlarmRinger.start(context, 7L)
        assertThat(AlarmRinger.isRinging).isTrue()
    }

    @Test
    fun `抑制只针对刚结束的那一条待办`() {
        AlarmRinger.start(context, 7L)
        AlarmRinger.stop()

        // 另一条待办的闹钟不受影响
        assertThat(AlarmRinger.wasRecentlyStopped(8L)).isFalse()
        AlarmRinger.start(context, 8L)
        assertThat(AlarmRinger.ringingTodoId).isEqualTo(8L)
    }

    @Test
    fun `响铃超过上限会自动停止`() {
        AlarmRinger.start(context, 7L)
        assertThat(AlarmRinger.isRinging).isTrue()

        // 旧实现里「闹钟页自己补响」这条路径完全没有超时，界面一旦以异常方式消失，
        // 铃声可以一直响下去。现在超时由铃声持有者统一保证。
        shadowOf(Looper.getMainLooper())
            .idleFor(AlarmRinger.MAX_RING_MS + 1_000L, TimeUnit.MILLISECONDS)

        assertThat(AlarmRinger.isRinging).isFalse()
    }

    @Test
    fun `先前的超时兜底不会把下一次闹钟一起停掉`() {
        AlarmRinger.start(context, 7L)
        AlarmRinger.stop()

        // 静置到「抑制窗口」与上一次的超时兜底都早已过去
        shadowOf(Looper.getMainLooper()).idleFor(
            AlarmRinger.MAX_RING_MS + AlarmRinger.SUPPRESS_AFTER_STOP_MS + 1_000L,
            TimeUnit.MILLISECONDS,
        )

        AlarmRinger.start(context, 7L)
        assertThat(AlarmRinger.isRinging).isTrue()

        // 新铃声有自己的超时，不会继承上一次剩下（或已到期）的那个定时任务
        shadowOf(Looper.getMainLooper())
            .idleFor(AlarmRinger.MAX_RING_MS / 2, TimeUnit.MILLISECONDS)
        assertThat(AlarmRinger.isRinging).isTrue()
    }

    @Test
    fun `没有在响时停止是空操作`() {
        AlarmRinger.stop()

        assertThat(AlarmRinger.isRinging).isFalse()
        assertThat(AlarmRinger.ringingTodoId).isEqualTo(AlarmRinger.NO_TODO)
    }
}
