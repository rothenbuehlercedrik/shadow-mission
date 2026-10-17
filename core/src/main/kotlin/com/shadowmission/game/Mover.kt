package com.shadowmission.game

import com.shadowmission.level.Level
import kotlin.math.abs
import kotlin.math.hypot

/** Moves a character along a planned path: walking along floors and climbing stairs. */
class Mover(private val level: Level, start: Position) {

    var floor: Int = start.floor
        private set
    var x: Float = start.x
        private set

    /** Feet height in world units; between floors while on stairs. */
    var y: Float = level.floorY(start.floor)
        private set

    /** +1 faces right, -1 faces left. */
    var facing: Int = 1

    private val steps = ArrayDeque<PathStep>()
    private var climbProgress = 0f

    val isMoving: Boolean get() = steps.isNotEmpty()
    val plannedSteps: List<PathStep> get() = steps.toList()
    val isOnStairs: Boolean get() = climbProgress > 0f

    /** Where a new route should start: the end of the staircase being climbed, or right here. */
    val planningStart: Position
        get() = (steps.firstOrNull() as? PathStep.Climb)?.takeIf { isOnStairs }?.let {
            if (it.up) Position(it.stairs.topFloor, it.stairs.right) else Position(it.stairs.bottomFloor, it.stairs.x)
        } ?: Position(floor, x)

    fun follow(path: List<PathStep>) {
        // Finish a staircase before turning around, so the character never floats mid-air.
        val current = (steps.firstOrNull() as? PathStep.Climb)?.takeIf { isOnStairs }
        steps.clear()
        if (current != null) steps += current
        steps += path
    }

    fun stop() = follow(emptyList())

    /** Puts the character somewhere directly, dropping any plan. Used for dragged bodies. */
    fun place(floor: Int, x: Float, y: Float) {
        steps.clear()
        climbProgress = 0f
        this.floor = floor
        this.x = x
        this.y = y
    }

    /** Advances along the path at [speed]. [onMove] reports each stretch walked along a floor. */
    fun update(delta: Float, speed: Float, onMove: (floor: Int, fromX: Float, toX: Float) -> Unit) {
        var budget = speed * delta
        while (budget > 0f && steps.isNotEmpty()) {
            when (val step = steps.first()) {
                is PathStep.Walk -> {
                    val dx = step.toX - x
                    val move = minOf(budget, abs(dx))
                    if (dx != 0f) facing = if (dx > 0) 1 else -1
                    val oldX = x
                    x += facing * move
                    budget -= move
                    onMove(floor, oldX, x)
                    if (x == step.toX) steps.removeFirst()
                }
                is PathStep.Climb -> {
                    val s = step.stairs
                    val length = hypot(s.width, level.floorHeight)
                    val move = minOf(budget * STAIRS_SPEED, (1f - climbProgress) * length)
                    climbProgress += move / length
                    budget -= move / STAIRS_SPEED
                    val t = if (step.up) climbProgress else 1f - climbProgress
                    x = s.x + s.width * t
                    y = level.floorY(s.bottomFloor) + level.floorHeight * t
                    facing = if (step.up) 1 else -1
                    if (climbProgress >= 1f - 1e-4f) {
                        climbProgress = 0f
                        floor = if (step.up) s.topFloor else s.bottomFloor
                        x = if (step.up) s.right else s.x
                        y = level.floorY(floor)
                        steps.removeFirst()
                    }
                }
            }
        }
    }

    companion object {
        /** Stairs are slower than flat floor. */
        const val STAIRS_SPEED = 0.7f
    }
}
