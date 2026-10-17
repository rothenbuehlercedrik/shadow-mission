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
    val doorFrame: Color = Color.valueOf("4a3424")
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
    val visionSuspicious: Color = Color.valueOf("f5b041").also { it.a = 0.24f }
    val visionSearching: Color = Color.valueOf("ef7d32").also { it.a = 0.28f }
    val visionAlarmed: Color = Color.valueOf("e8433a").also { it.a = 0.32f }
    val meterBack: Color = Color.valueOf("000000").also { it.a = 0.5f }
    val noise: Color = Color.valueOf("9fb4ff").also { it.a = 0.5f }
    val furniture: Color = Color.valueOf("3b2e2a")
    val furnitureEdge: Color = Color.valueOf("564239")
    val curtain: Color = Color.valueOf("5a2333")
    val curtainFold: Color = Color.valueOf("47192a")
    val stashed: Color = Color.valueOf("8a8f9c")
    val coin: Color = Color.valueOf("f2c94c")
    val slowMo: Color = Color.valueOf("3d5afe").also { it.a = 0.10f }
    val target: Color = Color.valueOf("c9a227")
    val targetMarker: Color = Color.valueOf("d7263d")
    val exit: Color = Color.valueOf("3fbf7f")
    val path: Color = Color.valueOf("e8ecf5").also { it.a = 0.55f }
    val pathRun: Color = Color.valueOf("ffb4a8").also { it.a = 0.7f }
    val blocked: Color = Color.valueOf("ff5a4e")
    val button: Color = Color.valueOf("e8ecf5").also { it.a = 0.12f }
    val buttonActive: Color = Color.valueOf("d7263d").also { it.a = 0.55f }
    val overlay: Color = Color.valueOf("05070d").also { it.a = 0.72f }
    val failText: Color = Color.valueOf("ff6b5e")
    val hudText: Color = Color.valueOf("c8cee0")
}
