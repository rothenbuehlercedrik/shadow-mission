package com.shadowmission.game

import com.shadowmission.level.HidingSpot
import com.shadowmission.level.Level

enum class Gait(val speed: Float, val noiseRadius: Float) {
    CROUCH(1.3f, 0f),
    DRAG(1.1f, 1f),
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

    /** Tucked away in a hiding spot: guards cannot see the assassin. */
    var hiddenIn: HidingSpot? = null

    /** The knocked-out guard being dragged, if any. */
    var dragging: Guard? = null

    /** Seconds left of an action that roots the assassin in place, like a takedown. */
    var busy: Float = 0f

    val gait: Gait get() = when {
        dragging != null -> Gait.DRAG
        running -> Gait.RUN
        crouching -> Gait.CROUCH
        else -> Gait.WALK
    }

    /** How far away guards can hear the assassin right now. */
    val noiseRadius: Float get() = if (isMoving) gait.noiseRadius else 0f

    fun follow(path: List<PathStep>, run: Boolean) {
        mover.follow(path)
        running = run && dragging == null
        if (running) crouching = false
    }

    fun stop() {
        mover.stop()
        running = false
    }

    /** Advances along the path. [onMove] reports each stretch walked, so doors on the way can open. */
    fun update(delta: Float, onMove: (floor: Int, fromX: Float, toX: Float) -> Unit) {
        if (busy > 0f) {
            busy -= delta
            return
        }
        mover.update(delta, gait.speed, onMove)
        if (!mover.isMoving) running = false
    }
}
