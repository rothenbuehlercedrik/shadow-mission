package com.shadowmission.desktop

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration
import com.shadowmission.ShadowMissionGame

/** Desktop build for quick testing without a phone. Pass `--screenshot out.png` to save one frame and quit. */
fun main(args: Array<String>) {
    val screenshot = args.indexOf("--screenshot").takeIf { it >= 0 }?.let { args.getOrNull(it + 1) }
    val config = Lwjgl3ApplicationConfiguration().apply {
        setTitle("Shadow Mission")
        setWindowedMode(1280, 720)
        useVsync(true)
        setForegroundFPS(60)
    }
    Lwjgl3Application(screenshot?.let(::ScreenshotGame) ?: ShadowMissionGame(), config)
}
