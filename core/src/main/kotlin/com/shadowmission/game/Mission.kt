package com.shadowmission.game

import com.shadowmission.level.Level
import kotlin.math.abs

enum class MissionStatus { PLAYING, CAUGHT }

/** Everything that changes while a mission is played. */
class Mission(val level: Level) {
    val building = Building(level)
    val nav = NavGraph(building)
    val assassin = Assassin(level, Position(level.player.floor, level.player.x))
    val guards: List<Guard> = level.guards.map { Guard(it, this) }

    var status = MissionStatus.PLAYING
        private set

    /** Seconds of play so far. */
    var time = 0f
        private set

    private var noiseTimer = 0f

    /** Plans a route to [target]; returns false when there is no way there. */
    fun moveAssassin(target: Position, run: Boolean): Boolean {
        if (status != MissionStatus.PLAYING) return false
        val path = nav.findPath(assassin.mover.planningStart, target) ?: return false
        assassin.follow(path, run)
        return true
    }

    fun update(delta: Float) {
        if (status != MissionStatus.PLAYING) return
        time += delta
        assassin.update(delta, ::walkedThrough)
        emitNoise(delta)
        guards.forEach { it.update(delta) }
    }

    /** Opens any door a character walked through. */
    fun walkedThrough(floor: Int, fromX: Float, toX: Float) {
        building.doorsBetween(floor, fromX, toX).forEach(building::open)
    }

    /** True when [guard] has the assassin in sight. */
    fun canSee(guard: Guard): Boolean {
        val a = assassin
        // Only characters on the same floor can see each other; stairs count as the floor they are nearest.
        if (abs(a.y - level.floorY(guard.floor)) > level.floorHeight / 2f) return false
        val dx = a.x - guard.x
        if (abs(dx) < BUMP_DISTANCE) return true
        if ((dx > 0) != (guard.facing > 0)) return false
        val dark = level.roomAt(guard.floor, a.x)?.dark == true
        var range = if (dark) VISION_RANGE_DARK else VISION_RANGE
        if (a.gait == Gait.CROUCH) range *= CROUCH_VISION_FACTOR
        val end = building.sightEnd(guard.floor, guard.x, guard.facing, range)
        return abs(dx) <= abs(end - guard.x)
    }

    /** A guard is certain: nearby guards come running. */
    fun raiseAlarm(source: Guard) {
        val at = Position(assassin.floor, assassin.x)
        guards.filter { it !== source }
            .filter { abs(it.floor - source.floor) <= 1 && abs(it.x - source.x) <= SHOUT_RADIUS }
            .forEach { it.alert(at) }
    }

    fun caught() {
        status = MissionStatus.CAUGHT
        assassin.stop()
    }

    private fun emitNoise(delta: Float) {
        noiseTimer -= delta
        val radius = assassin.noiseRadius
        if (radius <= 0f || noiseTimer > 0f) return
        noiseTimer = NOISE_INTERVAL
        val at = Position(assassin.floor, assassin.x)
        guards.filter { it.floor == at.floor && abs(it.x - at.x) <= radius }.forEach { it.hear(at) }
    }

    companion object {
        const val VISION_RANGE = 7f
        const val VISION_RANGE_DARK = 3.5f
        const val CROUCH_VISION_FACTOR = 0.7f
        const val BUMP_DISTANCE = 0.8f
        const val SHOUT_RADIUS = 12f
        const val NOISE_INTERVAL = 0.3f
    }
}
