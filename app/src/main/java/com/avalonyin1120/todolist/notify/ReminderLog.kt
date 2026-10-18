package com.avalonyin1120.todolist.notify

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 一行日志的时间格式（顶层常量：嵌套类访问外层 object 的私有成员容易出意外）。 */
private val LOG_TIME_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

private const val LOG_NAMES_MARKER = "\u0001"
private const val LOG_NAMES_SEP = "\u001F"

/**
 * 一行日志。
 *
 * [names] 只用于**导出脱敏**：界面显示与落盘用的是 [display]，名称原样在里面；
 * 导出时按 [names] 把它们替换成字数。落盘时用 [LOG_NAMES_MARKER] 把名字附在行尾，
 * 读回来再拆开——这样进程被杀后重启仍然能正确脱敏。
 */
internal data class Entry(
    val time: Long,
    val category: String,
    val message: String,
    val names: List<String> = emptyList(),
) {
    fun display(): String = "${LOG_TIME_FORMAT.format(Date(time))} [$category] $message"

    /** 导出用：名称 → `〈待办#2：7 字〉`。 */
    fun redacted(): String {
        var text = message
        names.distinct().sortedByDescending { it.length }.forEachIndexed { index, name ->
            text = text.replace(name, maskOf(name, index + 1))
        }
        return "${LOG_TIME_FORMAT.format(Date(time))} [$category] $text"
    }

    /** 落盘格式：`display` + 标记 + 名字（分隔符分隔）。 */
    fun serialize(): String = buildString {
        append(display())
        if (names.isNotEmpty()) {
            append(LOG_NAMES_MARKER).append(names.joinToString(LOG_NAMES_SEP))
        }
    }

    companion object {
        fun maskOf(name: String, index: Int): String =
            "〈待办#$index：${name.length} 字〉"
    }
}

/**
 * 应用内事件日志：把「到点发生了什么」与「用户对数据做了什么」都按时间顺序记下来。
 *
 * ## 为什么必须有它
 *
 * 「闹钟不响」这类问题**只能靠真机证据定位**：模拟器上没有 ColorOS 的省电策略、
 * 没有厂商削掉的后台启动、也复现不出「渠道被判静默」。此前每次排查都只能靠猜与
 * 让用户描述，往返好几轮还未必找到那一环。现在关键节点都留痕：
 * 排定 → 触发 → 是否被跳过 → 发通知（渠道 / 有没有声音 / 权限）→ 起铃 → 停止。
 *
 * 后来用户又提了一条：**增、删、改、完成这类数据操作也要记**，
 * 于是日志从「提醒诊断」扩成了「事件日志」，分类里多了 `新增 / 修改 / 删除 / 完成 / 设置`。
 * 排查「我明明改过时间」这种问题时，这两类日志必须能对上。
 *
 * ## 为什么落盘
 *
 * 真机上「应用被系统杀掉」正是最常见的场景，而那一刻内存里的东西全没了。
 * 因此同时追加写到 `filesDir/reminder-log.txt`，下次打开应用时读回来。
 *
 * ## 保留策略（用户要求）
 *
 * * **只留最近 7 天**：每天的日志在启动与写入时各清理一次；
 * * 上限 [MAX_ENTRIES] 行兜底，防止某天异常刷屏把文件撑大；
 * * 写入失败一律吞掉（磁盘满、权限异常都不该影响提醒本身）——日志永远不能成为故障源。
 *
 * ## 隐私（用户要求）
 *
 * 待办名称属于用户内容，**导出时必须脱敏**：把名称换成「〈待办#2：7 字〉」这种形态，
 * 只保留「这条日志里出现了几个字的名字」这个信息量。应用内查看不受影响（自己的手机上
 * 看自己的待办没有隐私问题），唯一的对外出口 [exportRedacted] 才做替换。
 */
object ReminderLog {

    // ------------------------------------------------------------------ 分类

    const val CAT_SCHEDULE = "排定"
    const val CAT_FIRE = "触发"
    const val CAT_NOTIFY = "通知"
    const val CAT_RING = "响铃"
    const val CAT_STOP = "停止"
    const val CAT_SYSTEM = "系统"
    const val CAT_ADD = "新增"
    const val CAT_EDIT = "修改"
    const val CAT_DELETE = "删除"
    const val CAT_DONE = "完成"
    const val CAT_SETTING = "设置"

    /** 界面上的分类筛选顺序。 */
    val ALL_CATEGORIES = listOf(
        CAT_ADD, CAT_EDIT, CAT_DONE, CAT_DELETE,
        CAT_SCHEDULE, CAT_FIRE, CAT_NOTIFY, CAT_RING, CAT_STOP,
        CAT_SETTING, CAT_SYSTEM,
    )

    private const val MAX_ENTRIES = 800

    /** 保留天数：超过就清掉（用户要求「七天前的日志自动清除」）。 */
    const val RETENTION_DAYS = 7

    private const val FILE_NAME = "reminder-log.txt"
    private const val TAG = "TodoReminderLog"

    private val lock = Any()
    private val entries = ArrayDeque<Entry>()
    private var file: File? = null

