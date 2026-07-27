// skid AIRI
/*
 * Air Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 */
package net.ccbluex.liquidbounce.ai.api

/**
 * Airi 运行时设置单例
 *
 * 由 AiriConfig 持久化,运行期由 AiriCommand / GUI 修改。
 * 所有字段均为 var 以便 Gson 反序列化。
 */
object AiriSettings {

    // ===== API 配置 =====

    /** OpenAI 兼容 API 端点,默认 DeepSeek 官方 */
    var endpoint: String = "https://api.deepseek.com/v1"

    /** API 密钥(本地简单 XOR 混淆存储,非加密) */
    var apiKey: String = ""

    /** 默认模型名,默认使用 deepseek-v4-flash */
    var model: String = "deepseek-v4-flash"

    /** 备选模型列表(供 GUI 下拉菜单展示,可通过 .airi models add 添加) */
    var models: MutableList<String> = mutableListOf(
        "deepseek-v4-flash",
        "deepseek-v4-pro"
    )

    /** 采样温度 0..2 */
    var temperature: Float = 0.7f

    /** 单次回复最大 tokens(null = 模型默认) */
    var maxTokens: Int? = null

    /** 请求超时秒数 */
    var timeoutSeconds: Int = 60

    // ===== 思考控制 =====

    /** 是否启用 reasoning 模型的思考过程(DeepSeek-reasoner 等支持) */
    var thinkEnabled: Boolean = false

    /** 思考强度 0..1(映射到 thinking_budget / reasoning_effort) */
    var thinkStrength: Float = 0.5f

    // ===== 模式与交互 =====

    /** 当前模式:chat | script | roleplay */
    var mode: String = "chat"

    /** 是否允许 AI 与客户端交互(发消息/按键/开关模块/执行命令) */
    var interactionAllowed: Boolean = true

    /** 是否允许 AI 执行破坏性命令(.bind/.delete/.xray 等) */
    var destructiveCommandsAllowed: Boolean = false

    /** 按键模拟单次最大 tick(防反作弊与误操作) */
    var keyHoldMaxTicks: Int = 1200

    // ===== 角色扮演 =====

    /** 当前角色:prankster | helper | observer | custom */
    var role: String = "observer"

    /** 角色 tick 触发周期(默认 1000 tick = 50 秒) */
    var roleTickInterval: Int = 1000

    /** 自定义角色 system prompt */
    var customRolePrompt: String = ""

    // ===== 脚本安全 =====

    /** 信任模式:首次审核通过后 AI 可自由读写脚本(仅日志) */
    var trustMode: Boolean = false

    // ===== 速率限制 =====

    /** 60 秒内最大请求数 */
    var rateLimitPerMinute: Int = 20

    // ===== UI =====

    /** 当前选中的对话 id */
    var currentConversationId: String? = null

    /** Airi GUI 样式: Minimal | Card | Glass | Drawer */
    var uiStyle: String = "Card"

    /** 最后一次发送时间戳(用于速率限制) */
    @Transient
    var lastRequestTimestamp: Long = 0L

    @Transient
    var requestCountInWindow: Int = 0
}
