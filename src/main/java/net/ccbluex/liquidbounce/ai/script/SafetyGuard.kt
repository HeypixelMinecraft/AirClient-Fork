// skid AIRI
/*
 * Air Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 */
package net.ccbluex.liquidbounce.ai.script

/**
 * 脚本安全审核
 *
 * 检测 AI 生成脚本中的潜在危险操作:
 *   - 系统命令执行(Runtime.exec / ProcessBuilder)
 *   - 退出 JVM(System.exit)
 *   - 反射访问私有/受保护成员
 *   - 访问本地文件系统(除 scripts 目录外)
 *   - 网络连接(URL.openConnection / Socket 等)
 *   - 加载本地库(System.load)
 *   - 危险 Java 类(Java.type 中包含 java.lang.Runtime 等)
 *
 * 仅作静态文本检查,不能保证完全安全。
 * 真正的安全保障来自 Nashorn 沙箱本身 + 用户的最终确认。
 */
object SafetyGuard {

    data class AuditResult(val safe: Boolean, val reason: String = "")

    /** 危险 Java 类全名(出现在 Java.type 中则拦截) */
    private val DANGEROUS_CLASSES = listOf(
        "java.lang.Runtime",
        "java.lang.ProcessBuilder",
        "java.lang.System",
        "java.lang.ClassLoader",
        "java.lang.Thread",
        "java.lang.Process",
        "java.io.File",
        "java.io.FileInputStream",
        "java.io.FileOutputStream",
        "java.io.RandomAccessFile",
        "java.nio.file.Files",
        "java.nio.file.Path",
        "java.nio.file.Paths",
        "java.net.URL",
        "java.net.Socket",
        "java.net.ServerSocket",
        "java.net.HttpURLConnection",
        "java.net.InetAddress",
        "javax.script.ScriptEngineManager",
        "jdk.internal.dynalink.beans.StaticClass",
        "java.lang.reflect.Field",
        "java.lang.reflect.Method",
        "java.lang.reflect.Constructor"
    )

    /** 危险关键字(出现在脚本中即拦截) */
    private val DANGEROUS_KEYWORDS = listOf(
        // 系统命令执行
        "Runtime.getRuntime().exec",
        "Runtime.getRuntime().exec(",
        "ProcessBuilder(",
        // 退出 JVM
        "System.exit(",
        // 加载本地库
        "System.load(",
        "System.loadLibrary(",
        // 反射访问私有字段
        "setAccessible(true)",
        "setAccessible( true)",
        // Nashorn 绕过
        "Java.extend",
        "load(",
        "loadWithNewGlobal(",
        // 危险全局
        "exit(",
        "quit("
    )

    /** 审核 AI 生成的脚本内容 */
    fun audit(content: String): AuditResult {
        if (content.isBlank()) return AuditResult(true)

        // 检查 Java.type 中的危险类
        val javaTypePattern = Regex("""Java\.type\s*\(\s*["']([^"']+)["']\s*\)""")
        javaTypePattern.findAll(content).forEach { match ->
            val className = match.groupValues[1]
            DANGEROUS_CLASSES.forEach { dangerous ->
                if (className == dangerous || className.startsWith("$dangerous.")) {
                    return AuditResult(false, "Access to restricted Java class: $className")
                }
            }
            // 拦截 java.io / java.net / java.nio 等敏感包
            if (className.startsWith("java.io.") ||
                className.startsWith("java.net.") ||
                className.startsWith("java.nio.") ||
                className.startsWith("java.lang.reflect.") ||
                className.startsWith("javax.script.") ||
                className.startsWith("org.lwjgl.")
            ) {
                return AuditResult(false, "Access to sensitive package: $className")
            }
        }

        // 检查危险关键字
        DANGEROUS_KEYWORDS.forEach { keyword ->
            if (content.contains(keyword)) {
                return AuditResult(false, "Forbidden operation detected: $keyword")
            }
        }

        // 检查反射调用
        val reflectPattern = Regex("""\.getDeclaredField\s*\(""")
        if (reflectPattern.containsMatchIn(content)) {
            return AuditResult(false, "Reflection access to declared fields is forbidden")
        }
        val reflectMethodPattern = Regex("""\.getDeclaredMethod\s*\(""")
        if (reflectMethodPattern.containsMatchIn(content)) {
            return AuditResult(false, "Reflection access to declared methods is forbidden")
        }

        // 检查 URL 字面量(网络访问)
        val urlPattern = Regex("""https?://[^"' )\s]+""")
        urlPattern.findAll(content).forEach { match ->
            val url = match.value
            // 允许在注释中提及 URL(简单判断:同行是否以 // 开头)
            val lineStart = content.lastIndexOf('\n', match.range.first).coerceAtLeast(0)
            val linePrefix = content.substring(lineStart, match.range.first).trim()
            if (!linePrefix.startsWith("//") && !linePrefix.startsWith("*")) {
                return AuditResult(false, "Network access to $url is forbidden in scripts")
            }
        }

        return AuditResult(true)
    }
}
