package com.shadowmission.render

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.audio.Music
import com.badlogic.gdx.audio.Sound
import com.badlogic.gdx.utils.Disposable
import com.shadowmission.game.GameEvent

/** Plays a sound for each game event, plus a quiet ambient loop during missions. */
class Sounds(private val enabled: () -> Boolean) : Disposable {

    private val effects: Map<GameEvent, Pair<Sound, Float>> = mapOf(
        GameEvent.FOOTSTEP to load("footstep", 0.3f),
        GameEvent.DOOR_OPENED to load("door", 0.5f),
        GameEvent.TAKEDOWN to load("takedown", 0.8f),
        GameEvent.BODY_PICKED_UP to load("pickup", 0.6f),
        GameEvent.BODY_HIDDEN to load("hide", 0.7f),
        GameEvent.COIN_THROWN to load("coin_throw", 0.5f),
        GameEvent.COIN_LANDED to load("coin_land", 0.7f),
        GameEvent.GUARD_SUSPICIOUS to load("suspicious", 0.6f),
        GameEvent.GUARD_ALARMED to load("alarmed", 0.7f),
        GameEvent.BODY_FOUND to load("body_found", 0.7f),
        GameEvent.TARGET_DOWN to load("target_down", 0.9f),
        GameEvent.COMPLETE to load("complete", 0.8f),
        GameEvent.CAUGHT to load("caught", 0.8f),
    )

    private val ambient: Music = Gdx.audio.newMusic(Gdx.files.internal("sounds/ambient.wav")).apply {
        isLooping = true
        volume = 0.35f
    }

    private fun load(name: String, volume: Float): Pair<Sound, Float> =
        Gdx.audio.newSound(Gdx.files.internal("sounds/$name.wav")) to volume

    fun play(event: GameEvent) {
        if (!enabled()) return
        effects[event]?.let { (sound, volume) -> sound.play(volume) }
    }

    fun startAmbient() {
        if (enabled() && !ambient.isPlaying) ambient.play()
    }

    fun stopAmbient() = ambient.stop()

    /** Called when the player toggles sound, so the ambient loop follows. */
    fun refresh(inMission: Boolean) {
        if (enabled() && inMission) startAmbient() else stopAmbient()
    }

    override fun dispose() {
        effects.values.forEach { it.first.dispose() }
        ambient.dispose()
    }
}
