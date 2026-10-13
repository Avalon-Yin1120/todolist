package com.avalonyin1120.todolist.ui

import com.avalonyin1120.todolist.testing.TestData
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Test

/**
 * 周视图色块排版算法。
 *
 * 全是纯函数，不依赖 Android 框架，所以直接放在 JVM 单测里钉死：
 * 尤其是「谁和谁重叠」与「泳道数」这两件事，画在屏幕上很难断言。
 */
class WeekLayoutTest {

    /** 固定用周一，2026-05-11。 */
    private val date: LocalDate = LocalDate.of(2026, 5, 11)

    // -------------------------------------------------- 不重叠

    @Test
    fun `不重叠的两条各自独占整列宽`() {
        val todos = listOf(
            TestData.todo(id = 1, startAt = at(2026, 5, 11, 9), endAt = at(2026, 5, 11, 10)),
            TestData.todo(id = 2, startAt = at(2026, 5, 11, 14), endAt = at(2026, 5, 11, 15)),
        )
        val blocks = WeekLayout.blocksOf(todos, date, TestData.ZONE)

        assertThat(blocks).hasSize(2)
        assertThat(blocks.map { it.lane }).containsExactly(0, 0).inOrder()
        assertThat(blocks.map { it.laneCount }).containsExactly(1, 1).inOrder()
    }

    @Test
    fun `首尾相接算不重叠`() {
        // 9-10 与 10-11 只是挨着，不该为对方让出半列宽
        val todos = listOf(
            TestData.todo(id = 1, startAt = at(2026, 5, 11, 9), endAt = at(2026, 5, 11, 10)),
            TestData.todo(id = 2, startAt = at(2026, 5, 11, 10), endAt = at(2026, 5, 11, 11)),
        )
        val blocks = WeekLayout.blocksOf(todos, date, TestData.ZONE)

        assertThat(blocks.map { it.laneCount }).containsExactly(1, 1).inOrder()
    }

    // -------------------------------------------------- 重叠

    @Test
    fun `完全重叠的两条分成两条泳道`() {
        val todos = listOf(
            TestData.todo(id = 1, startAt = at(2026, 5, 11, 9), endAt = at(2026, 5, 11, 10)),
            TestData.todo(id = 2, startAt = at(2026, 5, 11, 9), endAt = at(2026, 5, 11, 10)),
        )
        val blocks = WeekLayout.blocksOf(todos, date, TestData.ZONE)

        assertThat(blocks).hasSize(2)
        assertThat(blocks.map { it.todo.id }).containsExactly(1L, 2L).inOrder()
        assertThat(blocks.map { it.lane }).containsExactly(0, 1).inOrder()
        assertThat(blocks.map { it.laneCount }).containsExactly(2, 2).inOrder()
    }

    @Test
    fun `部分重叠的两条分成两条泳道`() {
        // A 10:00-12:00、B 11:00-13:00
        val todos = listOf(
            TestData.todo(id = 1, startAt = at(2026, 5, 11, 10), endAt = at(2026, 5, 11, 12)),
            TestData.todo(id = 2, startAt = at(2026, 5, 11, 11), endAt = at(2026, 5, 11, 13)),
        )
        val blocks = WeekLayout.blocksOf(todos, date, TestData.ZONE)

        assertThat(blocks.map { it.laneCount }).containsExactly(2, 2).inOrder()
        assertThat(blocks.map { it.lane }).containsExactly(0, 1).inOrder()
        assertNoSameLaneOverlap(blocks)
    }

