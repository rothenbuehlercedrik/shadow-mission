package com.shadowmission.game

import com.shadowmission.level.Level

enum class Gait(val speed: Float) {
    CROUCH(1.3f),
    WALK(2.4f),
    RUN(4.8f),
}

/** The player's character: follows a planned path, one step at a time. */
class Assassin(private val level: Level, start: Position) {

    var floor: Int = start.floor
        private set
    var x: Float = start.x
        private set

    /** Feet height in world units; between floors while on stairs. */
    var y: Float = level.floorY(start.floor)
        private set
    var facing: Int = 1
        private set
    var crouching: Boolean = false
    var running: Boolean = false
        private set

    private val steps = ArrayDeque<PathStep>()
    private var climbProgress = 0f

    val isMoving: Boolean get() = steps.isNotEmpty()
    val plannedSteps: List<PathStep> get() = steps.toList()
    val gait: Gait get() = when {
        running -> Gait.RUN
        crouching -> Gait.CROUCH
        else -> Gait.WALK
    }

    fun follow(path: List<PathStep>, run: Boolean) {
        // Finish a staircase before turning around, so the character never floats mid-air.
        val current = steps.firstOrNull() as? PathStep.Climb
        steps.clear()
        if (current != null) steps += current
        steps += path
        running = run
        if (run) crouching = false
    }

    fun stop() {
        val current = steps.firstOrNull() as? PathStep.Climb
        steps.clear()
        if (current != null) steps += current
        running = false
    }

    /** Advances along the path. [onDoor] is called for every door the assassin walks through. */
    fun update(delta: Float, onDoor: (floor: Int, fromX: Float, toX: Float) -> Unit) {
        var budget = gait.speed * delta
        while (budget > 0f && steps.isNotEmpty()) {
            when (val step = steps.first()) {
                is PathStep.Walk -> {
                    val dx = step.toX - x
                    val move = minOf(budget, kotlin.math.abs(dx))
                    if (dx != 0f) facing = if (dx > 0) 1 else -1
                    val oldX = x
                    x += facing * move
                    budget -= move
                    onDoor(floor, oldX, x)
                    if (x == step.toX) steps.removeFirst()
                }
                is PathStep.Climb -> {
                    val s = step.stairs
                    val length = kotlin.math.hypot(s.width, level.floorHeight)
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
        if (steps.isEmpty()) running = false
    }

    companion object {
        /** Stairs are slower than flat floor. */
        const val STAIRS_SPEED = 0.7f
    }
}
