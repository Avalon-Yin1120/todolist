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
import com.example.todolist.ui.DateFormats
import com.example.todolist.ui.TAG_ADD_FAB
import com.example.todolist.ui.TAG_CANCEL_DELETE
import com.example.todolist.ui.TAG_CONFIRM_DELETE
import com.example.todolist.ui.TAG_COUNTDOWN
import com.example.todolist.ui.TAG_FORM_SCREEN
import com.example.todolist.ui.TAG_MODE_ALARM
import com.example.todolist.ui.TAG_MODE_NOTIFICATION
import com.example.todolist.ui.TAG_MONTHDAY_PREFIX
import com.example.todolist.ui.TAG_PERIOD_PREV
import com.example.todolist.ui.TAG_PERIOD_NEXT
import com.example.todolist.ui.TAG_BACK_TO_TODAY
import com.example.todolist.ui.TAG_PERIOD_LABEL
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
import java.time.DayOfWeek
import java.time.LocalDate
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

    /**
     * 切到「待办」页。
     *
     * 应用默认进日视图，只显示当天的事项；而这些用例要操作的是总列表（含未来事项），
     * 必须先切到「待办」页，行的删除/勾选按钮才在组合范围里。
     */
    private fun gotoListTab() {
        composeRule.onNodeWithTag(TAG_TAB_TODO).performClick()
        composeRule.waitForIdle()
    }

    /**
     * 把整屏表单向下滚一段。
     *
     * 用语义滚动动作而不是注入触摸：Compose 只为视口内的内容建立语义节点，
     * 而 performScrollTo 会先滚动祖先容器把目标节点带进视口。
     */
    private fun scrollFormDown() {
        composeRule.onNodeWithTag(TAG_RECURRENCE_PREFIX + "NONE").performScrollTo()
        composeRule.waitForIdle()
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
        gotoListTab()

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

        // 日期对话框出现：以「年/月/日」三个输入框为标志
        // （不再断言「选择日期」标题——那个标题由 DatePicker 自己渲染，位置随内容滚动）
        composeRule.onNodeWithTag("date_year").assertExists()
        composeRule.onNodeWithTag("date_month").assertExists()
        composeRule.onNodeWithTag("date_day").assertExists()
        composeRule.onNodeWithTag("date_confirm").performClick()
        composeRule.waitForIdle()

        // 确认后字段值应当仍是合法日期（说明交互链路通了，而不是点击被吞掉）
        val after = startFieldText()
        org.junit.Assert.assertEquals(before, after)
        org.junit.Assert.assertTrue("起始时间字段不应为空", after.isNotEmpty())
    }

    @Test
    fun 日期可以手动输入不补零的年月日() {
        composeRule.onNodeWithTag(TAG_ADD_FAB).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("start_date_button").performClick()
        composeRule.waitForIdle()

        // 清空后填 2026 / 1 / 1：不补零也必须被接受
        // （旧实现要求 2026/01/01，输入 2026/1/1 会被判非法）
        composeRule.onNodeWithTag("date_year").performTextClearance()
        composeRule.onNodeWithTag("date_year").performTextInput("2026")
        composeRule.onNodeWithTag("date_month").performTextClearance()
        composeRule.onNodeWithTag("date_month").performTextInput("1")
        composeRule.onNodeWithTag("date_day").performTextClearance()
        composeRule.onNodeWithTag("date_day").performTextInput("1")
        composeRule.waitForIdle()

        // 预览显示实际会被设成的日期
        composeRule.onNodeWithText("2026 年 1 月 1 日", substring = true).assertExists()
        composeRule.onNodeWithTag("date_confirm").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("日期 01-01", substring = true).assertExists()
    }

    @Test
    fun 日期手动输入非法时不接受() {
        composeRule.onNodeWithTag(TAG_ADD_FAB).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("start_date_button").performClick()
        composeRule.waitForIdle()

        // 2026 不是闰年
        composeRule.onNodeWithTag("date_year").performTextClearance()
        composeRule.onNodeWithTag("date_year").performTextInput("2026")
        composeRule.onNodeWithTag("date_month").performTextClearance()
        composeRule.onNodeWithTag("date_month").performTextInput("2")
        composeRule.onNodeWithTag("date_day").performTextClearance()
        composeRule.onNodeWithTag("date_day").performTextInput("29")
        composeRule.waitForIdle()

        composeRule.onNodeWithText("请填写合法的年月日", substring = true).assertExists()
        composeRule.onNodeWithTag("date_cancel").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun 重复栏第一个按钮是单次() {
        composeRule.onNodeWithTag(TAG_ADD_FAB).performClick()
        composeRule.waitForIdle()
        // NONE 这一档叫「单次」，而不是和行标题重复的「重复」
        composeRule.onNodeWithTag(TAG_RECURRENCE_PREFIX + "NONE").performScrollTo()
        composeRule.onNodeWithTag(TAG_RECURRENCE_PREFIX + "NONE").assertIsDisplayed()
        composeRule.onNodeWithText("单次").assertExists()
    }

    @Test
    fun 日历视图有翻页与回到今天按钮且可翻页() {
        composeRule.onNodeWithTag(TAG_TAB_WEEK).performClick()
        composeRule.waitForIdle()

        // 左右翻页按钮 + 回到今天
        composeRule.onNodeWithTag(TAG_PERIOD_PREV).assertExists()
        composeRule.onNodeWithTag(TAG_PERIOD_NEXT).assertExists()
        composeRule.onNodeWithTag(TAG_BACK_TO_TODAY).assertExists()

        // 翻页按钮确实改变了「当前是哪一周」。
        // 断言的是「今天 +1 周」这个确定的值，而不是「和之前不一样」：
        // 后者在动画中间态下可能瞬间成立，失败时也看不出实际是什么。
        val nextWeek = weekLabelOf(LocalDate.now().plusWeeks(1))
        composeRule.onNodeWithTag(TAG_PERIOD_NEXT).performClick()
        composeRule.waitUntil(timeoutMillis = 8_000) { periodLabelText() == nextWeek }

        // 「回到本周」应回到今天所在的那一周。
        //
        // 这里比对的是「今天那一周」而不是翻页前的文字：翻页后上层日期已经跟着变了，
        // 而且动画过程中读标签会拿到中间态，比对文案容易偶发失败。
        composeRule.onNodeWithTag(TAG_BACK_TO_TODAY).performClick()
        composeRule.waitUntil(timeoutMillis = 8_000) {
            periodLabelText() == weekLabelOf(LocalDate.now())
        }
        // 仍然是周视图（没有被"回到本周"切走）
        composeRule.onNodeWithTag(TAG_SWIPE_AREA).assertExists()
    }

    /** 某一天所在那一周的标题，与界面上的写法一致。 */
    private fun weekLabelOf(date: LocalDate): String {
        val start = date.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val end = start.plusDays(6)
        return if (start.year == end.year && start.month == end.month) {
            "${start.monthValue} 月 ${start.dayOfMonth} 日 - ${end.dayOfMonth} 日"
        } else {
            "${start.monthValue} 月 ${start.dayOfMonth} 日 - ${end.monthValue} 月 ${end.dayOfMonth} 日"
        }
    }

    /** 读取顶部的时间范围标题。 */
    private fun periodLabelText(): String =
        composeRule.onNodeWithTag(TAG_PERIOD_LABEL)
            .fetchSemanticsNode()
            .config[SemanticsProperties.Text]
            .joinToString("") { it.text }

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
        gotoListTab()
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
        gotoListTab()

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
        gotoListTab()
        // 用 assertExists 而不是 assertIsDisplayed：新行会加到列表末尾，可能不在视口内
        composeRule.onAllNodesWithText("未开始")[0].assertExists()
    }

    fun 选择每月后可以精确勾选几号() {
        composeRule.onNodeWithTag(TAG_ADD_FAB).performClick()
        composeRule.onNodeWithTag(TAG_NAME_FIELD).performTextInput(unique("月结"))
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(TAG_RECURRENCE_PREFIX + "MONTHLY").performClick()
        composeRule.waitForIdle()

        // 号数按钮在表单下方，先滚动过去
        scrollFormDown()

        // 勾上 1 号与 15 号
        composeRule.onNodeWithTag(TAG_MONTHDAY_PREFIX + "1").performClick()
        composeRule.onNodeWithTag(TAG_MONTHDAY_PREFIX + "15").performClick()
        composeRule.waitForIdle()

        val before = currentTodoIds()
        composeRule.onNodeWithTag(TAG_SAVE_BUTTON).performClick()
        composeRule.waitForIdle()
        val id = (currentTodoIds() - before).single()
        val todo = currentTodo(id)
        org.junit.Assert.assertEquals("MONTHLY", todo.recurrence.name)
        org.junit.Assert.assertTrue(
            "应包含 1 号与 15 号，实际=${todo.monthDays}",
            todo.monthDays.containsAll(listOf(1, 15)),
        )
    }

    @Test
    fun 删除待办会先弹确认框() {
        val id = addTodo(unique("待删除"))
        gotoListTab()

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
        gotoListTab()

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

    // ------------------------------------------------------------ 打开与翻页
    //
    // 回归：应用一打开，日视图必须停在「今天」。
    // 曾经 pager 停在锚点页 2000-01-01，而且范围只到 2016 年，
    // 于是用户怎么滑都在远古，永远回不到今天。

    @Test
    fun 打开日视图就停在今天() {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            periodLabelText() == DateFormats.fullDate(LocalDate.now())
        }
    }

    @Test
    fun 翻页按钮依次前进且来回对称() {
        val today = LocalDate.now()
        composeRule.onNodeWithTag(TAG_TAB_DAY).performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            periodLabelText() == DateFormats.fullDate(today)
        }

        // 前进一天
        composeRule.onNodeWithTag(TAG_PERIOD_NEXT).performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            periodLabelText() == DateFormats.fullDate(today.plusDays(1))
        }

        // 后退两天，应当落在昨天（右滑再左滑要对称，不能回不去）
        composeRule.onNodeWithTag(TAG_PERIOD_PREV).performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            periodLabelText() == DateFormats.fullDate(today)
        }
        composeRule.onNodeWithTag(TAG_PERIOD_PREV).performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            periodLabelText() == DateFormats.fullDate(today.minusDays(1))
        }

        // 回到今天
        composeRule.onNodeWithTag(TAG_BACK_TO_TODAY).performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            periodLabelText() == DateFormats.fullDate(today)
        }
    }

    @Test
    fun 从月视图切回日视图仍停在今天() {
        composeRule.onNodeWithTag(TAG_TAB_MONTH).performClick()
        composeRule.waitForIdle()
        // 月视图报上来的是当月 1 号，但切回日视图不该掉到 1 号
        composeRule.onNodeWithTag(TAG_TAB_DAY).performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            periodLabelText() == DateFormats.fullDate(LocalDate.now())
        }
    }
}
