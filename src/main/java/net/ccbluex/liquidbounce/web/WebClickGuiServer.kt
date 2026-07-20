/*
 * Air Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 */
package net.ccbluex.liquidbounce.web

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import net.ccbluex.liquidbounce.LiquidBounce
import net.ccbluex.liquidbounce.config.*
import net.ccbluex.liquidbounce.event.EventHook
import net.ccbluex.liquidbounce.event.EventManager
import net.ccbluex.liquidbounce.event.GameTickEvent
import net.ccbluex.liquidbounce.event.Listenable
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.utils.client.ClientUtils.LOGGER
import org.lwjgl.input.Keyboard
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors

/**
 * Embedded HTTP server that exposes live module state to the web ClickGUI.
 *
 * Endpoints:
 *   GET  /                 landing page (links to /style-1..3)
 *   GET  /style-1          serves the Linear-style HTML
 *   GET  /style-2          serves the Brutalist-press HTML
 *   GET  /style-3          serves the Awwwards-glass HTML
 *   GET  /api/state        full JSON snapshot of modules + values
 *   POST /api/toggle       body { "module": "KillAura" }
 *   POST /api/property     body { "module", "property", "type", "value" }
 *
 * Static HTML is served from disk ./web-clickgui/ when present (dev),
 * otherwise from the embedded resource /web/ (production jar).
 *
 * All state mutations (toggle, setProperty) are queued and applied on
 * the main client thread via GameTickEvent so they stay thread-safe.
 */
object WebClickGuiServer : Listenable {

    const val PORT = 8790

    private var server: HttpServer? = null
    private var running = false

    /** Actions enqueued by HTTP worker threads, drained on the main tick. */
    private val actions = ConcurrentLinkedQueue<Runnable>()

    /** Last serialized state (cheap cache so polling clients receive identical payloads). */
    @Volatile
    private var lastStateJson: String? = null
    @Volatile
    private var lastStateHash: Long = 0

    /**
     * Start the embedded HTTP server and register the tick handler.
     * Safe to call multiple times — subsequent calls are no-ops.
     */
    fun start() {
        if (running) return
        try {
            val srv = HttpServer.create(InetSocketAddress(PORT), 0)
            srv.createContext("/api/state", ::handleState)
            srv.createContext("/api/toggle", ::handleToggle)
            srv.createContext("/api/property", ::handleProperty)
            srv.createContext("/", ::handleStatic)
            srv.executor = Executors.newFixedThreadPool(4)
            srv.start()
            server = srv
            running = true

            // Register tick handler to drain the action queue on the main thread
            EventManager.registerEventHook(
                GameTickEvent::class.java,
                EventHook(this, always = true, priority = 0) { onTick() }
            )

            LOGGER.info("[WebClickGUI] Server running at http://localhost:$PORT")
        } catch (t: Throwable) {
            LOGGER.error("[WebClickGUI] Failed to start server: ${t.message}")
        }
    }

    /**
     * Stop the embedded HTTP server.
     */
    fun stop() {
        if (!running) return
        server?.stop(0)
        server = null
        running = false
        LOGGER.info("[WebClickGUI] Server stopped")
    }

    override fun handleEvents(): Boolean = true

    private fun onTick() {
        var r: Runnable? = actions.poll()
        while (r != null) {
            try {
                r.run()
            } catch (t: Throwable) {
                LOGGER.error("[WebClickGUI] Action failed: ${t.message}")
            }
            r = actions.poll()
        }
    }

    // ============================================================
    // CORS + helpers
    // ============================================================
    private fun setCORS(ex: HttpExchange) {
        ex.responseHeaders.set("Access-Control-Allow-Origin", "*")
        ex.responseHeaders.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        ex.responseHeaders.set("Access-Control-Allow-Headers", "Content-Type, Content-Length")
    }

    private fun preflight(ex: HttpExchange): Boolean {
        if (ex.requestMethod != "OPTIONS") return false
        setCORS(ex)
        ex.sendResponseHeaders(204, -1)
        return true
    }

