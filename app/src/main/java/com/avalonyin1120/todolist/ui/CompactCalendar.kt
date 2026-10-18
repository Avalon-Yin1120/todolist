package com.avalonyin1120.todolist.ui

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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.avalonyin1120.todolist.domain.RecurrenceOptions
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * 自绘的紧凑日历。
 *
 * 为什么不用 Material3 的 [androidx.compose.material3.DatePicker]：它有一个
 * 大于对话框可用宽度的最小宽度约束，放在 AlertDialog 里时**最右一列会被裁掉**
 * （用户两次反馈这个问题）。自绘后宽度完全跟随父容器，7 列必然放得下。
 *
 * 交互：左右箭头翻月、手指左右横滑翻月（两者都走同一套页码映射，动效一致）、
 * 点某天选中、今天有描边。
 *
 * 横滑用 [HorizontalPager] 而不是自定义手势，原因与整体日历的 CalendarPager 相同：
 * 它天然提供「跟手」体验——滑到一半停住内容就停在半途，松手才吸附到最近一页。
 * 这里位于 AlertDialog 的 verticalScroll 里，横滑与竖滚不同轴，互不冲突。
 */
@Composable
fun CompactCalendar(
    month: YearMonth,
    selected: LocalDate?,
    today: LocalDate,
    onSelect: (LocalDate) -> Unit,
    /**
     * 点了左/右翻月箭头：参数是**相对**位移（-1 / +1）。
     *
     * 调用方必须拿「当前值」去累加，**不能**在组合时先算好 `month.plusMonths(1)` 再回调。
     * 后者是 v31 的真实 bug：连按两下时第二次点击用的还是组合时捕获的旧月份，
     * 两次都算出同一个月，于是「连按翻页只动一格」（用户反馈：新增待办里调日期、
     * 视图里跳转对话框都不灵，而视图顶部那排按钮因为按页码加减所以正常）。
     */
    onShiftMonth: (Int) -> Unit,
    /** 手指横滑停稳在某个月：参数是绝对月份（滑动本来就知道自己停在哪一页）。 */
    onSwipeMonth: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 初始页就是「当前月份」所在的绝对页：每次打开对话框都是新的组合，
    // 首帧即正确，不会先闪一下锚点页再滚过去。
    // coerceIn 只是兜底：手输年份（4 位数字）可能落在 ±100 年窗口之外，
    // 直接把越界页号交给 pager 会得到一页空白。
    val pagerState = rememberPagerState(
        initialPage = pageOfMonth(month).coerceIn(0, MONTH_PAGE_COUNT - 1),
        pageCount = { MONTH_PAGE_COUNT },
    )

    // 下面两个 effect 的键都不含回调/月份，会长期存活。
    // LaunchedEffect 不会看到重建之后的新 lambda，所以闭包里的值必须用
    // rememberUpdatedState 取最新——否则 collect 里用的还是首次组合那个
    // onSwipeMonth（以及过期的月份），上报会被静默丢弃或重复。
    val currentOnSwipeMonth by rememberUpdatedState(onSwipeMonth)
    val currentMonth by rememberUpdatedState(month)

    // 「我们自己要滚到哪一页」。非空期间**禁止**把落定页往上报。
    //
    // 这是「连按翻页只动一格」的根因：连按时前一次动画会被新目标取消，pager 先落定在
    // 中间那一页，这个中间值一旦上报回去就把上层月份写回旧值，用户的第二次、第三次点击
    // 白点。（v31 里箭头是「组合时月份 + 1」，我误以为是捕获了旧值；实测慢按三次能到
    // 三个月后、快按三次只到两个月后，才定位到是这里的动画竞赛。）
    var pendingPage by remember { mutableStateOf<Int?>(null) }

    // 外部改了 month（左右箭头按钮、或点了相邻月份的补位日期）→ 让 pager 跟过去。
    //
    // 「页码不同才滚」这个相等判断是**必须**的：pager 滑停后会把新月份通过下面的
    // effect 上报，上层再把同一个 month 传回来；若无条件 animateScrollToPage，
    // 两者就会互相打断，最终停在错误的页上。整体日历（TodoListScreen 的
    // CalendarPager）踩过这个坑，那里对 jumpRequest/selectedDate 的处理是同一道理。
    LaunchedEffect(month) {
        val target = pageOfMonth(month).coerceIn(0, MONTH_PAGE_COUNT - 1)
        if (target != pagerState.currentPage) {
            pendingPage = target
            try {
                pagerState.animateScrollToPage(target)
            } finally {
                // 只有「当前目标还是这一页」时才清空；已被更新的目标接管就别动它
                if (pendingPage == target) pendingPage = null
            }
        }
    }

    // 用户横滑结束后上报当前月份。
    //
    // 用 settledPage（滚动**停稳后**所在的那一页）而不是 currentPage：
    // currentPage 是「离吸附点最近的那一页」，拖动过程中就会来回变，
    // 拿它上报会让上层月份在手指还没松开时乱跳。
    // （Compose Foundation 1.7.6 里 PagerState 有 currentPage / settledPage / targetPage，
    // 没有 currentTargetPage。）
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            // 我们正主动滚过去、或用户手指还没离开：这时的落定值不代表用户的选择
            if (pendingPage != null || pagerState.isScrollInProgress) return@collect
            val shown = monthOfPage(page)
            // 只在月份真的变了才上报：snapshotFlow 会先发一次初始值，
            // 外部翻月导致的同一页也不该再发一次（否则又是一条回路）。
            if (shown != currentMonth) currentOnSwipeMonth(shown)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // 月份切换：箭头仍然直接改 month，由上面的 LaunchedEffect(month) 驱动 pager 动画，
        // 这样按钮与手势共用同一套页码，不会出现两套状态各说各话。
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(
                onClick = { onShiftMonth(-1) },
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
                onClick = { onShiftMonth(1) },
                modifier = Modifier.testTag(TAG_CALENDAR_NEXT_MONTH),
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下个月")
            }
        }

        // 星期表头：周一开头，与全应用一致；它属于控件框架而不是某一页，
        // 所以放在 pager 外面，不跟着左右滑。
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

        // 竖直高度由内容自身撑开：每页固定 6 行 × 7 列、格子 aspectRatio(1f)，
        // 行数与格子尺寸都固定，所以 pager 的高度是确定的，滑动时不会跳动。
        // 不要再加 fillMaxHeight：对话框里日历只该占它需要的那点高度。
        HorizontalPager(
            state = pagerState,
            // testTag 加在 pager 本身，仪器化测试可以对它做 swipeLeft/swipeRight 来翻月
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TAG_CALENDAR_PAGER),
        ) { page ->
            // 注意这里用 monthOfPage(page) 而不是外层的 month：
            // 每一页都必须画出「它自己那一页」对应的月份，滑到一半时相邻两页同时可见。
            MonthGrid(
                month = monthOfPage(page),
                selected = selected,
                today = today,
                onSelect = onSelect,
            )
        }
    }
}

