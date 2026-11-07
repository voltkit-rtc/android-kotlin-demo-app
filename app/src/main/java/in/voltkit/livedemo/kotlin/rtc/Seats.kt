package `in`.voltkit.livedemo.kotlin.rtc

/**
 * Seats are VoltKit slots: named places one person holds at a time, kept by the service. These are pure functions over the
 * claims, with no SDK calls, so they are easy to test and to reuse.
 */
data class SeatClaim(val identity: String, val state: String) {
    val away get() = state == "away"
}

typealias Claims = Map<String, SeatClaim>

object Seats {
    fun key(n: Int) = "seat-$n"
    fun number(key: String) = key.removePrefix("seat-").toInt()
    fun all(count: Int) = (1..count).map { key(it) }

    /** The seat someone holds. Away counts: their connection dropped and the service keeps it for them. */
    fun of(claims: Claims, identity: String): String? = claims.entries.firstOrNull { it.value.identity == identity }?.key

    fun firstFree(count: Int, claims: Claims, locked: Set<String>): String? = all(count).firstOrNull { it !in claims && it !in locked }

    /** The seat to give someone: the one they asked for if it is still free, else the next free one. */
    fun pick(wanted: Any?, count: Int, claims: Claims, locked: Set<String>): String? =
        if (wanted is String && wanted in all(count) && wanted !in claims && wanted !in locked) wanted else firstFree(count, claims, locked)

    fun apply(claims: Claims, key: String, identity: String?, state: String): Claims =
        if ((state == "held" || state == "away") && identity != null) claims + (key to SeatClaim(identity, state)) else claims - key
}
