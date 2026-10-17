package com.shadowmission.desktop

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PixmapIO
import com.badlogic.gdx.utils.BufferUtils
import com.badlogic.gdx.utils.ScreenUtils
import com.shadowmission.ShadowMissionGame
import com.shadowmission.game.Position
import com.shadowmission.render.MissionScreen

/**
 * Renders a few frames, saves a PNG of the screen and quits. Used to check visuals without a phone.
 * With [mission], that mission opens instead of the menu; [walkTo] sends the assassin somewhere and
 * the mission runs [seconds] of game time first.
 */
class ScreenshotGame(
    private val outputPath: String,
    mission: Int? = null,
    private val walkTo: Position? = null,
    private val run: Boolean = false,
    private val seconds: Float = 0f,
    /** "caught" or "complete" to jump straight to that end card. */
    private val end: String? = null,
) : ShadowMissionGame(mission) {
    private var frames = 0

    override fun render() {
        val missionScreen = screen as? MissionScreen
        if (frames == 0 && missionScreen != null) {
            val mission = missionScreen.mission
            walkTo?.let { mission.moveAssassin(it, run) }
            when (end) {
                "caught" -> mission.caught()
                "complete" -> {
                    mission.target.knockOut()
                    mission.moveAssassin(Position(mission.level.exit.floor, mission.level.exit.x), run = false)
                }
            }
            repeat((seconds * 60).toInt()) { mission.update(1f / 60f) }
        }
        super.render()
        if (++frames == 30) {
            val width = Gdx.graphics.backBufferWidth
            val height = Gdx.graphics.backBufferHeight
            val pixels = ScreenUtils.getFrameBufferPixels(0, 0, width, height, true)
            // Blending leaves partial alpha in the frame buffer; the screen ignores it, so the PNG should too.
            for (i in 3 until pixels.size step 4) pixels[i] = 255.toByte()
            val pixmap = Pixmap(width, height, Pixmap.Format.RGBA8888)
            BufferUtils.copy(pixels, 0, pixmap.pixels, pixels.size)
            PixmapIO.writePNG(Gdx.files.absolute(outputPath), pixmap, -1, false)
            pixmap.dispose()
            Gdx.app.exit()
        }
    }
}
