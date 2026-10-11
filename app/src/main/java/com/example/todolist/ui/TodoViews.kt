package com.example.todolist.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
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

/**
 * 周视图：横向 7 天、纵向 24 小时。
 *
 * 按需求只表达「有没有待办」，不展示详情；点击某一列跳到那一天的日视图。
 */
@Composable
fun WeeklyView(
    todos: List<Todo>,
    anchor: LocalDate,
    zone: ZoneId,
    onPickDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val weekDates = CalendarLayout.weekDates(anchor)
    Column(modifier = modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(40.dp))
            weekDates.forEach { date ->
                val marked = CalendarLayout.todosOn(todos, date, zone).isNotEmpty()
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
                        fontWeight = if (marked) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 8.dp),
        ) {
            // 左侧小时刻度
            Column(modifier = Modifier.width(40.dp)) {
                (0 until CalendarLayout.HOURS_PER_DAY).forEach { hour ->
                    Box(
                        modifier = Modifier
                            .height(HOUR_CELL_HEIGHT)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.TopEnd,
                    ) {
                        if (hour % 3 == 0) {
                            Text(
                                text = "%02d".format(hour),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(end = 4.dp),
                            )
                        }
                    }
                }
            }
            // 7 天 × 24 小时的底格 + 按时间比例精准填充的色块
            val dayHeight = HOUR_CELL_HEIGHT * CalendarLayout.HOURS_PER_DAY
            // 每小时的显示高度，用于把「小时」换算成「dp」
            val hourHeight = HOUR_CELL_HEIGHT
            weekDates.forEach { date ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(dayHeight)
                        .clickable { onPickDate(date) },
                ) {
                    // 底格：每小时一格，只作视觉刻度
                    Column(modifier = Modifier.fillMaxSize()) {
                        (0 until CalendarLayout.HOURS_PER_DAY).forEach { hour ->
                            Box(
                                modifier = Modifier
                                    .height(HOUR_CELL_HEIGHT)
                                    .fillMaxWidth()
                                    .padding(1.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                    ),
                            )
                        }
                    }
                    // 色块：起点与高度都按真实时刻计算，因此半小时的事就占半格，
                    // 而不是把整格填满——这正是「按时间比例精准填充」的含义。
                    CalendarLayout.busySpans(todos, date, zone).forEach { (startHour, endHour) ->
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 1.dp)
                                .offset(y = hourHeight * startHour.toFloat())
                                .fillMaxWidth()
                                .height(hourHeight * (endHour - startHour).toFloat())
                                .clip(RoundedCornerShape(2.dp))
                                .background(MaterialTheme.colorScheme.primary),
                        )
                    }
                }
            }
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

private val HOUR_CELL_HEIGHT = 22.dp

const val TAG_WEEK_COLUMN_PREFIX = "week_col_"
const val TAG_MONTH_CELL_PREFIX = "month_cell_"
const val TAG_TODO_EDIT = "todo_edit"
const val TAG_TODO_DELETE = "todo_delete"
