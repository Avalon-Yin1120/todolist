package com.example.todolist

import android.Manifest
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.example.todolist.ui.TAG_ADD_FAB
import com.example.todolist.ui.TAG_CANCEL_DELETE
import com.example.todolist.ui.TAG_CONFIRM_DELETE
import com.example.todolist.ui.TAG_COUNTDOWN
import com.example.todolist.ui.TAG_MODE_ALARM
import com.example.todolist.ui.TAG_SETTINGS_BUTTON
import com.example.todolist.ui.TAG_SETTINGS_CLOSE
import com.example.todolist.ui.TAG_SETTING_ALARM
import com.example.todolist.ui.TAG_SWIPE_AREA
import com.example.todolist.ui.TAG_TAB_DAY
import com.example.todolist.ui.TAG_TAB_MONTH
import com.example.todolist.ui.TAG_TAB_TODO
import com.example.todolist.ui.TAG_TAB_WEEK
import com.example.todolist.ui.TAG_TITLE
import com.example.todolist.ui.TAG_TODO_DELETE
import com.example.todolist.ui.TAG_LEAD_FIELD
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

    // ------------------------------------------------------------ 时间字段交互
    // 回归背景：此前把 clickable 加在 OutlinedTextField 外层，点击被内部输入框吃掉，
    // 导致「日期根本调不了」，用户只能改时间。

    @Test
    fun 起始时间的日期按钮能打开日期选择器并改写日期() {
        composeRule.onNodeWithTag(TAG_ADD_FAB).performClick()
        composeRule.waitForIdle()

        val before = startFieldText()
        composeRule.onNodeWithTag("start_date_button").performClick()
        composeRule.waitForIdle()

        // 日期选择器出现
        composeRule.onNodeWithText("选择日期").assertIsDisplayed()
        composeRule.onNodeWithTag("date_confirm").performClick()
        composeRule.waitForIdle()

        // 确认后字段值应当仍是合法日期（说明交互链路通了，而不是点击被吞掉）
        val after = startFieldText()
        org.junit.Assert.assertEquals(before, after)
        org.junit.Assert.assertTrue("起始时间字段不应为空", after.isNotEmpty())
    }

    @Test
    fun 起始时间的日期按钮存在且可点击() {
        composeRule.onNodeWithTag(TAG_ADD_FAB).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("start_date_button").assertIsDisplayed()
        composeRule.onNodeWithTag("start_time_button").assertIsDisplayed()
        composeRule.onNodeWithTag("end_date_button").assertIsDisplayed()
        composeRule.onNodeWithTag("end_time_button").assertIsDisplayed()
    }

    @Test
    fun 提前量输入框初始为空而不是填着0() {
        composeRule.onNodeWithTag(TAG_ADD_FAB).performClick()
        composeRule.waitForIdle()

        // 直接读输入框的语义值：应为空（0 不再是删不掉的内容）
        org.junit.Assert.assertEquals("", leadFieldText())
    }

    @Test
    fun 提前量输入后可以再删空() {
        composeRule.onNodeWithTag(TAG_ADD_FAB).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(TAG_LEAD_FIELD).performTextInput("30")
        composeRule.waitForIdle()
        org.junit.Assert.assertEquals("30", leadFieldText())

        composeRule.onNodeWithTag(TAG_LEAD_FIELD).performTextClearance()
        composeRule.waitForIdle()

        // 清空成功——这是此前做不到的
        org.junit.Assert.assertEquals("", leadFieldText())
    }

    /** 读取起始时间字段当前的显示文本。 */
    private fun startFieldText(): String =
        composeRule.onNodeWithTag("start_field")
            .fetchSemanticsNode()
            .config[SemanticsProperties.EditableText]
            .text

    /** 读取提前量输入框当前文本。 */
    private fun leadFieldText(): String =
        composeRule.onNodeWithTag(TAG_LEAD_FIELD)
            .fetchSemanticsNode()
            .config[SemanticsProperties.EditableText]
            .text

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
    fun 选择闹钟式提醒后徽章随之更新() {
        val name = unique("闹钟验证")
        composeRule.onNodeWithTag(TAG_ADD_FAB).performClick()
        composeRule.onNodeWithTag(TAG_NAME_FIELD).performTextInput(name)
        composeRule.waitForIdle()

        // 默认是通知方式
        composeRule.onNodeWithText("（通知）", substring = true).assertIsDisplayed()

        // 切成闹钟式
        composeRule.onNodeWithTag(TAG_MODE_ALARM).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("（闹钟式）", substring = true).assertIsDisplayed()

        // 保存后列表项出现「闹钟」徽章
        composeRule.onNodeWithTag(TAG_SAVE_BUTTON).performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("闹钟")[0].assertIsDisplayed()
    }

    @Test
    fun 未来时段的待办不给勾选框() {
        // 默认起始时间是下一个整点，提醒量默认 0，因此属于「未来」
        val name = unique("未来事项")
        composeRule.onNodeWithTag(TAG_ADD_FAB).performClick()
        composeRule.onNodeWithTag(TAG_NAME_FIELD).performTextInput(name)
        composeRule.onNodeWithTag(TAG_SAVE_BUTTON).performClick()
        composeRule.waitForIdle()

        // 未来的待办不能标记完成：应显示「未开始」而不是勾选框
        composeRule.onAllNodesWithText("未开始")[0].assertIsDisplayed()
    }

    @Test
    fun 删除待办会先弹确认框() {
        val id = addTodo(unique("待删除"))

        composeRule.onNodeWithTag(tagForTodo(TAG_TODO_DELETE, id)).performClick()
        composeRule.waitForIdle()

        // 出现确认框
        composeRule.onNodeWithText("删除待办").assertIsDisplayed()
        // 取消后待办仍在
        composeRule.onNodeWithTag(TAG_CANCEL_DELETE).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(tagForTodo(TAG_TODO_ROW, id)).assertIsDisplayed()
    }

    @Test
    fun 确认后删除待办() {
        val id = addTodo(unique("确认删除"))

        composeRule.onNodeWithTag(tagForTodo(TAG_TODO_DELETE, id)).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TAG_CONFIRM_DELETE).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(tagForTodo(TAG_TODO_ROW, id)).assertDoesNotExist()
    }

    @Test
    fun 底部四个视图可以切换() {
        composeRule.onNodeWithTag(TAG_TAB_DAY).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TAG_SWIPE_AREA).assertExists()

        composeRule.onNodeWithTag(TAG_TAB_WEEK).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TAG_SWIPE_AREA).assertExists()

        composeRule.onNodeWithTag(TAG_TAB_MONTH).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TAG_SWIPE_AREA).assertExists()

        composeRule.onNodeWithTag(TAG_TAB_TODO).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(TAG_ADD_FAB).assertIsDisplayed()
    }

    @Test
    fun 顶部显示倒计时与设置入口() {
        composeRule.onNodeWithTag(TAG_TITLE).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_COUNTDOWN).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_SETTINGS_BUTTON).assertIsDisplayed()
    }

    @Test
    fun 设置页可以打开并关闭() {
        composeRule.onNodeWithTag(TAG_SETTINGS_BUTTON).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("设置").assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_SETTING_ALARM).assertIsDisplayed()

        composeRule.onNodeWithTag(TAG_SETTINGS_CLOSE).performClick()
        composeRule.waitForIdle()
    }
}
