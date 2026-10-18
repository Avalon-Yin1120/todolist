package com.avalonyin1120.todolist.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * 个性化设置里「提前提醒快捷按钮」的规则。
 *
 * 重点盯两件事，都是 v30 测试挖出来的坑：
 * 1. **自定义项加得进去也退得掉**：用户填了「提前 25 分钟」，设置页必须能看见它、能取消它。
 * 2. **选满之后不再静默失败**：以前 `.take(6)` 悄悄丢掉新值，点了没反应还以为是程序坏了。
 */
class PersonalizationTest {

    private val custom = Personalization(leadPresets = listOf(0, 5, 25, 30, 60, 1440))

    // ---------------------------------------------------------------- 芯片候选

    @Test
    fun `出厂设置下芯片就是固定候选`() {
        assertThat(Personalization().leadChipChoices)
            .isEqualTo(Personalization.MINUTE_CHOICES)
    }

    @Test
    fun `自定义分钟数也进芯片候选`() {
        // 25 不在 MINUTE_CHOICES 里，但它被选中了 → 必须渲染出来，否则取消不掉
        assertThat(custom.leadChipChoices).contains(25)
        assertThat(custom.leadChipChoices).isEqualTo(
            listOf(0, 5, 10, 15, 25, 30, 60, 120, 1440)
        )
    }

    @Test
    fun `芯片候选去重且升序`() {
        val odd = Personalization(leadPresets = listOf(1440, 0, 1440, 5))
        val choices = odd.leadChipChoices
        assertThat(choices).isInOrder()
        assertThat(choices.count { it == 1440 }).isEqualTo(1)
    }

    // ---------------------------------------------------------------- 选满

    @Test
    fun `六个就算选满`() {
        assertThat(custom.leadPresets).hasSize(Personalization.MAX_PRESETS)
        assertThat(custom.isLeadPresetsFull).isTrue()
        assertThat(Personalization().isLeadPresetsFull).isFalse()
    }

    @Test
    fun `选满后没选中的不能点 已选中的仍可点`() {
        assertThat(custom.isLeadPresetsFull).isTrue()
        // 没选中的 45 分钟、10 分钟：加不上，按钮置灰
        assertThat(custom.canToggleLead(45)).isFalse()
        assertThat(custom.canToggleLead(10)).isFalse()
        // 已选中的：还能取消，所以永远可点
        assertThat(custom.canToggleLead(25)).isTrue()
        assertThat(custom.canToggleLead(1440)).isTrue()
    }

    @Test
    fun `没满时任何合法分钟数都能点`() {
        val partial = Personalization(leadPresets = listOf(0, 5))
        assertThat(partial.canToggleLead(45)).isTrue()
        assertThat(partial.canToggleLead(0)).isTrue()
    }

    // ---------------------------------------------------------------- 规范化

    @Test
    fun `规范化夹取越界去重排序并截断`() {
        val raw = Personalization(leadPresets = listOf(-5, 0, 25, 25, 10081, 30, 60, 120, 1440, 2880))
        val n = raw.normalized()
        assertThat(n.leadPresets).isEqualTo(listOf(0, 25, 30, 60, 120, 1440))
    }

    @Test
    fun `全被夹干净时回落到出厂值`() {
        // 一个都不合法 → 不能变成空列表，否则表单里那排按钮整块消失
        assertThat(Personalization(leadPresets = listOf(-1, 99999)).normalized().leadPresets)
            .isEqualTo(Personalization.DEFAULT_LEAD_PRESETS)
    }

    @Test
    fun `全零是合法值 保留`() {
        // 0 表示「准点提醒」，是合法值，不能当成空值处理
        assertThat(Personalization(leadPresets = listOf(0)).normalized().leadPresets)
            .isEqualTo(listOf(0))
    }

    // ---------------------------------------------------------------- 编码 / 解码

    @Test
    fun `自定义分钟数存得进读得出`() {
        val saved = custom.copy(colorMode = CardColorMode.MONO, doneColor = 0xFFEF4444)
        val restored = Personalization.decode(saved.encode())
        assertThat(restored).isEqualTo(saved.normalized())
        assertThat(restored.leadPresets).containsExactly(0, 5, 25, 30, 60, 1440)
        assertThat(restored.leadChipChoices).contains(25)
    }

    @Test
    fun `坏掉的一项只丢那一项`() {
        // mode 写错、颜色写错，都只该回落到各自的默认值，不该整份重置
        val p = Personalization.decode("mode=NOT_A_MODE;s.FUTURE=ZZZ;leads=0,25")
        assertThat(p.colorMode).isEqualTo(CardColorMode.STATUS)
        assertThat(p.statusColor(TimeBucket.FUTURE))
            .isEqualTo(Personalization.DEFAULT_STATUS_COLORS.getValue(TimeBucket.FUTURE))
        assertThat(p.leadPresets).containsExactly(0, 25)
    }

    @Test
    fun `空串与垃圾串回落到出厂设置`() {
        assertThat(Personalization.decode(null)).isEqualTo(Personalization())
        assertThat(Personalization.decode("")).isEqualTo(Personalization())
        assertThat(Personalization.decode("随便什么字")).isEqualTo(Personalization())
        assertThat(Personalization.decode("leads=").leadPresets)
            .isEqualTo(Personalization.DEFAULT_LEAD_PRESETS)
    }
}
