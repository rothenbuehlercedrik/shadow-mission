package com.shadowmission.level

import kotlin.math.abs

/** Catches level-file mistakes early, so a broken mission fails in tests instead of on the phone. */
object LevelValidator {

    private const val EPSILON = 0.001f

    fun validate(level: Level): List<String> {
        val errors = mutableListOf<String>()
        if (level.floors.isEmpty()) return listOf("level has no floors")

        level.floors.forEach { floor -> errors += validateFloor(floor) }

        level.stairs.forEachIndexed { i, stairs ->
            if (stairs.topFloor >= level.floors.size) {
                errors += "stairs $i lead above the top floor"
            } else {
                listOf(stairs.bottomFloor, stairs.topFloor).forEach { f ->
                    if (level.roomAt(f, stairs.x) == null || level.roomAt(f, stairs.right - EPSILON) == null) {
                        errors += "stairs $i are not inside rooms on floor $f"
                    }
                }
            }
        }

        fun checkSpawn(label: String, spawn: Spawn) {
            if (level.roomAt(spawn.floor, spawn.x) == null) errors += "$label is outside every room"
        }
        checkSpawn("player", level.player)
        checkSpawn("target", Spawn(level.target.floor, level.target.x))
        level.target.route.forEach { x ->
            if (level.roomAt(level.target.floor, x) == null) errors += "target route point $x is outside every room"
        }
        checkSpawn("exit", level.exit)
        level.hidingSpots.forEachIndexed { i, spot -> checkSpawn("hiding spot $i", Spawn(spot.floor, spot.x)) }
        level.guards.forEachIndexed { i, guard ->
            checkSpawn("guard $i", Spawn(guard.floor, guard.x))
            guard.route.forEach { x ->
                if (level.roomAt(guard.floor, x) == null) errors += "guard $i route point $x is outside every room"
            }
            if (guard.kind == GuardKind.PATROL && guard.route.size < 2) errors += "patrol guard $i needs at least two route points"
        }
        return errors
    }

    private fun validateFloor(floor: Floor): List<String> {
        val errors = mutableListOf<String>()
        val rooms = floor.rooms.sortedBy { it.x }
        if (rooms.isEmpty()) return listOf("floor ${floor.index} has no rooms")

        rooms.forEach { if (it.width <= 0f) errors += "room ${it.id} has no width" }
        rooms.zipWithNext().forEach { (a, b) ->
            when {
                b.x < a.right - EPSILON -> errors += "rooms ${a.id} and ${b.id} overlap"
                b.x > a.right + EPSILON -> errors += "gap between rooms ${a.id} and ${b.id}"
            }
        }
        val innerWalls = rooms.zipWithNext().map { (a, _) -> a.right }
        floor.doors.forEach { door ->
            if (innerWalls.none { abs(it - door.x) < EPSILON }) {
                errors += "door at ${door.x} on floor ${floor.index} is not on a wall between rooms"
            }
        }
        return errors
    }
}
