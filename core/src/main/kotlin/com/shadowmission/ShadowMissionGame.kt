package com.shadowmission

import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.badlogic.gdx.Input
import com.badlogic.gdx.Screen
import com.shadowmission.campaign.Campaign
import com.shadowmission.campaign.GdxProgress
import com.shadowmission.campaign.ProgressStore
import com.shadowmission.level.Level
import com.shadowmission.level.LevelLoader
import com.shadowmission.level.LevelValidator
import com.shadowmission.render.MissionScreen
import com.shadowmission.render.MissionSelectScreen
import com.shadowmission.render.Sounds

/** Owns the campaign and saved progress, and switches between the menu and missions. */
open class ShadowMissionGame(private val startMission: Int? = null) : Game() {

    lateinit var campaign: Campaign
        private set
    lateinit var progress: ProgressStore
        private set
    lateinit var sounds: Sounds
        private set

    /** Sound on or off, remembered between sessions. */
    var soundOn: Boolean
        get() = Gdx.app.getPreferences(SETTINGS).getBoolean("sound", true)
        set(value) {
            Gdx.app.getPreferences(SETTINGS).putBoolean("sound", value).flush()
        }

    override fun create() {
        campaign = Campaign.parse(Gdx.files.internal("levels/campaign.json").readString())
        progress = GdxProgress()
        sounds = Sounds { soundOn }
        // The Android back button returns to the menu instead of closing the app.
        Gdx.input.setCatchKey(Input.Keys.BACK, true)
        if (startMission != null) play(startMission) else showMenu()
    }

    fun loadLevel(index: Int): Level {
        val level = LevelLoader.parse(Gdx.files.internal(campaign.missions[index].file).readString())
        val errors = LevelValidator.validate(level)
        check(errors.isEmpty()) { "Level ${level.name} is invalid: $errors" }
        return level
    }

    fun play(index: Int) = replaceScreen(MissionScreen(this, index, loadLevel(index)))

    fun showMenu() = replaceScreen(MissionSelectScreen(this))

    private fun replaceScreen(next: Screen) {
        val old = screen
        setScreen(next)
        old?.dispose()
    }

    override fun dispose() {
        screen?.dispose()
        sounds.dispose()
    }

    companion object {
        private const val SETTINGS = "shadow-mission-settings"
    }
}
