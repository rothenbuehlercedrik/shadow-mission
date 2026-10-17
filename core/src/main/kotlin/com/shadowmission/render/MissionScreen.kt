package com.shadowmission.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputMultiplexer
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.OrthographicCamera
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.input.GestureDetector
import com.badlogic.gdx.utils.ScreenUtils
import com.badlogic.gdx.utils.viewport.ExtendViewport
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.shadowmission.level.Level

/** Shows one mission's building. Milestone 1: look around the level; nothing moves yet. */
class MissionScreen(private val level: Level) : ScreenAdapter() {

    private val camera = OrthographicCamera()
    private val viewport = ExtendViewport(VIEW_WIDTH, VIEW_HEIGHT, camera)
    private val cameraController = CameraController(camera, level)
    private val renderer = LevelRenderer(level)

    private val hudViewport = ScreenViewport()
    private val batch = SpriteBatch()
    private val font = BitmapFont()

    override fun show() {
        Gdx.input.inputProcessor = InputMultiplexer(GestureDetector(cameraController), cameraController.scrollInput)
        viewport.update(Gdx.graphics.width, Gdx.graphics.height)
        cameraController.centreOn(level.player.x, level.height / 2f)
    }

    override fun resize(width: Int, height: Int) {
        viewport.update(width, height)
        cameraController.clamp()
        hudViewport.update(width, height, true)
        // Keep HUD text a readable size on high-density phone screens.
        font.data.setScale(maxOf(1f, Gdx.graphics.density))
    }

    override fun render(delta: Float) {
        ScreenUtils.clear(Palette.sky)
        viewport.apply()
        renderer.render(camera.combined)

        hudViewport.apply()
        batch.projectionMatrix = hudViewport.camera.combined
        batch.begin()
        font.color = Palette.hudText
        val pad = 12f * maxOf(1f, Gdx.graphics.density)
        font.draw(batch, level.name, pad, hudViewport.worldHeight - pad)
        font.draw(batch, "Drag to look around, pinch to zoom", pad, hudViewport.worldHeight - pad - font.lineHeight * 1.3f)
        batch.end()
    }

    override fun dispose() {
        renderer.dispose()
        batch.dispose()
        font.dispose()
    }

    companion object {
        /** Metres of building visible across the screen at zoom 1. */
        const val VIEW_WIDTH = 20f
        const val VIEW_HEIGHT = 10f
    }
}
