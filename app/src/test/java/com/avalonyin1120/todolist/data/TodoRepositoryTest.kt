package com.avalonyin1120.todolist.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.avalonyin1120.todolist.data.local.TodoDatabase
import com.avalonyin1120.todolist.domain.Recurrence
import com.avalonyin1120.todolist.domain.RecurrenceException
import com.avalonyin1120.todolist.domain.RecurrenceExceptionKind
import com.avalonyin1120.todolist.domain.ReminderMode
import com.avalonyin1120.todolist.testing.TestData
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Room 持久化测试：真实建库、真实 SQL、真实 Flow 发射。
 * 用内存数据库，测试之间互不影响。
 */
@RunWith(RobolectricTestRunner::class)
class TodoRepositoryTest {

    private lateinit var database: TodoDatabase
    private lateinit var repository: TodoRepository

    private val now = TestData.NOW

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            TodoDatabase::class.java,
        ).allowMainThreadQueries().build()
        repository = TodoRepository(database.todoDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun newTodo(
        name: String = "小组会议",
        startAt: Long = now + 60 * TestData.MINUTE,
        leadMinutes: Int = 0,
        mode: ReminderMode = ReminderMode.NOTIFICATION,
    ) = TodoDraft(
        name = name,
        startAt = startAt,
        endAt = startAt + 60 * TestData.MINUTE,
        leadMinutes = leadMinutes,
        mode = mode,
    ).toTodo(existing = null, now = now)

    @Test
    fun `新增后可以按 id 查回且字段完整`() = runTest {
        val id = repository.insert(newTodo(name = "周会", leadMinutes = 15, mode = ReminderMode.ALARM))

        val loaded = repository.findById(id)
        assertThat(loaded).isNotNull()
        assertThat(loaded!!.name).isEqualTo("周会")
        assertThat(loaded.leadMinutes).isEqualTo(15)
        assertThat(loaded.mode).isEqualTo(ReminderMode.ALARM)
        assertThat(loaded.remindedOccurrenceStart).isNull()
    }

    @Test
    fun `列表按未完成优先起始时间升序`() = runTest {
        repository.insert(newTodo(name = "较晚", startAt = now + 200 * TestData.MINUTE))
        repository.insert(newTodo(name = "较早", startAt = now + 60 * TestData.MINUTE))
        val doneId = repository.insert(newTodo(name = "已完成", startAt = now + 10 * TestData.MINUTE))
        val done = repository.findById(doneId)!!
        repository.update(done.copy(isDone = true))

        val names = repository.observeAll().first().map { it.name }
        assertThat(names).containsExactly("较早", "较晚", "已完成").inOrder()
    }

    @Test
    fun `更新会覆盖字段`() = runTest {
        val id = repository.insert(newTodo(name = "旧名字"))
        val loaded = repository.findById(id)!!

        repository.update(loaded.copy(name = "新名字", leadMinutes = 30))

        val again = repository.findById(id)!!
        assertThat(again.name).isEqualTo("新名字")
        assertThat(again.leadMinutes).isEqualTo(30)
        assertThat(again.createdAt).isEqualTo(loaded.createdAt)
    }

    @Test
    fun `标记已提醒后其它字段不变`() = runTest {
        val id = repository.insert(newTodo(name = "会议"))

        repository.markReminded(id, now)

        val loaded = repository.findById(id)!!
        assertThat(loaded.remindedOccurrenceStart).isEqualTo(now)
        assertThat(loaded.name).isEqualTo("会议")
    }

    @Test
    fun `重叠查询只返回时间真正相交的未完成待办`() = runTest {
        // 10:00-11:00 已有
        val existing = repository.insert(newTodo(name = "已有会议", startAt = now + 60 * TestData.MINUTE))
        // 完全重叠
        val overlapping = repository.findOverlapping(now + 70 * TestData.MINUTE, now + 90 * TestData.MINUTE)
        assertThat(overlapping.map { it.name }).containsExactly("已有会议")

        // 紧邻但不相交
        val adjacent = repository.findOverlapping(now + 120 * TestData.MINUTE, now + 150 * TestData.MINUTE)
        assertThat(adjacent).isEmpty()

        // 排除自己
        val selfExcluded = repository.findOverlapping(
            now + 60 * TestData.MINUTE,
            now + 120 * TestData.MINUTE,
            excludeId = existing,
        )
        assertThat(selfExcluded).isEmpty()
    }

    @Test
    fun `已完成的待办不计入重叠`() = runTest {
        val id = repository.insert(newTodo(name = "已完成", startAt = now + 60 * TestData.MINUTE))
        repository.update(repository.findById(id)!!.copy(isDone = true))

        val overlapping = repository.findOverlapping(now + 70 * TestData.MINUTE, now + 90 * TestData.MINUTE)

        assertThat(overlapping).isEmpty()
    }

    @Test
    fun `周期与例外日期可以正确往返`() = runTest {
        val id = repository.insert(
            newTodo(name = "每天晨会").copy(
                recurrence = Recurrence.DAILY,
                recurrenceExceptions = listOf(
                    RecurrenceException("2026-03-01", RecurrenceExceptionKind.SKIPPED),
                    RecurrenceException("2026-03-02", RecurrenceExceptionKind.OVERRIDDEN),
                ),
            )
        )

        val loaded = repository.findById(id)!!

        assertThat(loaded.recurrence).isEqualTo(Recurrence.DAILY)
        assertThat(loaded.recurrenceExceptions).hasSize(2)
        assertThat(loaded.recurrenceExceptions.map { it.date })
            .containsExactly("2026-03-01", "2026-03-02")
        assertThat(loaded.recurrenceExceptions.map { it.kind })
            .containsExactly(RecurrenceExceptionKind.SKIPPED, RecurrenceExceptionKind.OVERRIDDEN)
    }

    @Test
    fun `完成例外存进同一个字段并读回`() = runTest {
        // 「完成本次」走的是已有的例外日期字段（`日期:枚举名`），因此只是多了一种枚举值：
        // 不动表结构、不动数据库版本，老库不需要迁移
        val id = repository.insert(
            newTodo(name = "每天晨会").copy(
                recurrence = Recurrence.DAILY,
                recurrenceExceptions = listOf(
                    RecurrenceException("2026-03-03", RecurrenceExceptionKind.COMPLETED),
                ),
            )
        )

        // 落到库里就是一行文本，和 SKIPPED / OVERRIDDEN 完全同一格式
        assertThat(database.todoDao().findById(id)!!.recurrenceExceptions)
            .isEqualTo("2026-03-03:COMPLETED")

        val loaded = repository.findById(id)!!
        assertThat(loaded.recurrenceExceptions)
            .containsExactly(
                RecurrenceException("2026-03-03", RecurrenceExceptionKind.COMPLETED)
            )
        // 完成状态没有被写进整条的 isDone
        assertThat(loaded.isDone).isFalse()
    }

    @Test
    fun `删除后查不到`() = runTest {
        val id = repository.insert(newTodo())

        repository.deleteById(id)

        assertThat(repository.findById(id)).isNull()
        assertThat(repository.observeAll().first()).isEmpty()
    }

    @Test
    fun `删除整个实体同样生效`() = runTest {
        val id = repository.insert(newTodo())
        val loaded = repository.findById(id)!!

        repository.delete(loaded)

        assertThat(repository.findAll()).isEmpty()
    }

    @Test
    fun `Flow 会在数据变化后重新发射`() = runTest {
        assertThat(repository.observeAll().first()).isEmpty()

        repository.insert(newTodo(name = "第一条"))

        assertThat(repository.observeAll().first().map { it.name }).containsExactly("第一条")
    }
}
