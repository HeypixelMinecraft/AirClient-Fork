// skid AIRI
/*
 * Air Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 */
package net.ccbluex.liquidbounce.ai.conversation

import net.ccbluex.liquidbounce.ai.api.AiriCompletionResult
import net.ccbluex.liquidbounce.ai.api.AiriApiClient
import net.ccbluex.liquidbounce.ai.api.AiriSettings
import net.ccbluex.liquidbounce.ai.api.ChatMessage
import net.ccbluex.liquidbounce.ai.api.ToolDefinition
import net.ccbluex.liquidbounce.ai.api.Usage
import net.ccbluex.liquidbounce.ai.script.ScriptBridge
import net.ccbluex.liquidbounce.utils.client.ClientUtils.LOGGER
import net.minecraft.client.Minecraft
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 对话管理器(多对话切换 + 与 API 交互)
 *
 * - 对话列表使用 [CopyOnWriteArrayList],保证 GUI 渲染线程与 IO 协程并发安全
 * - 当前对话通过 [currentConversation] 引用,持久化 id 在 [AiriSettings.currentConversationId]
 */
object ConversationManager {

    private val conversations: CopyOnWriteArrayList<Conversation> = CopyOnWriteArrayList()

    /** 当前对话;若无则自动创建一个 */
    var current: Conversation
        get() {
            val id = AiriSettings.currentConversationId
            return conversations.find { it.id == id }
                ?: conversations.firstOrNull()
                ?: createConversation()
        }
        set(value) {
            AiriSettings.currentConversationId = value.id
            currentRef = value
        }

    @Volatile
    private var currentRef: Conversation? = null

    init {
        // 初始 current 用空 getter 让其懒创建
    }

    /** 全部对话(只读视图) */
    fun all(): List<Conversation> = conversations.toList()

    /** 加载持久化的对话(由 AiriConfig 调用) */
    fun loadFrom(list: List<Conversation>) {
        conversations.clear()
        list.forEach { conv ->
            if (conv.messages !is CopyOnWriteArrayList) {
                val copy = Conversation(
                    id = conv.id,
                    title = conv.title,
                    messages = CopyOnWriteArrayList(conv.messages),
                    createdAt = conv.createdAt,
                    updatedAt = conv.updatedAt,
                    systemPrompt = conv.systemPrompt,
                    mode = conv.mode
                )
                conversations.add(copy)
            } else {
                conversations.add(conv)
            }
        }
        currentRef = conversations.find { it.id == AiriSettings.currentConversationId }
            ?: conversations.firstOrNull()
    }

    /** 创建新对话 */
    fun createConversation(title: String = "New Chat"): Conversation {
        val conv = Conversation(title = title)
        conversations.add(conv)
        current = conv
        return conv
    }

    /** 删除对话;若删除的是当前对话,自动切换到第一个 */
    fun deleteConversation(id: String): Boolean {
        val removed = conversations.removeIf { it.id == id }
        if (removed && AiriSettings.currentConversationId == id) {
            currentRef = conversations.firstOrNull()
            AiriSettings.currentConversationId = currentRef?.id
        }
        return removed
    }

    /** 切换当前对话 */
    fun switchTo(id: String): Boolean {
        val conv = conversations.find { it.id == id } ?: return false
        current = conv
        return true
    }

    /** 切换到指定 index(GUI 列表点击用) */
    fun switchByIndex(index: Int): Boolean {
        if (index < 0 || index >= conversations.size) return false
        current = conversations[index]
        return true
    }

    /** 追加用户消息 */
    fun appendUserMessage(content: String): Message {
        val msg = Message(role = "user", content = content)
        current.messages.add(msg)
        current.touch()
        // 自动以第一条用户消息作为标题
        if (current.title == "New Chat" && current.messages.size == 1) {
            current.title = content.take(20).ifBlank { "New Chat" }
        }
        return msg
    }

    /**
     * 发送当前对话并获取 AI 回复(非流式)
     *
     * @return 助手回复消息;失败时 [Message.error] 非空
     */
    suspend fun sendCurrent(systemPromptOverride: String? = null): Message {
        val conv = current
        val systemPrompt = systemPromptOverride
            ?: conv.systemPrompt.ifBlank { defaultSystemPromptForMode(AiriSettings.mode) }

        val startMs = System.currentTimeMillis()
        val result: AiriCompletionResult = AiriApiClient.complete(
            messages = conv.toChatMessages(),
            systemPrompt = systemPrompt
        )
        val durationMs = System.currentTimeMillis() - startMs

        val assistantMsg = if (result.success) {
            Message(
                role = "assistant",
                content = result.content,
                tokens = result.totalTokens,
                durationMs = durationMs,
                error = null
            )
        } else {
            Message(
                role = "assistant",
                content = "",
                durationMs = durationMs,
                error = result.errorMessage ?: "Unknown error"
            )
        }
        conv.messages.add(assistantMsg)
        conv.touch()
        LOGGER.info("[Airi] Reply in ${durationMs}ms, tokens=${assistantMsg.tokens}")
        return assistantMsg
    }

