package com.shadowmission.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.GL20
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.input.GestureDetector
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.utils.ScreenUtils
import com.badlogic.gdx.utils.viewport.ExtendViewport
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.shadowmission.game.Mission
import com.shadowmission.game.MissionStatus
import com.shadowmission.game.Position
import com.shadowmission.level.Level
import kotlin.math.abs

/** Plays one mission: move the assassin, take guards down quietly, avoid being seen. */
class MissionScreen(private val level: Level) : ScreenAdapter() {

    var mission = Mission(level)
        private set
    private val camera = OrthographicCamera()
    private val viewport = ExtendViewport(VIEW_WIDTH, VIEW_HEIGHT, camera)
    private val cameraController = CameraController(camera, level)
    private var renderer = LevelRenderer(mission)

    private val hudViewport = ScreenViewport()
    private val batch = SpriteBatch()
    private val hudShapes = ShapeRenderer()
    private val font = BitmapFont()
    private val layout = GlyphLayout()

    /** After tapping the coin button, the next tap in the building throws the coin there. */
    private var aimingCoin = false

    /** The finger holding the slow-motion button, or -1. */
    private var slowMoPointer = -1
    val slowMotion: Boolean get() = slowMoPointer >= 0

    private class Button(val id: String, val x: Float, val y: Float, val radius: Float, val label: String, val active: Boolean)

    private val ui: Float get() = maxOf(1f, Gdx.graphics.density)

    private fun buttons(): List<Button> {
        val r = BUTTON_RADIUS * ui
        val pad = 24f * ui
        val step = r * 2f + 16f * ui
        val a = mission.assassin
        val list = mutableListOf(
            Button("crouch", pad + r, pad + r, r, if (a.crouching) "Stand" else "Crouch", a.crouching),
            Button("coin", pad + r + step, pad + r, r, "Coin ${mission.coinsLeft}", aimingCoin),
        )
        if (a.dragging != null) list += Button("drop", pad + r + step * 2f, pad + r, r, "Drop", false)
        list += Button("slow", Gdx.graphics.width - pad - r, pad + r, r, "Slow", slowMotion)
        return list
    }

    private fun buttonAt(screenX: Float, screenY: Float): Button? {
        val hudY = Gdx.graphics.height - screenY
        return buttons().firstOrNull { Vector2.dst(it.x, it.y, screenX, hudY) <= it.radius * 1.15f }
    }

