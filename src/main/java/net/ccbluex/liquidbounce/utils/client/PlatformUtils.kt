/*
 * Air Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 */
package net.ccbluex.liquidbounce.utils.client

/**
 * Detects whether the client is running on an Android device via PojavLauncher / Zalith Launcher
 * (or any wrapper that emulates a desktop JVM on top of Android ART).
 *
 * Zalith / PojavLauncher launches the JVM with `-Dos.name=Linux -Dos.version=Android-XX`, so
 * we use that as the primary signal. Falls back to checking for the Android `os.version`
 * prefix, which covers other Android-JVM launchers as well.
 */
object PlatformUtils {

    val isAndroid: Boolean by lazy {
        val osName = System.getProperty("os.name", "")
        val osVersion = System.getProperty("os.version", "")

        // PojavLauncher / ZalithLauncher convention: os.name=Linux, os.version=Android-XX
        (osName.equals("Linux", ignoreCase = true) && osVersion.contains("Android", ignoreCase = true))
                // Generic Android detection via os.version
                || osVersion.startsWith("Android", ignoreCase = true)
    }
}
