package com.shadowmission.desktop

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.PixmapIO
import com.badlogic.gdx.utils.BufferUtils
import com.badlogic.gdx.utils.ScreenUtils
import com.shadowmission.ShadowMissionGame

/** Renders a few frames, saves a PNG of the screen and quits. Used to check visuals without a phone. */
class ScreenshotGame(private val outputPath: String) : ShadowMissionGame() {
    private var frames = 0

    override fun render() {
        super.render()
        if (++frames == 10) {
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
