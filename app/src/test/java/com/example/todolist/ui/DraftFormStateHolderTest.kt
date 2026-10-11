package com.example.todolist.ui

import com.example.todolist.domain.FormError
import com.example.todolist.domain.FormValidator
import com.example.todolist.testing.FakeClock
import com.example.todolist.testing.TestData
import com.google.common.truth.Truth.assertThat
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
        assertThat(form.state.value.useExactAlarm).isFalse()
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
            useExactAlarm = true,
        )

        assertThat(form.state.value).isEqualTo(
            DraftFormState(
                id = 42L,
                name = "周会",
                startAt = now + 10 * TestData.MINUTE,
                endAt = now + 40 * TestData.MINUTE,
                leadMinutes = 15,
                leadInput = "15",
                useExactAlarm = true,
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
        form.setUseExactAlarm(true)

        assertThat(form.validate()).isEmpty()

        val draft = form.toDraft()
        assertThat(draft.name).isEqualTo("小组会议")   // 已 trim
        assertThat(draft.leadMinutes).isEqualTo(30)
        assertThat(draft.useExactAlarm).isTrue()
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
}
