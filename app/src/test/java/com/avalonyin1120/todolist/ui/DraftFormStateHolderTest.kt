package com.avalonyin1120.todolist.ui

import com.avalonyin1120.todolist.domain.DaySpan
import com.avalonyin1120.todolist.domain.ReminderMode
import com.avalonyin1120.todolist.domain.FormError
import com.avalonyin1120.todolist.domain.FormValidator
import com.avalonyin1120.todolist.testing.FakeClock
import com.avalonyin1120.todolist.testing.TestData
import com.google.common.truth.Truth.assertThat
import java.time.LocalDate
import org.junit.Before
import org.junit.Test

/** 表单校验与默认值（纯 JVM，不需要 Android 环境）。 */
class DraftFormStateHolderTest {

    private lateinit var clock: FakeClock
    private lateinit var form: DraftFormStateHolder

    private val now = TestData.NOW

    @Before
    fun setUp() {
        clock = FakeClock(now)
        form = DraftFormStateHolder(clock)
    }

    @Test
    fun `新建时默认起始时间是下一个整点`() {
        // now 取整点，所以默认起始时间应为 now + 60 分钟
        form.open()

        assertThat(form.state.value.startAt).isEqualTo(now + 60 * TestData.MINUTE)
        assertThat(form.state.value.endAt).isEqualTo(now + 120 * TestData.MINUTE)
        assertThat(form.state.value.leadMinutes).isEqualTo(0)
        assertThat(form.state.value.mode).isEqualTo(ReminderMode.NOTIFICATION)
    }

    @Test
    fun `默认起始时间会对齐到整点`() {
        // 10:17 + 1 小时 = 11:17，向下取整到 11:00
        clock.initial = now + 17 * TestData.MINUTE
        form.open()

        assertThat(form.state.value.startAt).isEqualTo(now + 60 * TestData.MINUTE)
        assertThat(form.state.value.startAt!! % (60 * TestData.MINUTE)).isEqualTo(0L)
    }

    @Test
    fun `默认起始时间一定落在未来`() {
        // 已经是整点再加 3 秒，仍应落到下一个整点而不是当前整点
        clock.initial = now + 3000
        form.open()

        assertThat(form.state.value.startAt).isAtLeast(clock.now())
    }

    @Test
    fun `编辑已有项时用原有值填充`() {
        form.open(
            id = 42L,
            name = "周会",
            startAt = now + 10 * TestData.MINUTE,
            endAt = now + 40 * TestData.MINUTE,
            leadMinutes = 15,
            mode = ReminderMode.ALARM,
        )

        assertThat(form.state.value).isEqualTo(
            DraftFormState(
                id = 42L,
                name = "周会",
                startAt = now + 10 * TestData.MINUTE,
                endAt = now + 40 * TestData.MINUTE,
                leadMinutes = 15,
                leadInput = "15",
                mode = ReminderMode.ALARM,
            )
        )
    }

    // ---------------------------------------------------------- 提前量输入体验

    @Test
    fun `提前量为零时输入框是空的而不是填着删不掉的0`() {
        form.open(leadMinutes = 0)

        assertThat(form.state.value.leadInput).isEmpty()
        assertThat(form.state.value.leadMinutes).isEqualTo(0)
    }

    @Test
    fun `可以把提前量删空且语义上等于准点`() {
        form.open()
        form.setName("会议")

        form.setLeadMinutes(30)
        assertThat(form.state.value.leadInput).isEqualTo("30")

        // 用户用退格逐位删掉
        form.setLeadInput("3")
        form.setLeadInput("")

        assertThat(form.state.value.leadInput).isEmpty()
        assertThat(form.state.value.leadMinutes).isEqualTo(0)
        assertThat(form.state.value.leadInputInvalid).isFalse()
        // 名称与时间合法，因此整体校验应通过
        assertThat(form.validate()).isEmpty()
    }

