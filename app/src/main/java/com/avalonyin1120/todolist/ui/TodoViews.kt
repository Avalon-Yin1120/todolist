package com.avalonyin1120.todolist.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.avalonyin1120.todolist.domain.CardColorMode
import com.avalonyin1120.todolist.domain.Personalization
import com.avalonyin1120.todolist.domain.RecurrenceExpander
import com.avalonyin1120.todolist.domain.ReminderMode
import com.avalonyin1120.todolist.domain.TimeBucket
import com.avalonyin1120.todolist.domain.Todo
import com.avalonyin1120.todolist.domain.TodoClassifier
import com.avalonyin1120.todolist.domain.WeekWindow
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth


import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 当前生效的个性化设置。
 *
 * 为什么不是 CompositionLocal 也不是逐层传参：
 * * 逐层传参要把「配色」一路塞进日/周/月三种视图与卡片，四五个 Composable 的签名全被污染；
 * * CompositionLocal 需要在页面根部套 `CompositionLocalProvider`，而卡片位于
 *   `AnimatedContent` 的 lambda 里，套起来要动一堆大括号。
 *
 * 所以用一份**可观察的全局状态**：它只在 [com.avalonyin1120.todolist.ui.TodoRoute]
 * 里由设置同步过来（`mutableStateOf` 写入即触发重组），卡片直接读它。
 * 代价是它不是「每棵子树各自一份」，但个性化本来就该全局一致。
 */
object PersonalizationHolder {
    private val state = androidx.compose.runtime.mutableStateOf(Personalization())

    var current: Personalization
        get() = state.value
        set(value) {
            state.value = value
        }
}

/**
 * 各视图共用的配色规则。
 *
 * 配色与含义：
 * * 未来（还没到提醒时间）——淡蓝，表示「还早」
 * * 即将（提醒时间～开始时间）——琥珀，表示「要来了」
 * * 正在——绿色，表示「进行中」
 * * 过去——灰，表示「已过去」
 * * 已完成——一律浅灰并加删除线，与时间无关
 */
object TodoColors {

    data class Palette(val container: Color, val accent: Color)

    /**
     * 卡片配色。
     *
     * [personalization] 决定「按什么上色」与具体颜色（设置 → 个性化）：
     * * 按状态（默认）：未来 / 即将 / 进行中 / 已过去；
     * * 按提醒方式：通知 / 闹钟式 / 不通知；
     * * 统一颜色：只靠左侧色条与状态文字区分。
     *
     * **已完成永远用灰色**——它表达「做完了」，不该被个性化改掉，
     * 否则「红色 = 会响的闹钟」和「红色 = 已完成」会同时出现。
     */
    fun paletteOf(
        todo: Todo,
        now: Long,
        zone: ZoneId,
        personalization: Personalization = Personalization(),
    ): Palette {
        if (todo.isDone) {
            // 已完成是一个**独立维度**，颜色也可以个性化（默认灰）
            return fromAccent(Color(personalization.doneColor))
        }
        val accent = when (personalization.colorMode) {
            CardColorMode.STATUS -> personalization.statusColor(TodoClassifier.bucketOf(todo, now))
            CardColorMode.REMINDER -> personalization.reminderColor(todo.mode)
            CardColorMode.MONO -> Personalization.MONO_ACCENT
        }
        return fromAccent(Color(accent))
    }

    /**
     * 由强调色推出整张卡片的配色：浅底 = 白与强调色按固定比例混合。
     *
     * 为什么要算而不是让用户挑两个色：挑浅底很容易选中「字看不清」的组合，
     * 而 0.14 这个比例是照着原有一套手写配色的观感定下来的。
     */
    fun fromAccent(accent: Color): Palette =
        Palette(container = lerp(Color.White, accent, 0.14f), accent = accent)

    /** 月视图的红色深浅：档位越高表示当天越忙（图例用）。 */
    fun monthLevelColor(level: Int): Color = when (level) {
        0 -> Color(0x00000000)
        1 -> Color(0x33EF4444)
        2 -> Color(0x66EF4444)
        3 -> Color(0xAAEF4444)
        else -> Color(0xFFDC2626)
    }

    /**
     * 月视图的连续深浅：按忙碌小时数在「近白」与「深红」之间平滑插值，
     * 不再落到固定几档，于是 1 小时和 2.5 小时看起来也是不同的。
     */
    fun monthSmoothColor(hours: Double): Color {
        val t = CalendarLayout.busyIntensity(hours)
        if (t <= 0f) return Color(0x00000000)
        return lerp(Color(0xFFFDE7E7), Color(0xFFDC2626), t)
    }

