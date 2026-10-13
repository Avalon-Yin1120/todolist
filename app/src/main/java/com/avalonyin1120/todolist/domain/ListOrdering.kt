package com.avalonyin1120.todolist.domain

/**
 * 列表排序。
 *
 * 按需求「所有待办均按时间顺序排列」，因此两种排序都严格以开始时间为基准，
 * 区别只在于同类之间的分组顺序。
 */
object ListOrdering {

    /** 纯按时间顺序排列。 */
    fun byTime(todos: List<Todo>): List<Todo> =
        todos.sortedWith(compareBy({ it.startAt }, { it.name }))

    /**
     * 按完成状态再按时间排列：未完成在前，两组内部各自按时间顺序。
     *
     * 这里刻意不把「已完成」混在时间顺序里——需求是「按时间顺序排列，
     * 且已完成与未完成用不同颜色区分」，分组能让两种意图同时成立。
     */
    fun byDone(todos: List<Todo>): List<Todo> =
        todos.sortedWith(compareBy({ it.isDone }, { it.startAt }, { it.name }))
}
