/*
 * Air Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 */
package net.ccbluex.liquidbounce.ui.client.hud.element.elements


import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.ui.client.hud.HUD.addNotification
import net.ccbluex.liquidbounce.ui.client.hud.HUD.notifications
import net.ccbluex.liquidbounce.ui.client.hud.designer.GuiHudDesigner
import net.ccbluex.liquidbounce.ui.client.hud.element.Border
import net.ccbluex.liquidbounce.ui.client.hud.element.Element
import net.ccbluex.liquidbounce.ui.client.hud.element.ElementInfo
import net.ccbluex.liquidbounce.ui.client.hud.element.Side
import net.ccbluex.liquidbounce.ui.client.hud.element.elements.Notification.Companion.maxTextLength
import net.ccbluex.liquidbounce.ui.font.Fonts
import net.ccbluex.liquidbounce.utils.client.ClientUtils
import net.ccbluex.liquidbounce.utils.extensions.lerpWith
import net.ccbluex.liquidbounce.utils.render.ColorUtils.withAlpha
import net.ccbluex.liquidbounce.utils.render.RenderUtils
import net.ccbluex.liquidbounce.utils.render.RenderUtils.deltaTime
import net.ccbluex.liquidbounce.utils.render.RenderUtils.drawRect
import net.ccbluex.liquidbounce.utils.render.RenderUtils.drawRoundedBorder
import net.ccbluex.liquidbounce.utils.render.RenderUtils.drawRoundedRect
import net.minecraft.util.ResourceLocation
import java.awt.Color

/**
 * CustomHUD Notification element
 */
@ElementInfo(name = "Notifications", single = true, priority = -1)
class Notifications(
    x: Double = 0.0, y: Double = 30.0, scale: Float = 1F, side: Side = Side(Side.Horizontal.RIGHT, Side.Vertical.DOWN)
) : Element("Notifications", x, y, scale, side) {

    val horizontalFade by choices("HorizontalFade", arrayOf("InOnly", "OutOnly", "Both", "None"), "OutOnly")
    val style by choices("Style", arrayOf("Default", "Modern", "Outline", "Stamp"), "Default")
    val padding by int("Padding", 5, 1..20)
    val roundRadius by float("RoundRadius", 3f, 0f..10f)
    val color by color("BackgroundColor", Color.BLACK.withAlpha(128))
    val renderBorder by boolean("RenderBorder", false)
    val borderColor by color("BorderColor", Color.BLUE.withAlpha(255)) { renderBorder }
    val borderWidth by float("BorderWidth", 2f, 0.5F..5F) { renderBorder }

    private val exampleNotification = Notification("Example Title", "Example Description")

    private var index = 0

    override fun updateElement() {
        if (mc.currentScreen is GuiHudDesigner && ClientUtils.runTimeTicks % 60 == 0) {
            exampleNotification.severityType = SeverityType.entries[++index % SeverityType.entries.size]
        }
    }

    override fun drawElement(): Border? {
        var verticalOffset = 0f

        maxTextLength = maxOf(100, notifications.maxOfOrNull { it.textLength } ?: 0)

        notifications.removeIf { notification ->
            if (notification != exampleNotification) {
                notification.y = (notification.y..verticalOffset).lerpWith(RenderUtils.deltaTimeNormalized())
            }

            notification.drawNotification(this).also { if (!it) verticalOffset += Notification.MAX_HEIGHT + padding }
        }

        if (mc.currentScreen is GuiHudDesigner) {
            if (exampleNotification !in notifications) {
                index = 0
                addNotification(exampleNotification)
            }

            exampleNotification.fadeState = Notification.FadeState.STAY
            exampleNotification.textLength = Fonts.fontSemibold40.getStringWidth(exampleNotification.longestString)

            val notificationHeight = Notification.MAX_HEIGHT

            exampleNotification.y = 0F

            return Border(
                -(maxTextLength.toFloat() + 24 + 20), -notificationHeight.toFloat(), 0F, 0F
            )
        }

        return null
    }

    enum class SeverityType(val path: ResourceLocation) {
        SUCCESS(ResourceLocation("airclient/notifications/success.png")), RED_SUCCESS(ResourceLocation("airclient/notifications/redsuccess.png")), INFO(
            ResourceLocation("airclient/notifications/info.png")
        ),
        WARNING(ResourceLocation("airclient/notifications/warning.png")), ERROR(ResourceLocation("airclient/notifications/error.png"))
    }
}

