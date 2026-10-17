package com.shadowmission.game

import com.shadowmission.level.GuardKind
import com.shadowmission.level.GuardSpawn
import com.shadowmission.level.HidingSpot
import kotlin.math.abs
import kotlin.math.sign

/** Alert states from the plan, calmest first. */
enum class GuardState { PATROL, SUSPICIOUS, SEARCHING, ALARMED }

/**
 * A guard's brain: a small state machine driven by what the guard sees and hears.
 * Awareness fills while the assassin is in sight; crossing thresholds raises the alert state,
 * and losing contact lets it fall back one step at a time.
 */
class Guard(
    val spawn: GuardSpawn,
    private val mission: Mission,
    /** The mission's target: behaves like a guard but flees instead of fighting. */
    val isTarget: Boolean = false,
) {

    val mover = Mover(mission.level, Position(spawn.floor, spawn.x)).also { it.facing = spawn.facing }

    val floor: Int get() = mover.floor
    val x: Float get() = mover.x
    val y: Float get() = mover.y
    val facing: Int get() = mover.facing

    var state = GuardState.PATROL
        private set

    /** 0 = unaware, 1 = certain the assassin is there. */
    var awareness = 0f
        private set

    /** Where the guard last saw or heard something. */
    var pointOfInterest: Position? = null
        private set

    var seesAssassin = false
        private set

    /** Knocked out by the assassin: a body that other guards may find. */
    var down = false
        private set

    /** Where this body has been stashed, if anywhere. */
    var hiddenIn: HidingSpot? = null

    /** Another guard already found this body; it raises no new alarm. */
    var discovered = false

    private var routeIndex = 0
    private var waitTimer = 0f
    private var stateTimer = 0f
    private var lookTimer = 0f
    private var replanTimer = 0f
    private var poiVisited = false

    /** A beat between hearing something and turning towards it. */
    private var reactTimer = 0f

    private val speed: Float
        get() = when (state) {
            GuardState.PATROL -> PATROL_SPEED
            GuardState.SUSPICIOUS -> PATROL_SPEED
            GuardState.SEARCHING -> SEARCH_SPEED
            GuardState.ALARMED -> CHASE_SPEED
        }

    fun knockOut() {
        down = true
        mover.stop()
        state = GuardState.PATROL
        awareness = 0f
        seesAssassin = false
    }

    /** Moves a dragged body along with the assassin. */
    fun dragTo(floor: Int, x: Float, y: Float) = mover.place(floor, x, y)

    fun update(delta: Float) {
        if (down) return
        perceive(delta)
        noticeBodies()
        if (reactTimer > 0f && state == GuardState.SUSPICIOUS) {
            reactTimer -= delta
            mover.update(delta, speed) { f, from, to -> mission.walkedThrough(f, from, to) }
            return
        }
        when (state) {
            GuardState.PATROL -> patrol(delta)
            GuardState.SUSPICIOUS -> investigate(delta, giveUpAfter = SUSPICIOUS_TIME, calmTo = GuardState.PATROL)
            GuardState.SEARCHING -> investigate(delta, giveUpAfter = SEARCH_TIME, calmTo = GuardState.PATROL)
            GuardState.ALARMED -> chase(delta)
        }
        mover.update(delta, speed) { f, from, to -> mission.walkedThrough(f, from, to) }
    }

    /** A noise at [at] reached this guard. */
    fun hear(at: Position) {
        if (state >= GuardState.SEARCHING) return
        if (state == GuardState.PATROL) {
            reactTimer = REACTION_TIME
            mover.stop()
        }
        enter(GuardState.SUSPICIOUS)
        setPointOfInterest(at)
        awareness = maxOf(awareness, SUSPICIOUS_LEVEL)
    }

    /** Another guard raised the alarm at [at]. */
    fun alert(at: Position) {
        if (state >= GuardState.SEARCHING) return
        enter(GuardState.SEARCHING)
        setPointOfInterest(at)
        awareness = maxOf(awareness, SEARCH_LEVEL)
    }

    private fun perceive(delta: Float) {
        seesAssassin = mission.canSee(this)
        if (seesAssassin) {
            val a = mission.assassin
            setPointOfInterest(Position(a.floor, a.x))
            val distance = abs(a.x - x)
            // Close and in plain view fills awareness fast; at the edge of sight it takes a while.
            val rate = when {
                distance < 2f -> 3f
                distance < 4f -> 1.4f
                else -> 0.8f
            }
            awareness = minOf(1f, awareness + rate * delta)
        } else if (state != GuardState.ALARMED) {
            awareness = maxOf(0f, awareness - DECAY * delta)
        }

        when {
            awareness >= 1f && state != GuardState.ALARMED -> {
                enter(GuardState.ALARMED)
                mission.spotted()
                mission.raiseAlarm(this, Position(mission.assassin.floor, mission.assassin.x))
            }
            seesAssassin && awareness >= SEARCH_LEVEL && state < GuardState.SEARCHING -> enter(GuardState.SEARCHING)
            seesAssassin && state == GuardState.PATROL -> enter(GuardState.SUSPICIOUS)
        }
    }

    private fun noticeBodies() {
        if (state == GuardState.ALARMED) return
        val body = mission.people.firstOrNull { it.down && !it.discovered && it.hiddenIn == null && mission.assassin.dragging !== it && mission.canSee(this, it.floor, it.x, it.y) }
            ?: return
        body.discovered = true
        mission.emit(GameEvent.BODY_FOUND)
        enter(GuardState.SEARCHING)
        awareness = maxOf(awareness, SEARCH_LEVEL)
        setPointOfInterest(Position(body.floor, body.x))
        mission.raiseAlarm(this, Position(body.floor, body.x))
    }

    private fun setPointOfInterest(at: Position) {
        pointOfInterest = at
        poiVisited = false
    }

    private fun enter(newState: GuardState) {
        if (newState > state) {
            mission.emit(if (newState == GuardState.ALARMED) GameEvent.GUARD_ALARMED else GameEvent.GUARD_SUSPICIOUS)
        }
        state = newState
        stateTimer = 0f
        lookTimer = 0f
        replanTimer = 0f
    }

    private fun patrol(delta: Float) {
        if (spawn.kind == GuardKind.SENTRY || spawn.route.size < 2) {
            if (mover.isMoving) return
            if (mover.floor != spawn.floor || abs(mover.x - spawn.x) > 0.01f) {
                goTo(Position(spawn.floor, spawn.x))
                return
            }
            // Sentries stand still and slowly look one way, then the other.
            if (spawn.turnEvery <= 0f) {
                mover.facing = spawn.facing
                return
            }
            lookTimer += delta
            if (lookTimer >= spawn.turnEvery) {
                lookTimer = 0f
                mover.facing = -mover.facing
            }
            return
        }
        if (mover.isMoving) return
        if (mover.floor == spawn.floor && abs(mover.x - spawn.route[routeIndex]) < 0.01f) {
            waitTimer += delta
            if (waitTimer < PATROL_PAUSE) return
            waitTimer = 0f
            routeIndex = (routeIndex + 1) % spawn.route.size
        }
        goTo(Position(spawn.floor, spawn.route[routeIndex]))
    }

    /** Walks to the point of interest, looks around, and eventually calms down. */
    private fun investigate(delta: Float, giveUpAfter: Float, calmTo: GuardState) {
        if (seesAssassin) {
            // Stop and stare while making up their mind.
            mover.stop()
            mover.facing = sign(mission.assassin.x - x).toInt().takeIf { it != 0 } ?: facing
            stateTimer = 0f
            return
        }
        if (mover.isMoving) return
        val poi = pointOfInterest
        if (poi != null && !poiVisited) {
            // Head to where the noise or the glimpse was before looking around.
            poiVisited = true
            if (poi.floor != floor || abs(poi.x - x) > 0.1f) {
                goTo(poi)
                return
            }
        }
        stateTimer += delta
        lookTimer += delta
        if (lookTimer >= LOOK_AROUND_TIME) {
            lookTimer = 0f
            mover.facing = -mover.facing
        }
        if (stateTimer >= giveUpAfter) {
            enter(calmTo)
            pointOfInterest = null
            awareness = minOf(awareness, SUSPICIOUS_LEVEL * 0.5f)
        }
    }

    private fun chase(delta: Float) {
        val a = mission.assassin
        if (isTarget) {
            flee(delta)
            return
        }
        if (seesAssassin) {
            stateTimer = 0f
            if (a.floor == floor && !a.mover.isOnStairs && abs(a.x - x) < CATCH_DISTANCE) {
                mission.caught()
                return
            }
            replanTimer -= delta
            if (replanTimer <= 0f) {
                replanTimer = REPLAN_TIME
                goTo(Position(a.floor, a.x))
            }
            return
        }
        stateTimer += delta
        if (!mover.isMoving) pointOfInterest?.let { if (abs(it.x - x) > 0.1f || it.floor != floor) goTo(it) }
        if (stateTimer >= LOSE_TRACK_TIME) {
            // Lost them: keep searching, but no longer certain.
            enter(GuardState.SEARCHING)
            awareness = SEARCH_LEVEL
        }
    }

    /** The target runs to the far end of the floor, away from the assassin, and stays there scared. */
    private fun flee(delta: Float) {
        if (seesAssassin) stateTimer = 0f else stateTimer += delta
        if (!mover.isMoving && seesAssassin) {
            val f = mission.level.floors[floor]
            val away = if (mission.assassin.x < x) f.right - 0.5f else f.left + 0.5f
            if (abs(away - x) > 0.5f) goTo(Position(floor, away))
        }
        if (stateTimer >= LOSE_TRACK_TIME) {
            enter(GuardState.SEARCHING)
            awareness = SEARCH_LEVEL
        }
    }

    private fun goTo(target: Position) {
        mission.nav.findPath(mover.planningStart, target)?.let(mover::follow)
    }

    companion object {
        const val REACTION_TIME = 0.6f
        const val PATROL_SPEED = 1.6f
        const val SEARCH_SPEED = 2.6f
        const val CHASE_SPEED = 4.2f
        const val PATROL_PAUSE = 1.5f
        const val LOOK_AROUND_TIME = 1.3f
        const val SUSPICIOUS_TIME = 4f
        const val SEARCH_TIME = 8f
        const val LOSE_TRACK_TIME = 6f
        const val REPLAN_TIME = 0.4f
        const val CATCH_DISTANCE = 0.8f
        const val SUSPICIOUS_LEVEL = 0.2f
        const val SEARCH_LEVEL = 0.5f
        const val DECAY = 0.25f
    }
}
