package com.shadowmission.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType
import com.badlogic.gdx.math.Matrix4
import com.shadowmission.game.Gait
import com.shadowmission.game.Guard
import com.shadowmission.game.GuardState
import com.shadowmission.game.Mission
import com.shadowmission.game.PathStep
import com.shadowmission.level.Floor
import com.shadowmission.level.Level
import com.shadowmission.level.Room
import com.shadowmission.level.Stairs

/** Draws a mission with flat placeholder shapes until real sprites arrive. */
class LevelRenderer(private val mission: Mission) {

    private val level: Level = mission.level

    private val shapes = ShapeRenderer()

    /** Where the player last tapped, and whether that spot was reachable; fades after a moment. */
    var tapMarker: TapMarker? = null

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
        mission.guards.forEach(::drawVisionCone)
        mission.guards.forEach(::drawGuard)
        drawTarget()
        drawPathPreview()
        drawNoise()
        drawAssassin()
        drawTapMarker(tapMarker)
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
                if (mission.building.isOpen(door)) {
                    // Swung open: the panel is seen edge-on beside the empty frame.
                    shapes.rect(x + WALL / 2f, y, DOOR_OPEN_WIDTH, DOOR_HEIGHT)
                    shapes.color = Palette.doorFrame
                    shapes.rect(x - WALL / 2f, y + DOOR_HEIGHT - 0.06f, WALL, 0.06f)
                } else {
                    shapes.rect(x - DOOR_THICKNESS / 2f, y, DOOR_THICKNESS, DOOR_HEIGHT)
                }
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

    private fun drawVisionCone(guard: Guard) {
        val y = guard.y + EYE_HEIGHT
        val room = level.roomAt(guard.floor, guard.x)
        val range = if (room?.dark == true) VISION_RANGE_DARK else VISION_RANGE
        // Walls and closed doors block sight; open doorways let it through.
        val endX = mission.building.sightEnd(guard.floor, guard.x, guard.facing, range)
        shapes.color = when (guard.state) {
            GuardState.PATROL -> Palette.visionCalm
            GuardState.SUSPICIOUS -> Palette.visionSuspicious
            GuardState.SEARCHING -> Palette.visionSearching
            GuardState.ALARMED -> Palette.visionAlarmed
        }
        shapes.triangle(guard.x, y, endX, y + 0.9f, endX, y - 1.3f)
    }

    private fun drawGuard(guard: Guard) {
        val x = guard.x
        val y = guard.y
        drawPerson(x, y, Palette.guard)
        shapes.color = Palette.guardCap
        shapes.rect(x - 0.22f, y + BODY_HEIGHT - 0.12f, 0.44f, 0.14f)
        shapes.rect(x + guard.facing * 0.18f - 0.1f, y + BODY_HEIGHT - 0.12f, 0.2f, 0.06f)
        drawAlertIcon(guard, x, y + BODY_HEIGHT + 0.25f)
    }

    /** An awareness meter that fills up, then a red "!" once the guard is certain. */
    private fun drawAlertIcon(guard: Guard, x: Float, y: Float) {
        if (guard.state == GuardState.ALARMED) {
            shapes.color = Palette.visionAlarmed.cpy().also { it.a = 1f }
            shapes.rect(x - 0.07f, y + 0.2f, 0.14f, 0.45f)
            shapes.rect(x - 0.07f, y, 0.14f, 0.13f)
            return
        }
        if (guard.awareness <= 0.01f) return
        shapes.color = Palette.meterBack
        shapes.rect(x - 0.35f, y, 0.7f, 0.14f)
        shapes.color = if (guard.state >= GuardState.SEARCHING) Palette.visionSearching.cpy().also { it.a = 1f }
        else Palette.visionSuspicious.cpy().also { it.a = 1f }
        shapes.rect(x - 0.35f, y, 0.7f * guard.awareness, 0.14f)
    }

    private fun drawTarget() {
        val x = level.target.x
        val y = level.floorY(level.target.floor)
        drawPerson(x, y, Palette.target)
        shapes.color = Palette.targetMarker
        shapes.triangle(x - 0.25f, y + BODY_HEIGHT + 0.55f, x + 0.25f, y + BODY_HEIGHT + 0.55f, x, y + BODY_HEIGHT + 0.2f)
    }

