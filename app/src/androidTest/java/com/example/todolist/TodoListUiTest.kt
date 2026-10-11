package com.example.todolist

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.example.todolist.ui.TAG_ADD_FAB
import com.example.todolist.ui.TAG_EXACT_SWITCH
import com.example.todolist.ui.TAG_NAME_FIELD
import com.example.todolist.ui.TAG_SAVE_BUTTON
import com.example.todolist.ui.TAG_TODO_CHECKBOX
import com.example.todolist.ui.TAG_TODO_ROW
import com.example.todolist.ui.tagForTodo
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/**
 * 真机（模拟器）上的界面冒烟测试。
 *
 * 设计取舍：
 * * 按 testTag 与文本定位控件，不依赖坐标，换机型/分辨率也能跑。
 * * 每个用例使用唯一名称，行与勾选框用「带 id 的标签」定位，
 *   因此不依赖数据库是否为空、也不依赖用例执行顺序。
 * * 通知权限用 [GrantPermissionRule] 预先授予：否则系统的权限对话框会盖在界面之上，
 *   把 Activity 压到 PAUSED，Compose 规则会报「No compose hierarchies found」。
 * * 通知与闹钟的系统行为不在这一层断言，见单元测试与 README 里的 adb 验证步骤。
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class TodoListUiTest {

    private val composeRule = createAndroidComposeRule<MainActivity>()

    private val notificationPermissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    @get:Rule
    val rule: RuleChain = RuleChain
        .outerRule(notificationPermissionRule)
        .around(composeRule)

    private fun unique(prefix: String) = "$prefix-" + System.currentTimeMillis()

    /**
     * 每个用例前清空待办，保证互不影响。
     *
     * 用 DAO 清表而不是删除数据库文件：数据库文件与被测进程共享，
     * 删文件会让已经打开的 Room 连接指向不存在的表（SQLiteException）。
     */
    @Before
    fun clearTodos() {
        val application = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext
        val repository = (application as TodoApp).requireGraph().repository
        runBlocking {
            repository.findAll().forEach { todo -> repository.delete(todo) }
        }
        composeRule.waitForIdle()
    }

    /**
     * 读取当前库里的待办 id 集合。
     *
     * 仪器化测试与被测应用同进程，可直接通过 Application 的依赖图查询，
     * 用它确定「刚新增的这条」的 id，从而精准定位界面上的那一行。
     */
    private fun currentTodoIds(): Set<Long> {
        val application = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext
        val graph = (application as TodoApp).requireGraph()
        return runBlocking { graph.repository.findAll().map { it.id }.toSet() }
    }

    /** 通过界面新增一条待办，返回它在数据库里的 id。 */
    private fun addTodo(name: String): Long {
        val before = currentTodoIds()
        composeRule.onNodeWithTag(TAG_ADD_FAB).performClick()
        composeRule.onNodeWithTag(TAG_NAME_FIELD).performTextInput(name)
        composeRule.onNodeWithTag(TAG_SAVE_BUTTON).performClick()
        composeRule.waitForIdle()
        val added = currentTodoIds() - before
        return added.singleOrNull() ?: error("新增后无法确定新待办 id：before=$before added=$added")
    }

    @Test
    fun 启动后展示标题与新增按钮() {
        composeRule.onNodeWithText("待办提醒").assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_ADD_FAB).assertIsDisplayed()
    }

    @Test
    fun 新增一条待办后出现在列表中() {
        val name = unique("写代码")
        val id = addTodo(name)

        composeRule.onNodeWithText(name).assertIsDisplayed()
        composeRule.onNodeWithTag(tagForTodo(TAG_TODO_ROW, id)).assertIsDisplayed()
        // 保存后表单应关闭
        composeRule.onNodeWithText("取消").assertDoesNotExist()
    }

    @Test
    fun 名称为空时提交被拦截并给出提示() {
        composeRule.onNodeWithTag(TAG_ADD_FAB).performClick()
        composeRule.onNodeWithTag(TAG_SAVE_BUTTON).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("请填写名称").assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_NAME_FIELD).assertIsDisplayed()
    }

    @Test
    fun 勾选后展示已完成状态() {
        val id = addTodo(unique("勾选验证"))
        val checkboxTag = tagForTodo(TAG_TODO_CHECKBOX, id)

        // 勾选前应为未选中
        composeRule.onNodeWithTag(checkboxTag, useUnmergedTree = true).assertIsOff()

        composeRule.onNodeWithTag(checkboxTag).performClick()
        composeRule.waitForIdle()

        // 勾选后该行变为已选中。
        // 这里断言勾选框状态而不是「已完成」徽章：列表会随完成状态重排，
        // 被勾选的那条会挪到列表末尾，用徽章断言会依赖它是否仍在视口内。
        composeRule.onNodeWithTag(checkboxTag, useUnmergedTree = true).assertIsOn()
    }

    @Test
    fun 打开精确闹钟开关后策略徽章随之更新() {
        val name = unique("精确验证")
        composeRule.onNodeWithTag(TAG_ADD_FAB).performClick()
        composeRule.onNodeWithTag(TAG_NAME_FIELD).performTextInput(name)
        composeRule.waitForIdle()

        // 默认走普通通知策略
        composeRule.onNodeWithText("（普通通知）", substring = true).assertIsDisplayed()

        // 打开「使用精确闹钟」，预览文案应切换
        composeRule.onAllNodesWithTag(TAG_EXACT_SWITCH)[0].performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("（精确闹钟）", substring = true).assertIsDisplayed()

        // 保存后该行的徽章应为精确策略
        composeRule.onNodeWithTag(TAG_SAVE_BUTTON).performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("准点·精确")[0].assertIsDisplayed()
    }
}
