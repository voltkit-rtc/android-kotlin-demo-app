package `in`.voltkit.livedemo.kotlin.screens.room

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.voltkit.rtc.NetworkQualityLevel
import com.voltkit.rtc.Role
import `in`.voltkit.livedemo.kotlin.Voltkit
import `in`.voltkit.livedemo.kotlin.data.Api
import `in`.voltkit.livedemo.kotlin.data.ApiError
import `in`.voltkit.livedemo.kotlin.data.Session
import `in`.voltkit.livedemo.kotlin.rtc.MemberAction
import `in`.voltkit.livedemo.kotlin.rtc.MemberState
import `in`.voltkit.livedemo.kotlin.rtc.RoomEngine
import `in`.voltkit.livedemo.kotlin.rtc.Rules
import `in`.voltkit.livedemo.kotlin.rtc.SeatAction
import `in`.voltkit.livedemo.kotlin.rtc.Seats
import `in`.voltkit.livedemo.kotlin.ui.Avatar
import `in`.voltkit.livedemo.kotlin.ui.C
import `in`.voltkit.livedemo.kotlin.ui.G
import `in`.voltkit.livedemo.kotlin.ui.GhostButton
import `in`.voltkit.livedemo.kotlin.ui.GradientButton
import `in`.voltkit.livedemo.kotlin.ui.Ion
import `in`.voltkit.livedemo.kotlin.ui.R2
import `in`.voltkit.livedemo.kotlin.ui.RoleBadge
import `in`.voltkit.livedemo.kotlin.ui.SheetHost
import `in`.voltkit.livedemo.kotlin.ui.SheetRow
import `in`.voltkit.livedemo.kotlin.ui.T
import `in`.voltkit.livedemo.kotlin.ui.ToastKind
import `in`.voltkit.livedemo.kotlin.ui.V
import `in`.voltkit.livedemo.kotlin.ui.tap
import `in`.voltkit.livedemo.kotlin.ui.toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

/** For sheet actions that outlive the sheet: a call started from a sheet finishes after it closes. */
private val sheetScope: CoroutineScope = MainScope()

private val REASONS = listOf(
    "harassment" to "Harassment or hate",
    "nudity" to "Nudity or sexual content",
    "violence" to "Violence or threats",
    "underage" to "Appears to be under 18",
    "spam" to "Spam or scam",
    "other" to "Something else",
)

fun showReportSheet(id: String, name: String, roomId: String? = null) = SheetHost.show("report-sheet", "Report $name") {
    Column {
        for ((reason, label) in REASONS) {
            SheetRow(Ion.flagOutline, label, {
                close()
                sheetScope.launch {
                    try {
                        Api.report(id, reason, roomId)
                        toast("Thanks. We have your report.", ToastKind.Success)
                    } catch (e: ApiError) {
                        toast(e.message, ToastKind.Error)
                    }
                }
            }, id = "report-$reason")
        }
    }
}

private val ACTION_LOOK = mapOf(
    MemberAction.InviteSeat to Triple(Ion.personAddOutline, "invite-seat", "Invite to a seat"),
    MemberAction.RemoveSeat to Triple(Ion.arrowDownCircleOutline, "remove-seat", "Move to the audience"),
    MemberAction.ForceMuteMic to Triple(Ion.micOffOutline, "force-mute-mic", "Switch off their microphone"),
    MemberAction.ReleaseMic to Triple(Ion.micOutline, "release-mic", "Release their microphone"),
    MemberAction.ForceMuteCam to Triple(Ion.videocamOffOutline, "force-mute-cam", "Switch off their camera"),
    MemberAction.ReleaseCam to Triple(Ion.videocamOutline, "release-cam", "Release their camera"),
    MemberAction.MuteChat to Triple(Ion.chatboxEllipsesOutline, "mute-chat", "Mute in chat"),
    MemberAction.UnmuteChat to Triple(Ion.chatboxOutline, "unmute-chat", "Allow in chat"),
    MemberAction.MakeModerator to Triple(Ion.shieldCheckmarkOutline, "make-moderator", "Make moderator"),
    MemberAction.RemoveModerator to Triple(Ion.shieldOutline, "remove-moderator", "Remove as moderator"),
    MemberAction.DeclineHand to Triple(Ion.handLeftOutline, "decline-hand", "Decline their request"),
    MemberAction.RemoveFromRoom to Triple(Ion.exitOutline, "remove-from-room", "Remove from the room"),
)

