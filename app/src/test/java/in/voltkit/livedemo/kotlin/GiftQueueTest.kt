package `in`.voltkit.livedemo.kotlin

import `in`.voltkit.livedemo.kotlin.gifts.EntranceItem
import `in`.voltkit.livedemo.kotlin.gifts.GiftItem
import `in`.voltkit.livedemo.kotlin.gifts.GiftQueue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GiftQueueTest {
    private fun gift(id: String, tier: String = "basic") = GiftItem(id, "u", "rose", "a", "b", "g", 1, tier)
    private fun entrance(id: String) = EntranceItem(id, "u", "n", "lion")

    @Test fun playsInOrder() {
        val q = GiftQueue().apply { push(gift("1")); push(gift("2")) }
        assertEquals("1", q.next()?.id)
        assertEquals("2", q.next()?.id)
        assertNull(q.next())
    }

    @Test fun ignoresAMessageDeliveredTwice() {
        val q = GiftQueue().apply { push(gift("1")); push(gift("1")) }
        assertEquals(1, q.size)
    }

    @Test fun staysBoundedInABusyRoom() {
        val q = GiftQueue()
        repeat(200) { q.push(gift("$it")) }
        assertEquals(24, q.size)
    }

    @Test fun dropsEntrancesThenCheapGiftsBeforeARoyalOne() {
        val q = GiftQueue().apply { push(gift("royal", "royal")); push(entrance("e")) }
        repeat(24) { q.push(gift("b$it")) }
        val ids = generateSequence { q.next() }.map { it.id }.toList()
        assertTrue("royal" in ids)
        assertFalse("e" in ids)
    }

    @Test fun clears() {
        val q = GiftQueue().apply { push(gift("1")); clear() }
        assertEquals(0, q.size)
        assertNull(q.next())
    }
}
