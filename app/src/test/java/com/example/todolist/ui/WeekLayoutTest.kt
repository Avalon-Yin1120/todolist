package com.example.todolist.ui

import com.example.todolist.testing.TestData
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

    @Test
    fun `跨过午夜的待办在第二天从零点开始画`() {
        // 前一天 23:00 到当天 01:00
        val todo = TestData.todo(
            id = 1,
            startAt = at(2026, 5, 10, 23),
            endAt = at(2026, 5, 11, 1),
        )
        val blocks = WeekLayout.blocksOf(listOf(todo), date, TestData.ZONE)

        assertThat(blocks).hasSize(1)
        assertThat(blocks[0].startHour).isWithin(1e-6).of(0.0)
        assertThat(blocks[0].endHour).isWithin(1e-6).of(1.0)
        // 前一天那一列只画到午夜为止
        val previous = WeekLayout.blocksOf(listOf(todo), date.minusDays(1), TestData.ZONE)
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
        val blocks = WeekLayout.blocksOf(listOf(todo), date, TestData.ZONE)

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
        val blocks = WeekLayout.blocksOf(listOf(todo), date, TestData.ZONE)

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
        val blocks = WeekLayout.blocksOf(listOf(todo), date, TestData.ZONE)

        assertThat(blocks).hasSize(1)
        assertThat(blocks[0].startHour).isWithin(1e-6).of(0.0)
        assertThat(blocks[0].endHour).isWithin(1e-6).of(23.0)
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
