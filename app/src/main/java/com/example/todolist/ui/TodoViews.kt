package com.example.todolist.ui

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
import com.example.todolist.domain.RecurrenceExpander
import com.example.todolist.domain.TimeBucket
import com.example.todolist.domain.Todo
import com.example.todolist.domain.TodoClassifier
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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

    fun paletteOf(todo: Todo, now: Long, zone: ZoneId): Palette {
        if (todo.isDone) {
            return Palette(Color(0xFFE8E8EC), Color(0xFF9AA0A6))
        }
        return when (TodoClassifier.bucketOf(todo, now)) {
            TimeBucket.FUTURE -> Palette(Color(0xFFE3F0FF), Color(0xFF3B82F6))
            TimeBucket.SOON -> Palette(Color(0xFFFFF0DB), Color(0xFFF59E0B))
            TimeBucket.ONGOING -> Palette(Color(0xFFE3F8E9), Color(0xFF16A34A))
            TimeBucket.PAST -> Palette(Color(0xFFEFEFF2), Color(0xFF9AA0A6))
        }
    }

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
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
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

/** 刻度标签自身的高度，用来让最底下那个 24:00 不越出可视区。 */
private val WEEK_LABEL_HEIGHT = 12.dp

/**
 * 色块的最小高度。
 *
 * 高度仍然按真实时长计算；但 24 小时压进一屏后，15 分钟只剩几个 dp，
 * 会小到看不见、也点不到，所以给一个下限兜底（只影响极短的待办）。
 */
private val MIN_EVENT_HEIGHT = 12.dp

/** 低于这个高度就不再放名字：塞进去只会糊成一团。 */
private val MIN_EVENT_TEXT_HEIGHT = 20.dp

/**
 * [BoxWithConstraints] 拿不到有限高度时的回退值。
 *
 * 父容器不限高时 `maxHeight` 是 `Dp.Infinity`，直接除以 24 会得到无穷大，
 * 所有偏移量都会变成 NaN，所以必须退回一个确定的高度。
 */
private val WEEK_FALLBACK_HEIGHT = 480.dp

/** 左侧刻度标签：顶、中、底都要有，才方便估算某条待办落在哪个时段。 */
private val WEEK_LABEL_HOURS = listOf(0, 6, 12, 18, 24)

/**
 * 周视图：横向 7 天、纵向 24 小时。
 *
 * 与旧版的区别在于「24 小时全部可见」：整块高度按可用空间等比压缩，
 * 不再纵向滚动；待办画成高度即时长的实心色块，重叠的自动分泳道并排显示，
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
) {
    val weekDates = CalendarLayout.weekDates(anchor)
    val today = CalendarLayout.toLocalDate(now, zone)

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

        // ---- 时间网格：整块可见高度平分给 24 小时，于是全天一屏看完 ----
        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val gridHeight = if (maxHeight.value.isFinite()) maxHeight else WEEK_FALLBACK_HEIGHT
            val hourHeight = gridHeight / CalendarLayout.HOURS_PER_DAY

            Row(modifier = Modifier.fillMaxWidth().height(gridHeight)) {
                // 刻度栏用 offset 绝对定位几个标签；用等高的 24 行去排会把高度撑开
                Box(modifier = Modifier.width(WEEK_GUTTER_WIDTH).fillMaxHeight()) {
                    WEEK_LABEL_HOURS.forEach { hour ->
                        Text(
                            text = "%02d:00".format(hour),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                // 24:00 正好落到底边之外，往上收一个标签高度让它留在可视区内
                                .offset(
                                    y = (hourHeight * hour)
                                        .coerceAtMost(gridHeight - WEEK_LABEL_HEIGHT)
                                        .coerceAtLeast(0.dp),
                                )
                                .padding(end = 4.dp),
                        )
                    }
                }
                weekDates.forEach { date ->
                    WeekDayColumn(
                        date = date,
                        blocks = WeekLayout.blocksOf(todos, date, zone),
                        hourHeight = hourHeight,
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
                now = now,
                zone = zone,
            )
        }

        if (isToday) {
            // 只在今天这一列画「现在」，一眼看出今天还剩多少时间
            val passedHours = Instant.ofEpochMilli(now).atZone(zone)
                .toLocalTime().toSecondOfDay() / 3600.0
            Box(
                modifier = Modifier
                    .offset(y = hourHeight * passedHours.toFloat())
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.error),
            )
        }
    }
}

/** 一条待办的色块：高度即时长，颜色沿用日视图/列表的同一套配色。 */
@Composable
private fun WeekBlock(
    block: WeekEventBlock,
    columnWidth: Dp,
    hourHeight: Dp,
    now: Long,
    zone: ZoneId,
) {
    // 同一重叠簇里各占列宽的一份，于是重叠的两条会各占一半、并排显示
    val laneWidth = columnWidth / block.laneCount
    // 左右各留 1dp 缝，重叠时才看得出是两块；再窄也保证宽度为正，否则 width() 会抛异常
    val blockWidth = (laneWidth - 2.dp).coerceAtLeast(1.dp)
    val blockHeight = maxOf(hourHeight * (block.endHour - block.startHour).toFloat(), MIN_EVENT_HEIGHT)
    val palette = TodoColors.paletteOf(block.todo, now, zone)
    // 浅色底配黑字、深色底配白字，任何状态色下都读得清
    val textColor = if (palette.accent.luminance() > 0.5f) Color.Black else Color.White

    Box(
        modifier = Modifier
            .offset(x = laneWidth * block.lane, y = hourHeight * block.startHour.toFloat())
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
                    val level = CalendarLayout.busyLevel(busyHours)
                    val hasAny = busyHours > 0
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .padding(2.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(TodoColors.monthSmoothColor(busyHours))
                            .border(
                                width = if (date == today) 2.dp else 0.dp,
                                color = if (date == today) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    Color.Transparent
                                },
                                shape = RoundedCornerShape(6.dp),
                            )
                            .clickable { onPickDate(date) }
                            .testTag(TAG_MONTH_CELL_PREFIX + date.toString()),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = date.dayOfMonth.toString(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (inMonth) {
                                if (level >= 3) Color.White else MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            },
                            fontWeight = if (hasAny) FontWeight.Bold else FontWeight.Normal,
                        )
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
            Text("空闲", style = MaterialTheme.typography.labelSmall)
            // 图例按色带取样，与单元格的连续深浅保持一致
            (0..4).forEach { step ->
                val hours = CalendarLayout.SATURATION_HOURS * (step / 4.0).let { it * it }
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(TodoColors.monthSmoothColor(hours))
                        .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                )
            }
            Text("越忙越深（按当天待办总时长）", style = MaterialTheme.typography.labelSmall)
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
    val palette = TodoColors.paletteOf(todo, now, zone)

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
                    text = "${formatDateTime(todo.startAt, zone)} → ${formatTime(todo.endAt, zone)}",
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

private fun modeLabel(todo: Todo): String =
    if (todo.mode == com.example.todolist.domain.ReminderMode.ALARM) "闹钟" else "通知"

private fun formatDateTime(epochMillis: Long, zone: ZoneId): String =
    Instant.ofEpochMilli(epochMillis).atZone(zone)
        .format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))

private fun formatTime(epochMillis: Long, zone: ZoneId): String =
    Instant.ofEpochMilli(epochMillis).atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm"))

const val TAG_WEEK_COLUMN_PREFIX = "week_col_"
const val TAG_MONTH_CELL_PREFIX = "month_cell_"
const val TAG_TODO_EDIT = "todo_edit"
const val TAG_TODO_DELETE = "todo_delete"