    private fun sendJson(ex: HttpExchange, code: Int, json: String) {
        setCORS(ex)
        val body = json.toByteArray(StandardCharsets.UTF_8)
        ex.responseHeaders.set("Content-Type", "application/json; charset=utf-8")
        ex.responseHeaders.set("Cache-Control", "no-store")
        ex.sendResponseHeaders(code, body.size.toLong())
        ex.responseBody.use { os -> os.write(body) }
    }

    private fun sendError(ex: HttpExchange, code: Int, message: String?) {
        val o = JsonObject()
        o.addProperty("ok", false)
        o.addProperty("error", message ?: "error")
        sendJson(ex, code, o.toString())
    }

    private fun parseBody(ex: HttpExchange): JsonObject {
        ex.requestBody.use { is_ ->
            val raw = readAll(is_)
            if (raw.isEmpty()) return JsonObject()
            return JsonParser().parse(String(raw, StandardCharsets.UTF_8)).asJsonObject
        }
    }

    private fun readAll(is_: InputStream): ByteArray {
        val bos = java.io.ByteArrayOutputStream()
        val buf = ByteArray(4096)
        var n: Int
        while (is_.read(buf).also { n = it } != -1) bos.write(buf, 0, n)
        return bos.toByteArray()
    }

    // ============================================================
    // /api/state
    // ============================================================
    private fun handleState(ex: HttpExchange) {
        if (preflight(ex)) return
        try {
            sendJson(ex, 200, buildStateJson())
        } catch (t: Throwable) {
            sendError(ex, 500, t.message)
        }
    }

    private fun buildStateJson(): String {
        // cache: if no actions are pending and we have a cached snapshot, reuse it
        val currentHash = computeStateHash()
        if (actions.isEmpty() && lastStateJson != null && currentHash == lastStateHash) {
            return lastStateJson!!
        }

        val root = JsonObject()
        root.addProperty("ok", true)
        root.addProperty("timestamp", System.currentTimeMillis())

        val cats = JsonObject()
        var totalActive = 0
        var totalAll = 0
        for (cat in Category.entries) {
            val arr = JsonArray()
            for (m in LiquidBounce.moduleManager) {
                if (m.category != cat) continue
                arr.add(moduleJson(m))
                if (m.state) totalActive++
                totalAll++
            }
            cats.add(cat.name.lowercase(), arr)
        }
        root.add("categories", cats)
        root.addProperty("totalActive", totalActive)
        root.addProperty("totalModules", totalAll)
        root.addProperty("clientName", LiquidBounce.CLIENT_NAME)
        root.addProperty("version", LiquidBounce.clientVersionText)

        lastStateJson = root.toString()
        lastStateHash = currentHash
        return lastStateJson!!
    }

    /**
     * Cheap structural hash of the live state so identical polls return the
     * cached payload without re-serializing.
     */
    private fun computeStateHash(): Long {
        var h = 1L
        for (m in LiquidBounce.moduleManager) {
            h = h * 31 + m.name.hashCode()
            h = h * 31 + if (m.state) 1 else 0
            h = h * 31 + if (m.isHidden) 1 else 0
            h = h * 31 + m.keyBind
            for (v in m.values) {
                if (v.excluded || v.hidden) continue
                if (!v.shouldRender()) continue
                val value = v.get()
                h = h * 31 + v.name.hashCode()
                h = h * 31 + (value?.hashCode() ?: 0)
            }
        }
        return h
    }

    private fun moduleJson(m: Module): JsonObject {
        val o = JsonObject()
        o.addProperty("name", m.name)
        o.addProperty("category", m.category.name.lowercase())
        o.addProperty("enabled", m.state)
        o.addProperty("hidden", m.isHidden)
        o.addProperty("key", if (m.keyBind != 0) keyName(m.keyBind) else "")

        val propsArr = JsonArray()
        for (v in m.values) {
            if (v.excluded || v.hidden) continue
            if (!v.shouldRender()) continue
            val po = valueJson(v)
            if (po != null) propsArr.add(po)
        }
        o.add("props", propsArr)
        return o
    }

