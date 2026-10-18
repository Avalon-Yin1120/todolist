package com.avalonyin1120.todolist.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * 把一句中文变成待办设置（「明天下午三点半开周会，提前 20 分钟提醒」）。
 *
 * ## 设计原则
 *
 * * **宁可少认，不要乱认**：只认下面这张词表里写死的说法。没认出来的部分原样留在名称里，
 *   用户一眼能看出「它没把我的话吃掉」。绝不猜「大概是指下周」这种。
 * * **纯函数**：不给 now 之外的上下文、不碰网络、不写日志。于是单测可以钉死每一个词。
 * * **识别结果回填到表单**，不是直接建待办：用户能改，也能看见它读成了什么（[matched]）。
 *
 * ## 词表（报告里逐条列过）
 *
 * 日期：今天/今日/今早/今晚、明天/明日/明早/明晚、后天、大后天、昨天、前天、
 *   周一~周日 / 周天 / 星期X / 礼拜X、这周X / 本周X / 下周X、X月X日 / X月X号、
 *   下个月X号、N天后 / N天以后、下周末。
 * 时间段：凌晨 / 早上 / 早晨 / 清晨 / 上午 / 中午 / 午休 / 下午 / 傍晚 / 晚上 / 夜里。
 * 时间：X点半 / X点X分 / X点一刻 / X点三刻 / 整点 / HH:mm / HH点 / 中文数字（四点、十六点）。
 * 时长：X小时 / X个半小时……（见 [DURATION]）、半小时、一个半小时、X分钟。
 * 重复：每天/每日/天天、每周X、每星期X、每礼拜X、工作日、每周、每月X号、每月。
 * 提前：提前X分钟 / 提前半小时 / 提前X小时 / 提前一天。
 * 名称：剩下没被认走的字（去掉「提醒我」「记得」「要」这类开头的口头词）。
 */
object NaturalLanguage {

    /** 一天里各「时段词」的默认时刻（用户只说「下午」没说几点时用）。 */
    private val PERIOD_DEFAULTS: Map<String, LocalTime> = mapOf(
        "凌晨" to LocalTime.of(0, 0),
        "早上" to LocalTime.of(8, 0),
        "早晨" to LocalTime.of(8, 0),
        "清晨" to LocalTime.of(7, 0),
        "上午" to LocalTime.of(9, 0),
        "中午" to LocalTime.of(12, 0),
        "午休" to LocalTime.of(12, 30),
        "下午" to LocalTime.of(14, 0),
        "傍晚" to LocalTime.of(18, 0),
        "晚上" to LocalTime.of(20, 0),
        "夜里" to LocalTime.of(21, 0),
    )

    /** 「下午」类的词：遇到小时数 < 12 时要加 12 小时。 */
    private val PM_WORDS = setOf("下午", "傍晚", "晚上", "夜里")

    /** 「凌晨」类：12 点要当作 0 点。 */
    private val AM_WORDS = setOf("凌晨", "早上", "早晨", "清晨", "上午")

    private val WEEKDAY_WORDS = mapOf(
        "一" to DayOfWeek.MONDAY, "1" to DayOfWeek.MONDAY,
        "二" to DayOfWeek.TUESDAY, "2" to DayOfWeek.TUESDAY,
        "三" to DayOfWeek.WEDNESDAY, "3" to DayOfWeek.WEDNESDAY,
        "四" to DayOfWeek.THURSDAY, "4" to DayOfWeek.THURSDAY,
        "五" to DayOfWeek.FRIDAY, "5" to DayOfWeek.FRIDAY,
        "六" to DayOfWeek.SATURDAY, "6" to DayOfWeek.SATURDAY,
        "日" to DayOfWeek.SUNDAY, "天" to DayOfWeek.SUNDAY, "7" to DayOfWeek.SUNDAY,
    )

    /** 名称开头常见的口头词：解析完从名称前面剥掉，否则待办会叫「提醒我开周会」。 */
    private val NAME_PREFIXES = listOf(
        "提醒我", "请提醒我", "记得", "别忘了", "要", "需要", "安排", "帮我",
    )

    // ------------------------------------------------------------------ 结果