private fun act(engine: RoomEngine, action: MemberAction, id: String) {
    when (action) {
        MemberAction.InviteSeat -> engine.inviteToSeat(id)
        MemberAction.RemoveSeat -> engine.removeFromSeat(id)
        MemberAction.ForceMuteMic -> engine.forceMic(id, true)
        MemberAction.ReleaseMic -> engine.forceMic(id, false)
        MemberAction.ForceMuteCam -> engine.forceCam(id, true)
        MemberAction.ReleaseCam -> engine.forceCam(id, false)
        MemberAction.MuteChat -> engine.setChatMute(id, true)
        MemberAction.UnmuteChat -> engine.setChatMute(id, false)
        MemberAction.MakeModerator -> engine.makeModerator(id)
        MemberAction.RemoveModerator -> engine.removeModerator(id)
        MemberAction.DeclineHand -> engine.declineHand(id)
        MemberAction.RemoveFromRoom -> engine.removeFromRoom(id)
    }
}

fun showMemberSheet(
    engine: RoomEngine,
    identity: String,
    messageId: String? = null,
    onGift: (String) -> Unit,
    onMessage: (String) -> Unit,
) = SheetHost.show("member-sheet") {
    val member = engine.memberById[identity]
    val name = member?.name ?: "Guest"
    val mine = identity == engine.me.id
    val blocked = Session.guestOrNull?.blocked?.contains(identity) == true
    val myRole = engine.myRole
    val list = if (member == null) emptyList() else Rules.memberActions(
        myRole,
        MemberState(member.role, mine, member.seat != null, member.handRaised, member.micLocked, member.camLocked, identity in engine.chatMuted),
        engine.room.isVideo,
    )
    Column {
        Row(Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(member?.avatar, member?.frame ?: "none", 52.dp)
            Spacer(Modifier.width(22.dp))
            Column(Modifier.weight(1f)) {
                T("$name${if (mine) " (you)" else ""}", V.Heading, lines = 1)
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (member != null) RoleBadge(member.role.name)
                    member?.seat?.let {
                        Spacer(Modifier.width(6.dp))
                        T("Seat ${Seats.number(it)}", V.Tiny, C.textDim)
                    }
                }
            }
        }
        Column(Modifier.verticalScroll(rememberScrollState())) {
            for (a in list) {
                val (icon, id, label) = ACTION_LOOK.getValue(a)
                SheetRow(icon, label, { close(); act(engine, a, identity) }, danger = a == MemberAction.RemoveFromRoom, id = "action-$id")
            }
            if (messageId != null && (Rules.canModerate(myRole) || mine)) {
                SheetRow(Ion.trashOutline, "Delete this message", { close(); engine.deleteMessage(messageId) }, id = "action-delete-message")
            }
            SheetRow(Ion.giftOutline, if (mine) "Send yourself a gift" else "Send a gift", { close(); onGift(identity) }, gold = true, id = "action-gift")
            if (!mine) {
                SheetRow(Ion.chatbubbleEllipsesOutline, "Send a message", { close(); onMessage(identity) }, id = "action-message")
                SheetRow(Ion.flagOutline, "Report", { close(); showReportSheet(identity, name, engine.room.id) }, id = "action-report")
                SheetRow(Ion.banOutline, if (blocked) "Unblock" else "Block", {
                    close()
                    sheetScope.launch {
                        try {
                            if (blocked) Api.unblock(identity) else Api.block(identity)
                            Session.refresh()
                            toast(if (blocked) "Unblocked" else "$name is blocked", ToastKind.Success)
                        } catch (e: ApiError) {
                            toast(e.message, ToastKind.Error)
                        }
                    }
                }, danger = !blocked, id = "action-block")
            }
        }
    }
}

