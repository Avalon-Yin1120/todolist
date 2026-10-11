package com.example.todolist.domain

/** 待办事项相对当前时间的展示状态。 */
enum class TodoStatus {
    /** 尚未开始 */
    UPCOMING,

    /** 进行中（位于起止时间之间） */
    RUNNING,

    /** 已结束（超过终止时间，且未标记完成） */
    ENDED,

    /** 已被用户标记完成 */
    DONE,
}