    /** 解析结果。[matched] 是命中的词（给界面显示「我读懂了什么」）。 */
    data class Parsed(
        val name: String,
        val startAt: Long,
        val durationMinutes: Int,
        val recurrence: Recurrence = Recurrence.NONE,
        val weekDays: Set<DayOfWeek> = emptySet(),
        val monthDays: Set<Int> = emptySet(),
        val leadMinutes: Int = 0,
        val matched: List<String> = emptyList(),
        /** 下面四个标记决定「回填时只改哪些字段」——没认出来的绝不能动。 */
        val hasDate: Boolean = false,
        val hasTime: Boolean = false,
        val hasDuration: Boolean = false,
        val hasRecurrence: Boolean = false,
        val hasLead: Boolean = false,
        /** 是否因为「今天这个点已经过了」而自动挪到了明天（界面要说明，不能悄悄改）。 */
        val movedToTomorrow: Boolean = false,
    )

    /** 默认时长：一小时。 */
    const val DEFAULT_DURATION_MINUTES = 60

    // ------------------------------------------------------------------ 主入口

    /**
     * 解析 [input]。**返回 null 表示「一句话都没认出来」**（例如用户只输了「开周会」），
     * 此时界面应提示「没认出时间」，而不是拿一个错误的默认值填进表单。
     */
    fun parse(input: String, now: Long, zone: ZoneId): Parsed? {
        val matched = mutableListOf<String>()
        var rest = normalize(input)
        if (rest.isBlank()) return null

        // 1) 提前量（先摘掉，避免「提前 20 分钟」里的 20 被当成小时数）
        var leadMinutes = 0
        LEAD.find(rest)?.let { m ->
            leadMinutes = leadOf(m)
            matched += "提前${m.value}"
            rest = rest.removeRange(m.range)
        }

        // 2) 重复（先于日期：「每周一」里的「周一」不能被当成某一天）
        var recurrence = Recurrence.NONE
        var weekDays: Set<DayOfWeek> = emptySet()
        var monthDays: Set<Int> = emptySet()

        WORKDAY.find(rest)?.let { m ->
            recurrence = Recurrence.WEEKLY
            weekDays = setOf(
                DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
            )
            matched += "工作日"
            rest = rest.removeRange(m.range)
        }
        if (recurrence == Recurrence.NONE) {
            WEEKLY.find(rest)?.let { m ->
                recurrence = Recurrence.WEEKLY
                weekDays = m.groupValues[1].mapNotNull { WEEKDAY_WORDS[it.toString()] }.toSet()
                matched += "每周" + m.groupValues[1]
                rest = rest.removeRange(m.range)
            }
        }
        if (recurrence == Recurrence.NONE) {
            MONTHLY_DAY.find(rest)?.let { m ->
                recurrence = Recurrence.MONTHLY
                monthDays = setOf(cnNumber(m.groupValues[1]) ?: 1)
                matched += "每月${m.groupValues[1]}号"
                rest = rest.removeRange(m.range)
            }
        }
        if (recurrence == Recurrence.NONE) {
            DAILY.find(rest)?.let { m ->
                recurrence = Recurrence.DAILY
                matched += "每天"
                rest = rest.removeRange(m.range)
            }
        }
        // 光有「每周」「每月」没跟具体日子：按今天（或解析出的那一天）的星期/号数走，
        // 与表单「不选具体日子时回落到起始日」的既有语义一致。
        if (recurrence == Recurrence.NONE) {
            BARE_WEEKLY.find(rest)?.let { m ->
                recurrence = Recurrence.WEEKLY
                matched += "每周"
                rest = rest.removeRange(m.range)
            }
        }
        if (recurrence == Recurrence.NONE) {
            BARE_MONTHLY.find(rest)?.let { m ->
                recurrence = Recurrence.MONTHLY
                matched += "每月"
                rest = rest.removeRange(m.range)
            }
        }

        // 3) 日期
        // 3.0) 年份 / 月份偏移：「明年」「去年」「2027年」「下个月」
        var yearDelta = 0
        var monthDelta = 0
        YEAR.find(rest)?.let { m ->
            val word = m.groupValues[1]
            yearDelta = when {
                word == "前年" -> -2
                word == "去年" -> -1
                word == "今年" -> 0
                word == "明年" -> 1
                word == "后年" -> 2
                else -> {
                    val y = cnNumber(word.removeSuffix("年")) ?: currentYear(now, zone)
                    y - currentYear(now, zone)
                }
            }
            matched += word
            rest = rest.removeRange(m.range)
        }
        MONTH_OFFSET.find(rest)?.let { m ->
            monthDelta = when (m.value) {
                "上个月" -> -1
                "下下个月" -> 2
                "下个月" -> 1
                else -> 0
            }
            matched += m.value
            rest = rest.removeRange(m.range)
        }

        // 「下个月 10 号」「明年 5 号」：光有日号、没有「X 月」。只在有年/月偏移时才认，
        // 否则「10 号」这种孤立的日号会被误当成日期。
        var bareDay: Int? = null
        if ((yearDelta != 0 || monthDelta != 0) && !rest.contains("月")) {
            BARE_DAY.find(rest)?.let { m ->
                val d = cnNumber(m.groupValues[1])
                if (d != null && d in 1..31) {
                    bareDay = d
                    matched += m.value
                    rest = rest.removeRange(m.range)
                }
            }
        }
        var explicitToday = false
        var dayOffset: Long? = null
        var date: LocalDate? = null

        RELATIVE_DAY.find(rest)?.let { m ->
            val word = m.groupValues[1]
            dayOffset = when {
                word.startsWith("大后") -> 3L
                word.startsWith("后") -> 2L
                word.startsWith("明") || word.startsWith("次") -> 1L
                word.startsWith("昨") -> -1L
                word.startsWith("前") -> -2L
                else -> 0L
            }
            explicitToday = dayOffset == 0L
            matched += word
            rest = rest.removeRange(m.range)
        }

        if (date == null && dayOffset == null) {
            ABSOLUTE_DATE.find(rest)?.let { m ->
                val month = cnNumber(m.groupValues[1])
                val day = cnNumber(m.groupValues[2])
                if (month != null && day != null && month in 1..12 && day in 1..31) {
                    val baseYear = currentYear(now, zone) + yearDelta
                    date = runCatching { LocalDate.of(baseYear, month, day) }
                        .getOrNull()
                    matched += "${month}月${day}日"
                    rest = rest.removeRange(m.range)
                }
            }
        }

        if (date == null && dayOffset == null) {
            NDAYS.find(rest)?.let { m ->
                val n = cnNumber(m.groupValues[1])
                if (n != null && n in 0..365) {
                    dayOffset = n.toLong()
                    explicitToday = n == 0
                    matched += "N天后"
                    rest = rest.removeRange(m.range)
                }
            }
        }

        if (date == null && dayOffset == null) {
            WEEKDAY_DATE.find(rest)?.let { m ->
                val prefix = m.groupValues[1]      // 这周 / 本周 / 下周 / 空
                val days = m.groupValues[2].mapNotNull { WEEKDAY_WORDS[it.toString()] }.toSet()
                if (days.isNotEmpty()) {
                    val base = today(now, zone)
                    val target = nearestWeekday(base, days.single(), nextWeek = prefix == "下")
                    date = target
                    matched += (prefix + "周" + m.groupValues[2])
                    rest = rest.removeRange(m.range)
                }
            }
        }

        // 4) 时间
        var time: LocalTime? = null
        var period: String? = null

        // 3.5) 时间区间**先**试：「3 点到 5 点」「下午三点到五点」「15:00-17:00」。
        //
        // 必须先于时段词剥离，且两端的时段词各自独立。旧实现先剥掉一个时段词再用它套两端，
        // 于是「明天早上五点到下午六点」只认出「五点」：剩下的「到下午六点」被塞进名称，
        // 时长按默认 1 小时算，结束时间还是 6:00 而不是 18:00（用户报的 bug）。
        var rangeEnd: LocalTime? = null
        RANGE.find(rest)?.let { m ->
            val p1 = m.groupValues[1].ifBlank { null }
            val h1 = cnNumber(m.groupValues[2])
            val min1 = minuteOf(m.groupValues[3], m.groupValues[4])
            val p2 = m.groupValues[5].ifBlank { null }
            val h2 = cnNumber(m.groupValues[6])
            val min2 = minuteOf(m.groupValues[7], m.groupValues[8])
            if (h1 != null && h2 != null && h1 in 0..24 && h2 in 0..24) {
                var s = LocalTime.of(h1.coerceAtMost(23), min1)
                var e = LocalTime.of(h2.coerceAtMost(23), min2)
                // 结束端没写时段时沿用起始端（「下午三点到五点」= 15:00–17:00）
                val endPeriod = p2 ?: p1
                if (p1 != null) s = applyPeriod(s, p1)
                if (endPeriod != null) e = applyPeriod(e, endPeriod)
                // 两端都没写时段时才用「结束早于开始就当作下午」的老规矩（「11点到1点」）。
                // 写了时段就得照字面来：「晚上十一点到凌晨一点」是跨夜两小时，不能挪成 13:00。
                if (p1 == null && p2 == null && !e.isAfter(s) && e.hour < 12) {
                    e = e.plusHours(12)
                }
                time = s
                rangeEnd = e
                matched += "%d:%02d–%d:%02d".format(s.hour, s.minute, e.hour, e.minute)
                rest = rest.removeRange(m.range)
            }
        }

        // 单字时段：「晚九点」「早七点」「夜十一点」。只在后面紧跟「数字+点」时才认，
        // 免得把「晚会」「早餐」这类名字里的字当成时段（用户报过「明天晚九点吃药」被吃成「晚吃药」）。
        SHORT_PERIOD.find(rest)?.let { m ->
            period = when (m.groupValues[1]) {
                "早" -> "早上"
                "晚" -> "晚上"
                else -> "夜里"
            }
            matched += m.value
            rest = rest.removeRange(m.range)
        }
        PERIOD.find(rest)?.let { m ->
            period = m.value
            matched += m.value
            rest = rest.removeRange(m.range)
        }

        if (rangeEnd == null) CLOCK_COLON.find(rest)?.let { m ->
            val h = cnNumber(m.groupValues[1]) ?: -1
            val min = cnNumber(m.groupValues[2]) ?: 0
            if (h in 0..24 && min in 0..59) {
                time = LocalTime.of(h.coerceAtMost(23), min)
                matched += "%d:%02d".format(h, min)
                rest = rest.removeRange(m.range)
            }
        }

        if (time == null && rangeEnd == null) {
            CLOCK_POINT.find(rest)?.let { m ->
                val h = cnNumber(m.groupValues[1])
                val tail = m.groupValues[2]
                if (h != null && h in 0..24) {
                    val minute = when {
                        tail.contains("半") -> 30
                        tail.contains("一刻") -> 15
                        tail.contains("三刻") -> 45
                        tail.isBlank() -> 0
                        else -> cnNumber(tail) ?: 0
                    }
                    time = LocalTime.of(h.coerceAtMost(23), minute.coerceIn(0, 59))
                    matched += "%d:%02d".format(h, minute)
                    rest = rest.removeRange(m.range)
                }
            }
        }

        // 5) 时长
        var duration = DEFAULT_DURATION_MINUTES
        val durationMatch = DURATION.find(rest)
        val minutesMatch = if (durationMatch == null) MINUTES_DURATION.find(rest) else null
        when {
            durationMatch != null -> {
                val m = durationMatch
                val q = m.groupValues[1]
                val value = when {
                    q.contains("一个半") -> 90
                    q == "半" -> 30
                    q.endsWith("半") -> ((cnNumber(q.removeSuffix("半").removeSuffix("个")) ?: 1) * 60) + 30
                    else -> (cnNumber(q.removeSuffix("个")) ?: 1) * 60
                }
                duration = value.coerceIn(5, 24 * 60)
                matched += m.value
                rest = rest.removeRange(m.range)
            }

            minutesMatch != null -> {
                duration = (cnNumber(minutesMatch.groupValues[1]) ?: 10).coerceIn(5, 24 * 60)
                matched += minutesMatch.value
                rest = rest.removeRange(minutesMatch.range)
            }
        }

        // 6) 名称
        var name = rest.trim().trim('，', ',', '。', '.', '；', ';', '、', '：', ':')
        NAME_PREFIXES.forEach { prefix -> if (name.startsWith(prefix)) name = name.removePrefix(prefix) }
        name = name.trim().trim('，', ',', '。', '.')
        if (name.isBlank()) name = "待办"
        name = name.take(80)

        // 7) 合并成时间戳
        // 日期没给：默认今天；但如果算出来的时刻已经过去，且用户**没有**说「今天」，
        // 就顺延到明天——「下午 4 点开会」在晚上 6 点说出口，意思显然是明天。
        if (time == null && period == null) {
            // 一句话里连时间都没有（只有日期/重复）：按「现在时刻」排，仍然算解析成功
            if (matched.none { it.contains(":") } && dayOffset == null && date == null &&
                recurrence == Recurrence.NONE
            ) {
                return null
            }
        }

        val shifted = today(now, zone).plusYears(yearDelta.toLong()).plusMonths(monthDelta.toLong())
        val withDay = bareDay?.let { d ->
            runCatching { shifted.withDayOfMonth(d.coerceAtMost(shifted.lengthOfMonth())) }.getOrNull()
        }
        val baseDate = date ?: withDay
            ?: dayOffset?.let { today(now, zone).plusDays(it) }
            // 「明年 3 月 1 号」里的 3 月 1 号已被 ABSOLUTE_DATE 解析成今年，
            // 这里再按年/月偏移整体挪一次，两者就能组合
            ?: today(now, zone).plusYears(yearDelta.toLong()).plusMonths(monthDelta.toLong())

        var clock = time ?: period?.let { PERIOD_DEFAULTS[it] } ?: LocalTime.of(9, 0)
        // 区间两端的时段已经各自处理过；再拿残留的时段词套一遍会把它弄错
        // （「下午茶 3 点到 5 点」不该变成 15:00–17:00）。只对非区间句生效。
        if (rangeEnd == null && time != null && period != null) clock = applyPeriod(clock, period!!)

        var startDate = baseDate
        var start = LocalDateTime.of(startDate, clock).atZone(zone).toInstant().toEpochMilli()
        var moved = false
        if (dayOffset == null && date == null && !explicitToday && start <= now) {
            startDate = startDate.plusDays(1)
            start = LocalDateTime.of(startDate, clock).atZone(zone).toInstant().toEpochMilli()
            moved = true
        }

        // 周期待办且用户没指定具体日期：必须落到「下一场符合规则、并且还没到」的那一天。
        // 否则「每月 1 号」会被排到「今天 +1 天」，「每周一」在今天就是周一时会被排到周二。
        if (date == null && dayOffset == null && recurrence != Recurrence.NONE) {
            var candidate = baseDate
            var guard = 0
            while (guard < 400) {
                val at = LocalDateTime.of(candidate, clock).atZone(zone).toInstant().toEpochMilli()
                if (at > now && matchesRule(recurrence, weekDays, monthDays, candidate)) {
                    startDate = candidate
                    start = at
                    moved = candidate != baseDate
                    break
                }
                candidate = candidate.plusDays(1)
                guard++
            }
        }

        if (rangeEnd != null) {
            val s = LocalDateTime.of(startDate, clock)
            val e = LocalDateTime.of(startDate, rangeEnd)
            val eFixed = if (e.isAfter(s)) e else e.plusDays(1)
            duration = java.time.Duration.between(s, eFixed).toMinutes().toInt().coerceIn(5, 24 * 60)
        }

        return Parsed(
            name = name,
            startAt = start,
            durationMinutes = duration,
            recurrence = recurrence,
            weekDays = weekDays,
            monthDays = monthDays,
            leadMinutes = leadMinutes,
            matched = matched,
            movedToTomorrow = moved,
            hasDate = dayOffset != null || date != null || explicitToday,
            hasTime = time != null || period != null,
            hasDuration = rangeEnd != null || duration != DEFAULT_DURATION_MINUTES,
            hasRecurrence = recurrence != Recurrence.NONE,
            hasLead = leadMinutes > 0,
        )
    }