class Notification(
    var title: String,
    var description: String,
    private val delay: Long = 2000L,
    var severityType: Notifications.SeverityType = Notifications.SeverityType.INFO
) {
    var x = 0F

    // Spawn the notification 32 pixels above the last one - if exists.
    var y: Float = (notifications.lastOrNull()?.y ?: 0F) + MAX_HEIGHT * 2
    var textLength = 0

    val longestString
        get() = arrayOf(title, description).maxBy { Fonts.fontSemibold40.getStringWidth(it) }

    private var stay = delay
    private var fadeStep = 0F
    var fadeState = FadeState.IN

    /**
     * Used when the same module state changes within the fade in/stay time window.
     */
    fun replaceModuleNotification(title: String, description: String, severityType: Notifications.SeverityType) {
        if (fadeState.ordinal > 1) {
            return
        }

        // Re-setup every important information
        stay = delay
        this.severityType = severityType
        this.title = title
        this.description = description

        textLength = Fonts.fontSemibold40.getStringWidth(longestString)
        maxTextLength = maxOf(textLength, maxTextLength)

        notifications.sortBy { it.stay }
    }

    companion object {
        fun informative(title: String, message: String, delay: Long = 2000L) =
            Notification(title, message, delay, Notifications.SeverityType.INFO)

        fun informative(title: Module, message: String, delay: Long = 2000L) =
            Notification(title.spacedName, message, delay, Notifications.SeverityType.INFO)

        fun error(title: Module, message: String, delay: Long = 2000L) =
            Notification(title.spacedName, message, delay, Notifications.SeverityType.ERROR)

        fun warning(title: Module, message: String, delay: Long = 2000L) =
            Notification(title.spacedName, message, delay, Notifications.SeverityType.WARNING)

        var maxTextLength = 0
        const val MAX_HEIGHT = 32
        const val ICON_SIZE = 24
    }

    enum class FadeState {
        IN, STAY, OUT, END
    }

    init {
        textLength = Fonts.fontSemibold40.getStringWidth(longestString)
        maxTextLength = maxOf(maxTextLength, textLength)
    }

    fun drawNotification(element: Notifications): Boolean {
        val notificationWidth = getNotificationWidth(element)
        val extraSpace = 4F

        val currentX = when (fadeState) {
            FadeState.IN -> if (element.horizontalFade in arrayOf("InOnly", "Both")) x else notificationWidth
            FadeState.OUT -> if (element.horizontalFade in arrayOf("OutOnly", "Both")) x else notificationWidth
            else -> x
        }

        // 根据样式分支渲染
        when (element.style) {
            "Modern" -> drawModernStyle(element, currentX, extraSpace)
            "Outline" -> drawOutlineStyle(element, currentX, extraSpace, notificationWidth)
            "Stamp" -> drawStampStyle(element, currentX, extraSpace)
            else -> drawDefaultStyle(element, currentX, extraSpace)
        }

        val delta = deltaTime

        when (fadeState) {
            FadeState.IN -> {
                if (x < notificationWidth) {
                    x += delta
                }
                if (x >= notificationWidth) {
                    fadeState = FadeState.STAY
                    x = notificationWidth
                    fadeStep = notificationWidth
                }
                stay = delay
            }

            FadeState.STAY -> {
                if (textLength != maxTextLength) {
                    maxTextLength = maxOf(textLength, maxTextLength)
                    x = getNotificationWidth(element)
                    fadeStep = x
                }
                stay -= delta
                if (stay <= 0) {
                    fadeState = FadeState.OUT
                }
            }

            FadeState.OUT -> if (x > 0) {
                x -= delta
                y -= delta / 4F
            } else {
                fadeState = FadeState.END
            }

            FadeState.END -> return true
        }

        return false
    }

    /**
     * 不同样式的通知宽度计算。
     * Default/Toast/Banner 沿用原宽度；Modern 去掉图标改用左侧色条；Compact 只显示标题更窄。
     */
    private fun getNotificationWidth(element: Notifications): Float = when (element.style) {
        "Modern" -> maxTextLength + 24F
        "Outline" -> maxTextLength + ICON_SIZE + 16F
        "Stamp" -> maxTextLength + ICON_SIZE + 30F
        else -> maxTextLength + ICON_SIZE + 16F
    }

    /**
     * 根据 severity 类型获取柔和的强调色（避开霓虹/赛博/AI配色）。
     */
    private fun severityAccentColor(): Color = when (severityType) {
        Notifications.SeverityType.SUCCESS -> Color(120, 175, 105)      // 柔绿
        Notifications.SeverityType.RED_SUCCESS -> Color(190, 95, 75)    // 砖红
        Notifications.SeverityType.INFO -> Color(105, 140, 175)         // 雾蓝
        Notifications.SeverityType.WARNING -> Color(200, 170, 80)       // 暖黄
        Notifications.SeverityType.ERROR -> Color(195, 85, 70)          // 朱红
    }

    // ===== Default 样式（保留原逻辑） =====
    private fun drawDefaultStyle(element: Notifications, currentX: Float, extraSpace: Float) {
        drawRoundedRect(0F, -y - MAX_HEIGHT, -currentX - extraSpace, -y, element.color.rgb, element.roundRadius)

        if (element.renderBorder) {
            drawRoundedBorder(
                0F, -y - MAX_HEIGHT, -currentX - extraSpace, -y,
                element.borderWidth, element.borderColor.rgb, element.roundRadius
            )
        }

        val nearTopSpot = -y - MAX_HEIGHT + 10
        Fonts.fontSemibold40.drawString(title, ICON_SIZE + 8F - currentX, nearTopSpot - 5, Color.WHITE.rgb)
        Fonts.fontSemibold35.drawString(
            description, ICON_SIZE + 8F - currentX, nearTopSpot + Fonts.fontSemibold40.fontHeight - 2, Int.MAX_VALUE
        )
        RenderUtils.drawImage(
            severityType.path, -currentX + 2, -y - MAX_HEIGHT + 4, ICON_SIZE, ICON_SIZE, radius = element.roundRadius
        )
    }

    // ===== Modern 样式：现代极简卡片，左侧 severity 色条，无图标 =====
    private fun drawModernStyle(element: Notifications, currentX: Float, extraSpace: Float) {
        val bgColor = Color(44, 42, 40, 225)
        val accent = severityAccentColor()

        drawRoundedRect(0F, -y - MAX_HEIGHT, -currentX - extraSpace, -y, bgColor.rgb, element.roundRadius)

        // 左侧 severity 色条（贴近左边缘）
        drawRect(-currentX + 2F, -y - MAX_HEIGHT + 5F, -currentX + 5F, -y - 5F, accent.rgb)

        if (element.renderBorder) {
            drawRoundedBorder(
                0F, -y - MAX_HEIGHT, -currentX - extraSpace, -y,
                element.borderWidth, element.borderColor.rgb, element.roundRadius
            )
        }

        val nearTopSpot = -y - MAX_HEIGHT + 10
        // 标题用强调色，描述用浅灰
        Fonts.fontSemibold40.drawString(title, -currentX + 14F, nearTopSpot - 5, Color(245, 240, 232).rgb)
        Fonts.fontSemibold35.drawString(
            description, -currentX + 14F, nearTopSpot + Fonts.fontSemibold40.fontHeight - 2,
            Color(180, 175, 168).rgb
        )
    }

    // ===== Outline 样式：线框描边，透明底+severity色描边+标题severity色+描述白色 =====
    private fun drawOutlineStyle(element: Notifications, currentX: Float, extraSpace: Float, notificationWidth: Float) {
        val accent = severityAccentColor()

        // 透明底色（深色半透明，让描边可见）
        drawRoundedRect(0F, -y - MAX_HEIGHT, -currentX - extraSpace, -y, Color(0, 0, 0, 60).rgb, element.roundRadius)

        // severity 色描边
        drawRoundedBorder(
            0F, -y - MAX_HEIGHT, -currentX - extraSpace, -y,
            1.5F, accent.rgb, element.roundRadius
        )

        // 顶部 severity 细线装饰
        drawRect(-currentX - extraSpace + element.roundRadius, -y - MAX_HEIGHT,
            -element.roundRadius, -y - MAX_HEIGHT + 1F, accent.rgb)

        // 图标
        RenderUtils.drawImage(
            severityType.path, -currentX + 2, -y - MAX_HEIGHT + 4, ICON_SIZE, ICON_SIZE, radius = element.roundRadius
        )

        // 标题用 severity 色，描述用浅灰
        val nearTopSpot = -y - MAX_HEIGHT + 10
        Fonts.fontSemibold40.drawString(title, ICON_SIZE + 8F - currentX, nearTopSpot - 5, accent.rgb)
        Fonts.fontSemibold35.drawString(
            description, ICON_SIZE + 8F - currentX, nearTopSpot + Fonts.fontSemibold40.fontHeight - 2,
            Color(200, 196, 190).rgb
        )
    }

    // ===== Stamp 样式：邮戳印章，severity色圆形图章居中+图标在圆环正中央+右侧标题描述 =====
    private fun drawStampStyle(element: Notifications, currentX: Float, extraSpace: Float) {
        val bgColor = Color(250, 245, 232, 240)      // 信封米色
        val stampColor = severityAccentColor()         // 邮戳色=severity色
        val textColor = Color(44, 40, 34)             // 墨色
        val subColor = Color(130, 120, 105)           // 次要文字

        drawRoundedRect(0F, -y - MAX_HEIGHT, -currentX - extraSpace, -y, bgColor.rgb, element.roundRadius)

        if (element.renderBorder) {
            drawRoundedBorder(
                0F, -y - MAX_HEIGHT, -currentX - extraSpace, -y,
                element.borderWidth, element.borderColor.rgb, element.roundRadius
            )
        }

        // 邮戳圆形区域：图标 + 圆环在左侧，垂直居中
        val stampPadLeft = 4F
        val stampCX = -currentX + stampPadLeft + ICON_SIZE / 2F
        val stampCY = -y - MAX_HEIGHT / 2F
        val stampR = ICON_SIZE / 2F + 3F

        // 外圈（severity 色半透明）
        drawRoundedRect(stampCX - stampR, stampCY - stampR, stampCX + stampR, stampCY + stampR,
            Color(stampColor.red, stampColor.green, stampColor.blue, 100).rgb, stampR)
        // 内圈（背景色，形成圆环效果）
        drawRoundedRect(stampCX - stampR + 2F, stampCY - stampR + 2F, stampCX + stampR - 2F, stampCY + stampR - 2F,
            bgColor.rgb, stampR - 2F)

        // 图标在圆环正中央
        RenderUtils.drawImage(
            severityType.path,
            (stampCX - ICON_SIZE / 2F).toInt(),
            (stampCY - ICON_SIZE / 2F).toInt(),
            ICON_SIZE, ICON_SIZE, radius = element.roundRadius
        )

        // 右侧：标题 + 描述（垂直居中）
        val textStartX = stampCX + stampR + 8F
        val textCenterY = -y - MAX_HEIGHT / 2F
        Fonts.fontSemibold40.drawString(title, textStartX,
            textCenterY - Fonts.fontSemibold40.fontHeight / 2F - 2F, stampColor.rgb)
        Fonts.fontSemibold35.drawString(
            description, textStartX,
            textCenterY + Fonts.fontSemibold40.fontHeight / 2F + 2F, subColor.rgb)
    }
}