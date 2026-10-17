package com.shadowmission.level

import com.badlogic.gdx.utils.JsonReader
import com.badlogic.gdx.utils.JsonValue

/** Reads a level from the JSON format used in `assets/levels`. */
object LevelLoader {

    fun parse(json: String): Level {
        val root = JsonReader().parse(json)
        return Level(
            name = root.getString("name"),
            floorHeight = root.getFloat("floorHeight", 3f),
            floors = root.get("floors").mapIndexed { index, floor -> parseFloor(index, floor) },
            stairs = root.get("stairs")?.map {
                Stairs(it.getFloat("x"), it.getFloat("width"), it.getInt("bottomFloor"))
            } ?: emptyList(),
            player = parseSpawn(root.get("player")),
            guards = root.get("guards")?.map(::parseGuard) ?: emptyList(),
            target = parseSpawn(root.get("target")),
            exit = parseSpawn(root.get("exit")),
        )
    }

    private fun parseFloor(index: Int, json: JsonValue) = Floor(
        index = index,
        rooms = json.get("rooms").map {
            Room(it.getString("id"), it.getFloat("x"), it.getFloat("width"), it.getBoolean("dark", false))
        },
        doors = json.get("doors")?.map { Door(it.getFloat("x"), it.getBoolean("locked", false)) } ?: emptyList(),
    )

    private fun parseSpawn(json: JsonValue) = Spawn(json.getInt("floor"), json.getFloat("x"))

    private fun parseGuard(json: JsonValue) = GuardSpawn(
        floor = json.getInt("floor"),
        x = json.getFloat("x"),
        kind = GuardKind.valueOf(json.getString("kind", "patrol").uppercase()),
        route = json.get("route")?.asFloatArray()?.toList() ?: emptyList(),
        facing = json.getInt("facing", 1),
    )
}