    /**
     * 按比例在 [from] 与 [to] 之间取色。
     *
     * Compose 的 [androidx.compose.ui.graphics.lerp] 只接受 Color.lerp 扩展，
     * 这里显式做分量插值，便于在单测里直接断言。
     */
    fun lerp(from: Color, to: Color, t: Float): Color {
        val f = t.coerceIn(0f, 1f)
        return Color(
            red = from.red + (to.red - from.red) * f,
            green = from.green + (to.green - from.green) * f,
            blue = from.blue + (to.blue - from.blue) * f,
            alpha = from.alpha + (to.alpha - from.alpha) * f,
        )
    }
}

private val HEADER_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MM月dd日")

/** 日视图：当天待办列表。 */
@Composable
fun DailyView(
    todos: List<Todo>,
    date: LocalDate,
    now: Long,
    zone: ZoneId,
    onToggleDone: (Todo) -> Unit,
    onEdit: (Todo) -> Unit,
    onDelete: (Todo) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dayTodos = CalendarLayout.todosOn(todos, date, zone)
    if (dayTodos.isEmpty()) {
        CalendarEmptyHint("这一天没有待办", modifier)
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        // 底部留白与「待办」页一致：滚到底时最后一个待办不该被贴死在屏幕底边，
        // 也要给从底部弹出的 Snackbar 让位。
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 16.dp,
            bottom = 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(dayTodos, key = { "${it.id}-${it.startAt}" }) { todo ->
            TodoRowCard(
                todo = todo,
                now = now,
                zone = zone,
                onToggleDone = { onToggleDone(todo) },
                onEdit = { onEdit(todo) },
                onDelete = { onDelete(todo) },
            )
        }
    }
}

/** 周视图左侧时间刻度栏的宽度；表头留同样宽的占位才能和下面的列对齐。 */
private val WEEK_GUTTER_WIDTH = 40.dp

/**
 * 刻度标签行高。
 *
 * 定成一个固定值而不是「内容自适应」，是为了能把标签**垂直居中到整点线上**：
 * 直接 `offset(y = hourHeight * hour)` 会把文字的上边缘压在整点线上，
 * 于是标签看起来比它标注的时刻低半个字高（实测 720x1600/320dpi 下偏低约 14px≈7dp），
 * 而色块是按整点线精确定位的——两相对照就会觉得「色块偏了」。
 */
private val WEEK_LABEL_HEIGHT = 14.dp

/**
 * 色块的最小高度。
 *
 * 高度仍然按真实时长计算；但一天压进一屏后，15 分钟只剩几个 dp，
 * 会小到看不见、也点不到，所以给一个下限兜底（只影响极短的待办）。
 */
private val MIN_EVENT_HEIGHT = 12.dp

/** 低于这个高度就不再放名字：塞进去只会糊成一团。 */
private val MIN_EVENT_TEXT_HEIGHT = 20.dp

/**
 * [BoxWithConstraints] 拿不到有限高度时的回退值。
 *
 * 父容器不限高时 `maxHeight` 是 `Dp.Infinity`，直接除以窗口小时数会得到无穷大，
 * 所有偏移量都会变成 NaN，所以必须退回一个确定的高度。
 */
private val WEEK_FALLBACK_HEIGHT = 480.dp

/**
 * 色块与泳道边界之间留的缝。
 *
 * 左右**各留一份**：早先只把宽度减了 2dp 而没有平移，于是色块紧贴列分隔线、
 * 右边却空一条，整列看上去就是「整体偏左」。
 */
private val WEEK_BLOCK_GAP = 1.dp

/**
 * 周视图：横向 7 天，纵向只画设置里选定的那一段（默认 06:00~22:00）。
 *
 * 与最初版本的区别：整块高度按可用空间等比压缩，不纵向滚动；
 * 待办画成高度即时长的实心色块，重叠的自动分泳道并排显示，
 * 因此不会再有「一小时一个方块」的底格。点击某一列跳到那一天的日视图。
 */
