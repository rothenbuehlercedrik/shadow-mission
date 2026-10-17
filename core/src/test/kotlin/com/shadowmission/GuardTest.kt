package com.shadowmission

import com.shadowmission.game.Guard
import com.shadowmission.game.GuardState
import com.shadowmission.game.Mission
import com.shadowmission.game.MissionStatus
import com.shadowmission.game.Position
import com.shadowmission.level.Door
import com.shadowmission.level.Floor
import com.shadowmission.level.GuardKind
import com.shadowmission.level.GuardSpawn
import com.shadowmission.level.Level
import com.shadowmission.level.Room
import com.shadowmission.level.Spawn
import com.shadowmission.level.Stairs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GuardTest {

    /** Ground floor: two 10 m rooms with a door at x = 10. Upper floor: one long room. */
    private fun level(guards: List<GuardSpawn>, player: Spawn = Spawn(0, 2f)) = Level(
        name = "guard test",
        floorHeight = 3f,
        floors = listOf(
            Floor(0, listOf(Room("a", 0f, 10f), Room("b", 10f, 10f)), listOf(Door(10f))),
            Floor(1, listOf(Room("c", 0f, 20f)), emptyList()),
        ),
        stairs = listOf(Stairs(16f, 3f, 0)),
        player = player,
        guards = guards,
        target = GuardSpawn(1, 19f, GuardKind.SENTRY),
        exit = Spawn(0, 1f),
    )

    private fun sentry(floor: Int, x: Float, facing: Int) = GuardSpawn(floor, x, GuardKind.SENTRY, facing = facing)

    private fun Mission.run(seconds: Float, until: () -> Boolean = { false }) {
        repeat((seconds * 60).toInt()) {
            if (until()) return
            update(1f / 60f)
        }
    }

    @Test
    fun `patrol guard walks between its route points`() {
        val mission = Mission(level(listOf(GuardSpawn(1, 2f, GuardKind.PATROL, route = listOf(2f, 18f)))))
        val guard = mission.guards.single()
        var furthest = 0f
        repeat(60 * 15) {
            mission.update(1f / 60f)
            furthest = maxOf(furthest, guard.x)
        }
        assertEquals(18f, furthest, 0.01f)
        assertEquals(GuardState.PATROL, guard.state)
    }

    @Test
    fun `guard looking the other way does not notice a still assassin`() {
        val mission = Mission(level(listOf(sentry(0, 6f, facing = 1))))
        mission.run(3f)
        val guard = mission.guards.single()
        assertEquals(GuardState.PATROL, guard.state)
        assertEquals(0f, guard.awareness)
    }

    @Test
    fun `guard who sees the assassin raises the alarm and catches them`() {
        val mission = Mission(level(listOf(sentry(0, 8f, facing = -1))))
        val guard = mission.guards.single()
        mission.run(3f) { guard.state == GuardState.ALARMED }
        assertEquals(GuardState.ALARMED, guard.state)
        mission.run(5f) { mission.status == MissionStatus.CAUGHT }
        assertEquals(MissionStatus.CAUGHT, mission.status)
    }

    @Test
    fun `closed doors block sight until opened`() {
        val mission = Mission(level(listOf(sentry(0, 12f, facing = -1)), player = Spawn(0, 9f)))
        val guard = mission.guards.single()
        assertTrue(!mission.canSee(guard))
        mission.building.open(mission.level.floors[0].doorAt(10f)!!)
        assertTrue(mission.canSee(guard))
    }

    @Test
    fun `darkness and crouching shorten how far guards see`() {
        val lit = Mission(level(listOf(sentry(0, 8f, facing = -1)), player = Spawn(0, 2.5f)))
        assertTrue(lit.canSee(lit.guards.single()))
        lit.assassin.crouching = true
        lit.moveAssassin(Position(0, 2f), run = false)
        assertTrue(!lit.canSee(lit.guards.single()), "5.5 m away is beyond crouched sight range")
    }

    @Test
    fun `running is heard, sneaking is not`() {
        fun stateAfterMoving(run: Boolean, crouch: Boolean): GuardState {
            val mission = Mission(level(listOf(sentry(0, 7f, facing = 1))))
            mission.assassin.crouching = crouch
            mission.moveAssassin(Position(0, 4.5f), run)
            mission.run(0.5f)
            return mission.guards.single().state
        }
        assertEquals(GuardState.PATROL, stateAfterMoving(run = false, crouch = true))
        // The guard turns towards the noise, so they may already have spotted the assassin.
        assertTrue(stateAfterMoving(run = true, crouch = false) >= GuardState.SUSPICIOUS)
    }

    @Test
    fun `alarm brings nearby guards on the next floor`() {
        val mission = Mission(level(listOf(sentry(0, 8f, facing = -1), sentry(1, 5f, facing = 1))))
        val (spotter, helper) = mission.guards
        mission.run(2f) { spotter.state == GuardState.ALARMED }
        assertEquals(GuardState.ALARMED, spotter.state)
        assertTrue(helper.state >= GuardState.SEARCHING, "helper is ${helper.state}")
    }

    @Test
    fun `suspicious guard checks the noise, then goes back to their post`() {
        val mission = Mission(level(listOf(sentry(0, 8f, facing = -1)), player = Spawn(1, 2f)))
        val guard: Guard = mission.guards.single()
        guard.hear(Position(0, 3f))
        assertEquals(GuardState.SUSPICIOUS, guard.state)
        var closest = guard.x
        repeat(60 * 20) {
            mission.update(1f / 60f)
            closest = minOf(closest, guard.x)
        }
        assertEquals(3f, closest, 0.01f)
        assertEquals(GuardState.PATROL, guard.state)
        assertEquals(8f, guard.x, 0.01f)
    }
}