    // ------------------------------------------------------------------ 正则

    private val LEAD = Regex("提前(\\d+|[一二两三四五六七八九十]+)(分钟|分|小时|钟头|天|半小时)")
    private val WORKDAY = Regex("工作日")
    private val WEEKLY = Regex("每(?:周|星期|礼拜)([一二三四五六日天1-7]+)")
    private val MONTHLY_DAY = Regex("每(?:个)?月(\\d+|[一二三四五六七八九十]+)[号日]")
    private val DAILY = Regex("每天|每日|天天")
    private val BARE_WEEKLY = Regex("每(?:周|星期|礼拜)")
    private val BARE_MONTHLY = Regex("每(?:个)?月")

    private val RELATIVE_DAY = Regex("(大后天|后天|明天|明日|明早|明晚|次早|今天|今日|今早|今晚|昨天|昨日|前天)")
    private val ABSOLUTE_DATE = Regex("(\\d{1,2}|[一二三四五六七八九十]+)月(\\d{1,2}|[一二三四五六七八九十]+)[日号]")
    private val NDAYS = Regex("(\\d+|[一二三四五六七八九十]+)天(?:之?后)")
    private val WEEKDAY_DATE = Regex("(下周|下星期|下礼拜|这周|这星期|本周|本星期|周|星期|礼拜)([一二三四五六日天1-7])")

