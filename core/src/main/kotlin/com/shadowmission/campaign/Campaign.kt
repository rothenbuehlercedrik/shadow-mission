package com.shadowmission.campaign

import com.badlogic.gdx.utils.JsonReader

/** One mission in the campaign: its id (used for saved stars) and its level file under assets. */
data class CampaignEntry(val id: String, val file: String, val district: String)

/** The ordered list of missions. Each one unlocks once the one before has at least one star. */
class Campaign(val missions: List<CampaignEntry>) {

    fun isUnlocked(index: Int, progress: ProgressStore): Boolean =
        index == 0 || progress.stars(missions[index - 1].id) > 0

    companion object {
        fun parse(json: String): Campaign = Campaign(
            JsonReader().parse(json).get("missions").map {
                CampaignEntry(it.getString("id"), it.getString("file"), it.getString("district", ""))
            },
        )
    }
}

/** Best star count per mission, kept between sessions. */
interface ProgressStore {
    fun stars(id: String): Int

    /** Saves [stars] for [id] if it beats the best so far. */
    fun record(id: String, stars: Int)
}

class InMemoryProgress : ProgressStore {
    private val best = mutableMapOf<String, Int>()
    override fun stars(id: String): Int = best[id] ?: 0
    override fun record(id: String, stars: Int) {
        if (stars > stars(id)) best[id] = stars
    }
}