@Composable
fun WeeklyView(
    todos: List<Todo>,
    anchor: LocalDate,
    now: Long,
    zone: ZoneId,
    onPickDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    /** 纵向显示的起始小时（含），默认 06:00，可在设置里改 */
    windowStartHour: Int = WeekWindow.DEFAULT_START_HOUR,
    /** 纵向显示的结束小时（含），默认 22:00 */
    windowEndHour: Int = WeekWindow.DEFAULT_END_HOUR,
) {
    val weekDates = CalendarLayout.weekDates(anchor)
    val today = CalendarLayout.toLocalDate(now, zone)
    // 先夹一遍：设置里存的值可能越界，也可能被改动成非法组合
    val windowStart = WeekWindow.clampStart(windowStartHour, windowEndHour)
    val windowEnd = WeekWindow.clampEnd(windowEndHour, windowStartHour)
    val windowSpan = (windowEnd - windowStart).coerceAtLeast(WeekWindow.MIN_HOURS)
    val labelHours = WeekWindow.labelHours(windowStart, windowEnd)

    Column(modifier = modifier.fillMaxSize()) {
        // ---- 表头：周一~周日 + 日号 ----
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(WEEK_GUTTER_WIDTH))
            weekDates.forEach { date ->
                val isToday = date == today
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onPickDate(date) }
                        .padding(vertical = 6.dp)
                        .testTag(TAG_WEEK_COLUMN_PREFIX + date.toString()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = RecurrenceExpander.describeDayOfWeek(date.dayOfWeek).removePrefix("周"),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        text = date.dayOfMonth.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        // 今天要一眼认出来：主色 + 加粗
                        color = if (isToday) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }

        // ---- 时间网格：整块可见高度平分给「窗口小时数」，于是这一段一屏看完 ----
        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val availableHeight =
                if (maxHeight.value.isFinite()) maxHeight else WEEK_FALLBACK_HEIGHT
            // 上下各让出半个标签高度：刻度要「骑」在整点线上，最上面和最下面那两个
            // 若贴着边界就会被裁掉半个（实测：只做居中而不留边，首个刻度仍然比它的
            // 整点线低约 7dp，看上去还是「色块偏了」）。留出这半行之后，
            // 首尾刻度也落在可用区域内，且网格本身仍然居中。
            val gridHeight = (availableHeight - WEEK_LABEL_HEIGHT).coerceAtLeast(1.dp)
            val hourHeight = gridHeight / windowSpan

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(gridHeight)
                    .offset(y = WEEK_LABEL_HEIGHT / 2),
            ) {
                // 刻度栏用 offset 绝对定位几个标签；用逐小时等高行去排会把高度撑开
                Box(modifier = Modifier.width(WEEK_GUTTER_WIDTH).fillMaxHeight()) {
                    labelHours.forEach { hour ->
                        // 先按整点算位置，再上移半行高让文字**居中**在整点线上。
                        // 只减半行高不够准——文字实际行高由字体决定，所以给它一个固定
                        // 高度的容器并在里面居中，位置才是确定的。
                        val centeredY = hourHeight * (hour - windowStart) - WEEK_LABEL_HEIGHT / 2
                        Text(
                            text = "%02d:00".format(hour),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                // 这里**不做**夹取：整段网格已经上下各让出半个标签高度，
                                // 首尾刻度的自然位置正好落在可用区域内（首个的顶端恰好
                                // 贴在网格区顶部）。早先加了一句 coerceAtLeast(0.dp)，
                                // 结果首个刻度被压回 0、又比它的整点线低 7dp，
                                // 看起来仍像「色块偏了」。
                                .offset(y = centeredY)
                                .height(WEEK_LABEL_HEIGHT)
                                .padding(end = 4.dp),
                        )
                    }
                }
                weekDates.forEach { date ->
                    WeekDayColumn(
                        date = date,
                        blocks = WeekLayout.blocksOf(
                            todos = todos,
                            date = date,
                            zone = zone,
                            windowStartHour = windowStart,
                            windowEndHour = windowEnd,
                        ),
                        hourHeight = hourHeight,
                        windowStartHour = windowStart,
                        windowSpanHours = windowSpan,
                        now = now,
                        zone = zone,
                        isToday = date == today,
                        onPickDate = onPickDate,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * 周视图里的一天：一条淡竖线、若干个按泳道错开的色块，今天再多一条「现在」的指示线。
 *
 * 这里再套一层 [BoxWithConstraints] 是为了拿到本列的实际宽度——
 * 泳道宽度是按列宽算出来的，只有在这一层才量得到。
 */
@Composable
private fun WeekDayColumn(
    date: LocalDate,
    blocks: List<WeekEventBlock>,
    hourHeight: Dp,
    windowStartHour: Int,
    windowSpanHours: Int,
    now: Long,
    zone: ZoneId,
    isToday: Boolean,
    onPickDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxHeight()
            // 色块靠 offset 定位，会溢出列边界；裁掉才不会串到相邻列上
            .clipToBounds()
            .clickable { onPickDate(date) },
    ) {
        val columnWidth = maxWidth

        // 很淡的竖分隔线，帮助区分相邻两列
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        )

        blocks.forEach { block ->
            WeekBlock(
                block = block,
                columnWidth = columnWidth,
                hourHeight = hourHeight,
                windowStartHour = windowStartHour,
                now = now,
                zone = zone,
            )
        }

        if (isToday) {
            // 只在今天这一列画「现在」，一眼看出今天还剩多少时间。
            // 当前时刻落在窗口外时（例如凌晨 3 点看的却是 06:00~22:00 的窗口）
            // 不画，否则红线会跑到列外面去。
            val passedHours = Instant.ofEpochMilli(now).atZone(zone)
                .toLocalTime().toSecondOfDay() / 3600.0
            val offsetHours = passedHours - windowStartHour
            if (offsetHours >= 0.0 && offsetHours <= windowSpanHours) {
                Box(
                    modifier = Modifier
                        .offset(y = hourHeight * offsetHours.toFloat())
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.error),
                )
            }
        }
    }
}