    @Test
    fun `链式重叠的三条同属一簇且重新用回第一条泳道`() {
        // A 10-12、B 11-13、C 12-14：A 与 C 并不重叠，但都要算进同一个簇，
        // 否则 C 会以为自己独占整列宽、结果压在 A 上
        val todos = listOf(
            TestData.todo(id = 1, startAt = at(2026, 5, 11, 10), endAt = at(2026, 5, 11, 12)),
            TestData.todo(id = 2, startAt = at(2026, 5, 11, 11), endAt = at(2026, 5, 11, 13)),
            TestData.todo(id = 3, startAt = at(2026, 5, 11, 12), endAt = at(2026, 5, 11, 14)),
        )
        val blocks = WeekLayout.blocksOf(todos, date, TestData.ZONE)

        assertThat(blocks).hasSize(3)
        // 簇内泳道数取最大值：三者都是 2，而不是各算各的
        assertThat(blocks.map { it.laneCount }).containsExactly(2, 2, 2).inOrder()
        // C 与 A 首尾相接，可以复用第 0 条泳道
        assertThat(blocks.map { it.lane }).containsExactly(0, 1, 0).inOrder()
        assertNoSameLaneOverlap(blocks)
    }

    @Test
    fun `隔开一段时间的重叠簇各算各的泳道数`() {
        // 上午一簇两条、下午一簇两条，互不影响
        val todos = listOf(
            TestData.todo(id = 1, startAt = at(2026, 5, 11, 9), endAt = at(2026, 5, 11, 11)),
            TestData.todo(id = 2, startAt = at(2026, 5, 11, 10), endAt = at(2026, 5, 11, 12)),
            TestData.todo(id = 3, startAt = at(2026, 5, 11, 15), endAt = at(2026, 5, 11, 16)),
            TestData.todo(id = 4, startAt = at(2026, 5, 11, 15), endAt = at(2026, 5, 11, 17)),
        )
        val blocks = WeekLayout.blocksOf(todos, date, TestData.ZONE)

        assertThat(blocks.map { it.laneCount }).containsExactly(2, 2, 2, 2).inOrder()
        assertNoSameLaneOverlap(blocks)
    }

    // -------------------------------------------------- 当天边界
    //
    // 这一组刻意用「整天窗口」：默认窗口是 06:00~22:00（可在设置里改），
    // 而它们要验证的是「跨午夜怎么裁」，用默认窗口会把整条裁没，验不到东西。

    @Test
    fun `跨过午夜的待办在第二天从零点开始画`() {
        // 前一天 23:00 到当天 01:00
        val todo = TestData.todo(
            id = 1,
            startAt = at(2026, 5, 10, 23),
            endAt = at(2026, 5, 11, 1),
        )
        val blocks = fullDay(listOf(todo), date)

        assertThat(blocks).hasSize(1)
        assertThat(blocks[0].startHour).isWithin(1e-6).of(0.0)
        assertThat(blocks[0].endHour).isWithin(1e-6).of(1.0)
        // 前一天那一列只画到午夜为止
        val previous = fullDay(listOf(todo), date.minusDays(1))
        assertThat(previous).hasSize(1)
        assertThat(previous[0].startHour).isWithin(1e-6).of(23.0)
        assertThat(previous[0].endHour).isWithin(1e-6).of(24.0)
    }

    @Test
    fun `跨过午夜到次日中午的待办在第二天画到十二点`() {
        val todo = TestData.todo(
            id = 1,
            startAt = at(2026, 5, 10, 23),
            endAt = at(2026, 5, 11, 12),
        )
        val blocks = fullDay(listOf(todo), date)

        assertThat(blocks).hasSize(1)
        assertThat(blocks[0].startHour).isWithin(1e-6).of(0.0)
        assertThat(blocks[0].endHour).isWithin(1e-6).of(12.0)
    }

    @Test
    fun `整天的事占满零到二十四`() {
        val todo = TestData.todo(
            id = 1,
            startAt = at(2026, 5, 11, 0),
            endAt = at(2026, 5, 12, 0),
        )
        val blocks = fullDay(listOf(todo), date)

        assertThat(blocks).hasSize(1)
        assertThat(blocks[0].startHour).isWithin(1e-6).of(0.0)
        assertThat(blocks[0].endHour).isWithin(1e-6).of(24.0)
    }

