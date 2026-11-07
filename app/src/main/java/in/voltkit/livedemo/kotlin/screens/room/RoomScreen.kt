package `in`.voltkit.livedemo.kotlin.screens.room

import android.app.Activity
import android.app.Application
import android.media.AudioManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.voltkit.rtc.ClosedReason
import com.voltkit.rtc.ConnectionState
import com.voltkit.rtc.Role
import `in`.voltkit.livedemo.kotlin.Voltkit
import `in`.voltkit.livedemo.kotlin.data.Api
import `in`.voltkit.livedemo.kotlin.data.ApiError
import `in`.voltkit.livedemo.kotlin.data.Assets
import `in`.voltkit.livedemo.kotlin.data.RoomSession
import `in`.voltkit.livedemo.kotlin.data.Session
import `in`.voltkit.livedemo.kotlin.gifts.SvgaFiles
import `in`.voltkit.livedemo.kotlin.gifts.showGiftSheet
import `in`.voltkit.livedemo.kotlin.rtc.Problems
import `in`.voltkit.livedemo.kotlin.rtc.RoomEngine
import `in`.voltkit.livedemo.kotlin.rtc.Rules
import `in`.voltkit.livedemo.kotlin.screens.LocalNav
import `in`.voltkit.livedemo.kotlin.screens.Routes
import `in`.voltkit.livedemo.kotlin.screens.bottomInset
import `in`.voltkit.livedemo.kotlin.screens.compact
import `in`.voltkit.livedemo.kotlin.screens.keyboardHeight
import `in`.voltkit.livedemo.kotlin.screens.topInset
import `in`.voltkit.livedemo.kotlin.ui.Avatar
import `in`.voltkit.livedemo.kotlin.ui.Backdrop
import `in`.voltkit.livedemo.kotlin.ui.C
import `in`.voltkit.livedemo.kotlin.ui.Field
import `in`.voltkit.livedemo.kotlin.ui.GhostButton
import `in`.voltkit.livedemo.kotlin.ui.GradientButton
import `in`.voltkit.livedemo.kotlin.ui.Icon
import `in`.voltkit.livedemo.kotlin.ui.IconBtn
import `in`.voltkit.livedemo.kotlin.ui.Ion
import `in`.voltkit.livedemo.kotlin.ui.LivePill
import `in`.voltkit.livedemo.kotlin.ui.PoweredBy
import `in`.voltkit.livedemo.kotlin.ui.R2
import `in`.voltkit.livedemo.kotlin.ui.SheetHost
import `in`.voltkit.livedemo.kotlin.ui.Spinner
import `in`.voltkit.livedemo.kotlin.ui.T
import `in`.voltkit.livedemo.kotlin.ui.TagChip
import `in`.voltkit.livedemo.kotlin.ui.ToastKind
import `in`.voltkit.livedemo.kotlin.ui.V
import `in`.voltkit.livedemo.kotlin.ui.openUrl
import `in`.voltkit.livedemo.kotlin.ui.tap
import `in`.voltkit.livedemo.kotlin.ui.toast
import `in`.voltkit.livedemo.kotlin.ui.vertical
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** A room just created: its session goes straight to the room screen instead of joining a second time. */
object PendingRoom {
    private val sessions = mutableMapOf<String, RoomSession>()
    fun put(session: RoomSession) {
        sessions[session.room.id] = session
    }

    fun take(roomId: String): RoomSession? = sessions.remove(roomId)
}

/** Asks the app to take a room off the screen, wherever it is in the back stack: leaving from the notification does this. */
object RoomExit {
    var request by mutableStateOf<String?>(null)
}

data class Ended(val text: String, val canRejoin: Boolean)

private val GONE = setOf("room-ended", "banned", "room-full", "rtc-unavailable")

/**
 * Owns a room session: gets a token, makes the engine, and decides what to show when it ends. Lives as long as the room is in
 * the back stack, so a chat opened over the room keeps it going. What happens inside a room is in rtc/RoomEngine.kt.
 */
class RoomViewModel(private val app: Application, private val roomId: String) : ViewModel() {
    var engine by mutableStateOf<RoomEngine?>(null); private set
    var ended by mutableStateOf<Ended?>(null); private set
    private var exiting = false

    init {
        val pending = PendingRoom.take(roomId)
        if (pending != null) begin(pending) else join()
    }

