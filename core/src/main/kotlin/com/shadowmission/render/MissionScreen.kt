package com.shadowmission.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.ScreenAdapter
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
import com.shadowmission.game.Position
import com.shadowmission.level.Level

/** Plays one mission. Milestone 2: move the assassin around the building; guards don't react yet. */
class MissionScreen(level: Level) : ScreenAdapter() {

    val mission = Mission(level)
    private val camera = OrthographicCamera()
    private val viewport = ExtendViewport(VIEW_WIDTH, VIEW_HEIGHT, camera)
    private val cameraController = CameraController(camera, level)
    private val renderer = LevelRenderer(mission)

    private val hudViewport = ScreenViewport()
    private val batch = SpriteBatch()
    private val hudShapes = ShapeRenderer()
    private val font = BitmapFont()
    private val layout = GlyphLayout()

    private val ui: Float get() = maxOf(1f, Gdx.graphics.density)
    private val crouchCentre: Vector2 get() = Vector2(24f * ui + BUTTON_RADIUS * ui, 24f * ui + BUTTON_RADIUS * ui)

    override fun show() {
        cameraController.onTap = ::handleTap
        Gdx.input.inputProcessor = InputMultiplexer(GestureDetector(cameraController), cameraController.scrollInput)
        viewport.update(Gdx.graphics.width, Gdx.graphics.height)
        val a = mission.assassin
        cameraController.centreOn(a.x, a.y + mission.level.floorHeight / 2f)
    }

    private fun handleTap(screenX: Float, screenY: Float, count: Int) {
        val hudY = Gdx.graphics.height - screenY
        if (crouchCentre.dst(screenX, hudY) <= BUTTON_RADIUS * ui * 1.2f) {
            mission.assassin.crouching = !mission.assassin.crouching
            return
        }

        val world = viewport.unproject(Vector2(screenX, screenY))
        val level = mission.level
        val floor = MathUtils.clamp((world.y / level.floorHeight).toInt(), 0, level.floors.size - 1)
        val f = level.floors[floor]
        val x = MathUtils.clamp(world.x, f.left + EDGE_GAP, f.right - EDGE_GAP)
        val reachable = mission.moveAssassin(Position(floor, x), run = count >= 2)
        renderer.tapMarker = TapMarker(floor, x, reachable)
        cameraController.following = true
    }

    override fun resize(width: Int, height: Int) {
        viewport.update(width, height)
        cameraController.clamp()
        hudViewport.update(width, height, true)
        // Keep HUD text a readable size on high-density phone screens.
        font.data.setScale(ui)
    }

    override fun render(delta: Float) {
        val dt = minOf(delta, MAX_STEP)
        mission.update(dt)
        renderer.tapMarker?.let { it.age += dt }
        val a = mission.assassin
        cameraController.follow(a.x, a.y + mission.level.floorHeight / 2f, dt)

        ScreenUtils.clear(Palette.sky)
        viewport.apply()
        renderer.render(camera.combined)
        drawHud()
    }

    private fun drawHud() {
        hudViewport.apply()
        val crouching = mission.assassin.crouching
        val centre = crouchCentre
        val radius = BUTTON_RADIUS * ui

        Gdx.gl.glEnable(GL20.GL_BLEND)
        hudShapes.projectionMatrix = hudViewport.camera.combined
        hudShapes.begin(ShapeRenderer.ShapeType.Filled)
        hudShapes.color = if (crouching) Palette.buttonActive else Palette.button
        hudShapes.circle(centre.x, centre.y, radius, 32)
        hudShapes.end()
        Gdx.gl.glDisable(GL20.GL_BLEND)

        batch.projectionMatrix = hudViewport.camera.combined
        batch.begin()
        font.color = Palette.hudText
        val pad = 12f * ui
        font.draw(batch, mission.level.name, pad, hudViewport.worldHeight - pad)
        font.draw(batch, "Tap to walk, double tap to run. Drag to look, pinch to zoom.", pad, hudViewport.worldHeight - pad - font.lineHeight * 1.3f)
        layout.setText(font, if (crouching) "Stand" else "Crouch")
        font.draw(batch, layout, centre.x - layout.width / 2f, centre.y + layout.height / 2f)
        batch.end()
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
        const val BUTTON_RADIUS = 36f
        const val EDGE_GAP = 0.4f
        const val MAX_STEP = 1f / 20f
    }
}
