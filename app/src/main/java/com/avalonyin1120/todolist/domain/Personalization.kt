package com.avalonyin1120.todolist.domain

/**
 * 个性化设置：配色与「提前提醒」快捷按钮。
 *
 * 设计取舍：
 * * **只存一个强调色**，浅底色由它算出来。让人手挑两个色（底色 + 强调色）既难用
 *   又容易挑出「字看不清」的组合；给一个色、由代码按固定比例调浅，怎么选都不会难看。
 * * **所有字段都可缺省**：解析失败的项回落到默认值，而不是整份设置作废。
 *   这样以后新增一项时，老版本存下来的字符串仍然能读。
 * * [isDefault] 用来判断「恢复默认」按钮要不要可点。
 */
data class Personalization(
    /** 卡片颜色的依据：按状态 / 按提醒方式 / 统一单色。 */
    val colorMode: CardColorMode = CardColorMode.STATUS,
    /** 状态 → 强调色（ARGB）。缺的键回落到默认色。 */
    val statusColors: Map<TimeBucket, Long> = DEFAULT_STATUS_COLORS,
    /** 提醒方式 → 强调色（ARGB）。 */
    val reminderColors: Map<ReminderMode, Long> = DEFAULT_REMINDER_COLORS,
    /** 表单里「提前提醒」那排快捷按钮的分钟数（去重、升序、最多 [MAX_PRESETS] 个）。 */
    val leadPresets: List<Int> = DEFAULT_LEAD_PRESETS,
) {

    fun statusColor(bucket: TimeBucket): Long =
        statusColors[bucket] ?: DEFAULT_STATUS_COLORS.getValue(bucket)

    fun reminderColor(mode: ReminderMode): Long =
        reminderColors[mode] ?: DEFAULT_REMINDER_COLORS.getValue(mode)

    /** 与出厂设置完全一致（「恢复默认」按钮据此置灰）。 */
    val isDefault: Boolean
        get() = this == Personalization()

    /**
     * 规范化：夹取越界值、去重、排序。
     *
     * 每次写入前都过一遍，于是即使设置文件被手改坏，界面上也不会出现
     * 「提前量 100000 分钟」或「同一个按钮出现两次」这种怪状态。
     */
    fun normalized(): Personalization = copy(
        leadPresets = leadPresets
            .filter { it in MIN_LEAD_MINUTES..MAX_LEAD_MINUTES }
            .distinct()
            .sorted()
            .take(MAX_PRESETS)
            .ifEmpty { DEFAULT_LEAD_PRESETS },
        statusColors = TimeBucket.entries.associateWith { statusColor(it) },
        reminderColors = ReminderMode.entries.associateWith { reminderColor(it) },
    )

    /** 编码成一行字符串存进 SharedPreferences。 */
    fun encode(): String = buildString {
        append("v=1;mode=").append(colorMode.name)
        statusColors.entries.sortedBy { it.key.ordinal }.forEach {
            append(";s.").append(it.key.name).append('=').append(hex(it.value))
        }
        reminderColors.entries.sortedBy { it.key.ordinal }.forEach {
            append(";m.").append(it.key.name).append('=').append(hex(it.value))
        }
        append(";leads=").append(leadPresets.joinToString(","))
    }

    companion object {
        /** 表单快捷按钮的出厂值（与 v1 以来手写的五个按钮一致）。 */
        val DEFAULT_LEAD_PRESETS = listOf(0, 5, 30, 60, 1440)

        /** 可选的提前量（分钟）：够覆盖「准点 / 几分钟 / 几小时 / 一天」。 */
        val MINUTE_CHOICES = listOf(0, 5, 10, 15, 30, 60, 120, 1440)

        /** 快捷按钮最多几个：再多一行就挤了，`FlowRow` 也会折成两行。 */
        const val MAX_PRESETS = 6

        const val MIN_LEAD_MINUTES = 0

        /** 与 `FormValidator` 的上限一致（7 天）。 */
        const val MAX_LEAD_MINUTES = 10080

        /** 出厂配色：与 v1 以来的手写值相同，所以「恢复默认」不会让界面变样。 */
        val DEFAULT_STATUS_COLORS: Map<TimeBucket, Long> = mapOf(
            TimeBucket.FUTURE to 0xFF3B82F6, // 蓝：还早
            TimeBucket.SOON to 0xFFF59E0B,   // 琥珀：要来了
            TimeBucket.ONGOING to 0xFF16A34A, // 绿：进行中
            TimeBucket.PAST to 0xFF9AA0A6,   // 灰：已过去
        )

        val DEFAULT_REMINDER_COLORS: Map<ReminderMode, Long> = mapOf(
            ReminderMode.NOTIFICATION to 0xFF6366F1, // 靛：安静的通知
            ReminderMode.ALARM to 0xFFDC2626,        // 红：会响的闹钟
            ReminderMode.NONE to 0xFF6B7280,         // 灰：只记录
        )

        /** 「已完成」永远是这个颜色：它表达「做完了」，不该被个性化改掉。 */
        const val DONE_ACCENT = 0xFF9AA0A6L

        /** 统一单色模式用的强调色。 */
        const val MONO_ACCENT = 0xFF6366F1L

        /** 用户可选的色板（避免自定义颜色挑出看不清的组合）。 */
        val PALETTE_CHOICES: List<Long> = listOf(
            0xFF3B82F6, // 蓝
            0xFF06B6D4, // 青
            0xFF16A34A, // 绿
            0xFF84CC16, // 黄绿
            0xFFF59E0B, // 琥珀
            0xFFF97316, // 橙
            0xFFEF4444, // 红
            0xFFEC4899, // 粉
            0xFF8B5CF6, // 紫
            0xFF6366F1, // 靛
            0xFF64748B, // 石板灰
            0xFF9AA0A6, // 浅灰
        )

        fun hex(argb: Long): String = "%08X".format(argb and 0xFFFFFFFFL)

        /**
         * 解析 [encode] 的产物。**任何一项坏掉都只丢那一项**，
         * 不让整份个性化设置回退到出厂（否则用户挑了半天的颜色会因为一个错字全没）。
         */
        fun decode(raw: String?): Personalization {
            if (raw.isNullOrBlank()) return Personalization()
            val map = raw.split(';')
                .mapNotNull { part ->
                    val i = part.indexOf('=')
                    if (i <= 0) null else part.substring(0, i) to part.substring(i + 1)
                }
                .toMap()

            val mode = map["mode"]
                ?.let { name -> CardColorMode.entries.firstOrNull { it.name == name } }
                ?: CardColorMode.STATUS

            val statusColors = TimeBucket.entries.mapNotNull { bucket ->
                val v = map["s.${bucket.name}"]?.let(::parseHex) ?: return@mapNotNull null
                bucket to v
            }.toMap()

            val reminderColors = ReminderMode.entries.mapNotNull { m ->
                val v = map["m.${m.name}"]?.let(::parseHex) ?: return@mapNotNull null
                m to v
            }.toMap()

            val leads = map["leads"]
                ?.split(',')
                ?.mapNotNull { it.trim().toIntOrNull() }
                ?.takeIf { it.isNotEmpty() }
                ?: DEFAULT_LEAD_PRESETS

            return Personalization(
                colorMode = mode,
                statusColors = statusColors,
                reminderColors = reminderColors,
                leadPresets = leads,
            ).normalized()
        }

        private fun parseHex(text: String): Long? =
            text.trim().toLongOrNull(16)?.let { it and 0xFFFFFFFFL }
    }
}

/** 卡片配色的依据。 */
enum class CardColorMode {
    /** 按状态（未来 / 即将 / 进行中 / 已过去）——出厂设置。 */
    STATUS,

    /** 按提醒方式（通知 / 闹钟式 / 不通知）。 */
    REMINDER,

    /** 统一单色，只靠左侧色条与状态文字区分。 */
    MONO,
    ;

    val label: String
        get() = when (this) {
            STATUS -> "按状态"
            REMINDER -> "按提醒方式"
            MONO -> "统一颜色"
        }
}
