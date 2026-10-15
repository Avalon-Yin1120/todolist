package com.avalonyin1120.todolist.notify

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 提醒诊断日志：把「到点到底发生了什么」按时间顺序记下来，用户能在应用里直接看。
 *
 * ## 为什么必须有它
 *
 * 「闹钟不响」这类问题**只能靠真机证据定位**：模拟器上没有 ColorOS 的省电策略、
 * 没有厂商削掉的后台启动、也复现不出「渠道被判静默」。在此之前每次排查都只能靠猜与
 * 让用户描述，往返好几轮还未必找到那一环。
 *
 * 现在把关键节点都记下来：排定 → 触发 → 是否被跳过（已完成 / 已提醒过）→ 发通知
 * （渠道、有没有声音、通知权限）→ 起铃（内置音频 / 系统铃声 / 失败）→ 停止（哪条路径）。
 * 用户把这段日志复制给我，断在哪一环一目了然。
 *
 * ## 为什么要落盘
 *
 * 内存里的环形缓冲够不够？不够：真机上「应用被系统杀掉」正是最常见的场景，
 * 而那一刻内存里的东西全没了。所以同时追加写到 `filesDir/reminder-log.txt`，
 * 下次打开应用时读回来。
 *
 * ## 取舍
 *
 * * 只保留最近 [MAX_ENTRIES] 行：这是给人看的排查材料，不是审计日志。
 * * 写入失败一律吞掉（磁盘满、权限异常都不该影响提醒本身）——日志永远不能成为故障源。
 * * 不做异步、不加锁竞争的优化：调用点都在提醒链路的低频路径上，同步写足够。
 */
object ReminderLog {

    /** 分类：界面按它做前缀，便于快速扫。 */
    const val CAT_SCHEDULE = "排定"
    const val CAT_FIRE = "触发"
    const val CAT_NOTIFY = "通知"
    const val CAT_RING = "响铃"
    const val CAT_STOP = "停止"
    const val CAT_SYSTEM = "系统"

    private const val MAX_ENTRIES = 300
    private const val FILE_NAME = "reminder-log.txt"
    private const val TAG = "TodoReminderLog"

    private val formatter = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault())
    private val lock = Any()
    private val entries = ArrayDeque<String>()
    private var file: File? = null

    /** 应用启动时调用一次：读回上次的日志并准备落盘。 */
    fun init(context: Context) {
        synchronized(lock) {
            file = File(context.applicationContext.filesDir, FILE_NAME)
            if (entries.isNotEmpty()) return
            runCatching {
                file?.takeIf { it.exists() }?.readLines()?.let { lines ->
                    // 只装回最后 MAX_ENTRIES 行
                    val tail = if (lines.size > MAX_ENTRIES) lines.subList(lines.size - MAX_ENTRIES, lines.size) else lines
                    entries.addAll(tail)
                }
            }.onFailure { Log.w(TAG, "读取提醒日志失败：${it.message}") }
        }
    }

    /**
     * 记一行。
     *
     * @param category [CAT_SCHEDULE] 等分类，写入时用方括号括起来
     */
    fun add(category: String, message: String) {
        val line = "${formatter.format(Date())} [$category] $message"
        Log.i(TAG, line)
        synchronized(lock) {
            entries.addLast(line)
            while (entries.size > MAX_ENTRIES) entries.removeFirst()
            runCatching {
                file?.appendText(line + "\n")
                // 文件涨到两倍上限时重写一次，避免无限增长（重写用当前内存里的尾部）
                val target = file ?: return@runCatching
                if (target.length() > MAX_ENTRIES * 80L) {
                    target.writeText(entries.joinToString("\n", postfix = "\n"))
                }
            }
        }
    }

    /** 供界面展示：最新的在最前面。 */
    fun snapshotNewestFirst(): List<String> = synchronized(lock) { entries.toList().asReversed() }

    fun isEmpty(): Boolean = synchronized(lock) { entries.isEmpty() }

    fun clear() {
        synchronized(lock) {
            entries.clear()
            runCatching { file?.writeText("") }
        }
    }

    /**
     * 供测试复位（**仅供单测**）：静态状态会跨用例污染。
     */
    internal fun resetForTest() {
        synchronized(lock) {
            entries.clear()
            file = null
        }
    }
}
