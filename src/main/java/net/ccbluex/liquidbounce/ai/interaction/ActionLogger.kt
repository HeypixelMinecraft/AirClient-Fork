// skid AIRI
/*
 * Air Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 */
package net.ccbluex.liquidbounce.ai.interaction

import net.ccbluex.liquidbounce.utils.client.ClientUtils.LOGGER
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.CopyOnWriteArrayList

/**
 * AI 客户端交互操作日志
 *
 * 记录 AI 通过 [ClientInteractor] 执行的所有操作(成功/失败/被阻止),
 * 用于审计与调试。日志保存在内存中(最多 200 条),不写入文件以避免性能影响。
 */
object ActionLogger {

    data class Entry(
        val timestamp: Long,
        val operation: String,
        val result: String,
        val detail: String
    ) {
        val timeStr: String
            get() = SimpleDateFormat("HH:mm:ss").format(Date(timestamp))
    }

    private val entries = CopyOnWriteArrayList<Entry>()
    private const val MAX_ENTRIES = 200

    /** 记录一条操作日志 */
    fun log(operation: String, result: String, detail: String) {
        val entry = Entry(System.currentTimeMillis(), operation, result, detail)
        entries.add(entry)
        if (entries.size > MAX_ENTRIES) {
            entries.removeAt(0)
        }
        LOGGER.info("[Airi ActionLog] $operation | $result | $detail")
    }

    /** 读取所有日志条目(只读视图) */
    fun all(): List<Entry> = entries.toList()

    /** 清空日志 */
    fun clear() = entries.clear()

    /** 最近 N 条日志 */
    fun recent(n: Int = 20): List<Entry> = entries.takeLast(n)
}
