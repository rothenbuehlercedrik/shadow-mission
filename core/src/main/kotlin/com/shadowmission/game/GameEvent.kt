package com.shadowmission.game

/** Things worth a sound or a flash on screen. The mission collects them; the screen plays and clears them. */
enum class GameEvent {
    FOOTSTEP,
    DOOR_OPENED,
    TAKEDOWN,
    BODY_PICKED_UP,
    BODY_HIDDEN,
    COIN_THROWN,
    COIN_LANDED,
    GUARD_SUSPICIOUS,
    GUARD_ALARMED,
    BODY_FOUND,
    TARGET_DOWN,
    COMPLETE,
    CAUGHT,
}