    /** 年份：今年/明年/后年/去年/前年/2027年。 */
    private val YEAR = Regex("(前年|去年|今年|明年|后年|(?:\\d{4})年)")

    /** 月份偏移：下个月 / 下下个月 / 上个月 / 这个月。 */
    private val MONTH_OFFSET = Regex("(上个月|下下个月|下个月|这个月)")
    private val BARE_DAY = Regex("(\\d{1,2}|[一二三四五六七八九十]+)[号日]")

    private val PERIOD = Regex("(凌晨|早上|早晨|清晨|上午|中午|午休|下午|傍晚|晚上|夜里)")

    /** 单字时段（早/晚/夜），后面必须紧跟「数字+点/时」才认。 */
    private val SHORT_PERIOD = Regex("([早晚夜])(?=(?:\\d{1,2}|[一二三四五六七八九十两]+)(?:点|时))")

    /** 时段词，与 [PERIOD_DEFAULTS] 的键一一对应。 */
    private const val PERIOD_WORDS_ALT = "凌晨|早上|早晨|清晨|上午|中午|午休|下午|傍晚|晚上|夜里"

    /** 中文数字（含「两」）或 1~2 位阿拉伯数字。 */
    private const val NUMBER_ALT = "\\d{1,2}|[一二三四五六七八九十两]+"

