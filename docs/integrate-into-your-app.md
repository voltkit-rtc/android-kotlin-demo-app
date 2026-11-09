# Integrate VoltKit into your Android app

From an empty Android app to a working live room, in Kotlin, one step at a time. Each step says what to do and why. Every example comes
from this app, cut down to the part you need, with a link to the full file. Paths are under
[`app/src/main/java/in/voltkit/livedemo/kotlin/`](../app/src/main/java/in/voltkit/livedemo/kotlin/).

You need a VoltKit project and a backend that mints tokens. The backend for this app is
[voltkit-rtc-backend-demo](https://github.com/voltkit-rtc/voltkit-rtc-backend-demo); its
[server SDK guide](https://github.com/voltkit-rtc/voltkit-rtc-backend-demo/blob/main/docs/voltkit-server-sdk.md) covers that side, and its
[API reference](https://github.com/voltkit-rtc/voltkit-rtc-backend-demo/blob/main/docs/api-reference.md) lists the endpoints used below.

> [!NOTE]
> Your VoltKit API secret stays on your backend. The app only ever receives short-lived tokens.

## 1. Install

```kotlin
// app/build.gradle.kts
android {
    defaultConfig { minSdk = 24 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("in.voltkit:voltkit-rtc:0.9.17") // from Maven Central
}
```

The SDK brings WebRTC, OkHttp and coroutines. This app builds with AGP 9.1 and its built-in Kotlin: no Kotlin Gradle plugin, and no
`android.builtInKotlin` or `android.newDsl` settings. See [`app/build.gradle.kts`](../app/build.gradle.kts).

## 2. Permissions and the call service

In `AndroidManifest.xml` (the SDK adds `INTERNET`, `ACCESS_NETWORK_STATE` and `MODIFY_AUDIO_SETTINGS` itself):

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" />

<application android:name=".VoltKitApp" ...>
    <service
        android:name=".rtc.CallService"
        android:exported="false"
        android:foregroundServiceType="microphone|mediaPlayback" />
</application>
```

**Why the service:** Android cuts an app's network and microphone within seconds of it leaving the screen. A foreground service with a
notification keeps a call alive, and its Leave button ends it. Copy [`rtc/CallService.kt`](../app/src/main/java/in/voltkit/livedemo/kotlin/rtc/CallService.kt):
it takes the microphone type once that permission is granted. Start it when connected, stop it on leaving.

## 3. Initialize the SDK in your Application

```kotlin
class VoltKitApp : Application() {
    override fun onCreate() {
        super.onCreate()
        VoltKitRtc.initialize(this, RtcOptions())
    }
}
```

**Why there:** WebRTC starts once per process, and `initialize` must run before any client. `RtcOptions()` keeps the phone's own echo
canceller, the Android SDK's default. For a phone that echoes on the loudspeaker (the other person hears themselves), pass
`RtcOptions(hardwareEchoCanceler = false)`: that uses WebRTC's canceller, with the 0.9.17 loudspeaker tuning on phones that need it. This
app lets people switch in Profile → Echo cancellation, applied on the next start. See
[`VoltKitApp.kt`](../app/src/main/java/in/voltkit/livedemo/kotlin/VoltKitApp.kt).

## 4. Get a token from your backend

```kotlin
data class Credentials(val token: String, val wsUrl: String, val role: String)

suspend fun joinRoom(http: OkHttpClient, roomId: String, sessionToken: String): Credentials = withContext(Dispatchers.IO) {
    val request = Request.Builder()
        .url("https://api.example.com/v1/rooms/$roomId/join")
        .header("Authorization", "Bearer $sessionToken") // your app's own sign-in
        .post(ByteArray(0).toRequestBody())
        .build()
    http.newCall(request).execute().use { res ->
        if (!res.isSuccessful) throw IOException("Could not join (${res.code})")
        val c = JSONObject(res.body.string()).getJSONObject("credentials")
        Credentials(c.getString("token"), c.getString("wsUrl"), c.getString("role"))
    }
}
```

**Why ask right before joining:** a token lasts ten minutes and only the handshake needs it valid. See
[`data/Api.kt`](../app/src/main/java/in/voltkit/livedemo/kotlin/data/Api.kt).

## 5. Ask before going on air

Viewers need no permissions. Ask only when someone is about to publish:

```kotlin
val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
    if (granted[Manifest.permission.RECORD_AUDIO] == true) goOnAir()
}
// When they tap "Go live" or "Take a seat":
ask.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS))
```

This app shows its own explanation once first, and sends people to Settings when a permission is blocked. See
[`rtc/Consent.kt`](../app/src/main/java/in/voltkit/livedemo/kotlin/rtc/Consent.kt).

## 6. Create the client and connect

```kotlin
val client = StreamClient(context, credentials.wsUrl, credentials.token, StreamOptions(pauseCameraInBackground = true))

