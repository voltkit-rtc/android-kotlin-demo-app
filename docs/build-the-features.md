# Build the features

Seats, roles, moderation, chat, gifts, 1v1 calls and direct messages, the way this app builds them. Read
[Integrate VoltKit into your app](integrate-into-your-app.md) first: everything here runs on the connected `client` from there. Paths are
under [`app/src/main/java/in/voltkit/livedemo/kotlin/`](../app/src/main/java/in/voltkit/livedemo/kotlin/).

All the room logic is in one class, [`rtc/RoomEngine.kt`](../app/src/main/java/in/voltkit/livedemo/kotlin/rtc/RoomEngine.kt). Screens draw its
Compose state and call its methods. Every call below is a `suspend` function: run it in a coroutine and catch `RpcError`.

## Roles

| Role | Can |
| --- | --- |
| `host` | Everything: publish, seat people, moderate, end the room |
| `moderator` | On viewers only: mute them in chat, remove them, decline their requests. Cannot seat people |
| `cohost` | Publish. Someone on stage |
| `viewer` | Watch, chat, raise a hand |

The backend picks the role when it mints the token: the room's creator is the host, everyone else joins as a viewer.
[`rtc/Rules.kt`](../app/src/main/java/in/voltkit/livedemo/kotlin/rtc/Rules.kt) turns roles into what each person sees in the menus, and is
unit tested.

## Seats

A seat is a named slot (`seat-1`, `seat-2`, ...). Only a host may promote, so the host's app is the one that says yes.

```kotlin
// A viewer asks, optionally for one seat:
client.raiseHand(mapOf("requestRole" to "cohost", "slot" to "seat-3"))

// The host's app hears it on onHandRaised, and seats them. Claim first: if the seat has gone, nothing else happened.
try {
    client.claim("seat-3", viewerId)
    client.promote(viewerId, Role.cohost)
} catch (e: RpcError) {
    runCatching { client.release("seat-3") }
    runCatching { client.lowerHand(viewerId) }
}

// Decline a request, leave a seat, lock one:
client.lowerHand(viewerId)
client.release("seat-3")
client.blockSlot("seat-5") // unblockSlot to open it again
```

The new co-host's app publishes when `onRoleChanged` names it. Seats are in `client.joinResult?.claims` after connecting and change on
`onClaimChanged`. Moving someone back to the audience is `release` plus `promote(id, Role.viewer)`: a demotion alone keeps the seat
claimed. See [`rtc/Seats.kt`](../app/src/main/java/in/voltkit/livedemo/kotlin/rtc/Seats.kt).

**Invitations** go as a chat message to one person, which their app turns into a prompt:

```kotlin
client.sendMessage("", type = "invite", to = viewerId, metadata = mapOf("slot" to "seat-2"))
```

## Moderation

```kotlin
client.forceMute(id, "audio", true)   // "video" for the camera; false gives it back
client.muteChat(id, true)
client.promote(id, Role.moderator)    // Role.viewer to take it away

// Removing someone: ban on your backend first, so it mints no new token, then kick.
api.banFromRoom(roomId, id)           // POST /v1/rooms/:id/bans on the demo backend
client.kick(id, "Removed from the room")
```

The person removed gets `onClosed` with `ClosedReason.kicked`; someone force-muted gets `onForceMuted`.

## Room settings

Room-wide settings live in the room's metadata, so everyone sees a change at once:

```kotlin
client.setRoomMetadata(mapOf("layout" to "grid"), false, null)     // or "stage"
client.setRoomMetadata(mapOf("seatMode" to "open"), false, null)   // or "request"
client.onRoomMetadataChanged.collect { applySettings(it.metadata) }
```

## Speaking, network and weak video

```kotlin
client.onActiveSpeakers.collect { ids -> speaking = ids.toSet() }   // draw a ring around each
client.onNetworkQuality.collect { q -> network = q }                 // q.level, q.rttMs
client.onStreamFallback.collect { f -> /* f.fallback: a video paused to save the audio; show "Weak connection" */ }
```

## Room chat

```kotlin
client.sendMessage(text, metadata = mapOf("name" to me.name, "avatar" to me.avatar))
val history = client.getMessages(30, null) // oldest first
client.onMessage.collect { addLine(it) }    // your own come back here too
client.deleteMessage(messageId)            // yours, or anyone's for a moderator
```

**Hearts** are ephemeral messages: delivered, never stored.