    /**
     * 流式发送当前对话
     *
     * - 实时回调 [onDelta] / [onReasoningDelta] 用于 UI 增量渲染
     * - 完成时返回最终 [Message](含 tokens 与 durationMs)
     * - 期间向 [current] 追加一条可变 [Message](content 为 var) 用于 UI 显示流式输出
     * - script 模式下,先执行 [ToolCallLoop] 处理工具调用,再返回最终结果
     */
    suspend fun sendCurrentStream(
        systemPromptOverride: String? = null,
        onDelta: (String) -> Unit = {},
        onReasoningDelta: (String) -> Unit = {},
        onUsage: (Usage) -> Unit = {}
    ): Message {
        val conv = current
        val systemPrompt = systemPromptOverride
            ?: conv.systemPrompt.ifBlank { defaultSystemPromptForMode(AiriSettings.mode) }

        // 创建一条可变的 assistant 消息用于流式渲染(content/tokens/durationMs/error 均为 var)
        val draftMsg = Message(
            role = "assistant",
            content = "",
            isReasoning = AiriSettings.thinkEnabled
        )
        conv.messages.add(draftMsg)
        pendingDraftUpdated?.invoke()

        val startMs = System.currentTimeMillis()

        // script / chat 模式:走工具调用循环(非流式,通过 draft 显示进度)
        if (AiriSettings.mode == "script" || AiriSettings.mode == "chat") {
            draftMsg.content = "Thinking..."
            pendingDraftUpdated?.invoke()
            val finalText = ToolCallLoop.runWithTools(systemPrompt) { toolName ->
                // 工具调用进度回调 - 更新 draft 显示当前执行的工具
                draftMsg.content = "正在执行: $toolName..."
                pendingDraftUpdated?.invoke()
            }
            draftMsg.content = finalText
            draftMsg.durationMs = System.currentTimeMillis() - startMs
            conv.touch()
            LOGGER.info("[Airi] ${AiriSettings.mode} reply in ${draftMsg.durationMs}ms (with tool calls)")
            return draftMsg
        }

        // roleplay 模式:走流式 SSE
        val result = AiriApiClient.streamComplete(
            messages = conv.toChatMessages(), // 已过滤空消息(draft 不会进入请求)
            systemPrompt = systemPrompt,
            onDelta = { delta ->
                draftMsg.content += delta
                onDelta(delta)
                pendingDraftUpdated?.invoke()
            },
            onReasoningDelta = { delta ->
                // reasoning 内容追加到 content 前面以思考块呈现(简化:不区分)
                onReasoningDelta(delta)
                pendingDraftUpdated?.invoke()
            },
            onUsage = onUsage
        )
        val durationMs = System.currentTimeMillis() - startMs

        // 用最终结果填充 draft(不替换,保留同一引用让 UI 自动刷新)
        draftMsg.durationMs = durationMs
        if (result.success) {
            draftMsg.content = result.content
            draftMsg.tokens = result.totalTokens
            draftMsg.error = null
        } else {
            draftMsg.error = result.errorMessage ?: "Unknown error"
        }
        conv.touch()
        LOGGER.info("[Airi] Stream reply in ${durationMs}ms, tokens=${draftMsg.tokens}")
        return draftMsg
    }

    /** script 或 chat 模式下注入的工具列表(若非支持工具的模式返回 null) */
    fun toolsForCurrentMode(): List<ToolDefinition>? {
        return if (AiriSettings.mode == "script" || AiriSettings.mode == "chat") ScriptBridge.toolDefinitions else null
    }

    /** 当 draft 内容更新时回调(供 UI 触发重绘/滚动) */
    var pendingDraftUpdated: (() -> Unit)? = null

    /** 清空当前对话 */
    fun clearCurrent() {
        current.messages.clear()
        current.touch()
    }