    private fun valueJson(v: Value<*>): JsonObject? {
        val po = JsonObject()
        po.addProperty("name", v.name)

        when (v) {
            is BoolValue -> {
                po.addProperty("type", "bool")
                po.addProperty("value", v.get())
            }
            is IntValue -> {
                po.addProperty("type", "int")
                po.addProperty("value", v.get())
                po.addProperty("min", v.minimum)
                po.addProperty("max", v.maximum)
            }
            is FloatValue -> {
                po.addProperty("type", "float")
                po.addProperty("value", v.get())
                po.addProperty("min", v.minimum)
                po.addProperty("max", v.maximum)
            }
            is IntRangeValue -> {
                po.addProperty("type", "intrange")
                val range = v.get()
                po.addProperty("valueFirst", range.first)
                po.addProperty("valueLast", range.last)
                po.addProperty("min", v.minimum)
                po.addProperty("max", v.maximum)
            }
            is FloatRangeValue -> {
                po.addProperty("type", "floatrange")
                val range = v.get()
                po.addProperty("valueFirst", range.start)
                po.addProperty("valueLast", range.endInclusive)
                po.addProperty("min", v.minimum)
                po.addProperty("max", v.maximum)
            }
            is ListValue -> {
                po.addProperty("type", "mode")
                po.addProperty("value", v.get())
                val opts = JsonArray()
                for (opt in v.values) opts.add(JsonPrimitive(opt))
                po.add("options", opts)
            }
            is TextValue -> {
                po.addProperty("type", "text")
                po.addProperty("value", v.get())
            }
            is ColorValue -> {
                po.addProperty("type", "color")
                po.addProperty("value", argbToHex(v.selectedColor().rgb))
                po.addProperty("rainbow", v.rainbow)
            }
            is FontValue -> {
                po.addProperty("type", "text")
                po.addProperty("value", v.displayName)
            }
            is BlockValue -> {
                po.addProperty("type", "int")
                po.addProperty("value", v.get())
                po.addProperty("min", v.minimum)
                po.addProperty("max", v.maximum)
            }
            else -> {
                // Unknown value type: expose as opaque text
                po.addProperty("type", "text")
                po.addProperty("value", v.get()?.toString() ?: "")
            }
        }
        return po
    }

    // ============================================================
    // /api/toggle
    // ============================================================
    private fun handleToggle(ex: HttpExchange) {
        if (preflight(ex)) return
        try {
            val req = parseBody(ex)
            val name = if (req.has("module")) req["module"].asString else null
            if (name == null) { sendError(ex, 400, "missing module"); return }
            actions.add(Runnable {
                val m = LiquidBounce.moduleManager[name]
                m?.toggle()
            })
            val o = JsonObject()
            o.addProperty("ok", true)
            sendJson(ex, 200, o.toString())
        } catch (t: Throwable) {
            sendError(ex, 500, t.message)
        }
    }

    // ============================================================
    // /api/property
    // ============================================================
    private fun handleProperty(ex: HttpExchange) {
        if (preflight(ex)) return
        try {
            val req = parseBody(ex)
            val modName = if (req.has("module")) req["module"].asString else null
            val propName = if (req.has("property")) req["property"].asString else null
            val type = if (req.has("type")) req["type"].asString else "text"
            val valEl: JsonElement? = if (req.has("value")) req["value"] else null
            if (modName == null || propName == null || valEl == null) {
                sendError(ex, 400, "missing fields")
                return
            }
            actions.add(Runnable { applyProperty(modName, propName, type, valEl) })
            val o = JsonObject()
            o.addProperty("ok", true)
            sendJson(ex, 200, o.toString())
        } catch (t: Throwable) {
            sendError(ex, 500, t.message)
        }
    }

