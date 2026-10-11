package com.example.todolist.domain

/** 可替换的时间源，便于测试中操纵「现在」。 */
fun interface Clock {
    fun now(): Long
}

/** 生产实现：跟随系统时钟。 */
class SystemClock : Clock {
    override fun now(): Long = System.currentTimeMillis()
}