    /**
     * 时间区间：3点到5点 / 下午三点到五点 / 15:00-17:00 / 早上五点到下午六点。
     *
     * 两端各自带一个可选时段词（组 1、组 5），所以「早上 X 点到下午 Y 点」能分别解释；
     * 时刻必须带「点 / 时 / :」分隔符，避免把「五到六个」这类数量词当成时间。
     *
     * 组：1 起始时段、2 起始小时、3 起始分钟原文、4 起始分钟数字、
     *     5 结束时段、6 结束小时、7 结束分钟原文、8 结束分钟数字
     */
    private val RANGE = Regex(
        "(?:($PERIOD_WORDS_ALT)\\s*)?($NUMBER_ALT)\\s*(?:点|时|[:：])\\s*" +
            "(半|一刻|三刻|(?:($NUMBER_ALT)\\s*分?))?\\s*" +
            "(?:到|至|~|－|-|—)\\s*" +
            "(?:($PERIOD_WORDS_ALT)\\s*)?($NUMBER_ALT)\\s*(?:点|时|[:：])\\s*" +
            "(半|一刻|三刻|(?:($NUMBER_ALT)\\s*分?))?"
    )

    private val CLOCK_COLON = Regex("(\\d{1,2}|[一二三四五六七八九十]+)[:：](\\d{1,2}|[一二三四五六七八九十]+)")
    private val CLOCK_POINT = Regex("(\\d{1,2}|[一二三四五六七八九十两]+)(?:点|时)(半|一刻|三刻|(\\d{1,2}|[一二三四五六七八九十]+)分?)?")