    private fun applyProperty(modName: String, propName: String, type: String, valEl: JsonElement) {
        val m = LiquidBounce.moduleManager[modName] ?: return
        val v = m.values.find { it.name.equals(propName, ignoreCase = true) } ?: return
        try {
            when (type) {
                "bool" -> (v as? BoolValue)?.set(valEl.asBoolean)
                "int" -> (v as? IntValue)?.set(valEl.asInt)
                    ?: (v as? BlockValue)?.set(valEl.asInt)
                "float" -> (v as? FloatValue)?.set(valEl.asFloat)
                "intrange" -> (v as? IntRangeValue)?.set(valEl.asInt..v.get().last)
                "floatrange" -> (v as? FloatRangeValue)?.set(valEl.asFloat..v.get().endInclusive)
                "mode" -> (v as? ListValue)?.set(valEl.asString)
                "color" -> {
                    // For color values, parse hex and update the underlying Color
                    val colorVal = v as? ColorValue ?: return
                    val rgb = hexToArgb(valEl.asString)
                    colorVal.set(java.awt.Color(rgb, true))
                }
                "text" -> (v as? TextValue)?.set(valEl.asString)
                else -> (v as? TextValue)?.set(valEl.asString)
            }
        } catch (t: Throwable) {
            LOGGER.error("[WebClickGUI] Failed to set $modName.$propName: ${t.message}")
        }
    }

    // ============================================================
    // Static file serving (HTML)
    // ============================================================
    private fun handleStatic(ex: HttpExchange) {
        setCORS(ex)
        val path = ex.requestURI.path

        // route / to a small landing page
        if (path == "/" || path.isEmpty()) {
            sendHtml(ex, landingPage())
            return
        }

        var fileName: String? = null
        when {
            path == "/style-1" || path.startsWith("/style-1") ->
                fileName = "style-1-linear-anti-slop.html"
            path == "/style-2" || path.startsWith("/style-2") ->
                fileName = "style-2-brutalist-press.html"
            path == "/style-3" || path.startsWith("/style-3") ->
                fileName = "style-3-awwwards-glass.html"
            path == "/favicon.ico" -> {
                ex.sendResponseHeaders(204, -1)
                return
            }
        }

        if (fileName == null) {
            sendError(ex, 404, "not found")
            return
        }

        val body = readStatic(fileName)
        if (body == null) {
            sendError(ex, 404, "file not bundled: $fileName")
            return
        }
        ex.responseHeaders.set("Content-Type", "text/html; charset=utf-8")
        ex.responseHeaders.set("Cache-Control", "no-store")
        ex.sendResponseHeaders(200, body.size.toLong())
        ex.responseBody.use { os -> os.write(body) }
    }

    /**
     * Try disk first (./web-clickgui/ relative to the working directory),
     * then fall back to the embedded resource /web/<name>.
     */
    private fun readStatic(fileName: String): ByteArray? {
        // disk: try several candidate locations
        val candidates = arrayOf(
            "web-clickgui/$fileName",            // cwd (dev: ./run/web-clickgui)
            "../web-clickgui/$fileName",         // parent of cwd (dev: project root)
            "../../web-clickgui/$fileName",      // two levels up (dev: workspace root)
        )
        for (path in candidates) {
            try {
                val disk: Path = Paths.get(path)
                if (Files.exists(disk)) {
                    return Files.readAllBytes(disk)
                }
            } catch (_: IOException) { }
        }

        // embedded resource (production jar)
        try {
            WebClickGuiServer::class.java.getResourceAsStream("/web/$fileName")?.use { is_ ->
                return readAll(is_)
            }
        } catch (_: IOException) { }
        return null
    }

    private fun sendHtml(ex: HttpExchange, html: String) {
        val body = html.toByteArray(StandardCharsets.UTF_8)
        ex.responseHeaders.set("Content-Type", "text/html; charset=utf-8")
        ex.responseHeaders.set("Cache-Control", "no-store")
        ex.sendResponseHeaders(200, body.size.toLong())
        ex.responseBody.use { os -> os.write(body) }
    }