/** 一条待办的色块：高度即时长，颜色沿用日视图/列表的同一套配色。 */
@Composable
private fun WeekBlock(
    block: WeekEventBlock,
    columnWidth: Dp,
    hourHeight: Dp,
    windowStartHour: Int,
    now: Long,
    zone: ZoneId,
) {
    // 同一重叠簇里各占列宽的一份，于是重叠的两条会各占一半、并排显示
    val laneWidth = columnWidth / block.laneCount
    // 左右**各**留一道缝：早先只减宽度不平移，色块就紧贴列分隔线、右边却空着，
    // 整列看上去是「整体偏左」。再窄也保证宽度为正，否则 width() 会抛异常。
    val blockWidth = (laneWidth - WEEK_BLOCK_GAP * 2).coerceAtLeast(1.dp)
    val blockHeight = maxOf(hourHeight * (block.endHour - block.startHour).toFloat(), MIN_EVENT_HEIGHT)
    val palette = TodoColors.paletteOf(block.todo, now, zone, PersonalizationHolder.current)
    // 浅色底配黑字、深色底配白字，任何状态色下都读得清
    val textColor = if (palette.accent.luminance() > 0.5f) Color.Black else Color.White

    Box(
        modifier = Modifier
            .offset(
                x = laneWidth * block.lane + WEEK_BLOCK_GAP,
                y = hourHeight * (block.startHour - windowStartHour).toFloat(),
            )
            .width(blockWidth)
            .height(blockHeight)
            .clip(RoundedCornerShape(4.dp))
            .background(palette.accent)
            .testTag(tagForTodo(TAG_WEEK_BLOCK, block.todo.id)),
    ) {
        // 只有放得下文字时才写名字，行数按实际高度给，超出交给圆角矩形裁掉
        if (blockHeight >= MIN_EVENT_TEXT_HEIGHT) {
            Text(
                text = block.todo.name,
                style = MaterialTheme.typography.labelSmall,
                color = textColor,
                maxLines = ((blockHeight.value - 2f) / 12f).toInt().coerceAtLeast(1),
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp),
            )
        }
    }
}

/**
 * 月视图：按周排列的日历格子，红色越深表示当天待办总时长越长。
 * 点击某一天跳到日视图。
 */
