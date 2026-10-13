package com.avalonyin1120.todolist.domain

import com.avalonyin1120.todolist.testing.TestData
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * 时段分类：未来 / 即将 / 正在 / 过去，以及「哪些待办允许标记完成」。
 *
 * 这条规则同时决定了列表配色与勾选框是否出现，因此单独覆盖。
 */
class TodoClassifierTest {

    private val now = TestData.NOW
    private val minute = TestData.MINUTE

    @Test
    fun `提醒时间之前属于未来`() {
        // 30 分钟后开始、提前 10 分钟提醒 => 现在还没到提醒时间
        val todo = TestData.todo(
            startAt = now + 30 * minute,
            endAt = now + 60 * minute,
            leadMinutes = 10,
        )

        assertThat(TodoClassifier.bucketOf(todo, now)).isEqualTo(TimeBucket.FUTURE)
    }

    @Test
    fun `提醒时刻已到但还没开始属于即将`() {
        val todo = TestData.todo(
            startAt = now + 30 * minute,
            endAt = now + 60 * minute,
            leadMinutes = 60,
        )

        assertThat(TodoClassifier.bucketOf(todo, now)).isEqualTo(TimeBucket.SOON)
    }

    @Test
    fun `位于起止之间属于正在`() {
        val todo = TestData.todo(startAt = now - 10 * minute, endAt = now + 10 * minute)

        assertThat(TodoClassifier.bucketOf(todo, now)).isEqualTo(TimeBucket.ONGOING)
    }

    @Test
    fun `超过终止时间属于过去`() {
        val todo = TestData.todo(startAt = now - 120 * minute, endAt = now - 60 * minute)

        assertThat(TodoClassifier.bucketOf(todo, now)).isEqualTo(TimeBucket.PAST)
    }

    @Test
    fun `边界：恰好等于提醒时刻算即将`() {
        val todo = TestData.todo(startAt = now + 30 * minute, endAt = now + 60 * minute, leadMinutes = 30)

        assertThat(TodoClassifier.bucketOf(todo, now)).isEqualTo(TimeBucket.SOON)
    }

    @Test
    fun `边界：恰好等于起始时间算正在`() {
        val todo = TestData.todo(startAt = now, endAt = now + 30 * minute)

        assertThat(TodoClassifier.bucketOf(todo, now)).isEqualTo(TimeBucket.ONGOING)
    }

    @Test
    fun `边界：恰好等于终止时间算过去`() {
        val todo = TestData.todo(startAt = now - 30 * minute, endAt = now)

        assertThat(TodoClassifier.bucketOf(todo, now)).isEqualTo(TimeBucket.PAST)
    }

    // ------------------------------------------------------------ 勾选权限

    @Test
    fun `未来的待办不允许标记完成`() {
        val todo = TestData.todo(startAt = now + 120 * minute, endAt = now + 180 * minute)

        val result = TodoClassifier.classify(todo, now)

        assertThat(result.timeBucket).isEqualTo(TimeBucket.FUTURE)
        assertThat(result.canMarkDone).isFalse()
    }

    @Test
    fun `即将开始的待办可以标记完成`() {
        val todo = TestData.todo(startAt = now + 5 * minute, endAt = now + 35 * minute, leadMinutes = 30)

        assertThat(TodoClassifier.classify(todo, now).canMarkDone).isTrue()
    }

    @Test
    fun `正在进行的待办可以标记完成`() {
        val todo = TestData.todo(startAt = now - 5 * minute, endAt = now + 25 * minute)

        assertThat(TodoClassifier.classify(todo, now).canMarkDone).isTrue()
    }

    @Test
    fun `已过去但未完成的待办仍可标记完成`() {
        val todo = TestData.todo(startAt = now - 120 * minute, endAt = now - 60 * minute)

        val result = TodoClassifier.classify(todo, now)

        assertThat(result.timeBucket).isEqualTo(TimeBucket.PAST)
        assertThat(result.canMarkDone).isTrue()
    }

    @Test
    fun `已完成的未来待办仍允许取消完成`() {
        // 允许取消勾选，否则用户误勾后再也改不回来
        val todo = TestData.todo(
            startAt = now + 120 * minute,
            endAt = now + 180 * minute,
            isDone = true,
        )

        val result = TodoClassifier.classify(todo, now)

        assertThat(result.isDone).isTrue()
        assertThat(result.canMarkDone).isTrue()
    }
}