    @Test
    fun `从前一天延续过来的事只画到当天结束`() {
        // 5/9 20:00 一直到 5/11 23:00：当天这一段是 00:00-23:00
        val todo = TestData.todo(
            id = 1,
            startAt = at(2026, 5, 9, 20),
            endAt = at(2026, 5, 11, 23),
        )
        val blocks = fullDay(listOf(todo), date)

        assertThat(blocks).hasSize(1)
        assertThat(blocks[0].startHour).isWithin(1e-6).of(0.0)
        assertThat(blocks[0].endHour).isWithin(1e-6).of(23.0)
    }

    // -------------------------------------------------- 可见时间窗（默认 06:00~22:00）

    @Test
    fun `默认窗口把凌晨那段裁掉`() {
        // 05:00-08:00，默认窗口从 6 点开始 → 只画 06:00-08:00
        val todo = TestData.todo(
            id = 1,
            startAt = at(2026, 5, 11, 5),
            endAt = at(2026, 5, 11, 8),
        )
        val blocks = WeekLayout.blocksOf(listOf(todo), date, TestData.ZONE)

        assertThat(blocks).hasSize(1)
        assertThat(blocks[0].startHour).isWithin(1e-6).of(6.0)
        assertThat(blocks[0].endHour).isWithin(1e-6).of(8.0)
    }

    @Test
    fun `默认窗口把深夜那段裁掉`() {
        // 21:00-23:00，默认窗口到 22 点结束 → 只画 21:00-22:00
        val todo = TestData.todo(
            id = 1,
            startAt = at(2026, 5, 11, 21),
            endAt = at(2026, 5, 11, 23),
        )
        val blocks = WeekLayout.blocksOf(listOf(todo), date, TestData.ZONE)

        assertThat(blocks).hasSize(1)
        assertThat(blocks[0].startHour).isWithin(1e-6).of(21.0)
        assertThat(blocks[0].endHour).isWithin(1e-6).of(22.0)
    }

    @Test
    fun `整条都在窗口外的待办直接不画`() {
        val todos = listOf(
            TestData.todo(id = 1, startAt = at(2026, 5, 11, 1), endAt = at(2026, 5, 11, 3)),
            TestData.todo(id = 2, startAt = at(2026, 5, 11, 22), endAt = at(2026, 5, 11, 23)),
        )
        assertThat(WeekLayout.blocksOf(todos, date, TestData.ZONE)).isEmpty()
    }

    @Test
    fun `窗口外的那条不参与分泳道`() {
        // 04:00-05:00 整条在窗口外，09:00-10:00 在窗口内。
        // 若先分簇再裁剪，两者会被算成同一个重叠簇，09:00 那条就白白变成半宽。
        val todos = listOf(
            TestData.todo(id = 1, startAt = at(2026, 5, 11, 4), endAt = at(2026, 5, 11, 5)),
            TestData.todo(id = 2, startAt = at(2026, 5, 11, 9), endAt = at(2026, 5, 11, 10)),
        )
        val blocks = WeekLayout.blocksOf(todos, date, TestData.ZONE)

        assertThat(blocks).hasSize(1)
        assertThat(blocks[0].todo.id).isEqualTo(2L)
        assertThat(blocks[0].laneCount).isEqualTo(1)
    }

    @Test
    fun `窗口可以自定义`() {
        val todo = TestData.todo(
            id = 1,
            startAt = at(2026, 5, 11, 8),
            endAt = at(2026, 5, 11, 20),
        )
        val blocks = WeekLayout.blocksOf(
            todos = listOf(todo),
            date = date,
            zone = TestData.ZONE,
            windowStartHour = 8,
            windowEndHour = 18,
        )

        assertThat(blocks).hasSize(1)
        // 起止都被窗口收进来：色块高度就是窗口宽度，不再按真实时长溢出
        assertThat(blocks[0].startHour).isWithin(1e-6).of(8.0)
        assertThat(blocks[0].endHour).isWithin(1e-6).of(18.0)
    }