/** 一页：固定 42 天 = 6 行 × 7 列，行数与格子尺寸固定，因此每页高度一致。 */
@Composable
private fun MonthGrid(
    month: YearMonth,
    selected: LocalDate?,
    today: LocalDate,
    onSelect: (LocalDate) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        CalendarLayout.monthGridDatesFixed(month).chunked(7).forEach { week ->
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
                                // testTag 只加在「属于本页月份」的格子上。
                                // HorizontalPager 会同时组合相邻页，而补位日期会同时出现在
                                // 两个月里（例如 2026-01-29 既在 1 月页尾部，也在 2 月页头部），
                                // 两页都打标签就会出现重复节点，仪器化测试用 onNodeWithTag
                                // 定位会直接抛异常。补位日期仍然可以点击、仍然用淡色显示。
                                .then(
                                    if (inMonth) {
                                        Modifier.testTag(TAG_CALENDAR_DAY_PREFIX + date.toString())
                                    } else {
                                        Modifier
                                    },
                                ),
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
            }
        }
    }
}

// ---------------------------------------------------------------------------
// 页 <-> 月份 的换算：绝对基准，纯函数
//
// 与 TodoListScreen 里 dateOfPage/pageOfDate 是同一套思路：页 0 固定对应
// MONTH_EPOCH，页与月份互为反函数，不受任何会话状态影响，
// 于是滑动过程中每一页都能独立算出自己该画哪个月。
//
// 不用「可变锚点 + 页码偏移」做两次换算：那会让映射随滑动漂移，
// 项目在整体日历上已经踩过（表现为「左滑月份不变」「好像滑到头了」）。
// ---------------------------------------------------------------------------

/** 页 0 对应的月份。选 2000-01 只是为了远离实际使用范围，避免触到两端。 */
private val MONTH_EPOCH: YearMonth = YearMonth.of(2000, 1)

/** 锚点页：[MONTH_EPOCH] 对应的页号。 */
internal const val MONTH_MIDDLE = 1200

/** 页总数，必须是奇数，保证 [MONTH_MIDDLE] 两侧对称（覆盖 2000-01 前后各 100 年）。 */
internal const val MONTH_PAGE_COUNT = 2401

/** 第 [page] 页代表哪个月。 */
internal fun monthOfPage(page: Int): YearMonth =
    MONTH_EPOCH.plusMonths((page - MONTH_MIDDLE).toLong())

/** [month] 落在第几页。 */
internal fun pageOfMonth(month: YearMonth): Int =
    MONTH_MIDDLE + ChronoUnit.MONTHS.between(MONTH_EPOCH, month).toInt()

const val TAG_CALENDAR_PREV_MONTH = "calendar_prev_month"
const val TAG_CALENDAR_NEXT_MONTH = "calendar_next_month"
const val TAG_CALENDAR_MONTH_LABEL = "calendar_month_label"
const val TAG_CALENDAR_DAY_PREFIX = "calendar_day_"
const val TAG_CALENDAR_PAGER = "calendar_pager"
