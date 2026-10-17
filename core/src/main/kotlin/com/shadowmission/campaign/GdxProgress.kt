package com.shadowmission.campaign

import com.badlogic.gdx.Gdx

/** Progress saved with libGDX preferences (Android SharedPreferences, or a file on desktop). */
class GdxProgress : ProgressStore {
    private val prefs = Gdx.app.getPreferences("shadow-mission-progress")

    override fun stars(id: String): Int = prefs.getInteger("stars.$id", 0)

    override fun record(id: String, stars: Int) {
        if (stars <= stars(id)) return
        prefs.putInteger("stars.$id", stars)
        prefs.flush()
    }
}
