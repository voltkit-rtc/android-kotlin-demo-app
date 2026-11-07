package `in`.voltkit.livedemo.kotlin.rtc

import com.voltkit.rtc.Role

/**
 * What each role may do to whom, as VoltKit enforces it. The service refuses anything else, so this only decides which buttons
 * to show: a button that always fails is worse than no button.
 *
 * host        all of it, on anyone but themselves
 * moderator   on viewers only: mute in chat, remove. Handles raised hands. Grants nothing.
 * cohost      publishes; no powers over others
 * viewer      watches
 */
enum class MemberAction {
    InviteSeat, RemoveSeat, ForceMuteMic, ReleaseMic, ForceMuteCam, ReleaseCam,
    MuteChat, UnmuteChat, MakeModerator, RemoveModerator, DeclineHand, RemoveFromRoom,
}

data class MemberState(
    val role: Role = Role.viewer,
    val isSelf: Boolean = false,
    val seated: Boolean = false,
    val handRaised: Boolean = false,
    val micLocked: Boolean = false,
    val camLocked: Boolean = false,
    val chatMuted: Boolean = false,
)

enum class SeatAction { Request, Leave, Lock, Unlock, Invite }

object Rules {
    fun canPublish(role: Role) = role == Role.host || role == Role.cohost
    fun canModerate(role: Role) = role == Role.host || role == Role.moderator

    fun memberActions(actor: Role, target: MemberState, roomHasVideo: Boolean): List<MemberAction> {
        if (target.isSelf || target.role == Role.host) return emptyList()
        if (actor == Role.host) {
            return buildList {
                if (target.role == Role.cohost) {
                    add(MemberAction.RemoveSeat)
                    add(if (target.micLocked) MemberAction.ReleaseMic else MemberAction.ForceMuteMic)
                    if (roomHasVideo) add(if (target.camLocked) MemberAction.ReleaseCam else MemberAction.ForceMuteCam)
                } else if (target.role == Role.viewer) {
                    add(MemberAction.InviteSeat)
                }
                if (target.handRaised) add(MemberAction.DeclineHand)
                add(if (target.chatMuted) MemberAction.UnmuteChat else MemberAction.MuteChat)
                if (target.role == Role.viewer) add(MemberAction.MakeModerator)
                if (target.role == Role.moderator) add(MemberAction.RemoveModerator)
                add(MemberAction.RemoveFromRoom)
            }
        }
        if (actor == Role.moderator && target.role == Role.viewer) {
            return buildList {
                if (target.handRaised) add(MemberAction.DeclineHand)
                add(if (target.chatMuted) MemberAction.UnmuteChat else MemberAction.MuteChat)
                add(MemberAction.RemoveFromRoom)
            }
        }
        return emptyList()
    }

    fun seatActions(role: Role, empty: Boolean, locked: Boolean, mine: Boolean, iAmSeated: Boolean): List<SeatAction> {
        if (mine) return listOf(SeatAction.Leave)
        if (role == Role.host) {
            if (locked) return listOf(SeatAction.Unlock)
            return if (empty) listOf(SeatAction.Invite, SeatAction.Lock) else emptyList()
        }
        if (locked || !empty || iAmSeated) return emptyList()
        return if (role == Role.viewer) listOf(SeatAction.Request) else emptyList()
    }
}
