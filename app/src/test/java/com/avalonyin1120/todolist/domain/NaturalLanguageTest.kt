package com.avalonyin1120.todolist.domain

import com.avalonyin1120.todolist.testing.TestData
import com.google.common.truth.Truth.assertThat
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Test

/**
 * 「一句话新建」的解析规则。
 *
 * 每条用例都钉一个具体说法：解析器是**纯函数**（只依赖 now 与 zone），
 * 所以这里不碰 Android、不碰数据库，跑得很快。
 */
class NaturalLanguageTest {

    private val zone: ZoneId = TestData.ZONE

    /** 2026-10-18（周日）15:30。 */
    private fun now(): Long = local(2026, 10, 18, 15, 30)

    private fun local(y: Int, m: Int, d: Int, h: Int, min: Int = 0): Long =
        LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant().toEpochMilli()

    private fun parse(text: String): NaturalLanguage.Parsed? =
        NaturalLanguage.parse(text, now(), zone)

    private fun startOf(parsed: NaturalLanguage.Parsed?): String {
        assertThat(parsed).isNotNull()
        val dt = java.time.Instant.ofEpochMilli(parsed!!.startAt).atZone(zone)
        return "%04d-%02d-%02d %02d:%02d".format(dt.year, dt.monthValue, dt.dayOfMonth, dt.hour, dt.minute)
    }

    // ------------------------------------------------------------ 日期与时间

    @Test
    fun `明天下午三点半开会`() {
        val p = parse("明天下午三点半开会")
        assertThat(startOf(p)).isEqualTo("2026-10-19 15:30")
        assertThat(p!!.name).isEqualTo("开会")
        assertThat(p.durationMinutes).isEqualTo(60)
    }

    @Test
    fun `阿拉伯数字与中文数字等价`() {
        assertThat(startOf(parse("明天15:00开会"))).isEqualTo("2026-10-19 15:00")
        assertThat(startOf(parse("明天十五点开会"))).isEqualTo("2026-10-19 15:00")
        assertThat(startOf(parse("明天下午3点开会"))).isEqualTo("2026-10-19 15:00")
    }

    @Test
    fun `下午与上午的 12 小时制换算`() {
        assertThat(startOf(parse("明天上午10点开会"))).isEqualTo("2026-10-19 10:00")
        assertThat(startOf(parse("明天晚上8点吃饭"))).isEqualTo("2026-10-19 20:00")
        // 凌晨 12 点 = 0 点（不是 12 点）
        assertThat(startOf(parse("明天凌晨12点睡觉"))).isEqualTo("2026-10-19 00:00")
    }

    @Test
    fun `明确说今天就不擅自挪到明天`() {
        // now = 15:30，说「今天下午 2 点」——已经过去了
        val p = parse("今天下午2点开会")
        assertThat(startOf(p)).isEqualTo("2026-10-18 14:00")
        assertThat(p!!.movedToTomorrow).isFalse() // 明确说了「今天」就不挪
    }

    @Test
    fun `没说日期且时刻已过则顺延到明天`() {
        val p = parse("下午2点开会")
        assertThat(startOf(p)).isEqualTo("2026-10-19 14:00")
        assertThat(p!!.movedToTomorrow).isTrue()
    }

    @Test
    fun `星期几按最近的未来某天来算`() {
        // 2026-10-18 是周日；「周一」应该是 10-19
        assertThat(startOf(parse("周一上午9点例会"))).isEqualTo("2026-10-19 09:00")
        // 「下周周一」= 10-26
        assertThat(startOf(parse("下周一上午9点例会"))).isEqualTo("2026-10-19 09:00")
    }

    @Test
    fun `具体日期`() {
        assertThat(startOf(parse("11月3号下午4点交材料"))).isEqualTo("2026-11-03 16:00")
    }

    @Test
    fun `N 天后`() {
        assertThat(startOf(parse("3天后上午10点复查"))).isEqualTo("2026-10-21 10:00")
    }

    // ------------------------------------------------------------ 重复

    @Test
    fun `每天`() {
        val p = parse("每天晚上9点吃药")
        assertThat(p!!.recurrence).isEqualTo(Recurrence.DAILY)
        assertThat(startOf(p)).isEqualTo("2026-10-18 21:00")
    }

