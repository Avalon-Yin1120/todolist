package com.avalonyin1120.todolist.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * 周视图「可见时间窗」的规则。
 *
 * 它同时被设置页（生成候选小时）和界面（决定画哪一段）使用，
 * 所以夹取规则必须是一份、且经得起非法输入。
 */
class WeekWindowTest {

    @Test
    fun `默认窗口是六点到二十二点`() {
        assertThat(WeekWindow.DEFAULT_START_HOUR).isEqualTo(6)
        assertThat(WeekWindow.DEFAULT_END_HOUR).isEqualTo(22)
        assertThat(WeekWindow.span(6, 22)).isEqualTo(16)
    }

    @Test
    fun `合法输入原样返回`() {
        assertThat(WeekWindow.clampStart(6, 22)).isEqualTo(6)
        assertThat(WeekWindow.clampEnd(22, 6)).isEqualTo(22)
        assertThat(WeekWindow.span(9, 18)).isEqualTo(9)
    }

    @Test
    fun `起始小时不能等于或超过结束小时`() {
        // 结束是 10 点，起始最多只能是 8 点（至少留 2 小时窗口）
        assertThat(WeekWindow.clampStart(12, 10)).isEqualTo(8)
        // 结束是 22 点，起始给 24 也会被夹到 20
        assertThat(WeekWindow.clampStart(24, 22)).isEqualTo(20)
    }

    @Test
    fun `结束小时不能早于或等于起始小时`() {
        assertThat(WeekWindow.clampEnd(3, 10)).isEqualTo(12)
        assertThat(WeekWindow.clampEnd(24, 23)).isEqualTo(24)
    }

    @Test
    fun `负数与越界值都被夹进合法范围`() {
        assertThat(WeekWindow.clampStart(-5, 22)).isEqualTo(0)
        assertThat(WeekWindow.clampEnd(99, 6)).isEqualTo(24)
        assertThat(WeekWindow.clampStart(0, 0)).isEqualTo(0)
        assertThat(WeekWindow.clampEnd(0, 0)).isEqualTo(2)
    }

    @Test
    fun `窗口永远至少有最小间隔`() {
        // 各种退化组合都不能算出 0 或负数小时的窗口（那会让小时高度变成无穷大）
        for (start in -3..26) {
            for (end in -3..26) {
                val span = WeekWindow.span(start, end)
                assertThat(span).isAtLeast(WeekWindow.MIN_HOURS)
                assertThat(span).isAtMost(24)
            }
        }
    }

    @Test
    fun `候选范围让用户选不出非法组合`() {
        // 结束定在 22 点 → 起始最多 20 点
        assertThat(WeekWindow.startOptions(22).first).isEqualTo(0)
        assertThat(WeekWindow.startOptions(22).last).isEqualTo(20)

        // 起始定在 6 点 → 结束最少 8 点
        assertThat(WeekWindow.endOptions(6).first).isEqualTo(8)
        assertThat(WeekWindow.endOptions(6).last).isEqualTo(24)

        // 用候选范围里任意组合都能得到合法窗口
        for (start in WeekWindow.startOptions(22)) {
            for (end in WeekWindow.endOptions(start)) {
                assertThat(WeekWindow.span(start, end)).isAtLeast(WeekWindow.MIN_HOURS)
            }
        }
    }

    @Test
    fun `刻度按窗口宽度自动稀疏`() {
        // 16 小时 → 每 2 小时一个（9 个刻度）
        assertThat(WeekWindow.labelStepHours(6, 22)).isEqualTo(2)
        // 24 小时 → 每 3 小时一个（9 个刻度）；再密就挤，再疏就看不出时刻
        assertThat(WeekWindow.labelStepHours(0, 24)).isEqualTo(3)
        // 6 小时的小窗口可以每小时都标
        assertThat(WeekWindow.labelStepHours(9, 15)).isEqualTo(1)

        // 刻度数量始终受控，不会把刻度栏画成一团
        for (start in 0..22) {
            for (end in (start + WeekWindow.MIN_HOURS)..24) {
                assertThat(WeekWindow.labelHours(start, end).size).isAtMost(9)
            }
        }
    }

    @Test
    fun `刻度从窗口起点开始且不越界`() {
        val hours = WeekWindow.labelHours(6, 22)
        assertThat(hours.first()).isEqualTo(6)
        assertThat(hours).containsExactly(6, 8, 10, 12, 14, 16, 18, 20, 22).inOrder()
        assertThat(hours.all { it in 6..22 }).isTrue()
    }
}
