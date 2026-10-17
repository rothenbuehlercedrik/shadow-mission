package com.shadowmission

import com.shadowmission.game.Building
import com.shadowmission.game.Mission
import com.shadowmission.game.NavGraph
import com.shadowmission.game.PathStep
import com.shadowmission.game.Position
import com.shadowmission.level.Level
import com.shadowmission.level.LevelLoader
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MovementTest {

    // Guards are left out here so they cannot interfere; GuardTest covers them.
    private val level: Level =
        LevelLoader.parse(File(System.getProperty("assetsDir"), "levels/harbor_warehouse.json").readText())
            .copy(guards = emptyList())

    private fun nav() = NavGraph(Building(level))

    @Test
    fun `walking within one floor is a single step`() {
        val path = nav().findPath(Position(0, 2f), Position(0, 25f))
        assertEquals(listOf<PathStep>(PathStep.Walk(0, 25f)), path)
    }

    @Test
    fun `reaching the top floor climbs both staircases`() {
        val path = assertNotNull(nav().findPath(Position(0, 2f), Position(2, 5f)))
        val climbs = path.filterIsInstance<PathStep.Climb>()
        assertEquals(listOf(0, 1), climbs.map { it.stairs.bottomFloor })
        assertTrue(climbs.all { it.up })
        assertEquals(PathStep.Walk(2, 5f), path.last())
    }

    @Test
    fun `going down uses the stairs in reverse`() {
        val path = assertNotNull(nav().findPath(Position(1, 25f), Position(0, 5f)))
        val climb = path.filterIsInstance<PathStep.Climb>().single()
        assertFalse(climb.up)
        assertEquals(PathStep.Walk(0, 5f), path.last())
    }

    @Test
    fun `a locked door blocks the way`() {
        assertNotNull(nav().findPath(Position(0, 2f), Position(2, 25f)))
        // Lock the boss office door: both staircases land outside it, so there is no way in.
        val top = level.floors[2]
        val locked = level.copy(floors = level.floors.dropLast(1) + top.copy(doors = top.doors.map { it.copy(locked = true) }))
        val lockedNav = NavGraph(Building(locked))
        assertNull(lockedNav.findPath(Position(0, 2f), Position(2, 25f)))
        assertEquals(lockedNav.areaOf(2, 5f) + 1, lockedNav.areaOf(2, 25f))
    }

    @Test
    fun `assassin follows a path, takes the stairs and opens doors on the way`() {
        val mission = Mission(level)
        assertTrue(mission.moveAssassin(Position(1, 25f), run = false))
        repeat(60 * 30) { mission.update(1f / 60f) }
        val a = mission.assassin
        assertEquals(1, a.floor)
        assertEquals(25f, a.x, 0.001f)
        assertEquals(level.floorY(1), a.y, 0.001f)
        assertFalse(a.isMoving)
        val opened = level.floors.flatMapIndexed { i, f -> f.doors.filter(mission.building::isOpen).map { i to it.x } }
        // Corridor door on the way in, hall to break room door upstairs.
        assertEquals(listOf(0 to 10f, 1 to 20f), opened)
    }

    @Test
    fun `running is faster than walking and crouching is slower`() {
        fun distanceAfterOneSecond(run: Boolean, crouch: Boolean): Float {
            val mission = Mission(level)
            mission.assassin.crouching = crouch
            mission.moveAssassin(Position(0, 9f), run)
            repeat(60) { mission.update(1f / 60f) }
            return mission.assassin.x - level.player.x
        }
        val walk = distanceAfterOneSecond(run = false, crouch = false)
        assertTrue(distanceAfterOneSecond(run = true, crouch = false) > walk)
        assertTrue(distanceAfterOneSecond(run = false, crouch = true) < walk)
    }

    @Test
    fun `sight stops at closed doors and passes through open ones`() {
        val building = Building(level)
        val door = level.floors[0].doorAt(18f)!!
        assertEquals(18f - Building.WALL_HALF, building.sightEnd(0, 15f, 1, 7f), 0.001f)
        building.open(door)
        assertEquals(22f, building.sightEnd(0, 15f, 1, 7f), 0.001f)
        assertEquals(10f + Building.WALL_HALF, building.sightEnd(0, 15f, -1, 7f), 0.001f)
    }

    @Test
    fun `locked doors never open`() {
        val locked = level.floors[2].doorAt(12f)!!.copy(locked = true)
        val top = level.floors[2]
        val building = Building(level.copy(floors = level.floors.dropLast(1) + top.copy(doors = listOf(locked))))
        building.open(locked)
        assertFalse(building.isOpen(locked))
    }
}
