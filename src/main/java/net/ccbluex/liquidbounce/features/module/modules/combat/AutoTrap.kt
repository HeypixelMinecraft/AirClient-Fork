/*
 * Air Client
 * A free open source mixin-based injection hacked client for Minecraft using Minecraft Forge.
 */
// skid some
package net.ccbluex.liquidbounce.features.module.modules.combat

import net.ccbluex.liquidbounce.event.Render3DEvent
import net.ccbluex.liquidbounce.event.RotationUpdateEvent
import net.ccbluex.liquidbounce.event.handler
import net.ccbluex.liquidbounce.features.module.Category
import net.ccbluex.liquidbounce.features.module.Module
import net.ccbluex.liquidbounce.utils.attack.EntityUtils
import net.ccbluex.liquidbounce.utils.block.BlockUtils.isBlockBBValid
import net.ccbluex.liquidbounce.utils.block.center
import net.ccbluex.liquidbounce.utils.client.PacketUtils.sendPacket
import net.ccbluex.liquidbounce.utils.extensions.*
import net.ccbluex.liquidbounce.utils.inventory.InventoryUtils
import net.ccbluex.liquidbounce.utils.inventory.SilentHotbar
import net.ccbluex.liquidbounce.utils.inventory.inventorySlot
import net.ccbluex.liquidbounce.utils.render.RenderUtils
import net.ccbluex.liquidbounce.utils.rotation.Rotation
import net.ccbluex.liquidbounce.utils.rotation.RotationSettings
import net.ccbluex.liquidbounce.utils.rotation.RotationUtils
import net.ccbluex.liquidbounce.utils.rotation.RotationUtils.getVectorForRotation
import net.ccbluex.liquidbounce.utils.rotation.RotationUtils.setTargetRotation
import net.ccbluex.liquidbounce.utils.timing.MSTimer
import net.minecraft.block.BlockBush
import net.minecraft.init.Blocks
import net.minecraft.item.ItemBlock
import net.minecraft.item.ItemStack
import net.minecraft.network.play.client.C0APacketAnimation
import net.minecraft.util.BlockPos
import net.minecraft.util.EnumFacing
import net.minecraft.util.MovingObjectPosition
import net.minecraft.util.Vec3
import net.minecraftforge.event.ForgeEventFactory
import java.awt.Color
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Automatically places blocks around the target to trap them.
 * Uses proper raytrace to ensure blocks are not placed through entities.
 */
object AutoTrap : Module("AutoTrap", Category.COMBAT) {

    private val range by float("Range", 5F, 1F..6F)
    private val placeDelay by int("PlaceDelay", 300, 0..1000)
    private val autoBlock by choices("AutoBlock", arrayOf("Off", "Pick", "Spoof", "Switch"), "Spoof")
    private val swing by boolean("Swing", true)
    private val mark by boolean("Mark", true)
    private val trapMode by choices("TrapMode", arrayOf("Full", "Top", "Feet"), "Full")

    private val options = RotationSettings(this)

    private val timer = MSTimer()
    private val targetPositions = mutableListOf<BlockPos>()
    private var currentTarget: BlockPos? = null

    override fun onDisable() {
        targetPositions.clear()
        currentTarget = null
    }

    val onRotationUpdate = handler<RotationUpdateEvent> {
        val player = mc.thePlayer ?: return@handler
        val world = mc.theWorld ?: return@handler

        // Find the closest valid target
        val target = world.loadedEntityList
            .filter { EntityUtils.isSelected(it, true) }
            .minByOrNull { player.getDistanceSqToEntity(it) } ?: return@handler

        if (player.getDistanceSqToEntity(target) > (range * range).toDouble())
            return@handler

        // Check if we have blocks in hotbar
        val blockSlot = InventoryUtils.findBlockInHotbar() ?: return@handler

        // Calculate trap positions around target
        targetPositions.clear()
        val targetPos = BlockPos(target)

        when (trapMode.lowercase()) {
            "full" -> {
                // Full trap: 4 sides + top
                for (side in arrayOf(EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.EAST, EnumFacing.WEST)) {
                    targetPositions.add(targetPos.offset(side))
                }
                targetPositions.add(targetPos.up())
            }
            "top" -> {
                // Top only: just place on top
                targetPositions.add(targetPos.up())
            }
            "feet" -> {
                // Feet only: 4 sides at feet level
                for (side in arrayOf(EnumFacing.NORTH, EnumFacing.SOUTH, EnumFacing.EAST, EnumFacing.WEST)) {
                    targetPositions.add(targetPos.offset(side))
                }
            }
        }

        // Filter positions: only air blocks that can be placed into
        val validPositions = targetPositions.filter { pos ->
            world.isAirBlock(pos) && canPlaceAt(pos)
        }

        if (validPositions.isEmpty()) return@handler

        // Sort by distance to player
        val sortedPositions = validPositions.sortedBy { player.getDistanceSq(it) }

        for (pos in sortedPositions) {
            // Find a neighbor block we can click on to place
            val placeData = findPlaceData(pos) ?: continue

            currentTarget = pos

            // Set rotation
            val rotation = RotationUtils.toRotation(placeData.clickPos.center, false, player)
            if (options.rotationsActive) {
                setTargetRotation(rotation, options, if (options.keepRotation) options.resetTicks else 1)
            }

            if (timer.hasTimePassed(placeDelay)) {
                // Verify raytrace - make sure we can actually see the neighbor block
                val raytrace = performBlockRaytrace(rotation, mc.playerController.blockReachDistance)
                if (raytrace == null || raytrace.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) {
                    continue
                }

                // Verify raytrace hits the correct neighbor block
                if (raytrace.blockPos != placeData.clickPos) {
                    continue
                }

                // Place the block
                placeBlock(pos, raytrace.sideHit, raytrace.hitVec)
                timer.reset()
            }
            break
        }
    }