    @Test
    fun `输入超范围数字会立刻标记非法而不是静默变成0`() {
        form.open()
        form.setName("会议")
        form.setLeadInput("10081")

        assertThat(form.state.value.leadInputInvalid).isTrue()
        assertThat(form.validate()[FormValidator.FIELD_LEAD])
            .isEqualTo(FormError.LEAD_OUT_OF_RANGE)
    }

    @Test
    fun `输入中不会因为限制字符而丢失已输入内容`() {
        form.open()

        // 带字母与符号的输入被过滤掉，数字保留
        form.setLeadInput("1a2-3")

        assertThat(form.state.value.leadInput).isEqualTo("123")
        assertThat(form.state.value.leadMinutes).isEqualTo(123)
    }

    @Test
    fun `新建时使用记住的提前量作为默认值`() {
        val holder = DraftFormStateHolder(
            clock = clock,
            rememberedLeadMinutes = { 45 },
        )

        holder.open()

        assertThat(holder.state.value.leadMinutes).isEqualTo(45)
        assertThat(holder.state.value.leadInput).isEqualTo("45")
    }

    @Test
    fun `没有历史记录时默认提前量为准点`() {
        val holder = DraftFormStateHolder(
            clock = clock,
            rememberedLeadMinutes = { DraftFormStateHolder.FALLBACK_LEAD_MINUTES },
        )

        holder.open()

        assertThat(holder.state.value.leadMinutes).isEqualTo(0)
        assertThat(holder.state.value.leadInput).isEmpty()
    }

    // ---------------------------------------------------------- 新建时的默认提醒方式

    /**
     * 设置页把「默认提醒方式」换成了三选一，表单这一层就靠 [DraftFormStateHolder] 的
     * `defaultMode` 回调把这个档位带进来。三档都要走一遍：只测「能带出闹钟式」
     * 的话，`defaultMode` 被写成 `if (x) ALARM else NOTIFICATION` 也照样过，
     * 而「默认不通知」正是这次要支持的诉求。
     */
    @Test
    fun `新建时三档默认提醒方式都能带出来`() {
        ReminderMode.entries.forEach { mode ->
            val holder = DraftFormStateHolder(clock = clock, defaultMode = { mode })

            holder.open()

            assertThat(holder.state.value.mode).isEqualTo(mode)
        }
    }

    @Test
    fun `默认档是不通知时也能新建且不校验提前量`() {
        // 用户把默认档设成「不通知」，新建时表单里提前量整块是隐藏的：
        // 这一档必须能存下去，不能因为一个看不见的字段把人卡住
        val holder = DraftFormStateHolder(clock = clock, defaultMode = { ReminderMode.NONE })
        holder.open()
        holder.setName("只做记录")

        assertThat(holder.validate()).isEmpty()

        val draft = holder.toDraft()
        assertThat(draft.mode).isEqualTo(ReminderMode.NONE)
        assertThat(draft.leadMinutes).isEqualTo(0)
    }

    @Test
    fun `编辑已有项时不用默认档而用待办自己的档`() {
        // 设置里的默认档只作用于「新建」；编辑时如果被它顶掉，
        // 用户打开一条「只作记录」的待办再保存，就会莫名其妙变成响铃提醒
        val holder = DraftFormStateHolder(clock = clock, defaultMode = { ReminderMode.ALARM })

        holder.open(mode = ReminderMode.NONE)

        assertThat(holder.state.value.mode).isEqualTo(ReminderMode.NONE)
    }

    @Test
    fun `从默认档是不通知切到通知时先前填过的提前量不会被吞掉`() {
        // 「不通知」档下用户看不到提前量输入框，切回来时必须还是原样。
        // 这里连「默认档就是 NONE」这条路径一起钉住：新建 → 切档 → 值还在。
        val holder = DraftFormStateHolder(clock = clock, defaultMode = { ReminderMode.NONE })
        holder.open()
        holder.setName("会议")
        holder.setLeadMinutes(45)
        assertThat(holder.state.value.mode).isEqualTo(ReminderMode.NONE)

        holder.setMode(ReminderMode.NOTIFICATION)

        assertThat(holder.state.value.leadMinutes).isEqualTo(45)
        assertThat(holder.state.value.leadInput).isEqualTo("45")
        assertThat(holder.validate()).isEmpty()
    }

