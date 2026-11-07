package `in`.voltkit.livedemo.kotlin.rtc

import kotlin.math.ceil
import kotlin.math.min

/** A card's place on the stage, in dp. */
data class Cell(val x: Float, val y: Float, val w: Float, val h: Float) {
    val right get() = x + w
    val bottom get() = y + h
    fun overlaps(o: Cell) = x < o.right && o.x < right && y < o.bottom && o.y < bottom
}

object Layout {
    private const val GAP = 6f

    private fun grid(count: Int, cols: Int, w: Float, h: Float): List<Cell> {
        val rows = ceil(count / cols.toFloat()).toInt()
        val cellW = (w - GAP * (cols - 1)) / cols
        val cellH = (h - GAP * (rows - 1)) / rows
        return List(count) { i -> Cell((i % cols) * (cellW + GAP), (i / cols) * (cellH + GAP), cellW, cellH) }
    }

    /**
     * Places the video cards: index 0 is the host, the rest are seats in order. `grid` gives everyone the same card. `stage`
     * gives the host the top and the seats a strip below.
     */
    fun video(seats: Int, layout: String, width: Float, height: Float): List<Cell> {
        if (layout == "grid") return grid(seats, if (seats <= 4) 2 else 3, width, height)
        val others = seats - 1
        val cols = if (others <= 3) others else if (others == 5) 3 else 4
        val rows = ceil(others / cols.toFloat()).toInt()
        val stripH = min(height * 0.42f, rows * min((width / cols) * 1.05f, 150f))
        val hostH = height - stripH - GAP
        // A row that is not full is stretched, so nothing is left hanging.
        val strip = mutableListOf<Cell>()
        val rowH = (stripH - GAP * (rows - 1)) / rows
        var placed = 0
        for (r in 0 until rows) {
            val inRow = min(others - placed, if (r == rows - 1) others - placed else cols)
            val cellW = (width - GAP * (inRow - 1)) / inRow
            for (c in 0 until inRow) {
                strip += Cell(c * (cellW + GAP), hostH + GAP + r * (rowH + GAP), cellW, rowH)
                placed++
            }
        }
        return listOf(Cell(0f, 0f, width, hostH)) + strip
    }

    fun audioColumns(seats: Int) = if (seats == 8) 4 else 5
}
