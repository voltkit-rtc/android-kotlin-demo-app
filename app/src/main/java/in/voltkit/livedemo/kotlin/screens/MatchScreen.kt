package `in`.voltkit.livedemo.kotlin.screens

import android.app.Activity
import android.app.Application
import android.content.Context
import android.media.AudioManager
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.EaseOutQuad
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.voltkit.rtc.ClosedReason
import com.voltkit.rtc.ConnectionState
import com.voltkit.rtc.DeviceKind
import com.voltkit.rtc.LocalMediaState
import com.voltkit.rtc.PublishOptions
import com.voltkit.rtc.RemoteTrack
import com.voltkit.rtc.RpcError
import com.voltkit.rtc.StreamClient
import com.voltkit.rtc.StreamOptions
import com.voltkit.rtc.VideoPublishOptions
import com.voltkit.rtc.VideoQuality
import `in`.voltkit.livedemo.kotlin.Voltkit
import `in`.voltkit.livedemo.kotlin.data.Api
import `in`.voltkit.livedemo.kotlin.data.ApiError
import `in`.voltkit.livedemo.kotlin.data.GuestPublic
import `in`.voltkit.livedemo.kotlin.data.LiveSocket
import `in`.voltkit.livedemo.kotlin.data.MatchFound
import `in`.voltkit.livedemo.kotlin.data.Session
import `in`.voltkit.livedemo.kotlin.rtc.CallService
import `in`.voltkit.livedemo.kotlin.rtc.LocalVideo
import `in`.voltkit.livedemo.kotlin.rtc.PermissionAsker
import `in`.voltkit.livedemo.kotlin.rtc.Problems
import `in`.voltkit.livedemo.kotlin.rtc.RemoteVideo
import `in`.voltkit.livedemo.kotlin.rtc.askMediaConsent
import `in`.voltkit.livedemo.kotlin.rtc.copySoundReport
import `in`.voltkit.livedemo.kotlin.screens.room.showReportSheet
import `in`.voltkit.livedemo.kotlin.ui.Avatar
import `in`.voltkit.livedemo.kotlin.ui.Backdrop
import `in`.voltkit.livedemo.kotlin.ui.C
import `in`.voltkit.livedemo.kotlin.ui.Field
import `in`.voltkit.livedemo.kotlin.ui.G
import `in`.voltkit.livedemo.kotlin.ui.GhostButton
import `in`.voltkit.livedemo.kotlin.ui.GradientButton
import `in`.voltkit.livedemo.kotlin.ui.Icon
import `in`.voltkit.livedemo.kotlin.ui.IconBtn
import `in`.voltkit.livedemo.kotlin.ui.Ion
import `in`.voltkit.livedemo.kotlin.ui.PoweredBy
import `in`.voltkit.livedemo.kotlin.ui.R2
import `in`.voltkit.livedemo.kotlin.ui.SheetHost
import `in`.voltkit.livedemo.kotlin.ui.SheetRow
import `in`.voltkit.livedemo.kotlin.ui.T
import `in`.voltkit.livedemo.kotlin.ui.ToastKind
import `in`.voltkit.livedemo.kotlin.ui.V
import `in`.voltkit.livedemo.kotlin.ui.toast
import `in`.voltkit.livedemo.kotlin.ui.vertical
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.webrtc.VideoTrack

private const val PEER_AWAY_MS = 30_000L

class CallLine(val id: String, val mine: Boolean, val text: String)

/**
 * One random 1v1 call: a VoltKit room with two people, made by the backend's matchmaker. A closed session stays closed, so each
 * call gets its own client.
 */
class CallSession(context: Context, val call: MatchFound, private val onPeerLeft: () -> Unit, private val onStop: () -> Unit) {
    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val client = StreamClient(app, call.wsUrl, call.token, StreamOptions(pauseCameraInBackground = true))
    val peer: GuestPublic get() = call.peer

    var state by mutableStateOf(ConnectionState.idle); private set
    var local by mutableStateOf(LocalMediaState()); private set
    var localVideo by mutableStateOf<VideoTrack?>(null); private set
    var peerHere by mutableStateOf(false); private set
    var seconds by mutableIntStateOf(0); private set

