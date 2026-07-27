/*
 * Air Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 */
// Migrated from Leader-Lite AutoBlockIn
package net.ccbluex.liquidbounce.features.module.modules.player

import net.ccbluex.liquidbounce.event.GameTickEvent
import net.ccbluex.liquidbounce.event.MovementInputEvent
import net.ccbluex.liquidbounce.event.RotationUpdateEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.utils.block.block
import net.ccbluex.liquidbounce.utils.extensions.eyes
import net.ccbluex.liquidbounce.utils.extensions.onPlayerRightClick
import net.ccbluex.liquidbounce.utils.rotation.Rotation
import net.ccbluex.liquidbounce.utils.rotation.RotationSettings
import net.ccbluex.liquidbounce.utils.rotation.RotationUtils.setTargetRotation
import net.minecraft.block.Block
import net.minecraft.init.Blocks
import net.minecraft.item.ItemBlock
import net.minecraft.item.ItemStack
import net.minecraft.util.BlockPos
import net.minecraft.util.EnumFacing
import net.minecraft.util.MathHelper
import net.minecraft.util.MovingObjectPosition
import net.minecraft.util.Vec3
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * AutoBlockIn - 自我封装
 *
 * 迁移自 Leader-Lite AutoBlockIn，自动用方块把玩家自己包围起来。
 *
 * 主要流程:
 * 1) 选择最优方块物品 (obsidian > end_stone > planks > ...)
 * 2) BFS 搜索从已有方块到玩家头顶/侧边的路径，逐个放置
 * 3) 旋转辅助 + 容差检查 + 放置延迟
 *
 * 与原版的差异:
 * - 移除 SwapItemEvent (AirClient 无此事件)
 * - 使用 RotationUpdateEvent + setTargetRotation 替代原 UpdateEvent.setRotation
 * - 使用 MovementInputEvent 替代 MoveInputEvent
 * - 使用 GameTickEvent 替代 TickEvent
 */
object AutoBlockIn : Module("AutoBlockIn", Category.PLAYER, defaultState = false) {

    private val range by float("range", 4.5f, 3.0f..6.0f)
    private val speed by int("speed", 20, 5..100)
    private val placeDelay by int("place-delay", 50, 0..200)
    private val rotationTolerance by int("rotation-tolerance", 25, 5..100)
    private val showProgress by boolean("show-progress", true)

    private val options = RotationSettings(this).withoutKeepRotation()

    private val BLOCK_SCORE = mapOf(
        "obsidian" to 0,
        "end_stone" to 1,
        "planks" to 2,
        "log" to 2,
        "glass" to 3,
        "stained_glass" to 3,
        "hardened_clay" to 4,
        "stained_hardened_clay" to 4,
        "cloth" to 5
    )

    private var serverYaw = 0f
    private var serverPitch = 0f
    private var progress = 0f
    private var aimYaw = 0f
    private var aimPitch = 0f
    private var targetBlock: BlockPos? = null
    private var targetFacing: EnumFacing? = null
    private var targetHitVec: Vec3? = null
    private var lastSlot = -1
    private var lastPlaceTime = 0L

    private val DIRS = arrayOf(intArrayOf(1, 0, 0), intArrayOf(0, 0, 1), intArrayOf(-1, 0, 0), intArrayOf(0, 0, -1))
    private const val INSET = 0.05
    private const val STEP = 0.2
    private const val JIT = 0.02

    override fun onEnable() {
        val player = mc.thePlayer ?: return
        serverYaw = player.rotationYaw
        serverPitch = player.rotationPitch
        aimYaw = serverYaw
        aimPitch = serverPitch
        progress = 0f
        lastSlot = player.inventory.currentItem
        targetBlock = null
        targetFacing = null
        targetHitVec = null
        lastPlaceTime = 0L
    }

    override fun onDisable() {
        val player = mc.thePlayer
        if (lastSlot != -1 && player != null && player.inventory.currentItem != lastSlot) {
            player.inventory.currentItem = lastSlot
        }
        progress = 0f
        targetBlock = null
        targetFacing = null
        targetHitVec = null
    }