    private fun drawAssassin() {
        val a = mission.assassin
        val height = if (a.gait == Gait.CROUCH) CROUCH_HEIGHT else BODY_HEIGHT
        shapes.color = Palette.assassinOutline
        shapes.rect(a.x - 0.27f, a.y, 0.54f, height + 0.04f)
        drawPerson(a.x, a.y, Palette.assassin, height)
        shapes.color = Palette.scarf
        shapes.rect(a.x - 0.25f, a.y + height - 0.55f, 0.5f, 0.14f)
        // The scarf tail trails behind the direction of travel.
        shapes.rect(a.x - a.facing * 0.34f - 0.11f, a.y + height - 0.75f, 0.22f, 0.28f)
    }

    private fun drawNoise() {
        val a = mission.assassin
        val radius = a.noiseRadius
        if (radius <= 0f) return
        // Pulsing ring showing how far guards can hear the assassin.
        val pulse = (mission.time * 2f) % 1f
        shapes.set(ShapeType.Line)
        shapes.color = Palette.noise.cpy().also { it.a *= 1f - pulse }
        shapes.ellipse(a.x - radius * pulse, a.y - 0.2f, radius * 2f * pulse, 0.4f, 24)
        shapes.color = Palette.noise
        shapes.ellipse(a.x - radius, a.y - 0.2f, radius * 2f, 0.4f, 32)
        shapes.set(ShapeType.Filled)
    }

    private fun drawPathPreview() {
        val a = mission.assassin
        if (!a.isMoving) return
        shapes.color = if (a.running) Palette.pathRun else Palette.path
        var fx = a.x
        var fy = a.y
        a.plannedSteps.forEach { step ->
            val (tx, ty) = when (step) {
                is PathStep.Walk -> step.toX to level.floorY(step.floor)
                is PathStep.Climb -> if (step.up) step.stairs.right to level.floorY(step.stairs.topFloor)
                else step.stairs.x to level.floorY(step.stairs.bottomFloor)
            }
            dottedLine(fx, fy + PATH_LIFT, tx, ty + PATH_LIFT)
            fx = tx
            fy = ty
        }
        shapes.circle(fx, fy + PATH_LIFT, 0.16f, 12)
    }

    private fun dottedLine(x1: Float, y1: Float, x2: Float, y2: Float) {
        val length = kotlin.math.hypot(x2 - x1, y2 - y1)
        val dots = (length / 0.4f).toInt()
        for (i in 0..dots) {
            val t = if (dots == 0) 1f else i / dots.toFloat()
            shapes.circle(x1 + (x2 - x1) * t, y1 + (y2 - y1) * t, 0.06f, 6)
        }
    }

    private fun drawTapMarker(marker: TapMarker?) {
        if (marker == null || marker.age > TapMarker.LIFETIME) return
        val fade = 1f - marker.age / TapMarker.LIFETIME
        val y = level.floorY(marker.floor) + PATH_LIFT
        if (marker.reachable) {
            shapes.color = Palette.path.cpy().also { it.a *= fade }
            shapes.circle(marker.x, y, 0.3f * (1f + marker.age), 20)
        } else {
            shapes.color = Palette.blocked.cpy().also { it.a = fade }
            shapes.rectLine(marker.x - 0.3f, y - 0.3f, marker.x + 0.3f, y + 0.3f, 0.1f)
            shapes.rectLine(marker.x - 0.3f, y + 0.3f, marker.x + 0.3f, y - 0.3f, 0.1f)
        }
    }

    private fun drawPerson(x: Float, y: Float, color: Color, height: Float = BODY_HEIGHT) {
        shapes.color = color
        shapes.rect(x - 0.25f, y, 0.5f, height - 0.4f)
        shapes.circle(x, y + height - 0.2f, 0.2f, 16)
    }

    fun dispose() = shapes.dispose()

    companion object {
        const val WALL = 0.2f
        const val SLAB = 0.25f
        const val DOOR_HEIGHT = 2.2f
        const val DOOR_THICKNESS = 0.12f
        const val BODY_HEIGHT = 1.8f
        const val CROUCH_HEIGHT = 1.2f
        const val DOOR_OPEN_WIDTH = 0.18f
        const val PATH_LIFT = 0.15f
        const val EYE_HEIGHT = 1.6f
        const val VISION_RANGE = 7f
        const val VISION_RANGE_DARK = 3.5f
    }
}
