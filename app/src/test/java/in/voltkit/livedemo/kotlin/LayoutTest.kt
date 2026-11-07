package `in`.voltkit.livedemo.kotlin

import `in`.voltkit.livedemo.kotlin.rtc.Layout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutTest {
    private val w = 400f
    private val h = 500f
    private val cases = listOf(4 to "grid", 6 to "grid", 9 to "grid", 4 to "stage", 6 to "stage", 9 to "stage")

    @Test fun aCardForEveryone() = cases.forEach { (seats, layout) -> assertEquals(seats, Layout.video(seats, layout, w, h).size) }

    @Test fun everyCardInsideTheStage() = cases.forEach { (seats, layout) ->
        for (c in Layout.video(seats, layout, w, h)) {
            assertTrue(c.x >= -0.01f && c.y >= -0.01f && c.right <= w + 0.01f && c.bottom <= h + 0.01f)
            assertTrue(c.w > 40f && c.h > 40f)
        }
    }

    @Test fun nothingOverlaps() = cases.forEach { (seats, layout) ->
        val cells = Layout.video(seats, layout, w, h)
        for (i in cells.indices) for (j in i + 1 until cells.size) assertFalse(cells[i].overlaps(cells[j]))
    }

    @Test fun hostHasTheBiggestCardOnStage() {
        val cells = Layout.video(6, "stage", w, h)
        for (c in cells.drop(1)) assertTrue(cells[0].w * cells[0].h > c.w * c.h)
    }

    @Test fun everyoneTheSameInAGrid() = assertEquals(1, Layout.video(6, "grid", w, h).map { "%.2f".format(it.w) }.toSet().size)

    @Test fun audioColumns() = assertEquals(listOf(4, 5, 5), listOf(8, 10, 15).map { Layout.audioColumns(it) })
}
