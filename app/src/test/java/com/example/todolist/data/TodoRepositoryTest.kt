package com.example.todolist.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.todolist.data.local.TodoDatabase
import com.example.todolist.testing.TestData
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
        useExactAlarm: Boolean = false,
    ) = TodoDraft(
        name = name,
        startAt = startAt,
        endAt = startAt + 60 * TestData.MINUTE,
        leadMinutes = leadMinutes,
        useExactAlarm = useExactAlarm,
    ).toTodo(existing = null, now = now)

    @Test
    fun `新增后可以按 id 查回且字段完整`() = runTest {
        val id = repository.insert(newTodo(name = "周会", leadMinutes = 15, useExactAlarm = true))

        val loaded = repository.findById(id)
        assertThat(loaded).isNotNull()
        assertThat(loaded!!.name).isEqualTo("周会")
        assertThat(loaded.leadMinutes).isEqualTo(15)
        assertThat(loaded.useExactAlarm).isTrue()
        assertThat(loaded.notifiedAt).isNull()
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
    fun `标记已提醒后 other 字段不变`() = runTest {
        val id = repository.insert(newTodo(name = "会议"))

        repository.markNotified(id, now)

        val loaded = repository.findById(id)!!
        assertThat(loaded.notifiedAt).isEqualTo(now)
        assertThat(loaded.name).isEqualTo("会议")
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
