# VoltKit Kotlin

A complete live social app for Android, built on [VoltKit RTC](https://voltkit.in/rtc/android) and written to be read. It has video
rooms, audio rooms with seats, random 1v1 video calls, room chat, direct messages, moderation and SVGA gifts, all on the official Android
SDK, [`in.voltkit:voltkit-rtc`](https://voltkit.in/docs/android-sdk). The UI is Jetpack Compose.

Use it two ways: run it to see what VoltKit does, or open the file for the feature you need and copy the approach into your own app.

It is the same app as [VoltKit Native](https://github.com/voltkit-rtc/react-native-example-audio-video) (React Native) and
[VoltKit Flutter](https://github.com/voltkit-rtc/flutter-demo-app), on the same
[backend](https://github.com/voltkit-rtc/voltkit-rtc-backend-demo). All three can share a room.

## Run it

You need JDK 17 or newer and the Android SDK (compile SDK 36). An Android phone is best: an emulator's microphone is silent and its
cameras are simulated.

```bash
./gradlew installDebug
```

That runs against the hosted demo backend, so there is nothing else to set up. To use your own, run the
[backend](https://github.com/voltkit-rtc/voltkit-rtc-backend-demo#run-it) and point the app at it:

```bash
./gradlew installDebug -PapiUrl=https://api.example.com
```

> [!NOTE]
> Your VoltKit secret stays on the backend. The app only ever receives short-lived tokens.

## Where each feature lives

Paths are under [`app/src/main/java/in/voltkit/livedemo/kotlin/`](app/src/main/java/in/voltkit/livedemo/kotlin/).

| Feature | Code | VoltKit APIs it shows |
| --- | --- | --- |
| Starting the SDK | `VoltKitApp.kt` | `VoltKitRtc.initialize`, `RtcOptions` |
| Joining and leaving a room | `screens/room/RoomScreen.kt` | `onClosed`, `ClosedReason` |
| Everything a room does | `rtc/RoomEngine.kt` | `StreamClient`, `connect`, `publish`, `onTrack`, `onTokenExpiring`, `updateToken` |
| Drawing video | `rtc/VideoViews.kt` | `attachVideo`, `localVideoTrack`, `VoltKitRtc.eglBaseContext` |
| Video cards and audio seats | `screens/room/Stages.kt` | `onActiveSpeakers`, `onStreamFallback`, `RemoteTrack.paused` |
| Seats | `rtc/Seats.kt` | `claim`, `release`, `blockSlot`, `onClaimChanged` |
| Who may do what | `rtc/Rules.kt` | The four roles |
| Moderation | `screens/room/RoomSheets.kt` | `forceMute`, `kick`, `promote`, `muteChat`, `deleteMessage` |
| Permissions and consent | `rtc/Consent.kt` | Asked before going on air, never for viewers |
| Error messages | `rtc/Errors.kt` | `RpcError`, `ClosedReason` |
| 1v1 calls | `screens/MatchScreen.kt` | A room per call, `onParticipantLeft` |
| Room chat and hearts | `screens/room/RoomChat.kt` | `sendMessage`, `getMessages`, ephemeral messages |
| Lounge and direct messages | `screens/ChatScreens.kt` | `ChatClient` |
| SVGA gifts and entrance effects | `gifts/` | Gift messages sent by the backend |
| Staying alive in the background | `rtc/CallService.kt` | `pauseCameraInBackground` |

## How a room works

Everything VoltKit lives in one class, [`RoomEngine`](app/src/main/java/in/voltkit/livedemo/kotlin/rtc/RoomEngine.kt). Screens draw its
Compose state and call its methods, and hold no SDK logic.

**Collect flows before connecting.** Tracks that are already live arrive during `connect()`.

**The host's client grants seats.** Only a host may promote, so a viewer raises a hand and the host's app claims the seat, then promotes:

```kotlin
client.raiseHand(mapOf("requestRole" to "cohost", "slot" to "seat-3"))   // viewer

client.claim("seat-3", viewer)                                          // host: if the seat has gone, nothing has happened yet
client.promote(viewer, Role.cohost)
```

**Publish when `onRoleChanged` names you.** A promotion can move the client to another server, so `publish()` can fail with
`disconnected`. A `publish()` that throws is over, so the engine asks again once connected. `checkOnAir` keeps what the person wants apart
from whether they are publishing.

**Remember to leave.** `close()` releases the camera, the microphone and the socket. A closed client stays closed, so rejoining makes a new
`StreamClient`. A `ViewModel` owns each room, so a chat opened over it, or a configuration change, does not end it.

## Good to know

| Thing | Why |
| --- | --- |
| A foreground service runs while in a room or call | Android cuts an app's network and microphone within seconds of leaving the screen without one. Its notification has a Leave button |
| The camera goes off in the background | `pauseCameraInBackground = true`: viewers see the camera off, not a frozen picture, and it comes back on return |
| Video is drawn on a `TextureView` | A `SurfaceViewRenderer` cannot have rounded corners or views over it. `VideoTextureView` uses WebRTC's `EglRenderer` on the SDK's EGL context |
| Profile → Echo cancellation | The Android SDK uses the phone's echo canceller by default. If someone hears themselves from your phone on the loudspeaker, try WebRTC's. It applies on the next start |
| Two phones in one room echo on the loudspeaker | Each canceller removes only its own speaker's sound. Test in different rooms, or with earphones on one phone |
| No Flip button on some phones | It is hidden when the phone has one camera |

## Tests

```bash
./gradlew testDebugUnitTest    # seats, roles and permissions, video layouts, the gift queue
```

The room flows were tested on an Android emulator against the live backend and real VoltKit rooms, with browser-SDK clients as the other
people in the room, in debug and in a minified release build.

## Release builds

Copy [`key.properties.example`](key.properties.example) to `key.properties` and fill in your keystore. Without one the release build is left
unsigned, so a debug key never reaches users.

```bash
./gradlew assembleRelease
```

## Credits

MIT, see [LICENSE](LICENSE). The VoltKit name and logo are not part of that licence. Outfit is under the SIL Open Font License, Ionicons under
MIT, and the SVGA player under Apache 2.0.
