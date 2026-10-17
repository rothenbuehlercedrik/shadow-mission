package com.shadowmission.desktop

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.shadowmission.ShadowMissionGame
import com.shadowmission.game.Position

/**
 * Desktop build for quick testing without a phone.
 *
 * `--screenshot out.png` saves one frame and quits. Add `--walk-to FLOOR,X` (and optionally `--run`
 * and `--seconds N`) to send the assassin somewhere first.
 */
fun main(args: Array<String>) {
    fun arg(name: String) = args.indexOf(name).takeIf { it >= 0 }?.let { args.getOrNull(it + 1) }

    val config = Lwjgl3ApplicationConfiguration().apply {
        setTitle("Shadow Mission")
        setWindowedMode(1280, 720)
        useVsync(true)
        setForegroundFPS(60)
    }
    val game = arg("--screenshot")?.let { path ->
        val walkTo = arg("--walk-to")?.split(",")?.let { (floor, x) -> Position(floor.toInt(), x.toFloat()) }
        ScreenshotGame(path, walkTo, run = "--run" in args, seconds = arg("--seconds")?.toFloat() ?: 0f)
    } ?: ShadowMissionGame()
    Lwjgl3Application(game, config)
}