    @Test
    fun `每周多选星期几`() {
        val p = parse("每周一三五早上7点跑步")
        assertThat(p!!.recurrence).isEqualTo(Recurrence.WEEKLY)
        assertThat(p.weekDays).containsExactly(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
        assertThat(startOf(p)).isEqualTo("2026-10-19 07:00")
    }

    @Test
    fun `每月几号`() {
        val p = parse("每月1号上午10点交房租")
        assertThat(p!!.recurrence).isEqualTo(Recurrence.MONTHLY)
        assertThat(p.monthDays).containsExactly(1)
        assertThat(startOf(p)).isEqualTo("2026-11-01 10:00")
    }

    @Test
    fun `工作日`() {
        val p = parse("工作日上午9点打卡")
        assertThat(p!!.recurrence).isEqualTo(Recurrence.WEEKLY)
        assertThat(p.weekDays).hasSize(5)
        assertThat(p.weekDays).doesNotContain(DayOfWeek.SATURDAY)
    }

    // ------------------------------------------------------------ 时长与提前量

    @Test
    fun `时长与提前量`() {
        val p = parse("明天下午3点开两小时会，提前20分钟提醒")
        assertThat(p!!.durationMinutes).isEqualTo(120)
        assertThat(p.leadMinutes).isEqualTo(20)
        assertThat(startOf(p)).isEqualTo("2026-10-19 15:00")
    }

    @Test
    fun `半小时与一个半小时`() {
        assertThat(parse("明天下午3点开半小时会")!!.durationMinutes).isEqualTo(30)
        assertThat(parse("明天下午3点开一个半小时会")!!.durationMinutes).isEqualTo(90)
    }

    // ------------------------------------------------------------ 名称与失败

    @Test
    fun `名称会去掉开头的口头词`() {
        val p = parse("提醒我明天下午3点给妈妈打电话")
        assertThat(p!!.name).isEqualTo("给妈妈打电话")
    }

    @Test
    fun `认不出时间时返回 null`() {
        assertThat(parse("开周会")).isNull()
        assertThat(parse("")).isNull()
        assertThat(parse("   ")).isNull()
    }

    @Test
    fun `全角数字与冒号也能认`() {
        assertThat(startOf(parse("明天１５：００开会"))).isEqualTo("2026-10-19 15:00")
    }

    // ------------------------------------------------------------ 个人化编码

    @Test
    fun `个性化设置编码后能原样读回`() {
        val original = Personalization(
            colorMode = CardColorMode.REMINDER,
            statusColors = mapOf(TimeBucket.FUTURE to 0xFFEF4444L),
            reminderColors = mapOf(ReminderMode.ALARM to 0xFF8B5CF6L),
            leadPresets = listOf(0, 15, 60),
        )
        val decoded = Personalization.decode(original.encode())
        assertThat(decoded.colorMode).isEqualTo(CardColorMode.REMINDER)
        assertThat(decoded.statusColor(TimeBucket.FUTURE)).isEqualTo(0xFFEF4444L)
        assertThat(decoded.reminderColor(ReminderMode.ALARM)).isEqualTo(0xFF8B5CF6L)
        assertThat(decoded.leadPresets).containsExactly(0, 15, 60).inOrder()
    }

    @Test
    fun `个性化设置坏掉的项只丢那一项`() {
        val raw = "v=1;mode=REMINDER;s.FUTURE=zzzz;leads=0,99999,5"
        val decoded = Personalization.decode(raw)
        // 颜色坏了 → 回落默认；模式与快捷按钮仍然生效
        assertThat(decoded.colorMode).isEqualTo(CardColorMode.REMINDER)
        assertThat(decoded.statusColor(TimeBucket.FUTURE))
            .isEqualTo(Personalization.DEFAULT_STATUS_COLORS.getValue(TimeBucket.FUTURE))
        // 超范围的 99999 被过滤掉
        assertThat(decoded.leadPresets).containsExactly(0, 5).inOrder()
    }

    @Test
    fun `恢复默认就是出厂设置`() {
        assertThat(Personalization().isDefault).isTrue()
        assertThat(Personalization(leadPresets = listOf(0, 15)).isDefault).isFalse()
    }

    // ------------------------------------------------------------ 口语/年份/区间（用户反馈）

    @Test
    fun `晚九点与早七点这类单字时段`() {
        val p = parse("明天晚九点吃药")
        assertThat(startOf(p)).isEqualTo("2026-10-19 21:00")
        assertThat(p!!.name).isEqualTo("吃药")
        assertThat(startOf(parse("明天早七点跑步"))).isEqualTo("2026-10-19 07:00")
    }

    @Test
    fun `明年与下个月`() {
        assertThat(startOf(parse("明年3月1号上午9点开会"))).isEqualTo("2027-03-01 09:00")
        assertThat(startOf(parse("下个月10号下午2点交表"))).isEqualTo("2026-11-10 14:00")
        assertThat(startOf(parse("2027年5月20号上午10点体检"))).isEqualTo("2027-05-20 10:00")
    }

    @Test
    fun `时间区间直接给出起止`() {
        val p = parse("明天下午3点到5点开会")
        assertThat(startOf(p)).isEqualTo("2026-10-19 15:00")
        assertThat(p!!.durationMinutes).isEqualTo(120)
        // 15:00-17:00 这种写法也认
        assertThat(parse("明天15:00-17:00开会")!!.durationMinutes).isEqualTo(120)
    }

    @Test
    fun `只认出部分时不影响已填内容`() {
        // 模拟「两次输入叠加」：先只说重复，再只说时间
        val step1 = parse("每周六")
        assertThat(step1!!.hasRecurrence).isTrue()
        assertThat(step1.hasTime).isFalse()
        val step2 = parse("晚上九点")
        assertThat(step2!!.hasTime).isTrue()
        assertThat(step2.hasRecurrence).isFalse()
        // 时间句子不该把名称变成「待办」
        assertThat(step2.name).isEqualTo("待办")
    }
}
