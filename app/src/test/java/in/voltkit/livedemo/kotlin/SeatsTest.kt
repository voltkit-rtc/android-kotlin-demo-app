package `in`.voltkit.livedemo.kotlin

import `in`.voltkit.livedemo.kotlin.rtc.SeatClaim
import `in`.voltkit.livedemo.kotlin.rtc.Seats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SeatsTest {
    private val none = emptySet<String>()

    @Test fun numbersFromOne() = assertEquals(listOf("seat-1", "seat-2", "seat-3"), Seats.all(3))

    @Test fun awaySeatCountsAsTaken() {
        val claims = mapOf("seat-1" to SeatClaim("a", "away"))
        assertEquals("seat-2", Seats.firstFree(2, claims, none))
        assertEquals("seat-1", Seats.of(claims, "a"))
    }

    @Test fun skipsLockedSeatsAndRunsOut() {
        val claims = mapOf("seat-1" to SeatClaim("a", "held"))
        assertEquals("seat-3", Seats.firstFree(3, claims, setOf("seat-2")))
        assertNull(Seats.firstFree(1, claims, none))
    }

    @Test fun givesTheSeatAskedForWhenFreeElseTheNext() {
        val claims = mapOf("seat-2" to SeatClaim("a", "held"))
        assertEquals("seat-3", Seats.pick("seat-3", 4, claims, none))
        assertEquals("seat-1", Seats.pick("seat-2", 4, claims, none))
        assertEquals("seat-1", Seats.pick(null, 4, claims, none))
    }

    @Test fun ignoresASeatThatDoesNotExist() {
        assertEquals("seat-1", Seats.pick("seat-99", 2, emptyMap(), none))
        assertEquals("seat-1", Seats.pick(mapOf("evil" to true), 2, emptyMap(), none))
    }

    @Test fun followsTheServiceThroughHeldAwayAndFree() {
        var claims = Seats.apply(emptyMap(), "seat-1", "a", "held")
        assertEquals(SeatClaim("a", "held"), claims["seat-1"])
        claims = Seats.apply(claims, "seat-1", "a", "away")
        assertTrue(claims["seat-1"]!!.away)
        claims = Seats.apply(claims, "seat-1", null, "free")
        assertNull(claims["seat-1"])
    }

    @Test fun doesNotChangeTheMapItWasGiven() {
        val claims = emptyMap<String, SeatClaim>()
        Seats.apply(claims, "seat-1", "a", "held")
        assertTrue(claims.isEmpty())
    }
}