    /** Whether there is another camera to flip to. Phones with one camera hide the button. */
    var canFlip by mutableStateOf(true); private set
    val lines = mutableStateListOf<CallLine>()
    private var videoTrack by mutableStateOf<RemoteTrack?>(null)
    private var audioTrack by mutableStateOf<RemoteTrack?>(null)

    /** RemoteTrack.paused is kept current by the SDK but is not Compose state: this ticks when it changes. */
    private var pauseTick by mutableIntStateOf(0)
    private var ticker: Job? = null
    private var away: Job? = null
    private var closed = false

    val video: RemoteTrack? get() = pauseTick.let { videoTrack?.takeIf { !it.paused } }
    val peerMuted: Boolean get() = pauseTick.let { audioTrack?.paused ?: false }

    private fun <T> watch(flow: Flow<T>, block: suspend (T) -> Unit) {
        scope.launch { flow.collect { runCatching { block(it) } } }
    }

    init {
        watch(client.connectionState) { state = it }
        watch(client.onClosed) { if (it != ClosedReason.left && !closed) onPeerLeft() }
        watch(client.onTrack) { t ->
            if (t.identity != peer.id) return@watch
            if (t.source == "camera") videoTrack = t
            if (t.source == "mic") audioTrack = t
        }
        watch(client.onTrackEnded) { e ->
            if (videoTrack?.producerId == e.producerId) videoTrack = null
            if (audioTrack?.producerId == e.producerId) audioTrack = null
        }
        watch(client.onTrackMuted) { if (it.identity == peer.id) pauseTick++ }
        watch(client.localMedia) { local = it }
        watch(client.localVideoTrack) { localVideo = it }
        // A drop may be a network blip: their SDK reconnects. Give it time, as VoltKit's call guide does, so a peer whose app
        // was killed does not leave this side waiting forever.
        watch(client.onParticipantLeft) { e ->
            if (e.identity != peer.id) return@watch
            markPeerHere(false)
            if (e.reason == "disconnected" || e.reason == "presence-lost") {
                away?.cancel()
                away = scope.launch {
                    delay(PEER_AWAY_MS)
                    onPeerLeft()
                }
            } else {
                onPeerLeft()
            }
        }
        watch(client.onParticipantJoined) { e ->
            if (e.identity != peer.id) return@watch
            away?.cancel()
            markPeerHere(true)
        }
        watch(client.onMessage) { m ->
            val text = m.text
            if (m.type != "text" || text.isNullOrEmpty()) return@watch
            lines.add(0, CallLine(m.id, m.from != peer.id, text))
            while (lines.size > 40) lines.removeAt(lines.lastIndex)
        }
        join()
    }

    private fun markPeerHere(here: Boolean) {
        peerHere = here
        ticker?.cancel()
        ticker = if (here) {
            scope.launch {
                while (isActive) {
                    delay(1000)
                    seconds++
                }
            }
        } else {
            null
        }
    }

    private fun join() {
        scope.launch {
            try {
                client.connect(null)
                if (client.participants.value.any { it.identity == peer.id }) markPeerHere(true)
                CallService.start(app, "${Voltkit.APP_NAME} call", "Tap to return to the call", "End call") { onStop() }
                client.publish(PublishOptions(encoding = VideoPublishOptions(quality = VideoQuality.p720)))
                canFlip = runCatching { client.listDevices(DeviceKind.videoinput).size }.getOrDefault(0) != 1
                // The service takes the microphone type now the permission is there.
                CallService.start(app, "${Voltkit.APP_NAME} call", "Tap to return to the call", "End call") { onStop() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: RpcError) {
                if (e.code in setOf("source-already-published", "closed", "unpublished")) return@launch
                val problem = Problems.media(e)
                toast(problem.text, ToastKind.Error)
                if (problem.openSettings) PermissionAsker.openSettings()
            } catch (e: Exception) {
                toast(Problems.media(e).text, ToastKind.Error)
            }
        }
    }

    // A failed switch leaves the button as it was, and force-mute never happens in a call.
    private fun quiet(block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("CallSession", "media switch failed", e)
            }
        }
    }

    fun toggleMic() = quiet { client.setMicEnabled(!local.micEnabled) }
    fun toggleCam() = quiet { client.setCamEnabled(!local.camEnabled) }
    fun flipCamera() = quiet { client.switchCamera() }

    fun send(text: String) {
        scope.launch {
            try {
                client.sendMessage(text, null, null, null, null, false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toast("Your message was not sent.", ToastKind.Error)
            }
        }
    }

    fun close() {
        if (closed) return
        closed = true
        ticker?.cancel()
        away?.cancel()
        scope.cancel()
        CallService.stop(app)
        client.close()
    }
}