    private val DURATION = Regex("(?:持续|用时|开)?([一二两三四五六七八九十\\d]*(?:个)?(?:半)?)(小时|钟头)")
    private val MINUTES_DURATION = Regex("(\\d+|[一二三四五六七八九十]+)(?:分钟|分(?:钟)?)")

    // ------------------------------------------------------------------ 小工具

    /** 全角转半角、去空白（中文输入法打出来的「：」「１２」必须能认）。 */
    fun normalize(input: String): String {
        val sb = StringBuilder(input.length)
        input.forEach { ch ->
            sb.append(
                when (ch) {
                    in '０'..'９' -> '0' + (ch - '０')
                    '：' -> ':'
                    '　' -> ' '
                    else -> ch
                }
            )
        }
        return sb.toString().replace(Regex("\\s+"), " ").trim()
    }

    /** 中文数字（1..99）：四 / 十 / 十二 / 二十 / 二十三 / 两。认不出返回 null。 */
    fun cnNumber(text: String): Int? {
        val s = text.trim()
        if (s.isEmpty()) return null
        s.toIntOrNull()?.let { return it }
        var total = 0
        var current = 0
        var sawTen = false
        for (ch in s) {
            when (ch) {
                '十' -> {
                    total += (if (current == 0) 1 else current) * 10
                    current = 0
                    sawTen = true
                }
                '两' -> current = 2
                '一', '二', '三', '四', '五', '六', '七', '八', '九', '零' -> {
                    val v = when (ch) {
                        '零' -> 0
                        '一' -> 1
                        '二' -> 2
                        '三' -> 3
                        '四' -> 4
                        '五' -> 5
                        '六' -> 6
                        '七' -> 7
                        '八' -> 8
                        else -> 9
                    }
                    current = if (sawTen) v else current * 10 + v
                }
                else -> return null
            }
        }
        return total + current
    }

