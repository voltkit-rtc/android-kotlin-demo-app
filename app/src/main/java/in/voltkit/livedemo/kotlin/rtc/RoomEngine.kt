package `in`.voltkit.livedemo.kotlin.rtc

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.voltkit.rtc.ChatMessage
import com.voltkit.rtc.ClaimChanged
import com.voltkit.rtc.ClosedReason
import com.voltkit.rtc.ConnectionState
import com.voltkit.rtc.DeviceKind
import com.voltkit.rtc.HandRaised
import com.voltkit.rtc.JoinResult
import com.voltkit.rtc.LocalMediaState
import com.voltkit.rtc.NetworkQuality
import com.voltkit.rtc.ParticipantInfo
import com.voltkit.rtc.PublishOptions
import com.voltkit.rtc.RemoteTrack
import com.voltkit.rtc.Role
import com.voltkit.rtc.RoleChanged
import com.voltkit.rtc.RpcError
import com.voltkit.rtc.StreamClient
import com.voltkit.rtc.StreamOptions
import com.voltkit.rtc.VideoPublishOptions
import com.voltkit.rtc.VideoQuality
import `in`.voltkit.livedemo.kotlin.data.Api
import `in`.voltkit.livedemo.kotlin.data.ApiError
import `in`.voltkit.livedemo.kotlin.data.Credentials
import `in`.voltkit.livedemo.kotlin.data.GuestPublic
import `in`.voltkit.livedemo.kotlin.data.Room
import `in`.voltkit.livedemo.kotlin.ui.ToastKind
import `in`.voltkit.livedemo.kotlin.ui.toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.webrtc.VideoTrack

class Member(
    val identity: String,
    val name: String,
    val avatar: String?,
    val frame: String,
    val role: Role,
    val handRaised: Boolean,
    val wantedSeat: String?,
    val micLocked: Boolean,
    val camLocked: Boolean,
    val seat: String?,
)

/** A line in the room's chat: `text`, `gift` or `notice`. */
class ChatLine(val id: String, val kind: String, val text: String, val from: String? = null, val name: String? = null, val avatar: String? = null)

private val WELCOME = ChatLine("welcome", "notice", "Welcome. Be kind: harassment, nudity and spam get you removed. Tap anyone to report or block them.")
private const val MAX_LINES = 120

/**
 * The heart of a room: one VoltKit session and all its logic, so screens only draw state and call the methods here. Roles,
 * seats and moderation are enforced by the service; the host's client is the one that says yes to a seat, because only a host
 * may promote. Everything is Compose state, read on the main thread.
 */