// Collect before connecting: tracks already live arrive during connect().
scope.launch { client.onTrack.collect { track -> showTrack(track) } }
scope.launch { client.onTrackEnded.collect { e -> hideTrack(e.producerId) } }
scope.launch { client.onTrackMuted.collect { redraw() } }   // RemoteTrack.paused is already current
scope.launch { client.participants.collect { people = it } } // everyone, you included
scope.launch { client.onClosed.collect { reason -> onClosed(reason) } }

val joined = client.connect(mapOf("name" to me.name, "avatar" to me.avatar))
```

**Why the metadata:** everyone else reads it from `participants`, so names and avatars show without another request. Every flow is a hot
`SharedFlow` or `StateFlow`; collect on the main thread to touch UI state. One client is one session: after `close()` or a final
`onClosed`, make a new `StreamClient` to join again. See [`rtc/RoomEngine.kt`](../app/src/main/java/in/voltkit/livedemo/kotlin/rtc/RoomEngine.kt),
which holds all the room logic in one class with Compose state.

## 7. Publish

```kotlin
// A video room. With no quality, publish() sends the highest the project allows: choose one.
client.publish(PublishOptions(encoding = VideoPublishOptions(quality = VideoQuality.p720)))

// An audio room.
client.publish(PublishOptions(video = false))
```

**When:** a host publishes after connecting; anyone else after `onRoleChanged` makes them a co-host. A promotion can move the client to
another server, so `publish()` may throw `RpcError` with the code `disconnected`. A publish that throws is over: call it again once
`connectionState` is `connected`. `checkOnAir` in the engine does that. A missing permission comes back as `media-unavailable`, with
`data["permission"]` naming it.

## 8. Show video

`attachVideo` takes any `View` that is also a WebRTC `VideoSink`, and tells VoltKit how big the video is drawn, so the view receives the
copy that fits:

```kotlin
val view = SurfaceViewRenderer(context).apply { init(VoltKitRtc.eglBaseContext, null) }
val attachment = client.attachVideo(remoteTrack, view, true) // true: crop to fill
// When it goes off screen:
attachment.detach()
view.release()

// Your own camera:
client.localVideoTrack.collect { track -> track?.addSink(localView) }
```

A `SurfaceViewRenderer` cannot have rounded corners or views on top of it. For cards like this app's, use
[`rtc/VideoViews.kt`](../app/src/main/java/in/voltkit/livedemo/kotlin/rtc/VideoViews.kt): a `TextureView` drawn by WebRTC's `EglRenderer`,
with `RemoteVideo` and `LocalVideo` composables you can copy as they are. Show a remote video only while `track.paused` is false.

## 9. Controls

```kotlin
client.setMicEnabled(!client.localMedia.value.micEnabled)
client.setCamEnabled(!client.localMedia.value.camEnabled)
val front = client.switchCamera()       // true when the front camera is now in use
client.setSpeakerphoneOn(true)          // the loudspeaker; a headset still wins
```

`client.localMedia` is a `StateFlow` of what you send: draw the buttons from it. A phone with one camera has nothing to switch to, so this
app hides Flip when `client.listDevices(DeviceKind.videoinput)` returns one camera.

## 10. Handle what goes wrong

Branch on the code and write your own words. [`rtc/Errors.kt`](../app/src/main/java/in/voltkit/livedemo/kotlin/rtc/Errors.kt) has the full
mapping:

```kotlin
fun joinProblem(err: Throwable): Pair<String, Boolean> {
    val e = err as? RpcError ?: return "Could not connect. Check your connection and try again." to true
    return when (e.code) {
        "room-ended", "no-room" -> "This room has ended." to false
        "kicked" -> "You were removed from this room." to false
        "no-capacity" -> "This room is full right now. Try again in a moment." to true
        else -> "Could not connect. Check your connection and try again." to !e.isTerminal
    }
}
```

**Reconnecting needs no code.** The client retries and publishes again by itself. Show a banner while `connectionState` is
`ConnectionState.reconnecting`, and keep the room on screen. `onClosed` tells you how a session ended: `left`, `kicked`, `roomClosed`,
`replaced` or `gaveUp` (about a minute offline: offer a Join again button).

## 11. Refresh tokens when asked

```kotlin
scope.launch {
    client.onTokenExpiring.collect {
        val fresh = api.refreshRoomToken(roomId) // POST /v1/rooms/:id/token on the demo backend
        client.updateToken(fresh.token)           // goes to the live session, no reconnect
    }
}
```

## 12. Leave

```kotlin
client.close() // releases the camera, the microphone and the socket; others see "left"
```

Hold the client in a `ViewModel` so a configuration change or a screen opened over the room does not end it, and close it in
`onCleared`. See `RoomViewModel` in [`screens/room/RoomScreen.kt`](../app/src/main/java/in/voltkit/livedemo/kotlin/screens/room/RoomScreen.kt).

## Next

- Seats, roles, moderation, chat, gifts, 1v1 calls and DMs: [Build the features](build-the-features.md)
- VoltKit's own guides: [Android SDK](https://voltkit.in/docs/android-sdk) and the [Android reference](https://voltkit.in/docs/android-sdk-reference)
