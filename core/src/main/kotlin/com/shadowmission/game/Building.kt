package com.shadowmission.game

import com.shadowmission.level.Door
import com.shadowmission.level.Level
import kotlin.math.abs

/** A spot in the building: which floor, and how far along it. */
data class Position(val floor: Int, val x: Float)

/**
 * The live state of a level's building: which doors are open, and what walls block.
 * Closed unlocked doors are walkable (the assassin opens them on the way); locked doors
 * and plain walls are not. Only open doors let sight through.
 */
class Building(val level: Level) {

    private val openDoors = mutableSetOf<Door>()

    fun isOpen(door: Door): Boolean = door in openDoors

    fun open(door: Door) {
        if (!door.locked) openDoors += door
    }

    /** Wall positions on [floor], including the outer walls, left to right. */
    fun walls(floor: Int): List<Float> {
        val f = level.floors[floor]
        return f.rooms.map { it.x }.sorted() + f.right
    }

    fun canWalkThrough(floor: Int, wallX: Float): Boolean {
        val door = level.floors[floor].doorAt(wallX) ?: return false
        return !door.locked
    }

    /** Doors on [floor] crossed when moving between two x positions. */
    fun doorsBetween(floor: Int, fromX: Float, toX: Float): List<Door> {
        val lo = minOf(fromX, toX)
        val hi = maxOf(fromX, toX)
        if (lo == hi) return emptyList()
        return level.floors[floor].doors.filter { it.x >= lo && it.x <= hi }
    }

    /**
     * How far sight reaches from [x] on [floor] looking in [facing] direction, before a wall or
     * closed door stops it. Returns the x where sight ends, at most [range] away.
     */
    fun sightEnd(floor: Int, x: Float, facing: Int, range: Float): Float {
        val limit = x + facing * range
        val blocking = walls(floor)
            .filter { if (facing > 0) it > x else it < x }
            .sortedBy { abs(it - x) }
            .firstOrNull { wall -> level.floors[floor].doorAt(wall)?.let { !isOpen(it) } ?: true }
            ?: return limit
        val wallFace = blocking - facing * WALL_HALF
        return if (facing > 0) minOf(limit, wallFace) else maxOf(limit, wallFace)
    }

    companion object {
        const val WALL_HALF = 0.1f
    }
}