```kotlin
client.sendMessage("", type = "reaction", metadata = mapOf("emoji" to "heart"), ephemeral = true)
```

See [`screens/room/RoomChat.kt`](../app/src/main/java/in/voltkit/livedemo/kotlin/screens/room/RoomChat.kt).

## Gifts and entrance effects

The app never sends a gift message itself. It asks the backend, which checks and spends the coins, then sends a trusted message into
the room with the server SDK:

```kotlin
api.sendGift(roomId, giftId, toIdentity, count) // POST /v1/rooms/:id/gifts on the demo backend
```

Everyone's app hears it on `onMessage` with `type == "gift"` and plays the animation named in `metadata["svgaUrl"]`. Entrance effects
work the same way with `type == "entrance"`, sent by the backend when someone with a frame joins.
[`gifts/GiftStage.kt`](../app/src/main/java/in/voltkit/livedemo/kotlin/gifts/GiftStage.kt) plays them one at a time with
[SVGAPlayer-Android](https://github.com/yyued/SVGAPlayer-Android), falls back to the gift's icon if a file cannot play, and skips one that
stalls. [`gifts/Gifts.kt`](../app/src/main/java/in/voltkit/livedemo/kotlin/gifts/Gifts.kt) downloads the catalog's files when a room opens. With
R8 on, keep the player's classes: see [`app/proguard-rules.pro`](../app/proguard-rules.pro).

## 1v1 calls

A call is a VoltKit room with two people. The backend's matchmaker pairs people over Socket.IO and sends each phone its own token:

```kotlin
LiveSocket.emit("match:join")
LiveSocket.on("match:found") { payload ->
    val call = MatchFound.from(payload as JSONObject) // callId, token, wsUrl, peer
    val client = StreamClient(context, call.wsUrl, call.token, StreamOptions(pauseCameraInBackground = true))
    // connect, publish, show the peer's video full screen and your own small
}
LiveSocket.emit("match:end", JSONObject().put("callId", call.callId)) // hang up
```

When the peer drops (`onParticipantLeft` with reason `disconnected`), wait about 30 seconds before ending: their SDK may be reconnecting.
This app talks Socket.IO with a small client of its own on OkHttp's WebSocket,
[`data/LiveSocket.kt`](../app/src/main/java/in/voltkit/livedemo/kotlin/data/LiveSocket.kt). See
[`screens/MatchScreen.kt`](../app/src/main/java/in/voltkit/livedemo/kotlin/screens/MatchScreen.kt).

## Lounge and direct messages

Chat with no room uses `ChatClient`, with a chat token from the backend:

```kotlin
val channel = api.loungeToken()                    // POST /v1/chat/lounge/token: { token, wsUrl }
val chat = ChatClient(channel.wsUrl, channel.token)
scope.launch { chat.onMessage.collect { addBubble(it) } }
val joined = chat.connect()                        // joined.messages: the recent history, oldest first
chat.sendMessage(text, metadata = mapOf("name" to me.name, "avatar" to me.avatar))
chat.close()
```

Direct messages are stored by the backend (`POST /v1/conversations/:id/messages`) and relayed live on the conversation's chat channel.
See [`screens/ChatScreens.kt`](../app/src/main/java/in/voltkit/livedemo/kotlin/screens/ChatScreens.kt).

## Background

Start the foreground service once connected and stop it on leaving (step 2 of the integration guide). With
`StreamOptions(pauseCameraInBackground = true)`, viewers see the camera off while the app is in the background; the camera is released
about five seconds later and comes back as a new video when the app returns.

## Echo

- The Android SDK uses the phone's own echo canceller by default. `RtcOptions(hardwareEchoCanceler = false)` switches to WebRTC's, which
  from 0.9.17 is tuned for the loudspeaker on phones whose own canceller is Android's software one (`echoSuppression`, `auto` by default).
  Test an echoing phone both ways.
- Two phones on loudspeaker in one room always echo, with any SDK. Test echo from two separate rooms.
- **Copy a sound report** (room menu, or a 1v1 call's Safety menu) copies what VoltKit asks for with an echo report: the phone, Android
  version, where the sound plays, the call volume, the echo canceller in use and `getDiagnostics()`. See
  [`rtc/SoundReport.kt`](../app/src/main/java/in/voltkit/livedemo/kotlin/rtc/SoundReport.kt).

VoltKit's own guide: [Troubleshooting: echo](https://voltkit.in/docs/troubleshooting#echo).
