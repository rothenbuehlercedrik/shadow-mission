package com.shadowmission

import com.badlogic.gdx.Game
import com.badlogic.gdx.Gdx
import com.shadowmission.level.LevelLoader
import com.shadowmission.level.LevelValidator
import com.shadowmission.render.MissionScreen

open class ShadowMissionGame : Game() {

    override fun create() {
        val level = LevelLoader.parse(Gdx.files.internal("levels/test_warehouse.json").readString())
        val errors = LevelValidator.validate(level)
        check(errors.isEmpty()) { "Level ${level.name} is invalid: $errors" }
        setScreen(MissionScreen(level))
    }

    override fun dispose() {
        screen?.dispose()
    }
}