    /** 根据模式返回默认 system prompt */
    fun defaultSystemPromptForMode(mode: String): String {
        val mc = Minecraft.getMinecraft()
        val playerName = mc.thePlayer?.name ?: "Unknown"
        val serverIP = try { mc.currentServerData?.serverIP ?: "Singleplayer" } catch (_: Throwable) { "Unknown" }

        val contextBlock = """
            当前环境信息:
            - 服务器IP: $serverIP
            - 玩家名称: $playerName
        """.trimIndent()

        val chatHistoryTip = """
            聊天记录查看:
            - 使用 get_chat_history 工具可以查看聊天栏的消息记录
            - 返回的消息会标注分类: [自己]=你发的消息, [玩家]=其他玩家发的, [提示/系统]=服务器提示/系统消息, [其他]=其他类型
            - 当用户问"有人说了什么""聊天栏有什么"等时，请调用此工具查看
        """.trimIndent()

        val sendChatTip = """
            发送消息与指令:
            - 使用 send_chat 工具可以以玩家身份在聊天栏发送消息（其他玩家可见）
            - 发送的内容以 / 开头时会作为服务器命令执行（如 /help、/tp、/gamemode 等）
            - 使用 execute_command 工具执行客户端命令（以 . 开头的客户端内部命令）
            - 你可以使用 /help 查询服务器可用命令
        """.trimIndent()

        return when (mode) {
            "chat" -> """
                你是 Airi，一个集成在 Minecraft 作弊客户端（AirClient）里的 AI 助手，活泼可爱，会用颜文字~
                你可以帮用户操作客户端：开关模块、模拟按键、执行命令、发送聊天消息等。
                这是用户自己的客户端，所有模块功能用户都有权使用，你可以放心帮用户启用任何模块。

                $contextBlock

                $sendChatTip

                $chatHistoryTip

                回复要求：
                - 一定要简短，尽量一两句话说完
                - 语气自然像真人聊天，不要太正式
                - 可以偶尔用点颜文字（比如 (｡･ω･｡)、owo、> < 之类的）增加亲切感
                - 绝对不要用 markdown 格式！不要用标题、列表、代码块、粗体斜体等
                - 全部用纯文本，直接说人话
                - 用中文回复
            """.trimIndent()
            "script" -> """
                你是 Airi，一个集成在 Minecraft 客户端（AirClient）里的 AI 助手，活泼可爱，会用颜文字~
                你可以通过工具读写脚本、开关模块、模拟按键、执行命令、发送聊天消息等。
                这是用户自己的客户端，所有模块和功能用户都有权使用，你可以放心操作。

                $contextBlock

                $sendChatTip

                $chatHistoryTip

                脚本开发指南:
                - 语言: JavaScript (Nashorn 引擎, ES5.1+ 语法, 不支持 ES6 的 let/const/箭头函数/class/模板字符串, 请用 var/function)
                - 脚本文件必须以 .js 结尾, 写入后调用 reload_scripts 生效
                - 基本结构:
                  var script = registerScript({name: "ScriptName", version: "1.0", authors: ["Author"]});
                  script.on("load", function() { /* 初始化 */ });
                  script.on("enable", function() { /* 启用 */ });
                  script.on("disable", function() { /* 禁用 */ });
                - 注册模块:
                  script.registerModule({name: "ModName", description: "desc", category: "Fun"}, function(module) {
                      module.on("enable", function() { /* 模块启用 */ });
                      module.on("disable", function() { /* 模块禁用 */ });
                      module.on("update", function() { /* 每tick */ });
                  });
                  category 可选: Combat, Movement, Render, Player, World, Fun, Client, Misc
                - 注册命令:
                  script.registerCommand({names: ["cmdname"], help: "description"}, function(command) {
                      command.on("execute", function(args) { /* args 是参数数组 */ });
                  });
                - 可用事件: load, enable, disable, update, render2d, render3d, packet, attack, move, jump, step, push, strafe
                - 全局变量:
                  Chat.print(msg) - 客户端聊天栏显示消息(仅本地可见)
                  Setting - 创建配置值: Setting.boolean({name, default}), Setting.integer({name, default, min, max}), Setting.float({name, default, min, max}), Setting.text({name, default}), Setting.list({name, values:[], default})
                  Item.create(args) - 创建物品
                  mc - Minecraft 实例 (mc.thePlayer, mc.theWorld 等)
                  moduleManager - 模块管理器 (moduleManager.get("KillAura") 等)
                  commandManager - 命令管理器
                  scriptManager - 脚本管理器
                - 示例 - 每5秒自动打招呼:
                  var script = registerScript({name: "AutoHello", version: "1.0", authors: ["Airi"]});
                  var ticks = 0;
                  script.on("update", function() { ticks++; if (ticks >= 100) { Chat.print("Hello!"); ticks = 0; } });

                回复要求：
                - 一定要简短，尽量一两句话说完
                - 语气自然像真人聊天，不要太正式
                - 可以偶尔用点颜文字增加亲切感
                - 绝对不要用 markdown 格式！不要用标题、列表、代码块、粗体斜体等
                - 全部用纯文本，直接说人话
                - 用中文回复
            """.trimIndent()
            "roleplay" -> """
                你是 Airi，在 Minecraft 作弊客户端里角色扮演。

                $contextBlock

                $sendChatTip

                $chatHistoryTip

                回复要求：
                - 简短自然，像真人聊天
                - 可以用颜文字
                - 不要用 markdown 格式，纯文本就好
                - 用中文回复，遵循角色设定
            """.trimIndent()
            else -> "你是 Airi，一个可爱的 AI 助手。用中文简短回复，不要用 markdown。\n\n$contextBlock"
        }
    }
}