    private fun begin(session: RoomSession) {
        // A closed session stays closed, so rejoining needs a new client: a new engine is how that happens.
        val next = RoomEngine(
            app, session.room, Session.guest, session.credentials,
            isBlocked = { Session.guestOrNull?.blocked?.contains(it) == true },
            onClosed = ::onClosed,
            onFailed = ::onFailed,
        )
        engine = next
        next.start()
    }

    private fun dropEngine() {
        engine?.dispose()
        engine = null
    }

    fun join() {
        ended = null
        dropEngine()
        viewModelScope.launch {
            try {
                begin(Api.joinRoom(roomId))
            } catch (e: ApiError) {
                ended = Ended(e.message, e.code !in GONE)
            }
        }
    }

    private fun onClosed(reason: ClosedReason) {
        if (exiting) return
        SheetHost.closeAll()
        // Left from the notification: the room goes, even from under a chat opened over it.
        if (reason == ClosedReason.left) {
            exiting = true
            RoomExit.request = roomId
            return
        }
        dropEngine()
        ended = Ended(Problems.closed(reason), reason == ClosedReason.gaveUp)
    }

    private fun onFailed(err: Throwable) {
        val problem = Problems.join(err)
        SheetHost.closeAll()
        dropEngine()
        ended = Ended(problem.text, problem.canRetry)
    }

    /** Leaving on purpose. A host's leaving ends the room for everyone. */
    suspend fun exit() {
        exiting = true
        engine?.takeIf { it.isHost }?.endRoom()
    }

    override fun onCleared() {
        exiting = true
        dropEngine()
    }
}

@Composable
fun RoomScreen(roomId: String) {
    val app = LocalContext.current.applicationContext as Application
    val vm = viewModel(key = "room-$roomId") { RoomViewModel(app, roomId) }
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val activity = LocalContext.current as? Activity
    // A live room keeps the screen awake, and the volume keys change the call's volume.
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        activity?.volumeControlStream = AudioManager.STREAM_VOICE_CALL
        onDispose {
            view.keepScreenOn = false
            activity?.volumeControlStream = AudioManager.USE_DEFAULT_STREAM_TYPE
        }
    }

    val ended = vm.ended
    val engine = vm.engine
    when {
        ended != null -> Backdrop(modifier = Modifier.fillMaxSize()) {
            Column(
                Modifier.fillMaxSize().padding(horizontal = 40.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Ion.radioOutline, 46.dp, C.magenta)
                T(ended.text, V.Title, align = TextAlign.Center, id = "room-ended-text")
                if (ended.canRejoin) GradientButton("Join again", { vm.join() }, Modifier.fillMaxWidth(), id = "room-ended-rejoin")
                GhostButton("Back to home", { nav.popBackStack() }, Modifier.fillMaxWidth(), id = "room-ended-home")
            }
        }
        engine == null -> Backdrop(modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Spinner(size = 36.dp)
                Spacer(Modifier.height(14.dp))
                T("Joining…", color = C.textDim, id = "room-joining")
            }
        }
        else -> RoomView(engine) {
            scope.launch {
                vm.exit()
                nav.popBackStack()
            }
        }
    }
}