@Composable
fun MonthlyView(
    todos: List<Todo>,
    anchor: LocalDate,
    zone: ZoneId,
    today: LocalDate,
    onPickDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val month = YearMonth.from(anchor)
    val gridDates = CalendarLayout.monthGridDates(month)
    Column(modifier = modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 6.dp),
                )
            }
        }
        gridDates.chunked(7).forEach { week ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                week.forEach { date ->
                    val inMonth = YearMonth.from(date) == month
                    val busyHours = CalendarLayout.busyHours(todos, date, zone)
                    val count = RecurrenceExpander
                        .expandBetween(todos, date, date.plusDays(1), zone)
                        .size
                    val intensity = CalendarLayout.busyIntensity(busyHours)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .padding(2.dp)
                            .clip(RoundedCornerShape(6.dp))
                            // 白底而不是整格染色：底色一深，日期数字就看不清了。
                            // 忙碌程度改用底部那条进度条表达，颜色用品牌色而不是红色——
                            // 红色在中文语境里是「超时/告警」，拿来表示「这天比较忙」太吓人。
                            .background(
                                if (date == today) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
                                } else {
                                    Color.Transparent
                                }
                            )
                            .border(
                                width = if (date == today) 1.5.dp else 0.dp,
                                color = if (date == today) MaterialTheme.colorScheme.primary else Color.Transparent,
                                shape = RoundedCornerShape(6.dp),
                            )
                            .clickable { onPickDate(date) }
                            .testTag(TAG_MONTH_CELL_PREFIX + date.toString()),
                    ) {
                        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 3.dp, vertical = 2.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = date.dayOfMonth.toString(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (inMonth) {
                                        MaterialTheme.colorScheme.onSurface
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                    },
                                    fontWeight = if (date == today) FontWeight.Bold else FontWeight.Normal,
                                    modifier = Modifier.weight(1f),
                                )
                                // 数字角标：一眼看出「这天有几件事」，比「多忙」更有用
                                if (count > 0) {
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                            .padding(horizontal = 4.dp, vertical = 1.dp),
                                    ) {
                                        Text(
                                            text = count.toString(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.weight(1f))
                            // 底部进度条：长度/深浅 = 当天忙碌程度（按总时长），最多铺满整格
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(intensity.coerceIn(0f, 1f))
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(
                                        if (count > 0) {
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.35f + 0.65f * intensity)
                                        } else {
                                            Color.Transparent
                                        }
                                    ),
                            )
                        }
                    }
                }
            }
        }
        // 图例
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // 新语义只需一句话：角标=件数、底条=忙碌程度。不再用「越忙越深」的色带，
            // 因为整格染色已经被去掉了。
            Text(
                text = "右上角数字 = 当天待办件数，底部横条 = 忙碌程度（按总时长）",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 单条待办的卡片，日视图与总列表共用。 */
@Composable
fun TodoRowCard(
    todo: Todo,
    now: Long,
    zone: ZoneId,
    onToggleDone: (Todo) -> Unit,
    onEdit: (Todo) -> Unit,
    onDelete: (Todo) -> Unit,
    modifier: Modifier = Modifier,
) {
    val classification = TodoClassifier.classify(todo, now)
    val palette = TodoColors.paletteOf(todo, now, zone, PersonalizationHolder.current)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag(tagForTodo(TAG_TODO_ROW, todo.id)),
        colors = CardDefaults.cardColors(containerColor = palette.container),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 10.dp, end = 4.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 左侧色条：一眼看出时段
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(palette.accent),
            )
            Spacer(Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = todo.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (todo.isDone) TextDecoration.LineThrough else null,
                    color = if (todo.isDone) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = DateFormats.range(todo.startAt, todo.endAt, zone),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusChip(bucketLabel(classification.timeBucket))
                    if (todo.recurrence.isRecurring) {
                        StatusChip(RecurrenceExpander.describe(todo.recurrence))
                    }
                    StatusChip(modeLabel(todo))
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (classification.canMarkDone) {
                    // 未来的待办不给勾选框：还没发生的事谈不上完成
                    androidx.compose.material3.Checkbox(
                        checked = todo.isDone,
                        onCheckedChange = { onToggleDone(todo) },
                        modifier = Modifier.testTag(tagForTodo(TAG_TODO_CHECKBOX, todo.id)),
                    )
                } else {
                    Text(
                        text = "未开始",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
                androidx.compose.material3.IconButton(
                    onClick = { onEdit(todo) },
                    modifier = Modifier.testTag(tagForTodo(TAG_TODO_EDIT, todo.id)),
                ) {
                    Text("改", style = MaterialTheme.typography.labelMedium)
                }
                androidx.compose.material3.IconButton(
                    onClick = { onDelete(todo) },
                    modifier = Modifier.testTag(tagForTodo(TAG_TODO_DELETE, todo.id)),
                ) {
                    Text("删", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun StatusChip(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun CalendarEmptyHint(text: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun bucketLabel(bucket: TimeBucket): String = when (bucket) {
    TimeBucket.FUTURE -> "未来"
    TimeBucket.SOON -> "即将"
    TimeBucket.ONGOING -> "进行中"
    TimeBucket.PAST -> "已过去"
}

/**
 * 待办卡片上的提醒方式标签。
 *
 * 「不通知」也照样显示「不提醒」：卡片上一个标签都没有的话，用户会以为
 * 是提醒坏了或没排上，写出来才说明这是他自己选的、这条只做记录。
 */
private fun modeLabel(todo: Todo): String = when (todo.mode) {
    ReminderMode.ALARM -> "闹钟"
    ReminderMode.NOTIFICATION -> "通知"
    ReminderMode.NONE -> "不提醒"
}

// 日期/时间的格式化统一走 DateFormats（跨天卡片要写出结束日期，见 DateFormats.range），
// 这里不再各留一份私有实现——两份实现早晚会在「跨天怎么写」上分叉。

const val TAG_WEEK_COLUMN_PREFIX = "week_col_"
const val TAG_MONTH_CELL_PREFIX = "month_cell_"
const val TAG_TODO_EDIT = "todo_edit"
const val TAG_TODO_DELETE = "todo_delete"
