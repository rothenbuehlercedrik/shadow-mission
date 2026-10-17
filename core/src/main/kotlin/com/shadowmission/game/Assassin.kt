package com.shadowmission.game

import com.shadowmission.level.Level

enum class Gait(val speed: Float, val noiseRadius: Float) {
    CROUCH(1.3f, 0f),
    WALK(2.4f, 1.8f),
    RUN(4.8f, 6f),
}

/** The player's character: follows a planned path, one step at a time. */
class Assassin(level: Level, start: Position) {

    val mover = Mover(level, start)

    val floor: Int get() = mover.floor
    val x: Float get() = mover.x
    val y: Float get() = mover.y
    val facing: Int get() = mover.facing
    val isMoving: Boolean get() = mover.isMoving
    val plannedSteps: List<PathStep> get() = mover.plannedSteps

    var crouching: Boolean = false
    var running: Boolean = false
        private set

    val gait: Gait get() = when {
        running -> Gait.RUN
        crouching -> Gait.CROUCH
        else -> Gait.WALK
    }

    /** How far away guards can hear the assassin right now. */
    val noiseRadius: Float get() = if (isMoving) gait.noiseRadius else 0f

    fun follow(path: List<PathStep>, run: Boolean) {
        mover.follow(path)
        running = run
        if (run) crouching = false
    }

    fun stop() {
        mover.stop()
        running = false
    }

    /** Advances along the path. [onMove] reports each stretch walked, so doors on the way can open. */
    fun update(delta: Float, onMove: (floor: Int, fromX: Float, toX: Float) -> Unit) {
        mover.update(delta, gait.speed, onMove)
        if (!mover.isMoving) running = false
    }
}