@Composable
private fun RoomView(engine: RoomEngine, onExit: () -> Unit) {
    val context = LocalContext.current
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val room = engine.room
    var draft by remember { mutableStateOf(TextFieldValue("")) }
    var composing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { SvgaFiles.preload(context) }
    // The host's invitation: asked once, answered here.
    LaunchedEffect(engine) {
        snapshotFlow { engine.invitedTo }.collect { slot ->
            if (slot == null) return@collect
            if (askInvite(room.isVideo)) engine.acceptInvite() else engine.declineInvite()
        }
    }

    fun confirmExit() {
        scope.launch { if (confirmEnd(engine.isHost)) onExit() }
    }

    fun openThread(identity: String) {
        scope.launch {
            try {
                val c = Api.startConversation(identity)
                nav.navigate(Routes.thread(c.id, c.peer))
            } catch (e: ApiError) {
                toast(e.message, ToastKind.Error)
            }
        }
    }

    fun gift(to: String? = null) {
        fun place(identity: String, seat: String?) = if (identity == room.host.id) 0 else if (seat != null) 1 else 2
        val people = engine.members.sortedBy { place(it.identity, it.seat) }.take(40)
        showGiftSheet(room, people, to)
    }

    fun member(identity: String, messageId: String? = null) {
        showMemberSheet(engine, identity, messageId, onGift = { gift(it) }, onMessage = { openThread(it) })
    }

    fun people() {
        showPeopleSheet(engine) { member(it) }
    }

    fun more() = showMoreSheet(
        engine,
        onLeave = { confirmExit() },
        onPeople = { people() },
        onReportRoom = { showReportSheet(room.host.id, room.title, room.id) },
        onSite = { openUrl(context, Voltkit.SITE) },
    )

    // The room is only left on purpose: a stray back press should not end someone's live. Open sheets close first.
    BackHandler { confirmExit() }

    BoxWithConstraints(Modifier.fillMaxSize().background(C.bg)) {
        val top = topInset()
        val bottom = bottomInset()
        // Header, gaps and controls take the rest; the chat gets whatever is left under the stage.
        val stageHeight = ((maxHeight - top - bottom).value * 0.58f).roundToInt().toFloat()
        val stageWidth = maxWidth.value - 12f
        Image(painterResource(Assets.cover(room.theme)), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(vertical(listOf(Color(0xD1080610), Color(0x94080610), Color(0xF0080610)))))
        Column(Modifier.fillMaxSize().padding(top = top + 6.dp, bottom = bottom + 6.dp)) {
            Header(engine, onPeople = { people() }, onMore = { more() }, onHost = { member(room.host.id) })
            Banners(engine)
            Box(Modifier.padding(start = if (room.isVideo) 6.dp else 0.dp, end = if (room.isVideo) 6.dp else 0.dp, top = 8.dp)) {
                if (room.isVideo) {
                    VideoStage(engine, stageWidth, stageHeight, onMember = { member(it) }, onSeat = { showSeatSheet(engine, it) })
                } else {
                    AudioStage(engine, onMember = { member(it) }, onSeat = { showSeatSheet(engine, it) })
                }
            }
            Spacer(Modifier.height(6.dp))
            RoomChat(engine, Modifier.weight(1f).fillMaxWidth()) { line -> line.from?.let { member(it, line.id) } }
            PoweredBy()
            Spacer(Modifier.height(4.dp))
            ControlBar(engine, draft.text, onChat = { composing = true }, onGift = { gift() }, onHeart = { engine.sendHeart() }, onMore = { more() })
        }
        Reactions(engine, Modifier.fillMaxSize())
        `in`.voltkit.livedemo.kotlin.gifts.GiftStage(engine)
        if (composing) {
            Composer(
                draft,
                onDraft = { draft = it },
                onClose = { composing = false },
                onSend = { text ->
                    engine.sendChat(text)
                    draft = TextFieldValue("")
                },
            )
        }
    }
}

@Composable
private fun Header(engine: RoomEngine, onPeople: () -> Unit, onMore: () -> Unit, onHost: () -> Unit) {
    val room = engine.room
    val pending = if (Rules.canModerate(engine.myRole)) engine.hands.size else 0
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f)) {
            Row(
                Modifier
                    .tap(onHost, "Host", "room-host")
                    .background(Color(0x6B000000), RoundedCornerShape(R2.pill))
                    .padding(start = 3.dp, end = 12.dp, top = 3.dp, bottom = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(room.host.avatar, size = 36.dp)
                Spacer(Modifier.width(7.dp))
                Column(Modifier.widthIn(max = 150.dp)) {
                    T(room.title, V.Small, lines = 1)
                    T(room.host.name, V.Tiny, C.textDim, lines = 1)
                }
            }
        }
        Spacer(Modifier.width(6.dp))
        TagChip(compact(engine.roomCoins), Ion.trophy, C.gold)
        Spacer(Modifier.width(6.dp))
        LivePill()
        Spacer(Modifier.width(6.dp))
        IconBtn(Ion.people, "People, ${engine.members.size}", onPeople, 34.dp, badge = pending, id = "room-people")
        Spacer(Modifier.width(6.dp))
        IconBtn(Ion.ellipsisHorizontal, "Room menu", onMore, 34.dp, id = "room-more")
    }
}

@Composable
private fun Banners(engine: RoomEngine) {
    val reconnecting = engine.state == ConnectionState.reconnecting
    if (!reconnecting && !engine.walletLow) return
    Row(
        Modifier
            .padding(start = 12.dp, end = 12.dp, top = 6.dp)
            .fillMaxWidth()
            .background(if (reconnecting) Color(0x99000000) else Color(0xD9FB3B66), RoundedCornerShape(R2.sm))
            .padding(vertical = 6.dp)
            .testTag(if (reconnecting) "banner-reconnecting" else "banner-wallet"),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (reconnecting) {
            Spinner(C.white, 16.dp)
            Spacer(Modifier.width(8.dp))
            T("Reconnecting…", V.Small)
        } else {
            T("Demo credit is running out. This room may end soon.", V.Small)
        }
    }
}