enum class MatchPhase { Searching, Call, Ended }

/** Random 1v1 video calls. The backend's matchmaker makes the room and sends each phone its token over Socket.IO. */
class MatchViewModel(private val app: Application) : ViewModel() {
    var phase by mutableStateOf(MatchPhase.Searching); private set
    var session by mutableStateOf<CallSession?>(null); private set
    var waiting by mutableIntStateOf(0); private set
    var lastPeer by mutableStateOf<String?>(null); private set

    /** Set when the screen should go: the person said no, matching failed, or they ended it from the notification. */
    var done by mutableStateOf(false); private set
    private var started = false
    private val off = mutableListOf<() -> Unit>()

    fun start() {
        if (started) return
        started = true
        off += LiveSocket.on("match:found") { payload ->
            val found = (payload as? JSONObject)?.let { runCatching { MatchFound.from(it) }.getOrNull() } ?: return@on
            session?.close()
            session = CallSession(app, found, onPeerLeft = ::peerLeft, onStop = ::stop)
            phase = MatchPhase.Call
        }
        off += LiveSocket.on("match:waiting") { payload -> waiting = (payload as? JSONObject)?.optInt("waiting") ?: 0 }
        off += LiveSocket.on("match:ended") { payload ->
            val current = session ?: return@on
            if ((payload as? JSONObject)?.optString("callId") != current.call.callId) return@on
            lastPeer = current.peer.name
            current.close()
            session = null
            phase = MatchPhase.Ended
        }
        off += LiveSocket.on("match:error") { payload ->
            toast((payload as? JSONObject)?.optString("message")?.ifEmpty { null } ?: "Matching failed.", ToastKind.Error)
            done = true
        }
        viewModelScope.launch {
            if (askMediaConsent("video")) search() else done = true
        }
    }

    fun search() {
        hangUp()
        phase = MatchPhase.Searching
        LiveSocket.emit("match:join")
    }

    private fun hangUp() {
        val current = session ?: return
        LiveSocket.emit("match:end", JSONObject().put("callId", current.call.callId))
        current.close()
        session = null
    }

    private fun peerLeft() {
        lastPeer = session?.peer?.name
        hangUp()
        phase = MatchPhase.Ended
    }

    fun next() = search()

    fun stop() {
        hangUp()
        done = true
    }

    override fun onCleared() {
        off.forEach { it() }
        LiveSocket.emit("match:leave")
        hangUp()
    }
}

@Composable
fun MatchScreen() {
    val app = LocalContext.current.applicationContext as Application
    val vm = viewModel { MatchViewModel(app) }
    val nav = LocalNav.current
    val view = LocalView.current
    val activity = LocalContext.current as? Activity
    LaunchedEffect(Unit) { vm.start() }
    LaunchedEffect(vm.done) { if (vm.done) nav.popBackStack() }
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        activity?.volumeControlStream = AudioManager.STREAM_VOICE_CALL
        onDispose {
            view.keepScreenOn = false
            activity?.volumeControlStream = AudioManager.USE_DEFAULT_STREAM_TYPE
        }
    }
    val session = vm.session
    if (session != null) {
        androidx.compose.runtime.key(session.call.callId) { CallView(session, onNext = vm::next, onStop = vm::stop) }
        return
    }
    val top = topInset()
    val bottom = bottomInset()
    Backdrop(modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(top = top + 8.dp, bottom = bottom + 20.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Box(Modifier.padding(horizontal = 14.dp)) {
                IconBtn(Ion.close, "Close", { nav.popBackStack() }, 40.dp, id = "match-close")
            }
            Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                if (vm.phase == MatchPhase.Searching) Radar()
                Box(Modifier.size(104.dp).background(vertical(G.match), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Ion.videocam, 38.dp)
                }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (vm.phase == MatchPhase.Searching) {
                    T("Finding someone…", V.Title, id = "match-status")
                    Spacer(Modifier.height(8.dp))
                    T(
                        if (vm.waiting > 1) "${vm.waiting} people are looking right now" else "You will be connected as soon as someone else joins",
                        color = C.textDim,
                        align = TextAlign.Center,
                    )
                } else {
                    T(vm.lastPeer?.let { "$it left" } ?: "Call ended", V.Title, align = TextAlign.Center, id = "match-status")
                    Spacer(Modifier.height(20.dp))
                    GradientButton("Find someone new", { vm.search() }, Modifier.fillMaxWidth(), icon = Ion.shuffle, colors = G.match, id = "match-again")
                    Spacer(Modifier.height(8.dp))
                    GhostButton("Back to home", { nav.popBackStack() }, Modifier.fillMaxWidth())
                }
            }
            PoweredBy()
        }
    }
}

