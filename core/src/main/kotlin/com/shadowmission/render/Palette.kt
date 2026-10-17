package com.shadowmission.render

import com.badlogic.gdx.graphics.Color

/** Night-time palette from the plan: dark navy building, warm lamps, one accent per character. */
object Palette {
    val sky: Color = Color.valueOf("0b1020")
    val roomLit: Color = Color.valueOf("1c2742")
    val roomDark: Color = Color.valueOf("121a2e")
    val wall: Color = Color.valueOf("3a4566")
    val slab: Color = Color.valueOf("2b3452")
    val door: Color = Color.valueOf("6b4a32")
    val doorLocked: Color = Color.valueOf("8a3b2e")
    val stairs: Color = Color.valueOf("4c5878")
    val lamp: Color = Color.valueOf("ffd27a").also { it.a = 0.12f }
    val lampBulb: Color = Color.valueOf("ffe3a3")
    val assassin: Color = Color.valueOf("2a2a2e")
    val assassinOutline: Color = Color.valueOf("8a8f9c")
    val scarf: Color = Color.valueOf("d7263d")
    val guard: Color = Color.valueOf("5d6b7d")
    val guardCap: Color = Color.valueOf("394555")
    val visionCalm: Color = Color.valueOf("f2e394").also { it.a = 0.22f }
    val target: Color = Color.valueOf("c9a227")
    val targetMarker: Color = Color.valueOf("d7263d")
    val exit: Color = Color.valueOf("3fbf7f")
    val hudText: Color = Color.valueOf("c8cee0")
}
