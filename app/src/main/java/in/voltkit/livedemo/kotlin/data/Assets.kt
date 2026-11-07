package `in`.voltkit.livedemo.kotlin.data

import `in`.voltkit.livedemo.kotlin.R

/** Where everything bundled lives. The files are the React Native app's own. */
object Assets {
    val avatarKeys = (1..16).map { "a%02d".format(it) }
    val themeKeys = listOf("royal", "neon", "galaxy", "city", "sunset", "ocean")

    private val avatars = mapOf(
        "a01" to R.drawable.avatar_a01, "a02" to R.drawable.avatar_a02, "a03" to R.drawable.avatar_a03, "a04" to R.drawable.avatar_a04,
        "a05" to R.drawable.avatar_a05, "a06" to R.drawable.avatar_a06, "a07" to R.drawable.avatar_a07, "a08" to R.drawable.avatar_a08,
        "a09" to R.drawable.avatar_a09, "a10" to R.drawable.avatar_a10, "a11" to R.drawable.avatar_a11, "a12" to R.drawable.avatar_a12,
        "a13" to R.drawable.avatar_a13, "a14" to R.drawable.avatar_a14, "a15" to R.drawable.avatar_a15, "a16" to R.drawable.avatar_a16,
    )
    private val covers = mapOf(
        "royal" to R.drawable.cover_royal, "neon" to R.drawable.cover_neon, "galaxy" to R.drawable.cover_galaxy,
        "city" to R.drawable.cover_city, "sunset" to R.drawable.cover_sunset, "ocean" to R.drawable.cover_ocean,
    )
    private val gifts = mapOf(
        "heart" to R.drawable.gift_heart, "rose" to R.drawable.gift_rose, "ring" to R.drawable.gift_ring, "rocket" to R.drawable.gift_rocket,
        "crown" to R.drawable.gift_crown, "wolf" to R.drawable.gift_wolf, "lion" to R.drawable.gift_lion,
    )

    fun avatar(key: String?): Int = avatars[key] ?: R.drawable.avatar_a01
    fun cover(key: String): Int = covers[key] ?: R.drawable.cover_royal
    fun gift(key: String): Int = gifts[key] ?: R.drawable.gift_heart
    fun frame(frame: String): Int? = when (frame) {
        "lion" -> R.drawable.frame_lion
        "wolf" -> R.drawable.frame_wolf
        else -> null
    }
    fun crest(frame: String): Int? = when (frame) {
        "lion" -> R.drawable.crest_lion
        "wolf" -> R.drawable.crest_wolf
        else -> null
    }
}