fun showSeatSheet(engine: RoomEngine, seat: String) = SheetHost.show("seat-sheet", "Seat No.${Seats.number(seat)}") {
    val claim = engine.claims[seat]
    val locked = seat in engine.locked
    val list = Rules.seatActions(engine.myRole, claim == null, locked, claim?.identity == engine.me.id, engine.mySeat != null)
    val open = engine.seatMode == "open"
    Column {
        if (SeatAction.Request in list) {
            SheetRow(
                Ion.handLeftOutline,
                if (open) "Take this seat" else "Ask for this seat",
                { close(); engine.requestSeat(seat) },
                hint = if (open) "You go on stage straight away" else "The host decides",
                id = "seat-request",
            )
        }
        if (SeatAction.Leave in list) SheetRow(Ion.exitOutline, "Leave this seat", { close(); engine.leaveSeat() }, id = "seat-leave")
        if (SeatAction.Lock in list) SheetRow(Ion.lockClosedOutline, "Lock this seat", { close(); engine.lockSeat(seat) }, id = "seat-lock")
        if (SeatAction.Unlock in list) SheetRow(Ion.lockOpenOutline, "Unlock this seat", { close(); engine.unlockSeat(seat) }, id = "seat-unlock")
        if (SeatAction.Invite in list) {
            T("To fill this seat, tap someone in the audience list and invite them.", V.Small, C.textFaint, Modifier.padding(vertical = 6.dp))
        }
        if (list.isEmpty()) {
            T(if (locked) "The host has locked this seat." else "This seat is not available.", color = C.textDim, modifier = Modifier.padding(vertical = 6.dp))
        }
    }
}

private val ROLE_ORDER = mapOf(Role.host to 0, Role.moderator to 1, Role.cohost to 2, Role.viewer to 3)