    @Test
    fun `名称为空时校验失败`() {
        form.open()
        form.setName("   ")

        val errors = form.validate()

        assertThat(errors[FormValidator.FIELD_NAME]).isEqualTo(FormError.NAME_EMPTY)
    }

    @Test
    fun `名称过长时校验失败`() {
        form.open()
        form.setName("a".repeat(81))

        assertThat(form.validate()[FormValidator.FIELD_NAME])
            .isEqualTo(FormError.NAME_TOO_LONG)
    }

    @Test
    fun `终止时间早于起始时间时校验失败`() {
        form.open()
        form.setName("会议")
        form.setStartAt(now + 120 * TestData.MINUTE)
        // setStartAt 会把不合理的结束时间顺延，所以这里显式制造非法区间
        form.setEndAt(now + 60 * TestData.MINUTE)

        assertThat(form.validate()[FormValidator.FIELD_RANGE])
            .isEqualTo(FormError.END_NOT_AFTER_START)
    }

    @Test
    fun `提前量超范围时校验失败`() {
        form.open()
        form.setName("会议")
        form.setLeadMinutes(10081)

        assertThat(form.validate()[FormValidator.FIELD_LEAD])
            .isEqualTo(FormError.LEAD_OUT_OF_RANGE)
    }

    @Test
    fun `合法表单通过校验并产出草稿`() {
        form.open()
        form.setName("  小组会议  ")
        form.setLeadMinutes(30)
        form.setMode(ReminderMode.ALARM)

        assertThat(form.validate()).isEmpty()

        val draft = form.toDraft()
        assertThat(draft.name).isEqualTo("小组会议")   // 已 trim
        assertThat(draft.leadMinutes).isEqualTo(30)
        assertThat(draft.mode).isEqualTo(ReminderMode.ALARM)
    }

    // ---------------------------------------------------------- 不通知（第三档）

    @Test
    fun `选不通知时提前量非法也能提交`() {
        form.open()
        form.setName("会议")
        form.setLeadInput("10081")   // 超出 7 天上限
        assertThat(form.state.value.leadInputInvalid).isTrue()

        form.setMode(ReminderMode.NONE)

        // 界面上提前量整块是隐藏的，用户既看不到提示也没地方改，
        // 因此这一档必须放行，不能把人卡在表单里
        assertThat(form.validate()).isEmpty()

        val draft = form.toDraft()
        assertThat(draft.mode).isEqualTo(ReminderMode.NONE)
        // 落库时补 0，而不是把那个非法值一起写进数据库
        assertThat(draft.leadMinutes).isEqualTo(0)
    }

    @Test
    fun `切到不通知再切回来提前量还在`() {
        form.open()
        // 名称要填，否则 validate() 里会一直存着「名称不能为空」，
        // 断言「没有别的错误」就变成在断言名称本身，与这条用例想验证的东西无关
        form.setName("会议")
        form.setLeadMinutes(30)

        form.setMode(ReminderMode.NONE)
        form.setMode(ReminderMode.NOTIFICATION)

        // 切档只改方式，不许顺手清空用户填过的提前量
        assertThat(form.state.value.leadMinutes).isEqualTo(30)
        assertThat(form.state.value.leadInput).isEqualTo("30")
        assertThat(form.validate()).isEmpty()
    }

    @Test
    fun `修改起始时间到结束之后会自动顺延结束时间`() {
        form.open()
        val newStart = (form.state.value.endAt ?: 0L) + 60 * TestData.MINUTE

        form.setStartAt(newStart)

        assertThat(form.state.value.startAt).isEqualTo(newStart)
        assertThat(form.state.value.endAt)
            .isEqualTo(newStart + DraftFormStateHolder.DEFAULT_DURATION_MS)
    }

