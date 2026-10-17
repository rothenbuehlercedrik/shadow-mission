package com.shadowmission.game

import com.shadowmission.level.HidingSpot
import com.shadowmission.level.Level
import kotlin.math.abs

enum class MissionStatus { PLAYING, CAUGHT, COMPLETE }

/** What the player last asked the assassin to do. Movement orders finish when the assassin arrives. */
sealed interface Order {
    data class Takedown(val guard: Guard) : Order
    data class PickUp(val body: Guard) : Order
    data class Hide(val spot: HidingSpot) : Order
}

/** A thrown coin: flies for a moment, then lands with a noise that draws guards. */
class Coin(val from: Position, val fromY: Float, val to: Position) {
    var age = 0f
    val landed: Boolean get() = age >= FLIGHT_TIME

    companion object {
        const val FLIGHT_TIME = 0.5f
    }
}

/** Everything that changes while a mission is played. */
class Mission(val level: Level) {
    val building = Building(level)
    val nav = NavGraph(building)
    val assassin = Assassin(level, Position(level.player.floor, level.player.x))
    val guards: List<Guard> = level.guards.map { Guard(it, this) }
    val target = Guard(level.target, this, isTarget = true)

    /** Everyone in the building who can see, hear and be taken down. */
    val people: List<Guard> = guards + target

    /** Someone saw the assassin clearly at least once. Costs a star. */
    var detected = false
        private set

    /** Guards (not the target) knocked out. Each one costs the clean-kill star. */
    val extraTakedowns: Int get() = guards.count { it.down }

    val targetEliminated: Boolean get() = target.down

    var status = MissionStatus.PLAYING
        private set

    /** Seconds of play so far. */
    var time = 0f
        private set

    var order: Order? = null
        private set

    var coinsLeft = level.coins
        private set
    val coins = mutableListOf<Coin>()

    private var noiseTimer = 0f
    private var replanTimer = 0f

    /** Plans a route to [target]; returns false when there is no way there. */
    fun moveAssassin(target: Position, run: Boolean): Boolean {
        if (status != MissionStatus.PLAYING) return false
        order = null
        return walkAssassin(target, run)
    }

    fun orderTakedown(guard: Guard, run: Boolean): Boolean = startOrder(Order.Takedown(guard), Position(guard.floor, guard.x), run)

    fun orderPickUp(body: Guard): Boolean = startOrder(Order.PickUp(body), Position(body.floor, body.x), run = false)

    fun orderHide(spot: HidingSpot, run: Boolean): Boolean = startOrder(Order.Hide(spot), Position(spot.floor, spot.x), run)

    /** Lets go of the body being dragged, right where it is. */
    fun dropBody() {
        assassin.dragging = null
    }

    /** Throws a coin at [target]; it must land on the assassin's floor, within reach and in plain sight. */
    fun throwCoin(target: Position): Boolean {
        if (status != MissionStatus.PLAYING || coinsLeft <= 0 || assassin.mover.isOnStairs) return false
        if (target.floor != assassin.floor) return false
        val dx = target.x - assassin.x
        if (abs(dx) > COIN_RANGE || abs(dx) < 0.5f) return false
        val facing = if (dx > 0) 1 else -1
        val reach = building.sightEnd(assassin.floor, assassin.x, facing, COIN_RANGE)
        if (abs(dx) > abs(reach - assassin.x)) return false
        coinsLeft--
        coins += Coin(Position(assassin.floor, assassin.x), assassin.y + 1.4f, target)
        leaveHiding()
        return true
    }

    fun update(delta: Float) {
        if (status != MissionStatus.PLAYING) return
        time += delta
        assassin.update(delta, ::walkedThrough)
        carryBody()
        followOrder(delta)
        emitNoise(delta)
        updateCoins(delta)
        people.forEach { it.update(delta) }
        checkExit()
    }

    /** 1 star for finishing, 1 for never being seen, 1 for leaving everyone but the target alone. */
    fun stars(): Int {
        if (status != MissionStatus.COMPLETE) return 0
        return 1 + (if (!detected) 1 else 0) + (if (extraTakedowns == 0) 1 else 0)
    }

    fun spotted() {
        detected = true
    }

    private fun checkExit() {
        val a = assassin
        if (!targetEliminated || a.mover.isOnStairs || a.dragging != null) return
        if (a.floor == level.exit.floor && abs(a.x - level.exit.x) <= EXIT_REACH) {
            status = MissionStatus.COMPLETE
            assassin.stop()
            order = null
        }
    }

    /** Opens any door a character walked through. */
    fun walkedThrough(floor: Int, fromX: Float, toX: Float) {
        building.doorsBetween(floor, fromX, toX).forEach(building::open)
    }

    /** True when [guard] has the assassin in sight. */
    fun canSee(guard: Guard): Boolean {
        val a = assassin
        if (a.hiddenIn != null) return false
        val crouched = a.gait == Gait.CROUCH
        return canSee(guard, a.floor, a.x, a.y, if (crouched) CROUCH_VISION_FACTOR else 1f)
    }

