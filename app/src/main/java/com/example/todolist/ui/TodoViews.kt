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

    /** 月视图的红色深浅：档位越高表示当天越忙。 */
    fun monthLevelColor(level: Int): Color = when (level) {
        0 -> Color(0x00000000)
        1 -> Color(0x33EF4444)
        2 -> Color(0x66EF4444)
        3 -> Color(0xAAEF4444)
        else -> Color(0xFFDC2626)
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
            // 7 天 × 24 小时的格子
            weekDates.forEach { date ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onPickDate(date) },
                ) {
                    (0 until CalendarLayout.HOURS_PER_DAY).forEach { hour ->
                        val busy = CalendarLayout.hasTodoInHour(todos, date, hour, zone)
                        Box(
                            modifier = Modifier
                                .height(HOUR_CELL_HEIGHT)
                                .fillMaxWidth()
                                .padding(1.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    if (busy) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                ),
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
                    val busyMillis = CalendarLayout.busyDurationMillis(todos, date, zone)
                    val level = CalendarLayout.busyLevel(busyMillis / 3_600_000.0)
                    val hasAny = busyMillis > 0
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize()
                            .padding(2.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(TodoColors.monthLevelColor(level))
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
            (0..4).forEach { level ->
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(TodoColors.monthLevelColor(level))
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