    @Test
    fun `修改起始时间但结束时间仍合理时保持不动`() {
        form.open()
        val originalEnd = form.state.value.endAt
        val newStart = form.state.value.startAt!! + 5 * TestData.MINUTE

        form.setStartAt(newStart)

        assertThat(form.state.value.endAt).isEqualTo(originalEnd)
    }

    @Test
    fun `修改字段后清除对应错误`() {
        form.open()
        form.setName("")
        assertThat(form.validate()).isNotEmpty()

        form.setName("会议")

        assertThat(form.errors.value).isEmpty()
    }

    @Test
    fun `提前量的可读描述正确`() {
        assertThat(DraftFormState(leadMinutes = 0).leadLabel).isEqualTo("准点")
        assertThat(DraftFormState(leadMinutes = 45).leadLabel).isEqualTo("提前 45 分钟")
        assertThat(DraftFormState(leadMinutes = 120).leadLabel).isEqualTo("提前 2 小时")
        assertThat(DraftFormState(leadMinutes = 2880).leadLabel).isEqualTo("提前 2 天")
    }

    // ---------------------------------------------------------------- 默认日期

    @Test
    fun `新建时默认落在指定日期的那一天`() {
        val target = LocalDate.of(2025, 3, 8)

        form.open(startDate = target)

        val start = java.time.Instant.ofEpochMilli(form.state.value.startAt!!)
            .atZone(TestData.ZONE).toLocalDateTime()
        assertThat(start.toLocalDate()).isEqualTo(target)
        // 未来的日子用上午九点：用户在意的是「哪一天」，不是「现在几点」
        assertThat(start.hour).isEqualTo(DraftFormStateHolder.DEFAULT_PICKED_HOUR)
        assertThat(start.minute).isEqualTo(0)
    }

    @Test
    fun `指定日期就是今天时仍然用下一个整点`() {
        // TestData.NOW = 2025-01-15 10:00 UTC = 18:00 Asia/Shanghai
        form.open(startDate = LocalDate.of(2025, 1, 15))

        assertThat(form.state.value.startAt).isEqualTo(now + 60 * TestData.MINUTE)
    }

    @Test
    fun `编辑已有项时默认日期不生效`() {
        val target = LocalDate.of(2025, 3, 8)
        val existing = LocalDate.of(2025, 1, 20)
        val existingStart = existing.atTime(9, 30).atZone(TestData.ZONE)
            .toInstant().toEpochMilli()

        form.open(id = 7L, startAt = existingStart, startDate = target)

        assertThat(form.state.value.startAt).isEqualTo(existingStart)
    }

    // ---------------------------------------------------------------- 跨越天数

    @Test
    fun `默认是 1 天`() {
        form.open()

        assertThat(form.spanDays()).isEqualTo(1)
    }

    @Test
    fun `把跨度改成 2 天会保留结束时刻的钟点`() {
        form.open()
        val start = form.state.value.startAt!!
        val endHourBefore = java.time.Instant.ofEpochMilli(form.state.value.endAt!!)
            .atZone(TestData.ZONE).toLocalTime()

        form.setSpanDays(2)

        val newEnd = form.state.value.endAt!!
        assertThat(form.spanDays()).isEqualTo(2)
        assertThat(java.time.Instant.ofEpochMilli(newEnd).atZone(TestData.ZONE).toLocalTime())
            .isEqualTo(endHourBefore)
        assertThat(DaySpan.offsetDays(start, newEnd, TestData.ZONE)).isEqualTo(1)
    }

    @Test
    fun `跨度超出范围会被夹住`() {
        form.open()

        form.setSpanDays(999)
        assertThat(form.spanDays()).isEqualTo(DaySpan.MAX_SPAN_DAYS)

        form.setSpanDays(0)
        // 0 与负值一律按 1 天处理，不允许出现「结束早于开始」
        assertThat(form.spanDays()).isEqualTo(1)
    }
}
