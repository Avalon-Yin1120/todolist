package com.example.todolist.domain

/**
 * 表单字段级的校验错误。
 *
 * 刻意用枚举而不是 Android 字符串资源 id：这样校验规则可以在纯 JVM 测试里断言，
 * 文案映射交给 UI 层（ViewModel 负责把枚举翻成 R.string.*）。
 */
enum class FormError {
    NAME_EMPTY,
    NAME_TOO_LONG,
    END_NOT_AFTER_START,
    LEAD_OUT_OF_RANGE,
}

/** 一次性对三个字段做校验，返回空 map 表示全部通过。 */
object FormValidator {

    const val FIELD_NAME = "name"
    const val FIELD_RANGE = "range"
    const val FIELD_LEAD = "lead"

    fun validate(
        rawName: String,
        startAt: Long?,
        endAt: Long?,
        leadMinutes: Int,
    ): Map<String, FormError> = buildMap {
        val name = ReminderTime.normalizeName(rawName)
        when {
            name.isEmpty() -> put(FIELD_NAME, FormError.NAME_EMPTY)
            name.length > ReminderTime.MAX_NAME_LENGTH -> put(FIELD_NAME, FormError.NAME_TOO_LONG)
        }
        if (startAt == null || endAt == null || !ReminderTime.isValidRange(startAt, endAt)) {
            put(FIELD_RANGE, FormError.END_NOT_AFTER_START)
        }
        if (!ReminderTime.isValidLead(leadMinutes)) {
            put(FIELD_LEAD, FormError.LEAD_OUT_OF_RANGE)
        }
    }
}
