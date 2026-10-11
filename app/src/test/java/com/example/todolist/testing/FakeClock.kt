package com.example.todolist.testing

import com.example.todolist.domain.Clock

/** 可手动推进的时钟。 */
class FakeClock(var initial: Long = 0L) : Clock {
    override fun now(): Long = initial
}
