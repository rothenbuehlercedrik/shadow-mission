package com.shadowmission.game

import com.shadowmission.level.Level

/** Everything that changes while a mission is played. */
class Mission(val level: Level) {
    val building = Building(level)
    val nav = NavGraph(building)
    val assassin = Assassin(level, Position(level.player.floor, level.player.x))

    /** Plans a route to [target]; returns false when there is no way there. */
    fun moveAssassin(target: Position, run: Boolean): Boolean {
        val from = Position(assassin.floor, assassin.x)
        val start = (assassin.plannedSteps.firstOrNull() as? PathStep.Climb)?.let {
            // Mid-staircase: plan from the end of the stairs the assassin is finishing.
            if (it.up) Position(it.stairs.topFloor, it.stairs.right) else Position(it.stairs.bottomFloor, it.stairs.x)
        } ?: from
        val path = nav.findPath(start, target) ?: return false
        assassin.follow(path, run)
        return true
    }

    fun update(delta: Float) {
        assassin.update(delta) { floor, fromX, toX ->
            building.doorsBetween(floor, fromX, toX).forEach(building::open)
        }
    }
}
