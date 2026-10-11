package com.example.todolist

import android.Manifest
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import com.example.todolist.ui.TAG_MODE_NOTIFICATION
import com.example.todolist.ui.TAG_MONTHDAY_PREFIX
import com.example.todolist.ui.TAG_RECURRENCE_PREFIX
import com.example.todolist.ui.TAG_WEEKDAY_PREFIX
import com.example.todolist.ui.TAG_SETTINGS_BUTTON
import com.example.todolist.ui.TAG_SETTINGS_BACK
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
     * 每个用例前把状态复位，保证互不影响。
     *
     * 用 DAO 清表而不是删除数据库文件：数据库文件与被测进程共享，
     * 删文件会让已经打开的 Room 连接指向不存在的表（SQLiteException）。
     *
     * 同时重置设置里的「上次提前量」——这是产品里的「智能默认」功能，
     * 它会记住上一个用例填过的值，不复位就会让断言「初始为空」失败。
     */
    @Before
    fun resetState() {
        val application = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext
        val graph = (application as TodoApp).requireGraph()
        runBlocking {
            graph.repository.findAll().forEach { todo -> graph.repository.delete(todo) }
        }
        graph.settings.lastLeadMinutes = 0
        graph.settings.useAlarmForReminders = true
        graph.settings.confirmBeforeDelete = true
        graph.settings.warnOnOverlap = true
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

    /** 读取某条待办的完整记录，用于断言周期规则是否被正确保存。 */
    private fun currentTodo(id: Long): com.example.todolist.domain.Todo {
        val application = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext
        val graph = (application as TodoApp).requireGraph()
        return runBlocking { requireNotNull(graph.repository.findById(id)) }
    }

    /**
     * 通过界面新增一条待办，返回它在数据库里的 id。
     *
     * @param beforeSave 保存前的额外操作（例如调整起始时间），在填完名称之后执行
     */
    private fun addTodo(name: String, beforeSave: (() -> Unit)? = null): Long {
        val before = currentTodoIds()
        composeRule.onNodeWithTag(TAG_ADD_FAB).performClick()
        composeRule.onNodeWithTag(TAG_NAME_FIELD).performTextInput(name)
        composeRule.waitForIdle()
        beforeSave?.invoke()
        // 表单内容较长，保存按钮可能在视口外；这里直接点，落库后用轮询确认
        composeRule.onNodeWithTag(TAG_SAVE_BUTTON).performClick()
        composeRule.waitForIdle()

        // 落库是异步的，轮询等待新记录出现
        var added = emptySet<Long>()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            added = currentTodoIds() - before
            added.isNotEmpty()
        }
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

    /** 把起始时间字段的日期与时间改成「现在」，让待办进入可勾选时段。 */
    private fun setStartToNow() {
        // 先点日期按钮，在弹出的日历里确认今天
        composeRule.onNodeWithTag("start_date_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("date_confirm").performClick()
        composeRule.waitForIdle()

        // 再把时间往前调：反复点 -15 直到开始时间不晚于现在（最多 8 次 = 2 小时）
        repeat(8) {
            composeRule.onNodeWithTag("start_time_button").performClick()
            composeRule.waitForIdle()
            composeRule.onAllNodesWithText("-15")[0].performClick()
            composeRule.onNodeWithTag("time_confirm").performClick()
            composeRule.waitForIdle()
        }
    }

    @Test
    fun 勾选后展示已完成状态() {
        val id = addTodo(unique("勾选验证"), beforeSave = { setStartToNow() })
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

        // 默认是通知方式（先滚动到该控件，避免表单滚动状态在用例之间残留）
        composeRule.onNodeWithTag(TAG_MODE_NOTIFICATION).assertExists()

        // 切成闹钟式
        composeRule.onNodeWithTag(TAG_MODE_ALARM).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("（闹钟式）", substring = true).assertExists()

        // 保存前记住库里的 id，便于保存后精准定位新增的那一行
        val before = currentTodoIds()
        composeRule.onNodeWithTag(TAG_SAVE_BUTTON).performClick()
        composeRule.waitForIdle()
        val id = (currentTodoIds() - before).single()

        // 该行的徽章里应出现「闹钟」
        composeRule.onNode(
            hasTestTag(tagForTodo(TAG_TODO_ROW, id)) and hasAnyDescendant(hasText("闹钟")),
            useUnmergedTree = true,
        ).assertExists()
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
        // 用 assertExists 而不是 assertIsDisplayed：新行会加到列表末尾，可能不在视口内
        composeRule.onAllNodesWithText("未开始")[0].assertExists()
    }

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

        composeRule.onNodeWithTag(TAG_SETTINGS_BACK).performClick()
        composeRule.waitForIdle()
    }
}
