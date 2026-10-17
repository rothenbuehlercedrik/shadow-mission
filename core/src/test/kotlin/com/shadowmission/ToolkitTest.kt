package com.shadowmission

import com.shadowmission.game.Gait
import com.shadowmission.game.GuardState
import com.shadowmission.game.Mission
import com.shadowmission.game.MissionStatus
import com.shadowmission.game.Position
import com.shadowmission.level.Floor
import com.shadowmission.level.GuardKind
import com.shadowmission.level.GuardSpawn
import com.shadowmission.level.HidingKind
import com.shadowmission.level.HidingSpot
import com.shadowmission.level.Level
import com.shadowmission.level.Room
import com.shadowmission.level.Spawn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Takedowns, bodies, hiding and coins on a single 30 m floor. */
class ToolkitTest {

    private fun level(guards: List<GuardSpawn>, player: Float = 2f) = Level(
        name = "toolkit test",
        floorHeight = 3f,
        floors = listOf(Floor(0, listOf(Room("hall", 0f, 30f)), emptyList())),
        stairs = emptyList(),
        player = Spawn(0, player),
        guards = guards,
        target = GuardSpawn(0, 29f, GuardKind.SENTRY),
        exit = Spawn(0, 1f),
        hidingSpots = listOf(HidingSpot(0, 4f, HidingKind.CLOSET)),
        coins = 2,
    )

    private fun sentry(x: Float, facing: Int) = GuardSpawn(0, x, GuardKind.SENTRY, facing = facing)

    private fun Mission.run(seconds: Float, until: () -> Boolean = { false }) {
        repeat((seconds * 60).toInt()) {
            if (until()) return
            update(1f / 60f)
        }
    }

    @Test
    fun `sneaking up from behind takes a guard down`() {
        val mission = Mission(level(listOf(sentry(5f, facing = 1))))
        val guard = mission.guards.single()
        mission.assassin.crouching = true
        assertTrue(mission.orderTakedown(guard, run = false))
        mission.run(4f) { guard.down }
        assertTrue(guard.down)
        assertEquals(MissionStatus.PLAYING, mission.status)
    }

    @Test
    fun `charging a guard who sees you coming gets you caught`() {
        val mission = Mission(level(listOf(sentry(9f, facing = -1))))
        val guard = mission.guards.single()
        mission.orderTakedown(guard, run = false)
        mission.run(8f) { mission.status == MissionStatus.CAUGHT }
        assertFalse(guard.down)
        assertEquals(MissionStatus.CAUGHT, mission.status)
    }

    @Test
    fun `a guard who finds a body starts searching`() {
        val mission = Mission(level(listOf(sentry(5f, facing = 1), sentry(11f, facing = -1)), player = 2f))
        val (victim, witness) = mission.guards
        victim.knockOut()
        mission.run(0.2f)
        assertTrue(victim.discovered)
        assertEquals(GuardState.SEARCHING, witness.state)
    }

    @Test
    fun `a stashed body stays hidden`() {
        val mission = Mission(level(listOf(sentry(5f, facing = 1), sentry(11f, facing = -1))))
        val (victim, witness) = mission.guards
        victim.knockOut()
        victim.hiddenIn = mission.level.hidingSpots.single()
        mission.run(1f)
        assertFalse(victim.discovered)
        assertEquals(GuardState.PATROL, witness.state)
    }

    @Test
    fun `dragging a body into a closet hides it`() {
        val mission = Mission(level(listOf(sentry(8f, facing = 1))))
        val victim = mission.guards.single()
        victim.knockOut()
        assertTrue(mission.orderPickUp(victim))
        mission.run(6f) { mission.assassin.dragging != null }
        assertSame(victim, mission.assassin.dragging)
        assertEquals(Gait.DRAG, mission.assassin.gait)
        assertTrue(mission.orderHide(mission.level.hidingSpots.single(), run = true))
        assertFalse(mission.assassin.running, "can't run while dragging")
        mission.run(8f) { mission.assassin.dragging == null }
        assertNull(mission.assassin.dragging)
        assertEquals(mission.level.hidingSpots.single(), victim.hiddenIn)
    }

    @Test
    fun `guards cannot see a hidden assassin`() {
        val mission = Mission(level(listOf(GuardSpawn(0, 10f, GuardKind.PATROL, route = listOf(10f, 0.6f)))))
        assertTrue(mission.orderHide(mission.level.hidingSpots.single(), run = false))
        mission.run(2f)
        assertTrue(mission.assassin.hiddenIn != null)
        mission.run(12f)
        assertEquals(MissionStatus.PLAYING, mission.status)
        assertTrue(mission.guards.single().awareness < 0.05f)
    }

    @Test
    fun `a coin pulls a guard to where it lands`() {
        val mission = Mission(level(listOf(sentry(12f, facing = -1)), player = 2f))
        val guard = mission.guards.single()
        // Out of sight range, the guard only hears the coin land.
        assertTrue(mission.throwCoin(Position(0, 9.5f)))
        assertEquals(1, mission.coinsLeft)
        mission.run(1f)
        assertEquals(GuardState.SUSPICIOUS, guard.state)
        assertEquals(9.5f, guard.pointOfInterest!!.x)
    }

    @Test
    fun `coins only fly within range and run out`() {
        val mission = Mission(level(emptyList()))
        assertFalse(mission.throwCoin(Position(0, 25f)), "too far")
        assertTrue(mission.throwCoin(Position(0, 6f)))
        assertTrue(mission.throwCoin(Position(0, 6f)))
        assertFalse(mission.throwCoin(Position(0, 6f)), "out of coins")
    }
}
