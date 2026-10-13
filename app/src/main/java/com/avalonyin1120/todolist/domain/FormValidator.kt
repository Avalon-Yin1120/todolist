package com.avalonyin1120.todolist.domain

/**
 * 表单字段级的校验错误。
 *
 * 刻意用枚举而不是 Android 字符串资源 id：这样校验规则可以在纯 JVM 测试里断言，
 * 文案映射交给 UI 层（ViewModel/Compose 负责把枚举翻成用户可读文字）。
 */
enum class FormError {
    NAME_EMPTY,
    NAME_TOO_LONG,
    END_NOT_AFTER_START,
    LEAD_OUT_OF_RANGE,
}

/** 一次性对各字段做校验，返回空 map 表示全部通过。 */
object FormValidator {

    const val FIELD_NAME = "name"
    const val FIELD_RANGE = "range"
    const val FIELD_LEAD = "lead"

    /**
     * @param leadInputInvalid 输入框文本非空但解析失败或超出范围。
     *   这种输入不能静默按 0 处理，否则用户会误以为保存的是自己填的值。
     */
    fun validate(
        rawName: String,
        startAt: Long?,
        endAt: Long?,
        leadMinutes: Int,
        leadInputInvalid: Boolean = false,
    ): Map<String, FormError> = buildMap {
        val name = ReminderTime.normalizeName(rawName)
        when {
            name.isEmpty() -> put(FIELD_NAME, FormError.NAME_EMPTY)
            name.length > ReminderTime.MAX_NAME_LENGTH -> put(FIELD_NAME, FormError.NAME_TOO_LONG)
        }
        if (startAt == null || endAt == null || !ReminderTime.isValidRange(startAt, endAt)) {
            put(FIELD_RANGE, FormError.END_NOT_AFTER_START)
        }
        if (leadInputInvalid || !ReminderTime.isValidLead(leadMinutes)) {
            put(FIELD_LEAD, FormError.LEAD_OUT_OF_RANGE)
        }
    }
}