@Composable
private fun ControlBar(engine: RoomEngine, draft: String, onChat: () -> Unit, onGift: () -> Unit, onHeart: () -> Unit, onMore: () -> Unit) {
    val video = engine.room.isVideo
    val muted = engine.iMutedInChat
    val local = engine.local
    val micLive = local.publishing && local.micEnabled
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .weight(1f)
                .tap(if (muted) null else onChat, "Write a message", "chat-open")
                .height(42.dp)
                .background(C.glassStrong, RoundedCornerShape(R2.pill))
                .padding(horizontal = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(if (muted) Ion.lockClosed else Ion.chatbubbleEllipses, 15.dp, C.textDim)
            Spacer(Modifier.width(7.dp))
            T(if (muted) "Muted" else draft.ifEmpty { "Say hi…" }, V.Small, if (draft.isEmpty() || muted) C.textDim else C.white, lines = 1)
        }
        if (Rules.canPublish(engine.myRole)) {
            IconBtn(
                if (micLive) Ion.mic else Ion.micOff, if (local.micEnabled) "Mute" else "Unmute", { engine.toggleMic() }, 42.dp,
                danger = !micLive, id = "ctl-mic",
            )
            if (video) {
                IconBtn(
                    if (local.camEnabled) Ion.videocam else Ion.videocamOff, if (local.camEnabled) "Camera off" else "Camera on",
                    { engine.toggleCam() }, 42.dp, danger = !local.camEnabled, id = "ctl-cam",
                )
                if (engine.canFlip) IconBtn(Ion.cameraReverse, "Flip camera", { engine.flipCamera() }, 42.dp, id = "ctl-flip")
            }
        } else {
            if (engine.myRole == Role.viewer) {
                IconBtn(
                    Ion.handLeft, if (engine.myHand) "Cancel request" else "Ask to join the stage",
                    { if (engine.myHand) engine.cancelRequest() else engine.requestSeat() }, 42.dp,
                    active = engine.myHand, id = "ctl-hand",
                )
            }
            IconBtn(Ion.heart, "Send a heart", onHeart, 42.dp, tint = C.pink, id = "ctl-heart")
        }
        IconBtn(Ion.gift, "Send a gift", onGift, 42.dp, tint = C.gold, id = "ctl-gift")
        IconBtn(Ion.grid, "Room menu", onMore, 42.dp, id = "ctl-more")
    }
}

/**
 * The message box, above the keyboard. When the keyboard goes away it closes too and the draft stays in the "Say hi" pill, so
 * what was typed is neither hidden nor lost.
 */
@Composable
private fun Composer(draft: TextFieldValue, onDraft: (TextFieldValue) -> Unit, onClose: () -> Unit, onSend: (String) -> Unit) {
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = keyboardHeight()
    val bottom = bottomInset()
    var keyboardWasUp by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        onDraft(draft.copy(selection = TextRange(draft.text.length)))
        focus.requestFocus()
    }
    LaunchedEffect(keyboard > 0.dp) {
        if (keyboard > 0.dp) {
            keyboardWasUp = true
        } else if (keyboardWasUp) {
            keyboardWasUp = false
            onClose()
        }
    }
    fun dismiss() {
        focusManager.clearFocus()
        onClose()
    }
    fun send() {
        val body = draft.text.trim()
        if (body.isNotEmpty()) onSend(body)
        dismiss()
    }
    BackHandler { dismiss() }
    Column(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clickable(remember { MutableInteractionSource() }, null) { dismiss() },
        )
        Row(
            Modifier
                .padding(bottom = if (keyboard > 0.dp) keyboard else bottom)
                .fillMaxWidth()
                .background(C.plum)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Field(
                draft, onDraft, Modifier.weight(1f),
                id = "chat-input",
                hint = "Say something nice",
                maxLength = 280,
                height = 42.dp,
                radius = R2.pill,
                fontSize = 15,
                weight = FontWeight.Normal,
                action = ImeAction.Send,
                onSubmit = { send() },
                focus = focus,
            )
            Spacer(Modifier.width(8.dp))
            IconBtn(Ion.send, "Send", { send() }, 40.dp, active = true, id = "chat-send")
        }
    }
}