    /** 区间里的分钟数：「半」30、「一刻」15、「三刻」45、「5 点 30 分」取 30。 */
    private fun minuteOf(raw: String, num: String): Int = when {
        raw.contains("半") -> 30
        raw.contains("一刻") -> 15
        raw.contains("三刻") -> 45
        else -> cnNumber(num)?.coerceIn(0, 59) ?: 0
    }

    private fun leadOf(m: MatchResult): Int {
        val n = cnNumber(m.groupValues[1]) ?: 0
        return when (m.groupValues[2]) {
            "分钟", "分" -> n
            "小时", "钟头" -> n * 60
            "天" -> n * 24 * 60
            "半小时" -> 30
            else -> 0
        }.coerceIn(0, 10080)
    }

    /** 「下午4点」→ 16 点；「凌晨12点」→ 0 点。 */
    private fun applyPeriod(clock: LocalTime, period: String): LocalTime = when {
        period in PM_WORDS && clock.hour < 12 -> clock.plusHours(12)
        period in AM_WORDS && clock.hour == 12 -> LocalTime.of(0, clock.minute)
        else -> clock
    }

    /** 这一天是否符合该重复规则（与 RecurrenceExpander 的判定口径一致）。 */
    private fun matchesRule(
        recurrence: Recurrence,
        weekDays: Set<DayOfWeek>,
        monthDays: Set<Int>,
        date: LocalDate,
    ): Boolean = when (recurrence) {
        Recurrence.NONE -> true
        Recurrence.DAILY -> true
        Recurrence.WEEKLY -> date.dayOfWeek in weekDays
        Recurrence.MONTHLY -> date.dayOfMonth in monthDays
    }

    private fun currentYear(now: Long, zone: ZoneId): Int = today(now, zone).year

    private fun today(now: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(now).atZone(zone).toLocalDate()

    /**
     * 距离 [base] 最近的某个星期几。
     *
     * [nextWeek] 为 true 时表示「下周X」，严格取下一周的那一天（即使今天就是周X）。
     */
    private fun nearestWeekday(
        base: LocalDate,
        target: DayOfWeek,
        nextWeek: Boolean,
    ): LocalDate {
        val monday = base.minusDays((base.dayOfWeek.value - 1).toLong())
        val inThisWeek = monday.plusDays((target.value - 1).toLong())
        return when {
            nextWeek -> inThisWeek.plusWeeks(1)
            !inThisWeek.isBefore(base) -> inThisWeek
            else -> inThisWeek.plusWeeks(1)
        }
    }
}
