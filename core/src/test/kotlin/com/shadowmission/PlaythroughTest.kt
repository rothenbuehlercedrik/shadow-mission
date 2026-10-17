package com.shadowmission

import com.shadowmission.game.Guard
import com.shadowmission.game.Mission
import com.shadowmission.game.MissionStatus
import com.shadowmission.game.Position
import com.shadowmission.level.LevelLoader
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Plays the campaign missions start to finish with a scripted, patient player, to prove each one
 * can be finished with three stars. If a level edit breaks the intended route, these fail.
 */
class PlaythroughTest {

    private class Player(file: String) {
        val mission = Mission(LevelLoader.parse(File(System.getProperty("assetsDir"), file).readText()))
        val a get() = mission.assassin
        private var lastStates: List<Any> = emptyList()

        fun waitUntil(what: String, maxSeconds: Float = 60f, condition: () -> Boolean) {
            repeat((maxSeconds * 60).toInt()) {
                if (System.getProperty("trace") != null) {
                    val now = mission.people.map { it.state }
                    if (now != lastStates) println("t=${"%.2f".format(mission.time)} [$what] ${describe()}")
                    lastStates = now
                }
                if (mission.status == MissionStatus.CAUGHT) fail("caught while waiting for: $what. ${describe()}")
                if (condition()) return
                mission.update(1f / 60f)
            }
            fail("timed out waiting for: $what. ${describe()}")
        }

        fun describe(): String = "Assassin at ${a.floor}/${a.x} (y ${a.y}); " +
            mission.people.joinToString { "${if (it.isTarget) "target" else "guard"} ${it.floor}/${"%.1f".format(it.x)} facing ${it.facing} ${it.state}" }

        fun walkTo(floor: Int, x: Float) {
            assertTrue(mission.moveAssassin(Position(floor, x), run = false), "no route to $floor/$x")
            waitUntil("arriving at $floor/$x") { !a.isMoving }
        }

        fun hideIn(floor: Int, x: Float) {
            val spot = mission.level.hidingSpots.single { it.floor == floor && it.x == x }
            assertTrue(mission.orderHide(spot, run = false))
            waitUntil("hiding at $floor/$x") { a.hiddenIn == spot }
        }

        fun takeDown(guard: Guard) {
            assertTrue(mission.orderTakedown(guard, run = false))
            waitUntil("taking down the guard at ${guard.floor}/${guard.x}") { guard.down }
        }

        fun guardOn(floor: Int, routeStart: Float): Guard =
            mission.guards.single { it.spawn.floor == floor && it.spawn.x == routeStart }
    }

    private fun Player.finish() {
        waitUntil("completing the mission") { mission.status == MissionStatus.COMPLETE }
        assertFalse(mission.detected, "spotted on the way")
        assertEquals(3, mission.stars())
    }

    @Test
    fun `first contract through the loft`() = with(Player("levels/harbor_first_contract.json")) {
        walkTo(1, 13f)
        a.crouching = true
        takeDown(mission.target)
        a.crouching = false
        assertTrue(mission.moveAssassin(Position(0, mission.level.exit.x), run = false))
        finish()
    }

    @Test
    fun `pier 9 past the patrols and out over the balcony`() = with(Player("levels/harbor_pier9.json")) {
        val yard = guardOn(0, 15f)
        val breakRoom = guardOn(1, 18f)
        val target = mission.target

        waitUntil("the yard guard walking away") { yard.facing == 1 && yard.x > 15f }
        walkTo(1, 13.5f)

        // Crouch from here on: walking close behind a guard is loud enough to turn them around.
        a.crouching = true
        waitUntil("the break room guard walking away") { breakRoom.facing == 1 && breakRoom.x > 17f }
        hideIn(1, 19.5f)
        waitUntil("the break room guard passing the curtain") { breakRoom.facing == -1 && breakRoom.x < 18.5f }
        // The break room guard can see into the dark records room once its door is open: wait hidden.
        hideIn(1, 25.8f)

        waitUntil("Hale heading left") { target.facing == -1 && target.x < 22f }
        walkTo(2, 30.8f)
        waitUntil("Hale heading left again") { target.facing == -1 && target.x < 23f }
        a.crouching = false
        hideIn(2, 26.5f)

        waitUntil("Hale turning back at the far end") { target.facing == -1 && target.x > 24.9f }
        takeDown(target)
        assertTrue(mission.moveAssassin(Position(2, mission.level.exit.x), run = false))
        finish()
    }
}