@Composable
private fun Radar() {
    val p by rememberInfiniteTransition(label = "radar").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart), label = "radar",
    )
    Box(contentAlignment = Alignment.Center) {
        for (i in 0 until 3) {
            val t = EaseOutQuad.transform((p + i / 3f) % 1f)
            Box(
                Modifier
                    .size(230.dp)
                    .graphicsLayer {
                        alpha = 0.75f * (1 - t)
                        scaleX = 0.45f + 1.05f * t
                        scaleY = 0.45f + 1.05f * t
                    }
                    .border(2.dp, C.pink, CircleShape),
            )
        }
    }
}

private fun showSafety(session: CallSession, onBlocked: () -> Unit) {
    val peer = session.peer
    SheetHost.show("call-safety", "Safety") {
        val context = LocalContext.current.applicationContext
        Column {
            SheetRow(
                Ion.pulseOutline,
                "Copy a sound report",
                { close(); kotlinx.coroutines.MainScope().launch { copySoundReport(context, session.client, loudspeaker = true) } },
                hint = "Echo or other sound trouble? Send this to VoltKit",
                id = "call-sound-report",
            )
            SheetRow(Ion.flagOutline, "Report ${peer.name}", { close(); showReportSheet(peer.id, peer.name) }, id = "call-report")
            SheetRow(
                Ion.banOutline, "Block ${peer.name}",
                {
                    close()
                    kotlinx.coroutines.MainScope().launch {
                        try {
                            Api.block(peer.id)
                            Session.refresh()
                            toast("${peer.name} is blocked. You will not be matched again.", ToastKind.Success)
                            onBlocked()
                        } catch (e: ApiError) {
                            toast(e.message, ToastKind.Error)
                        }
                    }
                },
                hint = "You will not be matched again",
                danger = true,
                id = "call-block",
            )
        }
    }
}