fun showPeopleSheet(engine: RoomEngine, onMember: (String) -> Unit) = SheetHost.show("people-sheet") {
    val members = engine.members
    val hands = engine.hands
    val canAccept = engine.myRole == Role.host
    val canDecline = Rules.canModerate(engine.myRole)
    Column {
        T("In the room (${members.size})", V.Heading)
        Spacer(Modifier.height(12.dp))
        Column(Modifier.verticalScroll(rememberScrollState())) {
            if (hands.isNotEmpty()) {
                T("ASKING FOR A SEAT", V.Tiny, C.textFaint, Modifier.padding(bottom = 4.dp), spacing = 1f)
                for (m in hands) {
                    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(m.avatar, size = 38.dp)
                        Spacer(Modifier.width(10.dp))
                        T(m.name, V.Medium, lines = 1, modifier = Modifier.weight(1f))
                        if (canDecline) {
                            Spacer(Modifier.width(10.dp))
                            Box(
                                Modifier.tap({ engine.declineHand(m.identity) }, "Decline ${m.name}", "decline-${m.identity}")
                                    .background(C.glassStrong, RoundedCornerShape(R2.pill)).padding(horizontal = 12.dp, vertical = 7.dp),
                            ) { T("Decline", V.Tiny) }
                        }
                        if (canAccept) {
                            Spacer(Modifier.width(10.dp))
                            Box(
                                Modifier.tap({ engine.acceptHand(m.identity, m.wantedSeat) }, "Accept ${m.name}", "accept-${m.identity}")
                                    .background(C.gold, RoundedCornerShape(R2.pill)).padding(horizontal = 12.dp, vertical = 7.dp),
                            ) { T("Accept", V.Tiny, C.ink) }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            for (m in members.sortedBy { ROLE_ORDER[it.role] ?: 3 }) {
                Row(
                    Modifier.fillMaxWidth().tap({ close(); onMember(m.identity) }, m.name, "person-${m.identity}").padding(vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(m.avatar, size = 38.dp)
                    Spacer(Modifier.width(10.dp))
                    T(m.name, V.Medium, lines = 1, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(10.dp))
                    RoleBadge(m.role.name)
                }
            }
        }
    }
}

private val NETWORK_WORDS = mapOf(
    NetworkQualityLevel.excellent to "Excellent",
    NetworkQualityLevel.good to "Good",
    NetworkQualityLevel.poor to "Poor",
    NetworkQualityLevel.bad to "Bad",
)

fun showMoreSheet(engine: RoomEngine, onLeave: () -> Unit, onPeople: () -> Unit, onReportRoom: () -> Unit, onSite: () -> Unit) {
    sheetScope.launch(Dispatchers.Main.immediate) {
        val others = engine.hasOtherOutputs()
        SheetHost.show("more-sheet", "Room") {
            val isHost = engine.isHost
            val network = engine.network
            Column(Modifier.verticalScroll(rememberScrollState())) {
                SheetRow(Ion.peopleOutline, "People in the room", { close(); onPeople() }, id = "more-people")
                if (isHost && engine.room.isVideo) {
                    SheetRow(
                        Ion.gridOutline,
                        if (engine.layout == "stage") "Switch to grid layout" else "Switch to stage layout",
                        { close(); engine.setRoomLayout(if (engine.layout == "stage") "grid" else "stage") },
                        hint = "Everyone in the room sees the change",
                        id = "more-layout",
                    )
                }
                if (isHost) {
                    SheetRow(
                        if (engine.seatMode == "open") Ion.lockOpenOutline else Ion.handLeftOutline,
                        if (engine.seatMode == "open") "Require your approval for seats" else "Let anyone take a seat",
                        { close(); engine.setRoomSeatMode(if (engine.seatMode == "open") "request" else "open") },
                        id = "more-seat-mode",
                    )
                    SheetRow(Ion.volumeMuteOutline, "Mute everyone on stage", { close(); engine.muteAll() }, id = "more-mute-all")
                }
                if (engine.mySeat != null) SheetRow(Ion.arrowDownCircleOutline, "Leave my seat", { close(); engine.leaveSeat() }, id = "more-leave-seat")
                if (engine.myRole == Role.viewer && engine.myHand) SheetRow(Ion.handLeftOutline, "Cancel my request", { close(); engine.cancelRequest() })
                if (others) {
                    SheetRow(
                        if (engine.speakerphone) Ion.volumeHighOutline else Ion.earOutline,
                        if (engine.speakerphone) "Use the earpiece" else "Use the loudspeaker",
                        { close(); engine.useSpeakerphone(!engine.speakerphone) },
                    )
                }
                if (network != null) {
                    SheetRow(
                        Ion.cellularOutline,
                        "Connection: ${NETWORK_WORDS[network.level]}",
                        { close() },
                        hint = network.rttMs?.let { "$it ms round trip" },
                    )
                }
                if (!isHost) SheetRow(Ion.flagOutline, "Report this room", { close(); onReportRoom() }, id = "more-report")
                SheetRow(Ion.flashOutline, Voltkit.POWERED_BY, { close(); onSite() }, hint = "A Kotlin demo. Build yours at voltkit.in", gold = true)
                SheetRow(Ion.powerOutline, if (isHost) "End the room" else "Leave the room", { close(); onLeave() }, danger = true, id = "more-leave")
            }
        }
    }
}

suspend fun confirmEnd(isHost: Boolean): Boolean = SheetHost.ask<Boolean>("confirm-end") {
    Column {
        T(if (isHost) "End this room?" else "Leave this room?", V.Title)
        Spacer(Modifier.height(10.dp))
        T(if (isHost) "The room closes for everyone in it." else "You can come back while it is live.", color = C.textDim)
        Spacer(Modifier.height(16.dp))
        GradientButton(if (isHost) "End the room" else "Leave", { close(true) }, Modifier.fillMaxWidth(), colors = G.danger, id = "confirm-end-yes")
        Spacer(Modifier.height(10.dp))
        GhostButton("Stay", { close(false) }, Modifier.fillMaxWidth())
    }
} == true

suspend fun askInvite(video: Boolean): Boolean = SheetHost.ask<Boolean>("invite-sheet") {
    Column {
        T("The host invited you on stage", V.Title)
        Spacer(Modifier.height(10.dp))
        T(if (video) "Your camera and microphone go live to the room." else "Your microphone goes live to the room.", color = C.textDim)
        Spacer(Modifier.height(16.dp))
        GradientButton("Join the stage", { close(true) }, Modifier.fillMaxWidth(), id = "invite-accept")
        Spacer(Modifier.height(10.dp))
        GhostButton("No thanks", { close(false) }, Modifier.fillMaxWidth())
    }
} == true
