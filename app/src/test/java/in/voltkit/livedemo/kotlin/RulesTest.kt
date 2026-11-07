package `in`.voltkit.livedemo.kotlin

import com.voltkit.rtc.Role
import `in`.voltkit.livedemo.kotlin.rtc.MemberAction
import `in`.voltkit.livedemo.kotlin.rtc.MemberState
import `in`.voltkit.livedemo.kotlin.rtc.Rules
import `in`.voltkit.livedemo.kotlin.rtc.SeatAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RulesTest {
    private val roles = listOf(Role.host, Role.cohost, Role.moderator, Role.viewer)

    @Test fun matchesVoltKitAndTheSdk() {
        assertEquals(listOf(true, true, false, false), roles.map { Rules.canPublish(it) })
        assertEquals(listOf(true, false, true, false), roles.map { Rules.canModerate(it) })
        for (r in roles) {
            assertEquals(r.canPublish, Rules.canPublish(r))
            assertEquals(r.canModerate, Rules.canModerate(r))
        }
    }

    @Test fun nobodyActsOnTheHostOrThemselves() {
        for (actor in roles) {
            assertTrue(Rules.memberActions(actor, MemberState(role = Role.host), true).isEmpty())
            assertTrue(Rules.memberActions(actor, MemberState(isSelf = true), true).isEmpty())
        }
    }

    @Test fun cohostsAndViewersHaveNoPower() {
        for (actor in listOf(Role.cohost, Role.viewer)) {
            assertTrue(Rules.memberActions(actor, MemberState(), true).isEmpty())
            assertTrue(Rules.memberActions(actor, MemberState(role = Role.cohost), true).isEmpty())
        }
    }

    @Test fun hostHasTheFullSetOnACohost() = assertEquals(
        listOf(MemberAction.RemoveSeat, MemberAction.ForceMuteMic, MemberAction.ForceMuteCam, MemberAction.MuteChat, MemberAction.RemoveFromRoom),
        Rules.memberActions(Role.host, MemberState(role = Role.cohost, seated = true), true),
    )

    @Test fun releaseOnceSomethingIsLocked() {
        val actions = Rules.memberActions(Role.host, MemberState(role = Role.cohost, micLocked = true, camLocked = true, chatMuted = true), true)
        assertTrue(actions.containsAll(listOf(MemberAction.ReleaseMic, MemberAction.ReleaseCam, MemberAction.UnmuteChat)))
        assertFalse(MemberAction.ForceMuteMic in actions)
    }

    @Test fun noCameraControlsInAnAudioRoom() {
        val actions = Rules.memberActions(Role.host, MemberState(role = Role.cohost), false)
        assertFalse(MemberAction.ForceMuteCam in actions)
        assertFalse(MemberAction.ReleaseCam in actions)
    }

    @Test fun hostMakesAndUnmakesModerators() {
        assertTrue(MemberAction.MakeModerator in Rules.memberActions(Role.host, MemberState(), true))
        assertTrue(MemberAction.RemoveModerator in Rules.memberActions(Role.host, MemberState(role = Role.moderator), true))
    }

    @Test fun moderatorsKeepToViewers() {
        assertEquals(
            listOf(MemberAction.DeclineHand, MemberAction.MuteChat, MemberAction.RemoveFromRoom),
            Rules.memberActions(Role.moderator, MemberState(handRaised = true), true),
        )
        assertTrue(Rules.memberActions(Role.moderator, MemberState(role = Role.cohost), true).isEmpty())
        assertTrue(Rules.memberActions(Role.moderator, MemberState(role = Role.moderator), true).isEmpty())
    }

    private fun seat(role: Role, empty: Boolean = true, locked: Boolean = false, mine: Boolean = false, seated: Boolean = false) =
        Rules.seatActions(role, empty, locked, mine, seated)

    @Test fun viewerAsksForAnEmptySeatOnly() {
        assertEquals(listOf(SeatAction.Request), seat(Role.viewer))
        assertTrue(seat(Role.viewer, empty = false).isEmpty())
        assertTrue(seat(Role.viewer, locked = true).isEmpty())
    }

    @Test fun youLeaveYourOwnSeat() = assertEquals(listOf(SeatAction.Leave), seat(Role.cohost, empty = false, mine = true, seated = true))

    @Test fun hostLocksAndUnlocks() {
        assertEquals(listOf(SeatAction.Invite, SeatAction.Lock), seat(Role.host))
        assertEquals(listOf(SeatAction.Unlock), seat(Role.host, locked = true))
    }

    @Test fun moderatorHasNoSeatPowers() = assertTrue(seat(Role.moderator).isEmpty())

    @Test fun noSecondSeatForSomeoneSeated() = assertTrue(seat(Role.cohost, seated = true).isEmpty())
}
