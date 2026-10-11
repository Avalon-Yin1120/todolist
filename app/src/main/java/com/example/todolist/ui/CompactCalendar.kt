package com.example.todolist.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.todolist.domain.RecurrenceOptions
import java.time.LocalDate
import java.time.YearMonth

/**
 * 自绘的紧凑日历。
 *
 * 为什么不用 Material3 的 [androidx.compose.material3.DatePicker]：它有一个
 * 大于对话框可用宽度的最小宽度约束，放在 AlertDialog 里时**最右一列会被裁掉**
 * （用户两次反馈这个问题）。自绘后宽度完全跟随父容器，7 列必然放得下。
 *
 * 只保留必要的交互：左右翻月、点某天选中、今天有描边。
 */
@Composable
fun CompactCalendar(
    month: YearMonth,
    selected: LocalDate?,
    today: LocalDate,
    onSelect: (LocalDate) -> Unit,
    onMonthChange: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // 月份切换
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(
                onClick = { onMonthChange(month.minusMonths(1)) },
                modifier = Modifier.testTag(TAG_CALENDAR_PREV_MONTH),
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上个月")
            }
            Text(
                text = "${month.year} 年 ${month.monthValue} 月",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.testTag(TAG_CALENDAR_MONTH_LABEL),
            )
            IconButton(
                onClick = { onMonthChange(month.plusMonths(1)) },
                modifier = Modifier.testTag(TAG_CALENDAR_NEXT_MONTH),
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下个月")
            }
        }

        // 星期表头：周一开头，与全应用一致
        Row(modifier = Modifier.fillMaxWidth()) {
            RecurrenceOptions.WEEK_ORDER.forEach { day ->
                Text(
                    text = RecurrenceOptions.shortLabel(day),
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // 日期网格：6 行 × 7 列，用 aspectRatio 保证格子是正方形且能均分宽度
        val dates = CalendarLayout.monthGridDates(month)
        dates.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    val inMonth = YearMonth.from(date) == month
                    val isSelected = date == selected
                    val isToday = date == today
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(1.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .padding(2.dp)
                                .background(
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        Color.Transparent
                                    },
                                    shape = CircleShape,
                                )
                                .border(
                                    width = if (isToday && !isSelected) 1.dp else 0.dp,
                                    color = if (isToday && !isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        Color.Transparent
                                    },
                                    shape = CircleShape,
                                )
                                .clickable { onSelect(date) }
                                .testTag(TAG_CALENDAR_DAY_PREFIX + date.toString()),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = date.dayOfMonth.toString(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = when {
                                    isSelected -> MaterialTheme.colorScheme.onPrimary
                                    inMonth -> MaterialTheme.colorScheme.onSurface
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        .copy(alpha = 0.45f)
                                },
                                fontWeight = if (isSelected || isToday) {
                                    FontWeight.SemiBold
                                } else {
                                    FontWeight.Normal
                                },
                            )
                        }
                    }
                }
                // 最后一行不足 7 天时补占位，避免格子被拉伸
                repeat(7 - week.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}

const val TAG_CALENDAR_PREV_MONTH = "calendar_prev_month"
const val TAG_CALENDAR_NEXT_MONTH = "calendar_next_month"
const val TAG_CALENDAR_MONTH_LABEL = "calendar_month_label"
const val TAG_CALENDAR_DAY_PREFIX = "calendar_day_"