    val onRender3D = handler<Render3DEvent> {
        if (mark && currentTarget != null) {
            RenderUtils.drawBlockBox(currentTarget!!, Color(255, 50, 50, 100), false)
        }
    }

    /**
     * Check if a position can have a block placed at it
     * (at least one neighbor must be a solid, clickable block)
     */
    private fun canPlaceAt(pos: BlockPos): Boolean {
        val world = mc.theWorld ?: return false
        for (side in EnumFacing.entries) {
            val neighbor = pos.offset(side)
            if (!world.isAirBlock(neighbor) && isBlockBBValid(neighbor)) {
                return true
            }
        }
        return false
    }

    /**
     * Find placement data: which neighbor block to click and which face
     */
    private fun findPlaceData(pos: BlockPos): PlaceData? {
        val player = mc.thePlayer ?: return null
        val world = mc.theWorld ?: return null

        var bestData: PlaceData? = null
        var bestDist = Double.MAX_VALUE

        for (side in EnumFacing.entries) {
            val neighbor = pos.offset(side)
            if (world.isAirBlock(neighbor) || !isBlockBBValid(neighbor)) continue

            // Check that the entity is not blocking this placement position
            // (i.e. no entity's bounding box intersects with the placement position)
            val blockBounds = world.getBlockState(pos).block.getCollisionBoundingBox(
                world, pos, world.getBlockState(pos)
            ) ?: continue

            val isBlockedByEntity = world.loadedEntityList.any { entity ->
                entity != player && entity.entityBoundingBox.intersectsWith(blockBounds)
            }
            if (isBlockedByEntity) continue

            val dist = player.getDistanceSq(neighbor)
            if (dist < bestDist) {
                bestDist = dist
                bestData = PlaceData(neighbor, side.opposite)
            }
        }
        return bestData
    }

    private fun placeBlock(blockPos: BlockPos, side: EnumFacing, hitVec: Vec3) {
        val player = mc.thePlayer ?: return

        var stack = player.inventorySlot(SilentHotbar.currentSlot + 36).stack ?: return

        if (stack.item !is ItemBlock || (stack.item as ItemBlock).block is BlockBush
            || InventoryUtils.BLOCK_BLACKLIST.contains((stack.item as ItemBlock).block) || stack.stackSize <= 0
        ) {
            val blockSlot = InventoryUtils.findBlockInHotbar() ?: return

            if (autoBlock != "Off") {
                SilentHotbar.selectSlotSilently(
                    this,
                    blockSlot,
                    immediate = true,
                    render = autoBlock == "Pick",
                    resetManually = true
                )
            }

            stack = player.inventorySlot(blockSlot).stack
        }

        tryToPlaceBlock(stack, blockPos, side, hitVec)

        if (autoBlock == "Switch")
            SilentHotbar.resetSlot(this, true)

        if (swing) player.swingItem() else sendPacket(C0APacketAnimation())
    }

    private fun tryToPlaceBlock(
        stack: ItemStack,
        clickPos: BlockPos,
        side: EnumFacing,
        hitVec: Vec3,
    ): Boolean {
        val player = mc.thePlayer ?: return false
        val prevSize = stack.stackSize

        val clickedSuccessfully = player.onPlayerRightClick(clickPos, side, hitVec, stack)

        if (clickedSuccessfully) {
            if (stack.stackSize <= 0) {
                player.inventory.mainInventory[SilentHotbar.currentSlot] = null
                ForgeEventFactory.onPlayerDestroyItem(player, stack)
            } else if (stack.stackSize != prevSize || mc.playerController.isInCreativeMode)
                mc.entityRenderer.itemRenderer.resetEquippedProgress()

            currentTarget = null
        }

        return clickedSuccessfully
    }

    private fun performBlockRaytrace(rotation: Rotation, maxReach: Float): MovingObjectPosition? {
        val player = mc.thePlayer ?: return null
        val world = mc.theWorld ?: return null
        val eyes = player.eyes
        val rotationVec = getVectorForRotation(rotation)
        val reach = eyes + (rotationVec * maxReach.toDouble())
        return world.rayTraceBlocks(eyes, reach, false, true, false)
    }

    override val tag
        get() = trapMode

    private data class PlaceData(val clickPos: BlockPos, val side: EnumFacing)
}