    /** Holding the slow-motion button is handled before gestures, so it works while other fingers tap. */
    private val holdInput = object : InputAdapter() {
        override fun touchDown(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
            if (buttonAt(screenX.toFloat(), screenY.toFloat())?.id != "slow") return false
            slowMoPointer = pointer
            return true
        }

        override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
            if (pointer != slowMoPointer) return false
            slowMoPointer = -1
            return true
        }
    }

    override fun show() {
        cameraController.onTap = ::handleTap
        Gdx.input.inputProcessor = InputMultiplexer(holdInput, GestureDetector(cameraController), cameraController.scrollInput)
        viewport.update(Gdx.graphics.width, Gdx.graphics.height)
        val a = mission.assassin
        cameraController.centreOn(a.x, a.y + mission.level.floorHeight / 2f)
    }

    private fun restart() {
        renderer.dispose()
        mission = Mission(level)
        renderer = LevelRenderer(mission)
        aimingCoin = false
        cameraController.following = true
    }

    private fun handleTap(screenX: Float, screenY: Float, count: Int) {
        if (mission.status == MissionStatus.CAUGHT) {
            restart()
            return
        }
        when (buttonAt(screenX, screenY)?.id) {
            "crouch" -> mission.assassin.crouching = !mission.assassin.crouching
            "coin" -> aimingCoin = !aimingCoin && mission.coinsLeft > 0
            "drop" -> mission.dropBody()
            "slow" -> Unit
            else -> tapWorld(screenX, screenY, run = count >= 2)
        }
    }

    private fun tapWorld(screenX: Float, screenY: Float, run: Boolean) {
        val world = viewport.unproject(Vector2(screenX, screenY))
        val level = mission.level
        val floor = MathUtils.clamp((world.y / level.floorHeight).toInt(), 0, level.floors.size - 1)
        val f = level.floors[floor]
        val x = MathUtils.clamp(world.x, f.left + EDGE_GAP, f.right - EDGE_GAP)
        val here = Position(floor, x)
        cameraController.following = true

        if (aimingCoin) {
            aimingCoin = false
            renderer.tapMarker = TapMarker(floor, x, mission.throwCoin(here))
            return
        }

        val a = mission.assassin
        val guard = mission.guards.firstOrNull { !it.down && it.floor == floor && abs(it.x - x) < TAP_REACH }
        val body = mission.guards.firstOrNull {
            it.down && it.hiddenIn == null && a.dragging !== it && it.floor == floor && abs(it.x - x) < TAP_REACH * 1.5f
        }
        val spot = level.hidingSpots.firstOrNull { it.floor == floor && abs(it.x - x) < TAP_REACH * 1.2f }
        val ok = when {
            guard != null && a.dragging == null -> mission.orderTakedown(guard, run)
            body != null && a.dragging == null -> mission.orderPickUp(body)
            spot != null -> mission.orderHide(spot, run)
            else -> mission.moveAssassin(here, run)
        }
        renderer.tapMarker = TapMarker(floor, x, ok)
    }

    override fun resize(width: Int, height: Int) {
        viewport.update(width, height)
        cameraController.clamp()
        hudViewport.update(width, height, true)
        // Keep HUD text a readable size on high-density phone screens.
        font.data.setScale(ui)
    }

    override fun render(delta: Float) {
        val dt = minOf(delta, MAX_STEP) * if (slowMotion) SLOW_MOTION_SCALE else 1f
        mission.update(dt)
        renderer.tapMarker?.let { it.age += minOf(delta, MAX_STEP) }
        val a = mission.assassin
        cameraController.follow(a.x, a.y + mission.level.floorHeight / 2f, minOf(delta, MAX_STEP))

        ScreenUtils.clear(Palette.sky)
        viewport.apply()
        renderer.render(camera.combined)
        drawHud()
    }

    private fun drawHud() {
        hudViewport.apply()
        val w = hudViewport.worldWidth
        val h = hudViewport.worldHeight
        val buttons = buttons()

        Gdx.gl.glEnable(GL20.GL_BLEND)
        hudShapes.projectionMatrix = hudViewport.camera.combined
        hudShapes.begin(ShapeRenderer.ShapeType.Filled)
        if (slowMotion) {
            hudShapes.color = Palette.slowMo
            hudShapes.rect(0f, 0f, w, h)
        }
        buttons.forEach {
            hudShapes.color = if (it.active) Palette.buttonActive else Palette.button
            hudShapes.circle(it.x, it.y, it.radius, 32)
        }
        hudShapes.end()
        Gdx.gl.glDisable(GL20.GL_BLEND)

        batch.projectionMatrix = hudViewport.camera.combined
        batch.begin()
        font.color = Palette.hudText
        val pad = 12f * ui
        font.draw(batch, mission.level.name, pad, h - pad)
        font.draw(batch, hint(), pad, h - pad - font.lineHeight * 1.3f)
        buttons.forEach {
            layout.setText(font, it.label)
            font.draw(batch, layout, it.x - layout.width / 2f, it.y + layout.height / 2f)
        }
        batch.end()

        if (mission.status == MissionStatus.CAUGHT) drawCaught()
    }

    private fun hint(): String {
        val a = mission.assassin
        return when {
            aimingCoin -> "Tap where to throw the coin."
            a.dragging != null -> "Tap a closet, curtain or desk to hide the body, or Drop it."
            a.hiddenIn != null -> "Hidden. Tap anywhere to leave."
            slowMotion -> "Slow motion"
            else -> "Tap to walk, double tap to run. Tap a guard from behind to take them down."
        }
    }

    private fun drawCaught() {
        val w = hudViewport.worldWidth
        val h = hudViewport.worldHeight
        Gdx.gl.glEnable(GL20.GL_BLEND)
        hudShapes.begin(ShapeRenderer.ShapeType.Filled)
        hudShapes.color = Palette.overlay
        hudShapes.rect(0f, 0f, w, h)
        hudShapes.end()
        Gdx.gl.glDisable(GL20.GL_BLEND)

        batch.begin()
        drawCentred("You were caught", h / 2f + 40f * ui, 2f, Palette.failText)
        drawCentred("Tap anywhere to try again", h / 2f - 10f * ui, 1f, Palette.hudText)
        batch.end()
    }

    private fun drawCentred(text: String, y: Float, scale: Float, color: Color) {
        font.data.setScale(ui * scale)
        font.color = color
        layout.setText(font, text)
        font.draw(batch, layout, (hudViewport.worldWidth - layout.width) / 2f, y)
        font.data.setScale(ui)
    }

    override fun dispose() {
        renderer.dispose()
        batch.dispose()
        hudShapes.dispose()
        font.dispose()
    }

    companion object {
        /** Metres of building visible across the screen at zoom 1. */
        const val VIEW_WIDTH = 20f
        const val VIEW_HEIGHT = 10f
        const val BUTTON_RADIUS = 34f
        const val EDGE_GAP = 0.4f
        const val TAP_REACH = 0.6f
        const val MAX_STEP = 1f / 20f
        const val SLOW_MOTION_SCALE = 0.25f
    }
}