    val onRotationUpdate = handler<RotationUpdateEvent> {
        if (!state) return@handler
        val player = mc.thePlayer ?: return@handler
        val world = mc.theWorld ?: return@handler
        if (mc.currentScreen != null) return@handler

        serverYaw = player.rotationYaw
        serverPitch = player.rotationPitch

        updateProgress()

        val blockSlot = findBestBlockSlot()
        if (blockSlot != -1 && player.inventory.currentItem != blockSlot) {
            player.inventory.currentItem = blockSlot
        }

        val currentHeld: ItemStack? = player.inventory.currentItem?.let { player.inventory.getStackInSlot(it) }
        val holdingBlock = currentHeld != null && currentHeld!!.item is ItemBlock
        if (!holdingBlock) {
            targetBlock = null
            targetFacing = null
            targetHitVec = null
            return@handler
        }

        findBestPlacement()

        val tb = targetBlock
        val tf = targetFacing
        val tv = targetHitVec
        if (tb != null && tf != null && tv != null) {
            val eyes = player.eyes
            val dx = tv.xCoord - eyes.xCoord
            val dy = tv.yCoord - eyes.yCoord
            val dz = tv.zCoord - eyes.zCoord
            val dist = sqrt(dx * dx + dz * dz)

            var targetYaw = MathHelper.wrapAngleTo180_float(Math.toDegrees(atan2(dz, dx)).toFloat() - 90f)
            val targetPitch = -Math.toDegrees(atan2(dy.toDouble(), dist)).toFloat()

            val yawDiff = MathHelper.wrapAngleTo180_float(targetYaw - serverYaw)
            val pitchDiff = targetPitch - serverPitch

            val maxTurn = speed.toFloat()
            val yawStep = yawDiff.coerceIn(-maxTurn, maxTurn)
            val pitchStep = pitchDiff.coerceIn(-maxTurn, maxTurn)

            aimYaw = serverYaw + yawStep
            aimPitch = (serverPitch + pitchStep).coerceIn(-90f, 90f)

            if (options.rotationsActive) {
                setTargetRotation(Rotation(aimYaw, aimPitch), options = options)
            }
        }
    }

    val onTick = handler<GameTickEvent> {
        if (!state) return@handler
        val player = mc.thePlayer ?: return@handler
        if (mc.theWorld == null) return@handler
        if (mc.currentScreen != null) return@handler

        val tb = targetBlock ?: return@handler
        val tf = targetFacing ?: return@handler
        val tv = targetHitVec ?: return@handler

        if (!withinRotationTolerance(aimYaw, aimPitch)) return@handler

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastPlaceTime < placeDelay) return@handler
        lastPlaceTime = currentTime

