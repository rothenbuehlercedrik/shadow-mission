package com.shadowmission.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType
import com.badlogic.gdx.math.Matrix4
import com.shadowmission.level.Floor
import com.shadowmission.level.GuardKind
import com.shadowmission.level.GuardSpawn
import com.shadowmission.level.Level
import com.shadowmission.level.Room
import com.shadowmission.level.Stairs

/** Draws a level with flat placeholder shapes until real sprites arrive. */
class LevelRenderer(private val level: Level) {

    private val shapes = ShapeRenderer()

    fun render(projection: Matrix4) {
        shapes.projectionMatrix = projection
        Gdx.gl.glEnable(GL20.GL_BLEND)
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA)

        shapes.begin(ShapeType.Filled)
        level.floors.forEach { floor -> floor.rooms.forEach { drawRoom(floor.index, it) } }
        level.stairs.forEach(::drawStairs)
        level.floors.forEach(::drawWallsAndDoors)
        drawSlabs()
        drawExit()
        level.guards.forEach(::drawVisionCone)
        level.guards.forEach(::drawGuard)
        drawTarget()
        drawAssassin()
        shapes.end()

        Gdx.gl.glDisable(GL20.GL_BLEND)
    }

    private fun drawRoom(floor: Int, room: Room) {
        val y = level.floorY(floor)
        shapes.color = if (room.dark) Palette.roomDark else Palette.roomLit
        shapes.rect(room.x, y, room.width, level.floorHeight)
        if (!room.dark) {
            // A ceiling lamp throwing a soft cone of light onto the floor.
            val cx = room.x + room.width / 2f
            val top = y + level.floorHeight - SLAB
            val spread = minOf(room.width * 0.4f, 3.2f)
            shapes.color = Palette.lamp
            shapes.triangle(cx, top, cx - spread, y, cx + spread, y)
            shapes.color = Palette.lampBulb
            shapes.rect(cx - 0.25f, top - 0.12f, 0.5f, 0.12f)
        }
    }

    private fun drawStairs(stairs: Stairs) {
        val y = level.floorY(stairs.bottomFloor)
        val steps = 10
        val stepW = stairs.width / steps
        val stepH = level.floorHeight / steps
        shapes.color = Palette.stairs
        for (i in 0 until steps) {
            shapes.rect(stairs.x + i * stepW, y + i * stepH, stepW, stepH)
        }
    }

    private fun drawWallsAndDoors(floor: Floor) {
        val y = level.floorY(floor.index)
        val walls = floor.rooms.map { it.x } + floor.right
        walls.forEach { x ->
            val door = floor.doorAt(x)
            shapes.color = Palette.wall
            if (door == null) {
                shapes.rect(x - WALL / 2f, y, WALL, level.floorHeight)
            } else {
                shapes.rect(x - WALL / 2f, y + DOOR_HEIGHT, WALL, level.floorHeight - DOOR_HEIGHT)
                shapes.color = if (door.locked) Palette.doorLocked else Palette.door
                shapes.rect(x - DOOR_THICKNESS / 2f, y, DOOR_THICKNESS, DOOR_HEIGHT)
            }
        }
    }

    private fun drawSlabs() {
        shapes.color = Palette.slab
        for (i in 0..level.floors.size) {
            val y = level.floorY(i)
            val above = level.floors.getOrNull(i)
            val below = level.floors.getOrNull(i - 1)
            val left = minOf(above?.left ?: Float.MAX_VALUE, below?.left ?: Float.MAX_VALUE)
            val right = maxOf(above?.right ?: -Float.MAX_VALUE, below?.right ?: -Float.MAX_VALUE)
            // Leave a stairwell opening where stairs arrive from below.
            val openings = level.stairs.filter { it.topFloor == i }.map { it.x to it.right }.sortedBy { it.first }
            var cursor = left - WALL / 2f
            openings.forEach { (start, end) ->
                shapes.rect(cursor, y - SLAB, start - cursor, SLAB)
                cursor = end
            }
            shapes.rect(cursor, y - SLAB, right + WALL / 2f - cursor, SLAB)
        }
    }

    private fun drawExit() {
        val x = level.exit.x
        val y = level.floorY(level.exit.floor)
        shapes.color = Palette.exit
        shapes.rect(x - 0.45f, y, 0.9f, 0.08f)
        shapes.triangle(x - 0.3f, y + 2.3f, x + 0.3f, y + 2.3f, x, y + 1.95f)
    }

    private fun drawVisionCone(guard: GuardSpawn) {
        val y = level.floorY(guard.floor) + EYE_HEIGHT
        val room = level.roomAt(guard.floor, guard.x)
        val range = if (room?.dark == true) VISION_RANGE_DARK else VISION_RANGE
        // Walls and closed doors block sight, so the cone stops at the edge of the guard's room.
        val wallX = if (guard.facing > 0) room?.right else room?.x
        val endX = if (wallX == null) guard.x + guard.facing * range
        else guard.x + guard.facing * minOf(range, kotlin.math.abs(wallX - guard.x) - WALL / 2f)
        shapes.color = Palette.visionCalm
        shapes.triangle(guard.x, y, endX, y + 0.9f, endX, y - 1.3f)
    }

    private fun drawGuard(guard: GuardSpawn) {
        val y = level.floorY(guard.floor)
        drawPerson(guard.x, y, Palette.guard)
        shapes.color = Palette.guardCap
        shapes.rect(guard.x - 0.22f, y + BODY_HEIGHT - 0.12f, 0.44f, 0.14f)
        shapes.rect(guard.x + guard.facing * 0.18f - 0.1f, y + BODY_HEIGHT - 0.12f, 0.2f, 0.06f)
        if (guard.kind == GuardKind.SENTRY) {
            shapes.color = Palette.hudText
            shapes.rect(guard.x - 0.04f, y + BODY_HEIGHT + 0.15f, 0.08f, 0.08f)
        }
    }

    private fun drawTarget() {
        val x = level.target.x
        val y = level.floorY(level.target.floor)
        drawPerson(x, y, Palette.target)
        shapes.color = Palette.targetMarker
        shapes.triangle(x - 0.25f, y + BODY_HEIGHT + 0.55f, x + 0.25f, y + BODY_HEIGHT + 0.55f, x, y + BODY_HEIGHT + 0.2f)
    }

    private fun drawAssassin() {
        val x = level.player.x
        val y = level.floorY(level.player.floor)
        shapes.color = Palette.assassinOutline
        shapes.rect(x - 0.27f, y, 0.54f, BODY_HEIGHT + 0.04f)
        drawPerson(x, y, Palette.assassin)
        shapes.color = Palette.scarf
        shapes.rect(x - 0.25f, y + BODY_HEIGHT - 0.55f, 0.5f, 0.14f)
        shapes.rect(x - 0.45f, y + BODY_HEIGHT - 0.75f, 0.22f, 0.28f)
    }

    private fun drawPerson(x: Float, y: Float, color: Color) {
        shapes.color = color
        shapes.rect(x - 0.25f, y, 0.5f, BODY_HEIGHT - 0.4f)
        shapes.circle(x, y + BODY_HEIGHT - 0.2f, 0.2f, 16)
    }

    fun dispose() = shapes.dispose()

    companion object {
        const val WALL = 0.2f
        const val SLAB = 0.25f
        const val DOOR_HEIGHT = 2.2f
        const val DOOR_THICKNESS = 0.12f
        const val BODY_HEIGHT = 1.8f
        const val EYE_HEIGHT = 1.6f
        const val VISION_RANGE = 7f
        const val VISION_RANGE_DARK = 3.5f
    }
}
