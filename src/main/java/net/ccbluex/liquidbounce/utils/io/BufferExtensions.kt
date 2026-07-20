/*
 * Air Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 * https://github.com/lmx0721/AirClient
 */
package net.ccbluex.liquidbounce.utils.io

import java.nio.Buffer
import java.nio.ByteBuffer

/**
 * Prevents crashes when flip() is called from higher Java versions.
 * Catches Throwable (including NoSuchMethodError) for Java 8 compatibility.
 */
fun ByteBuffer.flipSafely() {
    try {
        flip()
    } catch (e: Throwable) {
        try {
            (this as Buffer).flip()
        } catch (any: Throwable) {
            any.printStackTrace()
        }
    }
}