@Composable
private fun CallView(session: CallSession, onNext: () -> Unit, onStop: () -> Unit) {
    val peer = session.peer
    val top = topInset()
    val bottom = bottomInset()
    val keyboard = keyboardHeight()
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    var typing by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf(TextFieldValue("")) }
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var keyboardWasUp by remember { mutableStateOf(false) }
    LaunchedEffect(typing) { if (typing) focus.requestFocus() }
    LaunchedEffect(keyboard > 0.dp) {
        if (keyboard > 0.dp) {
            keyboardWasUp = true
        } else if (keyboardWasUp) {
            keyboardWasUp = false
            typing = false
        }
    }
    fun send() {
        val body = text.text.trim()
        if (body.isNotEmpty()) session.send(body)
        text = TextFieldValue("")
        focusManager.clearFocus()
        typing = false
    }
    BackHandler(typing) {
        focusManager.clearFocus()
        typing = false
    }
    // Back in a call ends it, as the red button does.
    BackHandler(!typing) { onStop() }

    val video = session.video
    val local = session.local
    val camera = session.localVideo?.takeIf { local.camEnabled }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (video != null) {
            androidx.compose.runtime.key(video.producerId) { RemoteVideo(session.client, video, Modifier.fillMaxSize().testTag("remote-video")) }
        } else {
            Backdrop(modifier = Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Avatar(peer.avatar, peer.frame, 120.dp)
                    Spacer(Modifier.height(40.dp))
                    T(if (session.peerHere) "${peer.name} has their camera off" else "Connecting to ${peer.name}…", V.Heading, id = "call-waiting")
                }
            }
        }
        if (camera != null) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-12).dp, y = top + 64.dp)
                    .size(104.dp, 150.dp)
                    .clip(RoundedCornerShape(R2.sm))
                    .background(C.plum)
                    .border(1.5.dp, C.white, RoundedCornerShape(R2.sm)),
            ) {
                LocalVideo(camera, mirror = local.frontCamera, modifier = Modifier.fillMaxSize().testTag("local-video"))
            }
        }
        Box(Modifier.fillMaxWidth().height(130.dp).background(vertical(listOf(Color(0xBF050309), Color(0x00050309)))))
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = top + 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.background(Color(0x73000000), RoundedCornerShape(R2.pill)).padding(start = 3.dp, end = 14.dp, top = 3.dp, bottom = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(peer.avatar, size = 34.dp)
                Spacer(Modifier.width(8.dp))
                Column {
                    T(peer.name, V.Small)
                    T("${if (session.peerMuted) "Muted · " else ""}${clock(session.seconds)}", V.Tiny, C.textDim, id = "call-clock")
                }
            }
            Spacer(Modifier.weight(1f))
            IconBtn(Ion.shieldHalf, "Safety", { showSafety(session, onNext) }, 38.dp, id = "call-menu")
        }
        if (session.state == ConnectionState.reconnecting) {
            Box(Modifier.align(Alignment.Center).background(Color(0xA6000000), RoundedCornerShape(R2.pill)).padding(horizontal = 14.dp, vertical = 7.dp)) {
                T("Reconnecting…", V.Small)
            }
        }
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(vertical(G.scrimBottom))
                .padding(start = 12.dp, end = 12.dp, top = 36.dp, bottom = (if (keyboard > 0.dp) keyboard else bottom) + 10.dp),
        ) {
            LazyColumn(
                Modifier.fillMaxWidth().heightIn(max = 160.dp).testTag("call-chat"),
                reverseLayout = true,
                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.Bottom),
                contentPadding = PaddingValues(0.dp),
            ) {
                items(session.lines, key = { it.id }) { line ->
                    Box(Modifier.fillMaxWidth(), contentAlignment = if (line.mine) Alignment.CenterEnd else Alignment.CenterStart) {
                        Box(
                            Modifier
                                .widthIn(max = screenWidth * 0.8f)
                                .background(if (line.mine) Color(0xB88B5CF6) else Color(0x80000000), RoundedCornerShape(R2.md))
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                        ) { T("${if (line.mine) "" else "${peer.name}: "}${line.text}", V.Small) }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            if (typing) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Field(
                        text, { text = it }, Modifier.weight(1f),
                        id = "call-input",
                        hint = "Message ${peer.name}",
                        maxLength = 280,
                        height = 44.dp,
                        radius = R2.pill,
                        fontSize = 15,
                        weight = FontWeight.Normal,
                        fill = Color(0x24FFFFFF),
                        action = ImeAction.Send,
                        onSubmit = { send() },
                        focus = focus,
                    )
                    Spacer(Modifier.width(8.dp))
                    IconBtn(Ion.send, "Send", { send() }, 42.dp, active = true, id = "call-send")
                }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    val micOn = local.micEnabled && local.publishing
                    val camOn = local.camEnabled && local.publishing
                    IconBtn(if (micOn) Ion.mic else Ion.micOff, if (micOn) "Mute" else "Unmute", { session.toggleMic() }, 50.dp, danger = !micOn, id = "call-mic")
                    IconBtn(if (camOn) Ion.videocam else Ion.videocamOff, if (camOn) "Camera off" else "Camera on", { session.toggleCam() }, 50.dp, danger = !camOn, id = "call-cam")
                    if (session.canFlip) IconBtn(Ion.cameraReverse, "Flip camera", { session.flipCamera() }, 50.dp, id = "call-flip")
                    IconBtn(Ion.chatbubbleEllipses, "Send a message", { typing = true }, 50.dp, id = "call-chat-open")
                    IconBtn(Ion.playSkipForward, "Next person", onNext, 50.dp, active = true, id = "call-next")
                    IconBtn(Ion.call, "End call", onStop, 50.dp, danger = true, turns = 135f / 360f, id = "call-end")
                }
            }
        }
    }
}
