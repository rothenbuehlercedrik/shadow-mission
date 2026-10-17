package com.shadowmission.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.InputAdapter
import com.badlogic.gdx.ScreenAdapter
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.GlyphLayout
import com.badlogic.gdx.graphics.g2d.SpriteBatch
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.badlogic.gdx.utils.ScreenUtils
import com.badlogic.gdx.utils.viewport.ScreenViewport
import com.shadowmission.ShadowMissionGame
import com.shadowmission.level.LevelLoader

/** The contract list: pick an unlocked mission, see the stars earned so far. */
class MissionSelectScreen(private val game: ShadowMissionGame) : ScreenAdapter() {

    private val viewport = ScreenViewport()
    private val batch = SpriteBatch()
    private val shapes = ShapeRenderer()
    private val font = BitmapFont().also { f -> f.regions.forEach { it.texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear) } }
    private val layout = GlyphLayout()

    private val names = game.campaign.missions.map {
        LevelLoader.parse(Gdx.files.internal(it.file).readString()).name
    }

    private val ui: Float get() = maxOf(1f, Gdx.graphics.density)

    private fun cards(): List<UiButton> {
        val w = viewport.worldWidth
        val h = viewport.worldHeight
        val cardW = minOf(560f * ui, w - 48f * ui)
        val cardH = 64f * ui
        val gap = 12f * ui
        val top = h - 120f * ui
        return game.campaign.missions.indices.map { i ->
            UiButton("$i", names[i], (w - cardW) / 2f, top - (i + 1) * cardH - i * gap, cardW, cardH)
        }
    }

    private fun soundButton(): UiButton {
        val w = 110f * ui
        val h = 36f * ui
        return UiButton("sound", if (game.soundOn) "Sound: on" else "Sound: off", viewport.worldWidth - w - 16f * ui, viewport.worldHeight - h - 16f * ui, w, h)
    }

    override fun show() {
        Gdx.input.inputProcessor = object : InputAdapter() {
            override fun touchUp(screenX: Int, screenY: Int, pointer: Int, button: Int): Boolean {
                val y = Gdx.graphics.height - screenY.toFloat()
                if (soundButton().contains(screenX.toFloat(), y)) {
                    game.soundOn = !game.soundOn
                    return true
                }
                val index = cards().indexOfFirst { it.contains(screenX.toFloat(), y) }
                if (index >= 0 && game.campaign.isUnlocked(index, game.progress)) game.play(index)
                return true
            }
        }
    }

    override fun resize(width: Int, height: Int) {
        viewport.update(width, height, true)
    }

    override fun render(delta: Float) {
        ScreenUtils.clear(Palette.sky)
        viewport.apply()
        val cards = cards()
        val campaign = game.campaign

        val sound = soundButton()
        shapes.projectionMatrix = viewport.camera.combined
        shapes.begin(ShapeRenderer.ShapeType.Filled)
        Ui.button(shapes, sound)
        cards.forEachIndexed { i, card ->
            val unlocked = campaign.isUnlocked(i, game.progress)
            shapes.color = if (unlocked) Palette.panelLight else Palette.panel
            shapes.rect(card.x, card.y, card.width, card.height)
            shapes.color = if (unlocked) Palette.scarf else Palette.starOff
            shapes.rect(card.x, card.y, 6f * ui, card.height)
            if (unlocked) {
                val stars = game.progress.stars(campaign.missions[i].id)
                Ui.stars(shapes, card.x + card.width - 60f * ui, card.y + card.height / 2f, 9f * ui, stars)
            }
        }
        shapes.end()

        batch.projectionMatrix = viewport.camera.combined
        batch.begin()
        font.data.setScale(ui * 2.2f)
        font.color = Palette.hudText
        layout.setText(font, "SHADOW MISSION")
        font.draw(batch, layout, (viewport.worldWidth - layout.width) / 2f, viewport.worldHeight - 40f * ui)
        font.data.setScale(ui)
        font.color = Palette.mutedText
        layout.setText(font, "Choose a contract")
        font.draw(batch, layout, (viewport.worldWidth - layout.width) / 2f, viewport.worldHeight - 82f * ui)

        font.color = Palette.hudText
        layout.setText(font, sound.label)
        font.draw(batch, layout, sound.x + (sound.width - layout.width) / 2f, sound.y + (sound.height + layout.height) / 2f)

        cards.forEachIndexed { i, card ->
            val unlocked = campaign.isUnlocked(i, game.progress)
            font.color = if (unlocked) Palette.hudText else Palette.mutedText
            font.draw(batch, "${i + 1}.  ${card.label}", card.x + 22f * ui, card.y + card.height - 18f * ui)
            font.color = Palette.mutedText
            val sub = if (unlocked) campaign.missions[i].district else "Locked: earn a star on the mission before"
            font.draw(batch, sub, card.x + 22f * ui, card.y + 24f * ui)
        }
        batch.end()
    }

    override fun dispose() {
        batch.dispose()
        shapes.dispose()
        font.dispose()
    }
}
