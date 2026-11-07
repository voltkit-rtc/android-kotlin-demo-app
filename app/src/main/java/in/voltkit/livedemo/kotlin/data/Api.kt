package `in`.voltkit.livedemo.kotlin.data

import `in`.voltkit.livedemo.kotlin.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApiError(override val message: String, val status: Int, val code: String) : Exception(message)

/** The backend's REST API, exactly as the React Native and Flutter apps call it. */
object Api {
    val http: OkHttpClient = OkHttpClient.Builder().callTimeout(12, TimeUnit.SECONDS).build()
    private val json = "application/json".toMediaType()

    @Volatile var sessionToken: String? = null

    private suspend fun request(method: String, path: String, body: JSONObject? = null): JSONObject? = withContext(Dispatchers.IO) {
        val builder = Request.Builder().url("${Config.apiUrl}/v1$path")
        sessionToken?.let { builder.header("Authorization", "Bearer $it") }
        val payload = body?.toString()?.toRequestBody(json) ?: if (method == "GET" || method == "DELETE") null else "".toRequestBody(null)
        builder.method(method, payload)
        val response = try {
            http.newCall(builder.build()).execute()
        } catch (e: IOException) {
            throw ApiError("Could not reach the server. Check your connection.", 0, "network")
        }
        response.use { res ->
            val text = res.body.string()
            val data = try {
                if (text.isEmpty()) null else JSONObject(text)
            } catch (e: JSONException) {
                null // a proxy's error page: handled below by its status
            }
            if (!res.isSuccessful) {
                val error = data?.optJSONObject("error")
                throw ApiError(
                    error?.optString("message")?.takeIf { it.isNotEmpty() } ?: "Something went wrong. Try again.",
                    res.code,
                    error?.optString("code")?.takeIf { it.isNotEmpty() } ?: "unknown",
                )
            }
            data
        }
    }

    private fun <T> JSONArray?.map(block: (JSONObject) -> T): List<T> = if (this == null) emptyList() else List(length()) { block(getJSONObject(it)) }

    suspend fun createGuest(name: String, avatar: String): Pair<String, Guest> {
        val j = request("POST", "/guests", JSONObject().put("name", name).put("avatar", avatar).put("ageConfirmed", true))!!
        return j.getString("token") to Guest.from(j.getJSONObject("guest"))
    }

    suspend fun me() = Guest.from(request("GET", "/me")!!.getJSONObject("guest"))

    suspend fun updateMe(name: String? = null, avatar: String? = null, frame: String? = null) = Guest.from(
        request("PATCH", "/me", JSONObject().apply {
            name?.let { put("name", it) }
            avatar?.let { put("avatar", it) }
            frame?.let { put("frame", it) }
        })!!.getJSONObject("guest"),
    )

    suspend fun topUp() = Guest.from(request("POST", "/me/coins/topup")!!.getJSONObject("guest"))
    suspend fun deleteMe() { request("DELETE", "/me") }

    suspend fun blocks() = request("GET", "/blocks")!!.optJSONArray("guests").map { GuestPublic.from(it) }
    suspend fun block(id: String) { request("POST", "/blocks/$id") }
    suspend fun unblock(id: String) { request("DELETE", "/blocks/$id") }
    suspend fun report(target: String, reason: String, roomId: String? = null) {
        request("POST", "/reports", JSONObject().put("target", target).put("reason", reason).apply { roomId?.let { put("roomId", it) } })
    }

    suspend fun rooms() = request("GET", "/rooms")!!.optJSONArray("rooms").map { Room.from(it) }
    suspend fun createRoom(type: String, title: String, theme: String, seats: Int, seatMode: String, layout: String) = RoomSession.from(
        request(
            "POST", "/rooms",
            JSONObject().put("type", type).put("title", title).put("theme", theme).put("seats", seats).put("seatMode", seatMode).put("layout", layout),
        )!!,
    )
    suspend fun joinRoom(id: String) = RoomSession.from(request("POST", "/rooms/$id/join")!!)
    suspend fun refreshRoomToken(id: String) = Credentials.from(request("POST", "/rooms/$id/token")!!.getJSONObject("credentials"))
    suspend fun endRoom(id: String) { request("POST", "/rooms/$id/end") }
    suspend fun banFromRoom(id: String, identity: String) { request("POST", "/rooms/$id/bans", JSONObject().put("identity", identity)) }

    suspend fun gifts() = request("GET", "/gifts")!!.optJSONArray("gifts").map { Gift.from(it) }
    suspend fun sendGift(roomId: String, giftId: String, to: String, count: Int) = Guest.from(
        request("POST", "/rooms/$roomId/gifts", JSONObject().put("giftId", giftId).put("to", to).put("count", count))!!.getJSONObject("guest"),
    )

    suspend fun loungeToken() = ChatChannel.from(request("POST", "/chat/lounge/token")!!)
    suspend fun conversations() = request("GET", "/conversations")!!.optJSONArray("conversations").map { ConversationSummary.from(it) }
    suspend fun startConversation(peerId: String) =
        ConversationSummary.from(request("POST", "/conversations", JSONObject().put("peerId", peerId))!!.getJSONObject("conversation"))
    suspend fun conversation(id: String): Pair<List<DirectMessage>, ChatChannel> {
        val j = request("GET", "/conversations/$id")!!
        return j.optJSONArray("messages").map { DirectMessage.from(it) } to ChatChannel.from(j.getJSONObject("chat"))
    }
    suspend fun sendDirect(id: String, text: String) =
        DirectMessage.from(request("POST", "/conversations/$id/messages", JSONObject().put("text", text))!!.getJSONObject("message"))
    suspend fun markRead(id: String) { request("POST", "/conversations/$id/read") }

    suspend fun stats(): Stats {
        val j = request("GET", "/stats")!!
        return Stats(j.optInt("liveRooms"), j.optInt("online"), j.optInt("waiting"))
    }

    suspend fun rates(): Rates? {
        val r = request("GET", "/voltkit")?.optJSONObject("rates") ?: return null
        return Rates(r.opt("audio")?.takeIf { it != JSONObject.NULL }?.toString(), r.opt("video")?.takeIf { it != JSONObject.NULL }?.toString(), r.optString("unit"))
    }
}
