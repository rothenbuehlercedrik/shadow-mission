package com.shadowmission.render

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.math.MathUtils

/** A tappable rectangle on a menu or overlay, in screen pixels with y pointing up. */
class UiButton(val id: String, val label: String, val x: Float, val y: Float, val width: Float, val height: Float) {
    fun contains(px: Float, py: Float): Boolean = px >= x && px <= x + width && py >= y && py <= y + height
}

/** Small drawing helpers shared by the HUD and menus. */
object Ui {

    /** A filled five-point star centred on ([cx], [cy]). */
    fun star(shapes: ShapeRenderer, cx: Float, cy: Float, radius: Float, color: Color) {
        shapes.color = color
        val inner = radius * 0.45f
        for (i in 0 until 10) {
            val r1 = if (i % 2 == 0) radius else inner
            val r2 = if (i % 2 == 0) inner else radius
            val a1 = MathUtils.PI / 2f + i * MathUtils.PI / 5f
            val a2 = a1 + MathUtils.PI / 5f
            shapes.triangle(
                cx, cy,
                cx + MathUtils.cos(a1) * r1, cy + MathUtils.sin(a1) * r1,
                cx + MathUtils.cos(a2) * r2, cy + MathUtils.sin(a2) * r2,
            )
        }
    }

    /** A row of three stars, the first [filled] of them gold. */
    fun stars(shapes: ShapeRenderer, cx: Float, cy: Float, radius: Float, filled: Int) {
        for (i in 0 until 3) {
            star(shapes, cx + (i - 1) * radius * 2.4f, cy, radius, if (i < filled) Palette.starOn else Palette.starOff)
        }
    }

    fun button(shapes: ShapeRenderer, b: UiButton, primary: Boolean = false) {
        shapes.color = if (primary) Palette.buttonPrimary else Palette.panelLight
        shapes.rect(b.x, b.y, b.width, b.height)
    }
}
