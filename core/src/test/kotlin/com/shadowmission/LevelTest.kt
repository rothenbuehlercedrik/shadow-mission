package com.shadowmission

import com.shadowmission.level.Door
import com.shadowmission.level.Floor
import com.shadowmission.level.LevelLoader
import com.shadowmission.level.LevelValidator
import com.shadowmission.level.Room
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LevelTest {

    private val assetsDir = File(System.getProperty("assetsDir"))

    @Test
    fun `every shipped level loads and validates`() {
        val files = File(assetsDir, "levels").listFiles { f -> f.extension == "json" && f.name != "campaign.json" }!!.toList()
        assertTrue(files.isNotEmpty(), "no level files found")
        files.forEach { file ->
            val level = LevelLoader.parse(file.readText())
            assertEquals(emptyList(), LevelValidator.validate(level), "errors in ${file.name}")
        }
    }

    @Test
    fun `test level is parsed with its rooms, stairs and guards`() {
        val level = LevelLoader.parse(File(assetsDir, "levels/harbor_warehouse.json").readText())
        assertEquals(3, level.floors.size)
        assertEquals(30f, level.right)
        assertEquals("boss-office", level.roomAt(level.target.floor, level.target.x)?.id)
        assertEquals(listOf(19f, 29f), level.guards[0].route)
        assertEquals(2, level.stairs.size)
    }

    @Test
    fun `room lookup treats the right edge as the next room`() {
        val floor = Floor(0, listOf(Room("a", 0f, 5f), Room("b", 5f, 5f)), emptyList())
        assertEquals("b", floor.roomAt(5f)?.id)
        assertNull(floor.roomAt(10f))
    }

    @Test
    fun `validator reports overlaps, gaps and stray doors`() {
        val json = """
            {
              "name": "broken", "floorHeight": 3,
              "floors": [
                { "rooms": [ {"id":"a","x":0,"width":6}, {"id":"b","x":5,"width":5}, {"id":"c","x":12,"width":4} ],
                  "doors": [ {"x": 3} ] }
              ],
              "stairs": [ {"x": 1, "width": 2, "bottomFloor": 0} ],
              "player": {"floor": 0, "x": 11},
              "exit": {"floor": 0, "x": 1},
              "target": {"floor": 0, "x": 13},
              "guards": [ {"floor": 0, "x": 2, "kind": "patrol", "route": [2]} ]
            }
        """.trimIndent()
        val errors = LevelValidator.validate(LevelLoader.parse(json))
        assertTrue("rooms a and b overlap" in errors, errors.toString())
        assertTrue("gap between rooms b and c" in errors, errors.toString())
        assertTrue(errors.any { it.startsWith("door at 3.0") }, errors.toString())
        assertTrue("stairs 0 lead above the top floor" in errors, errors.toString())
        assertTrue("player is outside every room" in errors, errors.toString())
        assertTrue("patrol guard 0 needs at least two route points" in errors, errors.toString())
    }

    @Test
    fun `doors match walls within a small tolerance`() {
        val floor = Floor(0, emptyList(), listOf(Door(4f)))
        assertEquals(4f, floor.doorAt(4.005f)?.x)
        assertNull(floor.doorAt(4.5f))
    }
}