    /** 应用启动时调用一次：读回上次的日志、清掉过期项并准备落盘。 */
    fun init(context: Context) {
        synchronized(lock) {
            file = File(context.applicationContext.filesDir, FILE_NAME)
            if (entries.isNotEmpty()) return
            runCatching {
                val target = file?.takeIf { it.exists() } ?: return@runCatching
                val lines = target.readLines()
                val parsed = lines.mapNotNull(::parseLine)
                entries.addAll(parsed.takeLast(MAX_ENTRIES))
                // 启动时清一次过期项：用户可能隔了一个月才再打开应用
                val removed = pruneExpiredLocked()
                if (removed || lines.size > MAX_ENTRIES) rewriteLocked()
            }.onFailure { Log.w(TAG, "读取日志失败：${it.message}") }
        }
    }

    /**
     * 记一行。
     *
     * @param category [CAT_ADD] 等分类
     * @param names 这条日志里出现的**用户内容**（待办名称）。只为导出脱敏服务，
     *   界面与落盘都保留原文。
     */
    fun add(category: String, message: String, names: List<String> = emptyList()) {
        val entry = Entry(System.currentTimeMillis(), category, message, names.filter { it.isNotBlank() })
        Log.i(TAG, entry.display())
        synchronized(lock) {
            entries.addLast(entry)
            while (entries.size > MAX_ENTRIES) entries.removeFirst()
            runCatching {
                file?.appendText(entry.serialize() + "\n")
                val target = file ?: return@runCatching
                // 文件涨到两倍上限时重写一次（重写用当前内存里的尾部），避免无限增长
                if (target.length() > MAX_ENTRIES * 80L) rewriteLocked()
            }
        }
    }

    /** 供界面展示：最新的在最前面（原文，含待办名称）。 */
    fun snapshotNewestFirst(category: String? = null): List<String> =
        synchronized(lock) {
            entries
                .filter { category == null || it.category == category }
                .toList()
                .asReversed()
                .map { it.display() }
        }

    /**
     * 导出（复制）用：**名称已脱敏**。
     *
     * 这是日志唯一的对外出口，所以脱敏放在这里而不是写入时——
     * 写入时脱敏会让「应用内自己看」也丢失信息，而排查恰恰需要看到名字。
     */
    fun exportRedacted(category: String? = null): String =
        synchronized(lock) {
            val rows = entries
                .filter { category == null || it.category == category }
                .toList()
                .asReversed()
                .joinToString("\n") { it.redacted() }
            """
            |待办提醒 · 事件日志（已脱敏）
            |导出时间：${LOG_TIME_FORMAT.format(Date())}
            |保留范围：最近 $RETENTION_DAYS 天，共 ${entries.size} 条
            |说明：为保护隐私，待办名称已替换为「〈待办#N：字数〉」，其余内容原样保留。
            |
            """.trimMargin() + rows
        }

    /** 某分类下还有多少条（筛选芯片上的数字）。 */
    fun countOf(category: String): Int = synchronized(lock) { entries.count { it.category == category } }

    fun isEmpty(): Boolean = synchronized(lock) { entries.isEmpty() }

    fun clear() {
        synchronized(lock) {
            entries.clear()
            runCatching { file?.writeText("") }
        }
    }

    // ------------------------------------------------------------------ 内部

    private fun maskOf(name: String, index: Int): String = "〈待办#$index：${name.length} 字〉"

    /** 清掉超过保留期的条目；返回是否真的删过。 */
    private fun pruneExpiredLocked(): Boolean {
        val cutoff = System.currentTimeMillis() - RETENTION_DAYS * 24L * 60 * 60 * 1000
        val before = entries.size
        entries.removeAll { it.time < cutoff }
        return entries.size != before
    }

    private fun rewriteLocked() {
        runCatching {
            file?.writeText(entries.joinToString("\n", postfix = "\n") { it.serialize() })
        }
    }

    private fun parseLine(line: String): Entry? {
        if (line.isBlank()) return null
        val markerIndex = line.indexOf(LOG_NAMES_MARKER)
        val display = if (markerIndex >= 0) line.substring(0, markerIndex) else line
        val names = if (markerIndex >= 0) {
            line.substring(markerIndex + 1).split(LOG_NAMES_SEP).filter { it.isNotBlank() }
        } else {
            emptyList()
        }
        // 格式：yyyy-MM-dd HH:mm:ss [分类] 内容
        val close = display.indexOf(']')
        if (close < 0) return null
        val open = display.lastIndexOf('[', close)
        if (open < 0) return null
        val timeText = display.substring(0, open).trim()
        val time = runCatching { LOG_TIME_FORMAT.parse(timeText)?.time }.getOrNull() ?: return null
        val category = display.substring(open + 1, close).trim()
        val message = display.substring(close + 1).trim()
        return Entry(time, category, message, names)
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

    /** 供测试注入时钟与目录（**仅供单测**）。 */
    internal fun attachForTest(context: Context) {
        synchronized(lock) {
            file = File(context.applicationContext.filesDir, FILE_NAME)
        }
    }
}