        val mop = rayTraceBlock(aimYaw, aimPitch, range.toDouble())
        if (mop != null
            && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
            && mop.blockPos == tb
            && mop.sideHit == tf
        ) {
            val heldStack = player.inventory.currentItem?.let { player.inventory.getStackInSlot(it) }
            if (heldStack != null && heldStack.item is ItemBlock) {
                player.onPlayerRightClick(tb, tf, mop.hitVec, heldStack)
                player.swingItem()
                targetBlock = null
                targetFacing = null
                targetHitVec = null
            }
        }
    }

    val onMovementInput = handler<MovementInputEvent> {
        // 占位，与原版一致保留 moveFix 钩子但目前不修改输入
    }

    private fun updateProgress() {
        val player = mc.thePlayer ?: return
        val feetPos = BlockPos(player)
        var filled = 0
        val total = 9

        if (!isAir(feetPos.up(2))) filled++
        for (d in DIRS) {
            if (!isAir(feetPos.add(d[0], 0, d[2]))) filled++
            if (!isAir(feetPos.add(d[0], 1, d[2]))) filled++
        }
        progress = filled.toFloat() / total
    }

    private fun findBestBlockSlot(): Int {
        var bestSlot = -1
        var bestScore = Int.MAX_VALUE
        for (slot in 0..8) {
            val stack: ItemStack = mc.thePlayer.inventory.getStackInSlot(slot) ?: continue
            if (stack.stackSize == 0) continue
            if (stack.item !is ItemBlock) continue
            val block = (stack.item as ItemBlock).block
            val name = block.unlocalizedName.replace("tile.", "")
            val score = BLOCK_SCORE[name] ?: continue
            if (score < bestScore) {
                bestScore = score
                bestSlot = slot
                if (score == 0) break
            }
        }
        return bestSlot
    }

    private fun findBestPlacement() {
        val player = mc.thePlayer ?: return
        val feetPos = BlockPos(player)
        val eye = player.eyes
        val reach = range.toDouble()
        val reachSq = reach * reach

        val roofTarget = feetPos.up(2)
        if (!isAir(roofTarget)) {
            sidesAim(eye, reach, feetPos)
            return
        }

        val supports = mutableListOf<BlockData>()
        val minX = Math.floor(eye.xCoord - reach).toInt()
        val maxX = Math.floor(eye.xCoord + reach).toInt()
        val minY = Math.floor(eye.yCoord - 1).toInt()
        val maxY = Math.floor(eye.yCoord + reach).toInt()
        val minZ = Math.floor(eye.zCoord - reach).toInt()
        val maxZ = Math.floor(eye.zCoord + reach).toInt()

        for (x in minX..maxX) {
            for (y in minY..maxY) {
                for (z in minZ..maxZ) {
                    val p = BlockPos(x, y, z)
                    if (isAir(p)) continue
                    val dx = (x + 0.5) - eye.xCoord
                    val dy = (y + 0.5) - eye.yCoord
                    val dz = (z + 0.5) - eye.zCoord
                    if (dx * dx + dy * dy + dz * dz > (reach + 1) * (reach + 1)) continue

                    val d2 = dist2PointAABB(eye, x, y, z)
                    if (d2 > reachSq) continue

                    val mid = Vec3(x + 0.5, y + 0.5, z + 0.5)
                    val mop = mc.theWorld.rayTraceBlocks(eye, mid, false, false, false)
                    if (mop == null || mop.blockPos != p) continue

                    supports.add(BlockData(p, d2))
                }
            }
        }

        if (supports.isEmpty()) {
            sidesAim(eye, reach, feetPos)
            return
        }
        supports.sortBy { it.distance }
        for (bd in supports) {
            if (tryPlaceOnBlock(bd.pos, eye, reach, roofTarget)) return
        }

        // BFS 找路径
        val queue: ArrayDeque<BlockPos> = ArrayDeque()
        val parent: HashMap<BlockPos, BlockPos?> = HashMap()
        val visited: HashSet<BlockPos> = HashSet()
        for (bd in supports) {
            for (f in EnumFacing.values()) {
                val node = bd.pos.offset(f)
                if (!isAir(node) || visited.contains(node)) continue
                visited.add(node)
                parent[node] = null
                queue.add(node)
            }
        }
        var endNode: BlockPos? = null
        var nodesSeen = 0
        while (queue.isNotEmpty() && nodesSeen < 8964) {
            val cur = queue.removeFirst()
            nodesSeen++
            if (cur.distanceSq(roofTarget) <= 1.5) {
                endNode = cur
                break
            }
            for (f in EnumFacing.values()) {
                val nxt = cur.offset(f)
                if (visited.contains(nxt) || !isAir(nxt)) continue
                visited.add(nxt)
                parent[nxt] = cur
                queue.add(nxt)
            }
        }
        if (endNode == null) {
            sidesAim(eye, reach, feetPos)
            return
        }

        val path = mutableListOf<BlockPos>()
        var cur: BlockPos? = endNode
        while (cur != null) {
            path.add(cur)
            cur = parent[cur]
        }
        path.reverse()

        for (place in path) {
            if (isAir(place)) continue
            for (bd in supports) {
                if (isAdjacent(bd.pos, place) && tryPlaceOnBlock(bd.pos, eye, reach, place)) return
            }
            for (f in EnumFacing.values()) {
                val sup = place.offset(f)
                if (!isAir(sup) && tryPlaceOnBlock(sup, eye, reach, place)) return
            }
        }
        sidesAim(eye, reach, feetPos)
    }

    private fun isAdjacent(a: BlockPos, b: BlockPos): Boolean {
        val dx = abs(a.x - b.x)
        val dy = abs(a.y - b.y)
        val dz = abs(a.z - b.z)
        return (dx + dy + dz) == 1
    }

    private fun tryPlaceOnBlock(supportBlock: BlockPos, eye: Vec3, reach: Double, targetPos: BlockPos): Boolean {
        for (facing in EnumFacing.values()) {
            val placementPos = supportBlock.offset(facing)
            if (placementPos != targetPos) continue

            val n = Math.round(1.0 / STEP).toInt()
            for (r in 0..n) {
                var v = r * STEP + (Math.random() * JIT * 2 - JIT)
                if (v < 0) v = 0.0 else if (v > 1) v = 1.0
                for (c in 0..n) {
                    var u = c * STEP + (Math.random() * JIT * 2 - JIT)
                    if (u < 0) u = 0.0 else if (u > 1) u = 1.0

                    val hitPos = getHitPosOnFace(supportBlock, facing, u, v)
                    val rot = getRotationsWrapped(eye, hitPos.xCoord, hitPos.yCoord, hitPos.zCoord)
                    val mop = rayTraceBlock(rot[0], rot[1], reach)
                    if (mop != null
                        && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                        && mop.blockPos == supportBlock
                        && mop.sideHit == facing
                    ) {
                        targetBlock = supportBlock
                        targetFacing = facing
                        targetHitVec = mop.hitVec
                        aimYaw = rot[0]
                        aimPitch = rot[1]
                        return true
                    }
                }
            }
        }
        return false
    }

    private fun sidesAim(eye: Vec3, reach: Double, feetPos: BlockPos) {
        val goals = mutableListOf<BlockPos>()
        for (d in DIRS) {
            val headPos = feetPos.add(d[0], 1, d[2])
            if (isAir(headPos)) goals.add(headPos)
        }
        for (d in DIRS) {
            val feetGoal = feetPos.add(d[0], 0, d[2])
            if (isAir(feetGoal)) goals.add(feetGoal)
        }
        findBestForGoals(goals, eye, reach)
    }

    private fun findBestForGoals(goals: List<BlockPos>, eye: Vec3, reach: Double) {
        for (goal in goals) {
            for (facing in EnumFacing.values()) {
                val support = goal.offset(facing)
                if (isAir(support)) continue
                val center = Vec3(support.x + 0.5, support.y + 0.5, support.z + 0.5)
                if (eye.distanceTo(center) > reach) continue

                val n = Math.round(1.0 / STEP).toInt()
                for (r in 0..n) {
                    var v = r * STEP + (Math.random() * JIT * 2 - JIT)
                    if (v < 0) v = 0.0 else if (v > 1) v = 1.0
                    for (c in 0..n) {
                        var u = c * STEP + (Math.random() * JIT * 2 - JIT)
                        if (u < 0) u = 0.0 else if (u > 1) u = 1.0
                        val hitPos = getHitPosOnFace(support, facing.opposite, u, v)
                        val rot = getRotationsWrapped(eye, hitPos.xCoord, hitPos.yCoord, hitPos.zCoord)
                        val mop = rayTraceBlock(rot[0], rot[1], reach)
                        if (mop != null
                            && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK
                            && mop.blockPos == support
                            && mop.sideHit == facing.opposite
                        ) {
                            targetBlock = support
                            targetFacing = facing.opposite
                            targetHitVec = mop.hitVec
                            aimYaw = rot[0]
                            aimPitch = rot[1]
                            return
                        }
                    }
                }
            }
        }
    }

    private fun getHitPosOnFace(block: BlockPos, face: EnumFacing, u: Double, v: Double): Vec3 {
        var x = block.x + 0.5
        var y = block.y + 0.5
        var z = block.z + 0.5
        when (face) {
            EnumFacing.DOWN -> {
                y = block.y + INSET
                x = block.x + u
                z = block.z + v
            }
            EnumFacing.UP -> {
                y = block.y + 1.0 - INSET
                x = block.x + u
                z = block.z + v
            }
            EnumFacing.NORTH -> {
                z = block.z + INSET
                x = block.x + u
                y = block.y + v
            }
            EnumFacing.SOUTH -> {
                z = block.z + 1.0 - INSET
                x = block.x + u
                y = block.y + v
            }
            EnumFacing.WEST -> {
                x = block.x + INSET
                z = block.z + u
                y = block.y + v
            }
            EnumFacing.EAST -> {
                x = block.x + 1.0 - INSET
                z = block.z + u
                y = block.y + v
            }
        }
        return Vec3(x, y, z)
    }

    private fun isAir(pos: BlockPos): Boolean {
        val block: Block? = pos.block
        return block == Blocks.air
            || block == Blocks.water
            || block == Blocks.flowing_water
            || block == Blocks.lava
            || block == Blocks.flowing_lava
            || block == Blocks.fire
    }

    private fun rayTraceBlock(yaw: Float, pitch: Float, range: Double): MovingObjectPosition? {
        val yawRad = Math.toRadians(yaw.toDouble())
        val pitchRad = Math.toRadians(pitch.toDouble())
        val x = -Math.sin(yawRad) * Math.cos(pitchRad)
        val y = -Math.sin(pitchRad)
        val z = Math.cos(yawRad) * Math.cos(pitchRad)
        val start = mc.thePlayer.eyes
        val end = start.addVector(x * range, y * range, z * range)
        return mc.theWorld.rayTraceBlocks(start, end)
    }

    private fun withinRotationTolerance(targetYaw: Float, targetPitch: Float): Boolean {
        val dy = abs(MathHelper.wrapAngleTo180_float(targetYaw - serverYaw))
        val dp = abs(MathHelper.wrapAngleTo180_float(targetPitch - serverPitch))
        return dy <= rotationTolerance && dp <= rotationTolerance
    }

    private fun dist2PointAABB(p: Vec3, x: Int, y: Int, z: Int): Double {
        val minX = x.toDouble(); val maxX = x + 1.0
        val minY = y.toDouble(); val maxY = y + 1.0
        val minZ = z.toDouble(); val maxZ = z + 1.0
        val cx = clamp(p.xCoord, minX, maxX)
        val cy = clamp(p.yCoord, minY, maxY)
        val cz = clamp(p.zCoord, minZ, maxZ)
        val dx = p.xCoord - cx
        val dy = p.yCoord - cy
        val dz = p.zCoord - cz
        return dx * dx + dy * dy + dz * dz
    }

    private fun clamp(v: Double, lo: Double, hi: Double) = if (v < lo) lo else if (v > hi) hi else v

    private fun getRotationsWrapped(eye: Vec3, tx: Double, ty: Double, tz: Double): FloatArray {
        val dx = tx - eye.xCoord
        val dy = ty - eye.yCoord
        val dz = tz - eye.zCoord
        val hd = sqrt(dx * dx + dz * dz)
        var yaw = Math.toDegrees(atan2(dz, dx)).toFloat() - 90f
        yaw = normYaw(yaw)
        val pitch = Math.toDegrees(-atan2(dy, hd)).toFloat()
        return floatArrayOf(yaw, pitch)
    }

    private fun normYaw(yaw: Float): Float {
        var y = (yaw % 360f + 360f) % 360f
        return if (y > 180f) y - 360f else y
    }

    fun getSlot(): Int = lastSlot

    private data class BlockData(val pos: BlockPos, val distance: Double)
}
