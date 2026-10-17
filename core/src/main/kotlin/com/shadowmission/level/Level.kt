package com.shadowmission.level

/**
 * A mission building seen in cross-section. Units are metres: x runs left to right,
 * floors stack upwards and each floor is [floorHeight] tall.
 */
data class Level(
    val name: String,
    val floorHeight: Float,
    val floors: List<Floor>,
    val stairs: List<Stairs>,
    val player: Spawn,
    val guards: List<GuardSpawn>,
    val target: Spawn,
    val exit: Spawn,
    val hidingSpots: List<HidingSpot> = emptyList(),
    /** Coins the assassin starts with, for distractions. */
    val coins: Int = 3,
) {
    val left: Float get() = floors.minOf { it.left }
    val right: Float get() = floors.maxOf { it.right }
    val height: Float get() = floors.size * floorHeight

    fun floorY(floor: Int): Float = floor * floorHeight

    fun roomAt(floor: Int, x: Float): Room? = floors.getOrNull(floor)?.roomAt(x)
}

data class Floor(
    val index: Int,
    val rooms: List<Room>,
    val doors: List<Door>,
) {
    val left: Float get() = rooms.minOf { it.x }
    val right: Float get() = rooms.maxOf { it.right }

    fun roomAt(x: Float): Room? = rooms.firstOrNull { x >= it.x && x < it.right }

    fun doorAt(x: Float, tolerance: Float = 0.01f): Door? =
        doors.firstOrNull { kotlin.math.abs(it.x - x) <= tolerance }
}

data class Room(
    val id: String,
    val x: Float,
    val width: Float,
    /** Dark rooms have no lamp; guards see less far inside them. */
    val dark: Boolean = false,
) {
    val right: Float get() = x + width
}

/** A door sits in the wall between two neighbouring rooms, at the wall's x position. */
data class Door(
    val x: Float,
    val locked: Boolean = false,
)

/** Stairs climb from [bottomFloor] at [x] up to the next floor at [x] + [width]. */
data class Stairs(
    val x: Float,
    val width: Float,
    val bottomFloor: Int,
) {
    val topFloor: Int get() = bottomFloor + 1
    val right: Float get() = x + width
}

data class Spawn(
    val floor: Int,
    val x: Float,
)

enum class GuardKind { PATROL, SENTRY }

data class GuardSpawn(
    val floor: Int,
    val x: Float,
    val kind: GuardKind = GuardKind.PATROL,
    /** Patrol turning points on the guard's floor; empty for sentries. */
    val route: List<Float> = emptyList(),
    /** +1 faces right, -1 faces left. */
    val facing: Int = 1,
)

enum class HidingKind { CLOSET, CURTAIN, DESK }

/** A place to hide in, or to stash a body in. */
data class HidingSpot(
    val floor: Int,
    val x: Float,
    val kind: HidingKind = HidingKind.CLOSET,
)
