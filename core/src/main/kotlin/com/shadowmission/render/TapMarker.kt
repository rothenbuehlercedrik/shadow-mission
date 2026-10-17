package com.shadowmission.render

/** Brief feedback where the player tapped: a ripple if reachable, a red cross if not. */
class TapMarker(val floor: Int, val x: Float, val reachable: Boolean) {
    var age = 0f

    companion object {
        const val LIFETIME = 0.6f
    }
}
