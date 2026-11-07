package `in`.voltkit.livedemo.kotlin.gifts

sealed class StageItem(val id: String, val url: String)

class GiftItem(
    id: String,
    url: String,
    val icon: String,
    val fromName: String,
    val toName: String,
    val giftName: String,
    val count: Int,
    val tier: String,
) : StageItem(id, url)

class EntranceItem(id: String, url: String, val name: String, val frame: String) : StageItem(id, url)

/**
 * Waiting gifts, in order. In a busy room the line can outgrow anyone's patience: past the limit the cheapest waiting item goes
 * first, so a royal gift is not dropped to make way for a rose.
 */
class GiftQueue {
    private val items = ArrayDeque<StageItem>()
    val size get() = items.size

    fun push(item: StageItem) {
        if (items.any { it.id == item.id }) return
        items.addLast(item)
        while (items.size > LIMIT) items.removeAt(cheapestIndex())
    }

    fun next(): StageItem? = items.removeFirstOrNull()

    fun clear() = items.clear()

    private fun rank(item: StageItem) = when (item) {
        is EntranceItem -> 0
        is GiftItem -> when (item.tier) {
            "basic" -> 1
            "premium" -> 2
            else -> 3
        }
    }

    private fun cheapestIndex(): Int {
        var best = 0
        for (i in 1 until items.size) if (rank(items[i]) < rank(items[best])) best = i
        return best
    }

    private companion object {
        const val LIMIT = 24
    }
}
