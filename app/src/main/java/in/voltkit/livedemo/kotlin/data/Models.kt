package `in`.voltkit.livedemo.kotlin.data

import com.voltkit.rtc.Role
import org.json.JSONObject

fun roleFrom(value: String?): Role = Role.entries.firstOrNull { it.name == value } ?: Role.viewer

private fun JSONObject.str(key: String, fallback: String = ""): String = if (isNull(key)) fallback else optString(key, fallback)
private fun JSONObject.strOrNull(key: String): String? = if (!has(key) || isNull(key)) null else optString(key)

open class GuestPublic(val id: String, val name: String, val avatar: String, val frame: String) {
    companion object {
        fun from(j: JSONObject) = GuestPublic(j.str("id"), j.str("name", "Guest"), j.str("avatar", "a01"), j.str("frame", "none"))
    }
}

class Guest(
    id: String,
    name: String,
    avatar: String,
    frame: String,
    val coins: Int,
    val giftsReceived: Int,
    val blocked: List<String>,
    val restricted: Boolean,
) : GuestPublic(id, name, avatar, frame) {
    companion object {
        fun from(j: JSONObject): Guest {
            val blocked = j.optJSONArray("blocked")
            return Guest(
                j.str("id"), j.str("name", "Guest"), j.str("avatar", "a01"), j.str("frame", "none"),
                j.optInt("coins"), j.optInt("giftsReceived"),
                List(blocked?.length() ?: 0) { blocked!!.getString(it) },
                j.optBoolean("restricted"),
            )
        }
    }
}

data class Room(
    val id: String,
    val type: String,
    val title: String,
    val theme: String,
    val seats: Int,
    val seatMode: String,
    val layout: String,
    val host: GuestPublic,
    val viewers: Int,
    val giftCoins: Int,
) {
    val isVideo get() = type == "video"

    companion object {
        fun from(j: JSONObject) = Room(
            j.str("id"), j.str("type", "video"), j.str("title"), j.str("theme", "royal"), j.optInt("seats", 4),
            j.str("seatMode", "request"), j.str("layout", "stage"), GuestPublic.from(j.getJSONObject("host")),
            j.optInt("viewers"), j.optInt("giftCoins"),
        )
    }
}

data class Credentials(val token: String, val wsUrl: String, val role: Role) {
    override fun toString() = "Credentials(wsUrl=$wsUrl, role=$role)" // never the token

    companion object {
        fun from(j: JSONObject) = Credentials(j.str("token"), j.str("wsUrl"), roleFrom(j.strOrNull("role")))
    }
}

data class RoomSession(val room: Room, val credentials: Credentials) {
    companion object {
        fun from(j: JSONObject) = RoomSession(Room.from(j.getJSONObject("room")), Credentials.from(j.getJSONObject("credentials")))
    }
}

data class Gift(val id: String, val name: String, val price: Int, val tier: String, val svgaUrl: String, val icon: String) {
    companion object {
        fun from(j: JSONObject) = Gift(j.str("id"), j.str("name"), j.optInt("price"), j.str("tier", "basic"), j.str("svgaUrl"), j.str("icon", "heart"))
    }
}

data class ConversationLast(val text: String, val at: String)

class ConversationSummary(val id: String, val peer: GuestPublic, val last: ConversationLast?, val unread: Int) {
    companion object {
        fun from(j: JSONObject): ConversationSummary {
            val last = j.optJSONObject("last")
            return ConversationSummary(
                j.str("id"), GuestPublic.from(j.getJSONObject("peer")),
                last?.let { ConversationLast(it.str("text"), it.str("at")) }, j.optInt("unread"),
            )
        }
    }
}

data class DirectMessage(val id: String, val from: String, val text: String, val at: String) {
    companion object {
        fun from(j: JSONObject) = DirectMessage(j.str("id"), j.str("from"), j.str("text"), j.str("at"))
    }
}

data class ChatChannel(val token: String, val wsUrl: String) {
    override fun toString() = "ChatChannel(wsUrl=$wsUrl)"

    companion object {
        fun from(j: JSONObject) = ChatChannel(j.str("token"), j.str("wsUrl"))
    }
}

class MatchFound(val callId: String, val token: String, val wsUrl: String, val peer: GuestPublic) {
    companion object {
        fun from(j: JSONObject) = MatchFound(j.str("callId"), j.str("token"), j.str("wsUrl"), GuestPublic.from(j.getJSONObject("peer")))
    }
}

data class Rates(val audio: String?, val video: String?, val unit: String)

data class Stats(val liveRooms: Int, val online: Int, val waiting: Int)