    private fun landingPage(): String {
        val styles = arrayOf(
            Triple("1", "Linear / anti-slop",   "#f43f5e"),
            Triple("2", "Brutalist register",   "#c1272d"),
            Triple("3", "Awwwards glass",       "#a78bfa")
        )
        val cards = styles.joinToString("") { (n, label, accent) ->
            """<a class="card" href="/style-$n" style="--a:$accent">
<span class="num">$n</span>
<span class="lbl">$label</span>
<span class="open">Open &rarr;</span>
</a>"""
        }
        return """<!doctype html><html><head><meta charset="utf-8"><title>${LiquidBounce.CLIENT_NAME} Web ClickGUI</title>
<style>
body{font-family:-apple-system,system-ui,sans-serif;background:#0a0a0c;color:#e5e5e7;
display:grid;place-items:center;min-height:100vh;margin:0;padding:32px}
.wrap{text-align:center;max-width:920px;width:100%}
.h{font-size:30px;font-weight:700;margin:0 0 8px;letter-spacing:-0.02em}
.s{color:#888;margin:0 0 32px;font-size:14px}
.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(240px,1fr));gap:14px;text-align:left}
.card{position:relative;padding:22px 22px 20px;border:1px solid #2a2a30;border-radius:14px;
background:#15151a;text-decoration:none;color:#fff;display:grid;grid-template-rows:auto 1fr auto;gap:14px;
transition:transform .2s cubic-bezier(.16,1,.3,1),border-color .2s,background .2s;overflow:hidden}
.card::before{content:"";position:absolute;left:0;top:0;height:3px;width:100%;
background:linear-gradient(90deg,var(--a),transparent);opacity:.7}
.card:hover{transform:translateY(-3px);border-color:var(--a);background:#18181d}
.card .num{font-family:ui-monospace,"SF Mono",monospace;font-size:34px;font-weight:700;color:var(--a);line-height:1}
.card .lbl{font-size:14px;font-weight:500;color:#e5e5e7}
.card .open{font-family:ui-monospace,"SF Mono",monospace;font-size:11px;color:#666;letter-spacing:.08em;text-transform:uppercase}
.card:hover .open{color:var(--a)}
.port{margin-top:36px;color:#555;font-family:ui-monospace,"SF Mono",monospace;font-size:11px}
</style></head>
<body><div class="wrap">
<div class="h">${LiquidBounce.CLIENT_NAME} Web ClickGUI</div>
<div class="s">Live, real-time module control. Pick a style:</div>
<div class="grid">$cards</div>
<div class="port">API at /api/state &middot; port $PORT</div>
</div></body></html>"""
    }

    // ============================================================
    // Conversions
    // ============================================================
    private fun keyName(keyCode: Int): String {
        return try {
            Keyboard.getKeyName(keyCode) ?: ""
        } catch (_: Throwable) {
            ""
        }
    }

    /** ARGB int to #RRGGBB (alpha dropped for the HTML color input). */
    private fun argbToHex(argb: Int): String {
        val r = (argb shr 16) and 0xFF
        val g = (argb shr 8) and 0xFF
        val b = argb and 0xFF
        return String.format("#%02X%02X%02X", r, g, b)
    }

    /** #RRGGBB or #AARRGGBB to ARGB int. */
    private fun hexToArgb(hex: String?): Int {
        if (hex == null) return -0x1 // 0xFFFFFFFF
        val h = hex.replace("#", "").trim()
        val a: Int
        val r: Int
        val g: Int
        val b: Int
        try {
            when (h.length) {
                6 -> {
                    a = 255
                    r = Integer.parseInt(h.substring(0, 2), 16)
                    g = Integer.parseInt(h.substring(2, 4), 16)
                    b = Integer.parseInt(h.substring(4, 6), 16)
                }
                8 -> {
                    a = Integer.parseInt(h.substring(0, 2), 16)
                    r = Integer.parseInt(h.substring(2, 4), 16)
                    g = Integer.parseInt(h.substring(4, 6), 16)
                    b = Integer.parseInt(h.substring(6, 8), 16)
                }
                else -> return -0x1
            }
        } catch (_: NumberFormatException) {
            return -0x1
        }
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }
}