class RoomEngine(
    context: Context,
    val room: Room,
    val me: GuestPublic,
    credentials: Credentials,
    private val isBlocked: (String) -> Boolean,
    private val onClosed: (ClosedReason) -> Unit,
    private val onFailed: (Throwable) -> Unit,
) {
    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val tokenRole = credentials.role

    /** Phones stop giving a backgrounded app its camera: it goes off there, and comes back with the app. */
    val client = StreamClient(app, credentials.wsUrl, credentials.token, StreamOptions(pauseCameraInBackground = true))
    val isHost = room.host.id == me.id

    /** Seats besides the host. A video room counts the host's own card in `seats`. */
    val seats = if (room.isVideo) room.seats - 1 else room.seats

    var state by mutableStateOf(ConnectionState.idle); private set
    var claims by mutableStateOf<Claims>(emptyMap()); private set
    var locked by mutableStateOf(emptySet<String>()); private set
    var layout by mutableStateOf(room.layout); private set
    var seatMode by mutableStateOf(room.seatMode); private set
    var roomCoins by mutableIntStateOf(room.giftCoins); private set
    var chatMuted by mutableStateOf(emptySet<String>()); private set
    var invitedTo by mutableStateOf<String?>(null); private set
    var iMutedInChat by mutableStateOf(false); private set
    var walletLow by mutableStateOf(false); private set
    var network by mutableStateOf<NetworkQuality?>(null); private set
    var speakerphone by mutableStateOf(true); private set

    /** Whether there is another camera to flip to. Phones with one camera hide the button. */
    var canFlip by mutableStateOf(true); private set
    var members by mutableStateOf(emptyList<Member>()); private set
    var memberById by mutableStateOf(emptyMap<String, Member>()); private set
    var local by mutableStateOf(LocalMediaState()); private set
    var localVideo by mutableStateOf<VideoTrack?>(null); private set
    var speaking by mutableStateOf(emptySet<String>()); private set
    var held by mutableStateOf(emptySet<String>()); private set
    private var videoById by mutableStateOf(emptyMap<String, RemoteTrack>())
    private var audioById by mutableStateOf(emptyMap<String, RemoteTrack>())

    /** RemoteTrack.paused is kept current by the SDK but is not Compose state: this ticks when it changes. */
    private var pauseTick by mutableIntStateOf(0)

    /** The room's chat, newest first. Kept here, so it survives a chat screen opened over the room. */
    val chat = mutableStateListOf(WELCOME)

    private var participants: List<ParticipantInfo> = emptyList()
    private val greeted = mutableSetOf<String>()
    private var historyLoaded = false
    private val invites = mutableMapOf<String, String>()
    private var disposed = false

    // What this person means to do, kept apart from whether they are publishing. Going on air spans a role change and
    // sometimes a reconnect, in either order, so checkOnAir compares the two.
    private var wantOnAir = isHost
    private var publishingNow = false

    val myRole: Role get() = memberById[me.id]?.role ?: client.joinResult?.role ?: tokenRole
    val mySeat: String? get() = Seats.of(claims, me.id)
    val myHand: Boolean get() = memberById[me.id]?.handRaised ?: false
    val hands: List<Member> get() = members.filter { it.handRaised && it.role == Role.viewer }

    fun micIsOn(identity: String): Boolean {
        pauseTick
        if (identity == me.id) return local.publishing && local.micEnabled
        val track = audioById[identity] ?: return false
        return !track.paused
    }

    /** Someone's camera, while it is on. */
    fun videoOf(identity: String): RemoteTrack? {
        pauseTick
        val track = videoById[identity] ?: return null
        return if (track.paused) null else track
    }

    private fun <T> watch(flow: Flow<T>, block: suspend (T) -> Unit) {
        scope.launch {
            flow.collect {
                try {
                    block(it)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "event handler failed", e)
                }
            }
        }
    }

    init {
        // Collected before connecting: tracks already live arrive during connect().
        watch(client.connectionState) { onState(it) }
        watch(client.onClosed) { reason ->
            stopBackground()
            if (!disposed) onClosed(reason)
        }
        watch(client.onTrack) { t ->
            if (t.isVideo && t.source == "camera") videoById = videoById + (t.identity to t)
            if (t.isAudio && t.source == "mic") audioById = audioById + (t.identity to t)
        }
        watch(client.onTrackEnded) { e ->
            if (videoById[e.identity]?.producerId == e.producerId) videoById = videoById - e.identity
            if (audioById[e.identity]?.producerId == e.producerId) audioById = audioById - e.identity
        }
        watch(client.onTrackMuted) { pauseTick++ }
        watch(client.localMedia) { local = it }
        watch(client.localVideoTrack) { localVideo = it }
        watch(client.participants) {
            participants = it
            rebuildMembers()
        }
        watch(client.onRoleChanged) { onRole(it) }
        watch(client.onHandRaised) { onHand(it) }
        watch(client.onClaimChanged) { onClaim(it) }
        watch(client.onRoomMetadataChanged) { applySettings(it.metadata) }
        watch(client.onChatMuted) { e ->
            if (e.identity == me.id) {
                iMutedInChat = e.muted
                toast(if (e.muted) "${whoIs(e.by)} muted you in this chat" else "You can chat again")
            }
            chatMuted = if (e.muted) chatMuted + e.identity else chatMuted - e.identity
        }
        watch(client.onForceMuted) { e ->
            val what = when (e.kind) {
                "audio" -> "microphone"
                "video" -> "camera"
                else -> "camera and microphone"
            }
            toast(if (e.paused) "${whoIs(e.by)} switched off your $what" else "You can switch your $what back on")
        }
        watch(client.onMessage) { onMessage(it) }
        watch(client.onMessageDeleted) { e -> chat.removeAll { it.id == e.id } }
        // A name comes with the join. A client that sets it afterwards sends it just after, as an update.
        watch(client.onParticipantJoined) { greet(it.identity, it.metadata) }
        watch(client.onParticipantUpdated) { greet(it.identity, it.metadata) }
        watch(client.onWalletLow) { walletLow = !it.cleared }
        watch(client.onTokenExpiring) { refreshToken() }
        watch(client.onNetworkQuality) { network = it }
        watch(client.onActiveSpeakers) { speaking = it.toSet() }
        watch(client.onStreamFallback) { f -> held = if (f.fallback) held + f.producerId else held - f.producerId }
        // Seats and settings changed while the socket was away come with the rejoin.
        watch(client.onReconnected) { seed(it) }
    }

    fun start() {
        scope.launch {
            val join = try {
                client.connect(mapOf("name" to me.name, "avatar" to me.avatar, "frame" to me.frame))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (!disposed) onFailed(e)
                return@launch
            }
            seed(join)
            startBackground()
            client.setSpeakerphoneOn(speakerphone)
            loadHistory()
            checkOnAir()
        }
    }

    private fun startBackground() = CallService.start(app, room.title, "Tap to return to the room", "Leave") { client.close() }

    private fun stopBackground() = CallService.stop(app)

    // Seats and room settings: read from the join result, then followed through events.
    private fun seed(join: JoinResult) {
        claims = join.claims.associate { it.key to SeatClaim(it.identity, it.state) }
        locked = join.blockedSlots.toSet()
        applySettings(join.metadata)
        // Whoever is already here has already arrived.
        greeted += join.participants.map { it.identity }
        rebuildMembers()
    }

    private fun applySettings(meta: Map<String, Any?>?) {
        (meta?.get("layout") as? String)?.let { layout = it }
        (meta?.get("seatMode") as? String)?.let { seatMode = it }
    }

    private fun rebuildMembers() {
        val hostId = room.host.id
        members = participants.map { p ->
            val d = p.metadata ?: emptyMap()
            val isRoomHost = p.identity == hostId
            Member(
                identity = p.identity,
                name = d["name"] as? String ?: if (isRoomHost) room.host.name else "Guest",
                avatar = d["avatar"] as? String ?: if (isRoomHost) room.host.avatar else null,
                frame = d["frame"] as? String ?: if (isRoomHost) room.host.frame else "none",
                role = p.role,
                handRaised = p.handRaised,
                wantedSeat = p.handMetadata?.get("slot") as? String,
                micLocked = p.muted,
                camLocked = p.videoBlocked,
                seat = Seats.of(claims, p.identity),
            )
        }
        memberById = members.associateBy { it.identity }
    }

    private fun onState(next: ConnectionState) {
        state = next
        if (next == ConnectionState.connected) {
            loadHistory()
            checkOnAir()
        }
    }

    private suspend fun refreshToken() {
        try {
            // A new token for the same session goes to the live connection, with no reconnect.
            client.updateToken(Api.refreshRoomToken(room.id).token)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The session keeps its token: only a reconnect after it has expired would need a new one.
        }
    }

    private fun checkOnAir() {
        if (state == ConnectionState.connected && wantOnAir && Rules.canPublish(myRole) && !local.publishing && !publishingNow) goOnAir()
    }

    private fun goOnAir() {
        publishingNow = true
        scope.launch {
            try {
                // With no quality, publish() sends the highest the project allows: pick one on purpose.
                client.publish(
                    if (room.isVideo) {
                        PublishOptions(encoding = VideoPublishOptions(quality = if (room.seats > 6) VideoQuality.p480 else VideoQuality.p720))
                    } else {
                        PublishOptions(video = false)
                    },
                )
                // The service takes the microphone type now the permission is there.
                startBackground()
                if (room.isVideo) canFlip = cameraCount() != 1
            } catch (e: RpcError) {
                when (e.code) {
                    // Cut off by the move to the publishing server. A publish that throws is over, so ask again once back.
                    "disconnected", "not-connected" -> scope.launch {
                        delay(500)
                        checkOnAir()
                    }
                    "unpublished", "closed" -> Unit
                    else -> publishFailed(e)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                publishFailed(e)
            } finally {
                publishingNow = false
            }
        }
    }

    private suspend fun publishFailed(e: Throwable) {
        wantOnAir = false
        val problem = Problems.media(e)
        toast(problem.text, ToastKind.Error)
        if (problem.openSettings) PermissionAsker.openSettings()
        Seats.of(claims, me.id)?.let { runCatching { client.release(it) } }
    }

    private fun onRole(e: RoleChanged) {
        if (e.identity != me.id) return
        if (Rules.canPublish(e.role)) {
            wantOnAir = true
            toast("You are on stage", ToastKind.Success)
            checkOnAir()
        } else {
            wantOnAir = false
            toast(if (e.role == Role.moderator) "You are now a moderator" else "You are back in the audience")
            // A demotion leaves the seat claimed on the service, and an empty card behind it.
            Seats.of(claims, me.id)?.let { seat -> scope.launch { runCatching { client.release(seat) } } }
        }
    }

    /** The host's side of a seat: claim first, so a seat that has gone means nothing else happened. */
    private suspend fun seatGuest(identity: String, wanted: Any?) {
        val seat = Seats.pick(wanted, seats, claims, locked)
        if (seat == null) {
            runCatching { client.lowerHand(identity) }
            toast("All seats are taken")
            return
        }
        try {
            client.claim(seat, identity)
            client.promote(identity, Role.cohost)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            runCatching { client.release(seat) }
            runCatching { client.lowerHand(identity) }
            toast(Problems.action(e), ToastKind.Error)
        }
    }

    private fun onHand(e: HandRaised) {
        if (e.identity == me.id) {
            if (!e.raised && e.wasDeclined) toast("The host declined your request")
            return
        }
        if (!e.raised || !isHost) return
        val invitedSeat = invites[e.identity]
        if (invitedSeat != null || seatMode == "open") {
            invites.remove(e.identity)
            scope.launch { seatGuest(e.identity, invitedSeat ?: e.slot) }
            return
        }
        toast("${memberById[e.identity]?.name ?: "Someone"} is asking for a seat")
    }

    private fun onClaim(e: ClaimChanged) {
        val holder = claims[e.key]?.identity
        claims = Seats.apply(claims, e.key, e.identity, e.state)
        if (e.isBlocked) locked = locked + e.key
        if (e.reason == "unblocked") locked = locked - e.key
        // Someone gave up their seat: back to the audience, which only the host can do for them.
        if (isHost && e.isFree && e.reason == "released" && holder != null && memberById[holder]?.role == Role.cohost) {
            scope.launch { runCatching { client.promote(holder, Role.viewer) } }
        }
        rebuildMembers()
    }

    private fun whoIs(identity: String) = if (identity == room.host.id) "The host" else "A moderator"

    private fun toLine(m: ChatMessage): ChatLine? {
        val meta = m.metadata ?: emptyMap()
        val text = m.text
        if (m.type == "text" && !text.isNullOrBlank()) {
            return ChatLine(m.id, "text", text, m.from, meta["name"] as? String ?: "Guest", meta["avatar"] as? String)
        }
        if (m.type == "gift") {
            val count = (meta["count"] as? Number)?.toInt() ?: 1
            val times = if (count > 1) " ×$count" else ""
            return ChatLine(m.id, "gift", "${meta["fromName"] ?: "Someone"} sent ${meta["giftName"] ?: "a gift"}$times to ${meta["toName"] ?: "the host"}", m.from)
        }
        return null
    }

    private fun push(line: ChatLine) {
        if (chat.any { it.id == line.id }) return
        chat.add(0, line)
        while (chat.size > MAX_LINES) chat.removeAt(chat.lastIndex)
    }

    private fun greet(identity: String, metadata: Map<String, Any?>?) {
        val name = metadata?.get("name") as? String ?: return
        if (identity == me.id || !greeted.add(identity)) return
        push(ChatLine("joined-$identity-${System.currentTimeMillis()}", "notice", "$name joined"))
    }

    private fun loadHistory() {
        if (historyLoaded) return
        historyLoaded = true
        scope.launch {
            val history = try {
                client.getMessages(30, null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return@launch // the chat works without its history
            }
            val older = history.mapNotNull { toLine(it) }.filter { it.from == null || !isBlocked(it.from) }.reversed()
            chat.removeAll { it.id == WELCOME.id }
            chat.addAll(older.filter { o -> chat.none { it.id == o.id } })
            chat.add(WELCOME)
            while (chat.size > MAX_LINES) chat.removeAt(chat.lastIndex)
        }
    }

    private fun onMessage(m: ChatMessage) {
        val total = m.metadata?.get("roomCoins")
        if (m.type == "gift" && total is Number) roomCoins = total.toInt()
        if (m.type == "invite" && m.to == me.id) invitedTo = m.metadata?.get("slot") as? String ?: "any"
        if (isBlocked(m.from)) return
        toLine(m)?.let { push(it) }
    }

    private fun run(done: String? = null, block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
                if (done != null) toast(done, ToastKind.Success)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiError) {
                toast(e.message, ToastKind.Error)
            } catch (e: Exception) {
                Log.w(TAG, "action failed", e)
                toast(Problems.action(e), ToastKind.Error)
            }
        }
    }

    // --- actions ----------------------------------------------------------------------------------------------------

    fun toggleMic() {
        if (!local.publishing) {
            wantOnAir = true
            if (!publishingNow) goOnAir()
            return
        }
        run { client.setMicEnabled(!local.micEnabled) }
    }

    fun toggleCam() = run { client.setCamEnabled(!local.camEnabled) }
    fun flipCamera() = run { client.switchCamera() }

    fun requestSeat(slot: String? = null) {
        scope.launch {
            if (!askMediaConsent(room.type)) return@launch
            run { client.raiseHand(buildMap { put("requestRole", "cohost"); if (slot != null) put("slot", slot) }) }
        }
    }

    fun cancelRequest() = run { client.lowerHand(null) }

    fun leaveSeat() {
        wantOnAir = false
        val seat = mySeat
        run {
            client.unpublish()
            if (seat != null) client.release(seat)
        }
    }

    fun acceptHand(identity: String, wanted: String?) {
        scope.launch { seatGuest(identity, wanted) }
    }

    fun declineHand(identity: String) = run { client.lowerHand(identity) }

    fun inviteToSeat(identity: String, slot: String? = null) {
        val seat = slot ?: Seats.firstFree(seats, claims, locked) ?: return toast("All seats are taken")
        // Remembered here, so only someone actually invited skips the queue.
        invites[identity] = seat
        run("Invitation sent") { client.sendMessage("", "invite", identity, mapOf("slot" to seat), null, false) }
    }

    fun acceptInvite() {
        val slot = invitedTo.takeIf { it != "any" }
        invitedTo = null
        scope.launch {
            if (!askMediaConsent(room.type)) return@launch
            run { client.raiseHand(buildMap { put("requestRole", "cohost"); if (slot != null) put("slot", slot); put("invited", true) }) }
        }
    }

    fun declineInvite() {
        invitedTo = null
    }

    fun removeFromSeat(identity: String) = run("Moved to the audience") {
        Seats.of(claims, identity)?.let { client.release(it) }
        client.promote(identity, Role.viewer)
    }

    fun lockSeat(key: String) = run { client.blockSlot(key) }
    fun unlockSeat(key: String) = run { client.unblockSlot(key) }

    fun forceMic(identity: String, off: Boolean) = run(if (off) "Microphone off" else "Released") { client.forceMute(identity, "audio", off) }
    fun forceCam(identity: String, off: Boolean) = run(if (off) "Camera off" else "Released") { client.forceMute(identity, "video", off) }

    fun muteAll() = run("Everyone on stage is muted") {
        participants.filter { it.role == Role.cohost && !it.muted }.forEach { client.forceMute(it.identity, "audio", true) }
    }

    fun setChatMute(identity: String, muted: Boolean) = run(if (muted) "Muted in chat" else "Can chat again") { client.muteChat(identity, muted) }
    fun makeModerator(identity: String) = run("Now a moderator") { client.promote(identity, Role.moderator) }
    fun removeModerator(identity: String) = run("No longer a moderator") { client.promote(identity, Role.viewer) }

    fun removeFromRoom(identity: String) = run("Removed from the room") {
        // Kick stops the tokens minted so far. The ban stops the backend minting another, so it goes first.
        Api.banFromRoom(room.id, identity)
        client.kick(identity, "Removed from the room")
    }

    fun setRoomLayout(next: String) = run { client.setRoomMetadata(mapOf("layout" to next), false, null) }
    fun setRoomSeatMode(next: String) = run { client.setRoomMetadata(mapOf("seatMode" to next), false, null) }

    suspend fun endRoom() {
        try {
            Api.endRoom(room.id)
        } catch (e: ApiError) {
            toast(e.message, ToastKind.Error)
        }
    }

    fun sendChat(text: String) = run {
        client.sendMessage(text, null, null, mapOf("name" to me.name, "avatar" to me.avatar, "frame" to me.frame), null, false)
    }

    fun sendHeart() {
        scope.launch { runCatching { client.sendMessage("", "reaction", null, mapOf("emoji" to "heart"), null, true) } }
    }

    fun deleteMessage(id: String) {
        scope.launch {
            try {
                client.deleteMessage(id)
            } catch (e: RpcError) {
                toast("Could not delete that message.", ToastKind.Error)
            }
        }
    }

    fun useSpeakerphone(on: Boolean) {
        speakerphone = on
        client.setSpeakerphoneOn(on)
    }

    private suspend fun cameraCount(): Int = try {
        client.listDevices(DeviceKind.videoinput).size
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        0
    }

    /** Whether there is anywhere else to send the sound. */
    suspend fun hasOtherOutputs(): Boolean = try {
        client.listDevices(DeviceKind.audiooutput).size > 1
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        false
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        scope.cancel()
        stopBackground()
        client.close()
    }

    private companion object {
        const val TAG = "RoomEngine"
    }
}
