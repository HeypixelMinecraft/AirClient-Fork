/*
 * Air Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 */
package net.ccbluex.liquidbounce.utils.client

import net.ccbluex.liquidbounce.utils.attack.RollingArrayLongBuffer

object PPSCounter {
    // 缓冲区容量从 99999 调整为 1000：每秒最多记录 1000 个包已足够覆盖极端 burst，
    // 同时将常驻内存占用从 ~1.6MB（2 × 99999 × 8B）降到 ~16KB。
    private const val BUFFER_CAPACITY = 1000
    private val TIMESTAMP_BUFFERS = Array(PacketType.entries.size) { RollingArrayLongBuffer(BUFFER_CAPACITY) }

    /**
     * Registers a packet type
     *
     * @param type The type
     */
    fun registerType(type: PacketType) = TIMESTAMP_BUFFERS[type.ordinal].add(System.currentTimeMillis())

    /**
     * Gets the count of sent and received packets that have occurred in the last 1000ms
     *
     * @param type The packet type
     * @return The PPS
     */
    fun getPPS(type: PacketType) = TIMESTAMP_BUFFERS[type.ordinal].getTimestampsSince(System.currentTimeMillis() - 1000L)

    enum class PacketType { SEND, RECEIVED }
}
