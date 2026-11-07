package `in`.voltkit.livedemo.kotlin.rtc

import com.voltkit.rtc.ClosedReason
import com.voltkit.rtc.RpcError

object Problems {
    fun closed(reason: ClosedReason) = when (reason) {
        ClosedReason.left -> "You left the room."
        ClosedReason.kicked -> "You were removed from this room."
        ClosedReason.roomClosed -> "This room has ended."
        ClosedReason.replaced -> "You joined this room from another device."
        ClosedReason.gaveUp -> "The connection was lost."
    }

    data class Join(val text: String, val canRetry: Boolean)

    fun join(err: Throwable): Join {
        val e = err as? RpcError ?: return Join("Could not connect. Check your connection and try again.", true)
        return when (e.code) {
            "bad-token", "no-token" -> Join("Your session expired. Join again.", true)
            "room-ended", "no-room" -> Join("This room has ended.", false)
            "kicked" -> Join("You were removed from this room.", false)
            "quota-exhausted", "daily-limit-reached", "monthly-limit-reached", "tenant-suspended", "billing-unavailable" ->
                Join("Live rooms are unavailable right now.", false)
            "participant-quota", "viewer-quota", "no-capacity" -> Join("This room is full right now. Try again in a moment.", true)
            else -> Join("Could not connect. Check your connection and try again.", !e.isTerminal)
        }
    }

    /** Words for a failed action in a room. Never shows the service's raw message. */
    fun action(err: Throwable): String = when ((err as? RpcError)?.code) {
        "forbidden" -> "You do not have permission to do that."
        "no-participant" -> "That person has left."
        "slot-taken" -> "Someone just took that seat."
        "slot-blocked" -> "That seat is locked."
        "slot-occupied" -> "Someone is sitting there."
        "chat-muted" -> "You have been muted in this chat."
        "rate-limited" -> "Slow down a little."
        "force-muted" -> "The host has this switched off."
        "stage-full" -> "The stage is full."
        "disconnected", "not-connected" -> "Reconnecting. Try again in a moment."
        else -> "That did not work. Try again."
    }

    data class Media(val text: String, val openSettings: Boolean)

    /** publish() names a missing permission in data["permission"]. */
    fun media(err: Throwable): Media {
        val e = err as? RpcError
        return when (e?.code) {
            "media-unavailable" -> {
                when (e.data?.get("permission")?.toString()) {
                    "CAMERA" -> Media("Allow the camera in Settings to go on air.", true)
                    "RECORD_AUDIO" -> Media("Allow the microphone in Settings to go on air.", true)
                    else -> Media("The camera or microphone could not start. Another app may be using it.", false)
                }
            }
            "no-camera", "camera-failed" -> Media("The camera could not start. Another app may be using it.", false)
            "media-disabled" -> Media("Video or audio is switched off for this app.", false)
            else -> Media("Could not start the camera and microphone.", false)
        }
    }
}
