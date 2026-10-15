package com.avalonyin1120.todolist.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * 「已确认项」（[SettingsStore.confirmedHealthItems]）的读写测试。
 *
 * 为什么值得单独测：「提醒可靠性检查」页有一半开关系统读不到真实状态，那一页的计数与顶部提示
 * 完全依赖这份集合（**用户确认优先于系统读数**）。它一旦读不出来或写不下去，用户就回到老样子——
 * 照着路径改完、勾了「我已设置好」，回到页面还是「还有 N 项没设置好」；而渠道一旦创建，
 * 应用自己改不动它的静默/横幅/锁屏，那句「还有 N 项」就永远消不掉（本次要消灭的正是这种死循环）。
 *
 * 两条持久化契约在这里钉住：
 * * **键名不能改**——改了等于把装过旧版本的用户的勾选全部清空；
 * * **缺键时必须读到空集**——从没有这个键的版本升级上来，界面行为要与升级前一致。
 *
 * 与 [SettingsStoreTest] 分开成两个类，是因为那边的重点是「默认提醒方式」的迁移逻辑，
 * 两批用例各自清空一次 SharedPreferences，互不依赖执行顺序。
 */
@RunWith(RobolectricTestRunner::class)
class ConfirmedHealthItemsTest {

    private lateinit var context: Context

    /**
     * 与 `SharedPreferencesSettingsStore` 里的 PREFS_NAME / KEY 必须一致。
     *
     * 那边两个常量都是 private，这里只能重写一份。名字对不上时，「清空设置」会默默清到
     * 另一个文件上，测试结果就取决于执行顺序——所以每个用例都先清空再断言，
     * 而且这里要断言的正是「键名」与「读出来是拷贝」这两件事本身：故意写死。
     */
    private val prefsName = "todolist_settings"
    private val keyConfirmedHealthItems = "confirmed_health_items"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Robolectric 在同一个测试 JVM 里共享 SharedPreferences：上一条用例写下的值会留到这一条
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun prefs() = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    private fun store() = SharedPreferencesSettingsStore(context)

    @Test
    fun `老用户没有这个键时读出来是空集`() {
        // 向后兼容：升级上来的用户 SharedPreferences 里根本没有这个键，读出来必须是
        // 「什么都没确认」——不能抛异常，更不能凭空塞一个默认勾选项进去：
        // 那会让页面显示「都设置好了」，而用户系统里的开关其实一个都没开。
        assertThat(store().confirmedHealthItems).isEmpty()
        assertThat(prefs().contains(keyConfirmedHealthItems)).isFalse()
    }

    @Test
    fun `确认项写下去换个实例还读得到`() {
        store().confirmedHealthItems = setOf("lockscreen_banner", "battery")

        // 换一个实例读：必须真的落盘。用户勾完往往就按「去设置」跳走了，本页随时可能被销毁，
        // 只留在内存里等于白勾，回来还是「还有 N 项」。
        assertThat(store().confirmedHealthItems)
            .containsExactly("lockscreen_banner", "battery")
        assertThat(prefs().getStringSet(keyConfirmedHealthItems, null))
            .containsExactly("lockscreen_banner", "battery")

        // 取消勾选同样要落盘（用户发现自己其实没设置完时会取消）
        store().confirmedHealthItems = setOf("battery")
        assertThat(store().confirmedHealthItems).containsExactly("battery")
    }

    @Test
    fun `读出来的是另一份集合`() {
        store().confirmedHealthItems = setOf("lockscreen_banner", "battery")

        // getStringSet 交回来的是 SharedPreferences **内部持有**的那个实例：调用方一旦就地改它，
        // 改动既绕过 commit/apply（不落盘），又污染内存里的值。契约是「getter 交出一份拷贝」，
        // 所以这里直接拿内部那一份来比「不是同一个实例」。
        val raw = prefs().getStringSet(keyConfirmedHealthItems, null)

        assertThat(raw).containsExactly("lockscreen_banner", "battery")
        assertThat(store().confirmedHealthItems).isNotSameInstanceAs(raw)
    }

    @Test
    fun `内存实现默认什么都没确认`() {
        // 测试与预览用的内存实现，默认值必须和生产实现一致（空集）：
        // 否则同一个界面在两处会算出两个不同的「还有 N 项」。
        assertThat(InMemorySettingsStore().confirmedHealthItems).isEmpty()
        assertThat(InMemorySettingsStore(confirmedHealthItems = setOf("battery")).confirmedHealthItems)
            .containsExactly("battery")
    }
}
