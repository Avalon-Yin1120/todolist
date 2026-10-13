package com.avalonyin1120.todolist.domain

/**
 * 待办在当前时刻的「归属时段」，用于列表配色与是否可标记完成。
 *
 * 与 [TodoStatus] 的区别：这里表达的是用户视角的时间关系（未来/即将/正在/过去），
 * 而 [TodoStatus] 表达的是完成状态。
 */
enum class TimeBucket {
    /** 还没到提醒时间。 */
    FUTURE,

    /** 提醒时间已到、但还没开始（提醒时间 ～ 起始时间）。 */
    SOON,

    /** 位于起止时间之间。 */
    ONGOING,

    /** 已经超过终止时间。 */
    PAST,
    ;

    /** 未来的待办不允许标记完成——还没发生的事谈不上「完成」。 */
    val allowsMarkDone: Boolean get() = this != FUTURE
}

/**
 * 列表展示所需的分类结果。
 *
 * @param timeBucket 时间归属
 * @param isDone 是否已完成
 * @param canMarkDone 是否允许用户勾选完成
 */
data class TodoClassification(
    val timeBucket: TimeBucket,
    val isDone: Boolean,
    val canMarkDone: Boolean,
)

/** 判定某条待办当前属于哪个时段。 */
object TodoClassifier {

    fun classify(todo: Todo, now: Long): TodoClassification {
        val bucket = bucketOf(todo, now)
        return TodoClassification(
            timeBucket = bucket,
            isDone = todo.isDone,
            // 未来的待办不能标记完成；已完成的自然可以取消完成
            canMarkDone = bucket.allowsMarkDone || todo.isDone,
        )
    }

    fun bucketOf(todo: Todo, now: Long): TimeBucket {
        val remindAt = ReminderTime.remindAt(todo.startAt, todo.leadMinutes)
        return when {
            now < remindAt -> TimeBucket.FUTURE
            now < todo.startAt -> TimeBucket.SOON
            now < todo.endAt -> TimeBucket.ONGOING
            else -> TimeBucket.PAST
        }
    }
}