    /** True when [guard] can see the spot at ([x], [y]); [rangeFactor] shrinks sight for hard-to-see things. */
    fun canSee(guard: Guard, floor: Int, x: Float, y: Float, rangeFactor: Float = 1f): Boolean {
        if (guard.down) return false
        // Only things on the guard's floor are visible; stairs count as the floor they are nearest.
        if (abs(y - level.floorY(guard.floor)) > level.floorHeight / 2f) return false
        val dx = x - guard.x
        if (abs(dx) < BUMP_DISTANCE) return true
        if ((dx > 0) != (guard.facing > 0)) return false
        val dark = level.roomAt(guard.floor, x)?.dark == true
        val range = (if (dark) VISION_RANGE_DARK else VISION_RANGE) * rangeFactor
        val end = building.sightEnd(guard.floor, guard.x, guard.facing, range)
        return abs(dx) <= abs(end - guard.x)
    }

    /** A guard calls out: nearby guards come to [at]. */
    fun raiseAlarm(source: Guard, at: Position) {
        guards.filter { it !== source && !it.down }
            .filter { abs(it.floor - source.floor) <= 1 && abs(it.x - source.x) <= SHOUT_RADIUS }
            .forEach { it.alert(at) }
    }

    fun caught() {
        status = MissionStatus.CAUGHT
        assassin.stop()
        order = null
    }

    private fun startOrder(newOrder: Order, target: Position, run: Boolean): Boolean {
        if (status != MissionStatus.PLAYING) return false
        if (!walkAssassin(target, run)) return false
        order = newOrder
        replanTimer = REPLAN_TIME
        return true
    }

    private fun walkAssassin(target: Position, run: Boolean): Boolean {
        val path = nav.findPath(assassin.mover.planningStart, target) ?: return false
        leaveHiding()
        assassin.follow(path, run)
        return true
    }

    private fun leaveHiding() {
        assassin.hiddenIn = null
    }

    private fun followOrder(delta: Float) {
        val a = assassin
        if (a.busy > 0f || a.mover.isOnStairs) return
        when (val o = order) {
            null -> Unit
            is Order.Takedown -> {
                val g = o.guard
                if (g.down) {
                    order = null
                } else if (g.floor == a.floor && !g.mover.isOnStairs && abs(g.x - a.x) <= TAKEDOWN_REACH) {
                    order = null
                    a.stop()
                    // An alarmed guard is ready for it; anyone else, and always the target, goes down.
                    if (g.isTarget || g.state != GuardState.ALARMED) {
                        g.knockOut()
                        a.busy = TAKEDOWN_TIME
                        noise(Position(a.floor, a.x), TAKEDOWN_NOISE)
                    }
                } else {
                    // The guard keeps moving, so keep re-aiming at them.
                    replanTimer -= delta
                    if (replanTimer <= 0f || !a.isMoving) {
                        replanTimer = REPLAN_TIME
                        nav.findPath(a.mover.planningStart, Position(g.floor, g.x))?.let { a.follow(it, a.running) }
                    }
                }
            }
            is Order.PickUp -> if (!a.isMoving) {
                order = null
                val body = o.body
                if (body.floor == a.floor && abs(body.x - a.x) <= PICKUP_REACH) {
                    body.hiddenIn = null
                    a.dragging = body
                }
            }
            is Order.Hide -> if (!a.isMoving) {
                order = null
                val body = a.dragging
                if (body != null) {
                    body.hiddenIn = o.spot
                    body.dragTo(o.spot.floor, o.spot.x, level.floorY(o.spot.floor))
                    a.dragging = null
                } else {
                    a.hiddenIn = o.spot
                    a.crouching = false
                }
            }
        }
    }

    private fun carryBody() {
        val body = assassin.dragging ?: return
        val a = assassin
        body.dragTo(a.floor, a.x - a.facing * DRAG_OFFSET, a.y)
    }

    private fun updateCoins(delta: Float) {
        coins.forEach { coin ->
            val wasFlying = !coin.landed
            coin.age += delta
            if (wasFlying && coin.landed) noise(coin.to, COIN_NOISE)
        }
        coins.removeAll { it.age > Coin.FLIGHT_TIME + 1.5f }
    }

    private fun emitNoise(delta: Float) {
        noiseTimer -= delta
        val radius = assassin.noiseRadius
        if (radius <= 0f || noiseTimer > 0f) return
        noiseTimer = NOISE_INTERVAL
        noise(Position(assassin.floor, assassin.x), radius)
    }

    private fun noise(at: Position, radius: Float) {
        people.filter { !it.down && it.floor == at.floor && abs(it.x - at.x) <= radius }.forEach { it.hear(at) }
    }

    companion object {
        const val VISION_RANGE = 7f
        const val VISION_RANGE_DARK = 3.5f
        const val CROUCH_VISION_FACTOR = 0.7f
        const val BUMP_DISTANCE = 0.8f
        const val SHOUT_RADIUS = 12f
        const val NOISE_INTERVAL = 0.3f
        const val TAKEDOWN_REACH = 0.9f
        const val TAKEDOWN_TIME = 0.8f
        const val TAKEDOWN_NOISE = 2f
        const val PICKUP_REACH = 0.9f
        const val DRAG_OFFSET = 0.7f
        const val COIN_RANGE = 9f
        const val COIN_NOISE = 5f
        const val REPLAN_TIME = 0.3f
        const val EXIT_REACH = 0.7f
    }
}
