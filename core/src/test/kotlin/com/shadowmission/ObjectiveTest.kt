package com.shadowmission

import com.shadowmission.campaign.Campaign
import com.shadowmission.campaign.CampaignEntry
import com.shadowmission.campaign.InMemoryProgress
import com.shadowmission.game.Building
import com.shadowmission.game.Mission
import com.shadowmission.game.NavGraph
import com.shadowmission.game.MissionStatus
import com.shadowmission.game.Position
import com.shadowmission.level.Floor
import com.shadowmission.level.GuardKind
import com.shadowmission.level.GuardSpawn
import com.shadowmission.level.Level
import com.shadowmission.level.LevelLoader
import com.shadowmission.level.LevelValidator
import com.shadowmission.level.Room
import com.shadowmission.level.Spawn
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ObjectiveTest {

    /** One 30 m room: assassin at 2, exit at 1, target standing at 7 looking away. */
    private fun level(guards: List<GuardSpawn> = emptyList()) = Level(
        name = "objective test",
        floorHeight = 3f,
        floors = listOf(Floor(0, listOf(Room("hall", 0f, 30f)), emptyList())),
        stairs = emptyList(),
        player = Spawn(0, 2f),
        guards = guards,
        target = GuardSpawn(0, 7f, GuardKind.SENTRY, facing = 1),
        exit = Spawn(0, 1f),
    )

    private fun Mission.run(seconds: Float, until: () -> Boolean = { false }) {
        repeat((seconds * 60).toInt()) {
            if (until()) return
            update(1f / 60f)
        }
    }

    private fun Mission.eliminateTargetAndLeave() {
        assassin.crouching = true
        assertTrue(orderTakedown(target, run = false))
        run(10f) { targetEliminated }
        assertTrue(targetEliminated)
        moveAssassin(Position(level.exit.floor, level.exit.x), run = false)
        run(10f) { status != MissionStatus.PLAYING }
    }

    @Test
    fun `the exit only counts once the target is down`() {
        val mission = Mission(level())
        mission.moveAssassin(Position(0, 1f), run = false)
        mission.run(2f)
        assertEquals(MissionStatus.PLAYING, mission.status)
    }

    @Test
    fun `a clean run earns three stars`() {
        val mission = Mission(level())
        mission.eliminateTargetAndLeave()
        assertEquals(MissionStatus.COMPLETE, mission.status)
        assertEquals(3, mission.stars())
    }

    @Test
    fun `knocking out a guard costs a star`() {
        val mission = Mission(level(listOf(GuardSpawn(0, 4f, GuardKind.SENTRY, facing = 1))))
        mission.assassin.crouching = true
        mission.orderTakedown(mission.guards.single(), run = false)
        mission.run(6f) { mission.guards.single().down }
        mission.eliminateTargetAndLeave()
        assertEquals(1, mission.extraTakedowns)
        assertEquals(2, mission.stars())
    }

    @Test
    fun `being spotted costs a star`() {
        val mission = Mission(level())
        mission.spotted()
        mission.eliminateTargetAndLeave()
        assertEquals(2, mission.stars())
    }

    @Test
    fun `the target flees instead of catching the assassin`() {
        val mission = Mission(level().copy(target = GuardSpawn(0, 6f, GuardKind.SENTRY, facing = -1)))
        mission.run(5f)
        assertEquals(MissionStatus.PLAYING, mission.status)
        assertTrue(mission.detected)
        assertTrue(mission.target.x > 20f, "target ran to the far end, now at ${mission.target.x}")
    }

    @Test
    fun `missions unlock one after another and keep the best stars`() {
        val campaign = Campaign(listOf(CampaignEntry("a", "a.json", ""), CampaignEntry("b", "b.json", "")))
        val progress = InMemoryProgress()
        assertTrue(campaign.isUnlocked(0, progress))
        assertFalse(campaign.isUnlocked(1, progress))
        progress.record("a", 2)
        progress.record("a", 1)
        assertEquals(2, progress.stars("a"))
        assertTrue(campaign.isUnlocked(1, progress))
    }

    @Test
    fun `every campaign mission exists and is valid`() {
        val assets = File(System.getProperty("assetsDir"))
        val campaign = Campaign.parse(File(assets, "levels/campaign.json").readText())
        assertTrue(campaign.missions.isNotEmpty())
        assertEquals(campaign.missions.size, campaign.missions.map { it.id }.toSet().size, "mission ids must be unique")
        campaign.missions.forEach { entry ->
            val level = LevelLoader.parse(File(assets, entry.file).readText())
            assertEquals(emptyList(), LevelValidator.validate(level), entry.file)
            val nav = NavGraph(Building(level))
            val start = Position(level.player.floor, level.player.x)
            val target = Position(level.target.floor, level.target.x)
            assertNotNull(nav.findPath(start, target), "${entry.file}: no way to the target")
            assertNotNull(nav.findPath(target, Position(level.exit.floor, level.exit.x)), "${entry.file}: no way to the exit")
        }
    }
}