    @Test
    fun `窗口参数非法时按最小间隔兜底`() {
        val todo = TestData.todo(
            id = 1,
            startAt = at(2026, 5, 11, 10),
            endAt = at(2026, 5, 11, 11),
        )
        // start == end 会被夹成「至少 2 小时」的窗口，而不是画出零高度或直接崩
        val blocks = WeekLayout.blocksOf(
            todos = listOf(todo),
            date = date,
            zone = TestData.ZONE,
            windowStartHour = 10,
            windowEndHour = 10,
        )

        assertThat(blocks).hasSize(1)
        // start==end 被夹成 08:00~12:00（至少 2 小时），所以这条 10:00-11:00 完整落在窗口里
        assertThat(blocks[0].startHour).isWithin(1e-6).of(10.0)
        assertThat(blocks[0].endHour).isWithin(1e-6).of(11.0)
    }

    // -------------------------------------------------- 非法/退化数据

    @Test
    fun `零时长与结束早于开始的待办被丢弃`() {
        val todos = listOf(
            // 起止相同
            TestData.todo(id = 1, startAt = at(2026, 5, 11, 9), endAt = at(2026, 5, 11, 9)),
            // 结束早于开始
            TestData.todo(id = 2, startAt = at(2026, 5, 11, 15), endAt = at(2026, 5, 11, 14)),
        )
        assertThat(WeekLayout.blocksOf(todos, date, TestData.ZONE)).isEmpty()
    }

    @Test
    fun `不属于这一天的待办不出现`() {
        val todos = listOf(
            TestData.todo(id = 1, startAt = at(2026, 5, 13, 9), endAt = at(2026, 5, 13, 10)),
        )
        assertThat(WeekLayout.blocksOf(todos, date, TestData.ZONE)).isEmpty()
    }

    @Test
    fun `没有待办时返回空列表`() {
        assertThat(WeekLayout.blocksOf(emptyList(), date, TestData.ZONE)).isEmpty()
    }

    // -------------------------------------------------- 排序

    @Test
    fun `结果按开始时间升序`() {
        // 故意打乱输入顺序
        val todos = listOf(
            TestData.todo(id = 3, startAt = at(2026, 5, 11, 18), endAt = at(2026, 5, 11, 19)),
            TestData.todo(id = 1, startAt = at(2026, 5, 11, 8), endAt = at(2026, 5, 11, 9)),
            TestData.todo(id = 4, startAt = at(2026, 5, 11, 21), endAt = at(2026, 5, 11, 22)),
            TestData.todo(id = 2, startAt = at(2026, 5, 11, 12), endAt = at(2026, 5, 11, 13)),
        )
        val blocks = WeekLayout.blocksOf(todos, date, TestData.ZONE)

        assertThat(blocks.map { it.startHour })
            .containsExactly(8.0, 12.0, 18.0, 21.0).inOrder()
        blocks.zipWithNext { a, b -> assertThat(b.startHour).isAtLeast(a.startHour) }
    }

    // -------------------------------------------------- 通用

    /**
     * 「整天窗口」（00:00~24:00）下的排版结果。
     *
     * 默认窗口是 06:00~22:00，验证「跨午夜怎么裁」这类用例必须显式要一整天，
     * 否则整条会被窗口裁没，断言就变成在验别的东西了。
     */
    private fun fullDay(todos: List<com.avalonyin1120.todolist.domain.Todo>, day: LocalDate) =
        WeekLayout.blocksOf(
            todos = todos,
            date = day,
            zone = TestData.ZONE,
            windowStartHour = 0,
            windowEndHour = 24,
        )

    /** 同一泳道里的两块在时间上不允许相交，否则说明泳道分错了。 */
    private fun assertNoSameLaneOverlap(blocks: List<WeekEventBlock>) {
        blocks.forEachIndexed { index, a ->
            blocks.drop(index + 1).forEach { b ->
                if (a.lane == b.lane) {
                    assertThat(a.endHour <= b.startHour || b.endHour <= a.startHour).isTrue()
                }
            }
        }
    }

    private fun at(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0): Long =
        LocalDate.of(year, month, day).atTime(hour, minute)
            .atZone(TestData.ZONE).toInstant().toEpochMilli()
}
