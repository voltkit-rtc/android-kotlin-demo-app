package `in`.voltkit.livedemo.kotlin.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.voltkit.rtc.ChatClient
import com.voltkit.rtc.ChatMessage
import com.voltkit.rtc.ClosedReason
import `in`.voltkit.livedemo.kotlin.data.Api
import `in`.voltkit.livedemo.kotlin.data.ApiError
import `in`.voltkit.livedemo.kotlin.data.ChatChannel
import `in`.voltkit.livedemo.kotlin.data.ConversationSummary
import `in`.voltkit.livedemo.kotlin.data.GuestPublic
import `in`.voltkit.livedemo.kotlin.data.LiveSocket
import `in`.voltkit.livedemo.kotlin.data.Session
import `in`.voltkit.livedemo.kotlin.screens.room.showReportSheet
import `in`.voltkit.livedemo.kotlin.ui.Avatar
import `in`.voltkit.livedemo.kotlin.ui.Backdrop
import `in`.voltkit.livedemo.kotlin.ui.C
import `in`.voltkit.livedemo.kotlin.ui.Field
import `in`.voltkit.livedemo.kotlin.ui.G
import `in`.voltkit.livedemo.kotlin.ui.GhostButton
import `in`.voltkit.livedemo.kotlin.ui.Icon
import `in`.voltkit.livedemo.kotlin.ui.IconBtn
import `in`.voltkit.livedemo.kotlin.ui.Ion
import `in`.voltkit.livedemo.kotlin.ui.R2
import `in`.voltkit.livedemo.kotlin.ui.SheetHost
import `in`.voltkit.livedemo.kotlin.ui.SheetRow
import `in`.voltkit.livedemo.kotlin.ui.Spinner
import `in`.voltkit.livedemo.kotlin.ui.T
import `in`.voltkit.livedemo.kotlin.ui.ToastKind
import `in`.voltkit.livedemo.kotlin.ui.V
import `in`.voltkit.livedemo.kotlin.ui.diagonal
import `in`.voltkit.livedemo.kotlin.ui.hairline
import `in`.voltkit.livedemo.kotlin.ui.tap
import `in`.voltkit.livedemo.kotlin.ui.toast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

// Chat outside a room: the inbox, the Public Lounge and direct messages, each over a chat-only ChatClient. Room chat is in
// screens/room/RoomChat.kt.

@Composable
fun InboxScreen(focus: Int) {
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf(emptyList<ConversationSummary>()) }
    var loaded by remember { mutableStateOf(false) }
    suspend fun load() {
        try {
            list = Api.conversations()
        } catch (e: ApiError) {
            // The lounge still works, and the next focus tries again.
        }
        loaded = true
    }
    LaunchedEffect(focus) { load() }
    DisposableEffect(Unit) {
        val off = LiveSocket.on("dm:new") { scope.launch { load() } }
        onDispose { off() }
    }
    val top = topInset()
    Backdrop(modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().testTag("inbox-list"), contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = top + 12.dp, bottom = 110.dp)) {
            item {
                Column(Modifier.padding(bottom = 14.dp)) {
                    T("Chat", V.Display)
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .tap({ nav.navigate(Routes.LOUNGE) }, "Public lounge", "open-lounge", pressedScale = 0.98f)
                            .background(diagonal(listOf(Color(0xFF3A1578), Color(0xFF9D2BC0))), RoundedCornerShape(R2.lg))
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(46.dp).background(Color(0x2EFFFFFF), CircleShape), contentAlignment = Alignment.Center) { Icon(Ion.planet, 24.dp) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            T("Public Lounge", V.Heading)
                            T("Talk with everyone online", V.Small, Color(0xBFFFFFFF))
                        }
                        Icon(Ion.chevronForward, 20.dp)
                    }
                    Spacer(Modifier.height(16.dp))
                    T("Direct messages", V.Small, C.textDim)
                }
            }
            if (list.isEmpty() && loaded) {
                item {
                    Column(Modifier.fillMaxWidth().padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Ion.chatbubblesOutline, 36.dp, C.textDim)
                        Spacer(Modifier.height(10.dp))
                        T("No messages yet. Tap someone in a room to start a conversation.", color = C.textDim, align = TextAlign.Center)
                    }
                }
            }
            items(list, key = { it.id }) { item -> ConversationRow(item) { nav.navigate(Routes.thread(item.id, item.peer)) } }
        }
    }
}

@Composable
private fun ConversationRow(item: ConversationSummary, onClick: () -> Unit) {
    Row(
        Modifier
            .padding(bottom = 6.dp)
            .fillMaxWidth()
            .tap(onClick, "Conversation with ${item.peer.name}", "conversation-${item.peer.id}", pressedAlpha = 0.8f)
            .background(C.glass, RoundedCornerShape(R2.md))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(item.peer.avatar, size = 48.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            T(item.peer.name, V.Medium, lines = 1)
            T(item.last?.text ?: "Say hello", V.Small, C.textDim, lines = 1)
        }
        Spacer(Modifier.width(12.dp))
        Column(horizontalAlignment = Alignment.End) {
            item.last?.let { T(shortTime(it.at), V.Tiny, C.textFaint) }
            if (item.unread > 0) {
                Box(
                    Modifier.padding(top = 4.dp).height(19.dp).widthIn(min = 19.dp).background(C.pink, RoundedCornerShape(10.dp)).padding(horizontal = 5.dp),
                    contentAlignment = Alignment.Center,
                ) { T("${item.unread}", V.Tiny, size = 10) }
            }
        }
    }
}

class Bubble(val id: String, val mine: Boolean, val text: String, val name: String? = null, val avatar: String? = null, val from: String? = null)

@Composable
private fun MessageBubble(item: Bubble, showName: Boolean, onName: ((Bubble) -> Unit)? = null) {
    val named = showName && !item.mine
    val round = 18.dp
    val tight = 5.dp
    val maxWidth = LocalConfiguration.current.screenWidthDp.dp * 0.78f
    val shape = RoundedCornerShape(round, round, if (item.mine) tight else round, if (item.mine) round else tight)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (item.mine) Arrangement.End else Arrangement.Start, verticalAlignment = Alignment.Bottom) {
        if (named) {
            Box(Modifier.tap({ onName?.invoke(item) }, item.name)) { Avatar(item.avatar, size = 30.dp) }
            Spacer(Modifier.width(6.dp))
        }
        Column(Modifier.widthIn(max = maxWidth), horizontalAlignment = if (item.mine) Alignment.End else Alignment.Start) {
            if (named) {
                Box(Modifier.tap({ onName?.invoke(item) }, "${item.name}, options").padding(start = 8.dp, bottom = 2.dp)) {
                    T(item.name ?: "Guest", V.Tiny, C.gold)
                }
            }
            Box(
                Modifier
                    .then(if (item.mine) Modifier.background(diagonal(G.video), shape) else Modifier.background(C.glassStrong, shape))
                    .padding(horizontal = 13.dp, vertical = 8.dp),
            ) { T(item.text) }
        }
    }
}

@Composable
private fun InputBar(id: String, disabled: Boolean = false, onSend: (String) -> Unit) {
    var text by remember { mutableStateOf(TextFieldValue("")) }
    val keyboard = keyboardHeight()
    val bottom = bottomInset()
    fun send() {
        val body = text.text.trim()
        if (body.isEmpty()) return
        onSend(body)
        text = TextFieldValue("")
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(C.plum)
            .padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = (if (keyboard > 0.dp) keyboard else bottom) + 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Field(
            text, { text = it }, Modifier.weight(1f),
            id = id,
            hint = "Message",
            maxLength = 1000,
            height = 44.dp,
            radius = R2.pill,
            fontSize = 15,
            weight = FontWeight.Normal,
            enabled = !disabled,
            action = ImeAction.Send,
            onSubmit = { send() },
        )
        Spacer(Modifier.width(8.dp))
        IconBtn(Ion.send, "Send", { send() }, 44.dp, active = true, disabled = disabled, id = "$id-send")
    }
}

@Composable
private fun ChatHeader(title: String, onBack: () -> Unit, subtitle: String? = null, avatar: String? = null, right: (@Composable () -> Unit)? = null) {
    val focusManager = LocalFocusManager.current
    val line = hairline()
    Row(
        Modifier
            .fillMaxWidth()
            .drawBehind { drawLine(C.glassBorder, Offset(0f, size.height), Offset(size.width, size.height), line.toPx()) }
            .padding(start = 12.dp, end = 12.dp, top = topInset() + 8.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBtn(Ion.chevronBack, "Back", { focusManager.clearFocus(); onBack() }, 38.dp, id = "chat-back")
        Spacer(Modifier.width(10.dp))
        if (avatar != null) {
            Avatar(avatar, size = 38.dp)
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            T(title, V.Heading, lines = 1)
            if (subtitle != null) T(subtitle, V.Tiny, C.textDim, lines = 1)
        }
        right?.invoke()
    }
}

enum class LinkState { Connecting, Connected, Failed }

/** One chat channel over its own VoltKit connection: no room, no media, just messages. */
abstract class ChatLinkModel : ViewModel() {
    var state by mutableStateOf(LinkState.Connecting); protected set
    protected var client: ChatClient? = null

    protected abstract suspend fun open(): ChatChannel
    protected abstract fun onMessage(m: ChatMessage)
    protected open fun onHistory(history: List<ChatMessage>) = Unit

    protected fun start() {
        viewModelScope.launch {
            try {
                val channel = open()
                val chat = ChatClient(channel.wsUrl, channel.token)
                client = chat
                viewModelScope.launch { chat.onMessage.collect { onMessage(it) } }
                viewModelScope.launch { chat.onClosed.collect { if (it != ClosedReason.left) state = LinkState.Failed } }
                // Whatever was said while the socket was away was not delivered: the rejoin brings it.
                viewModelScope.launch { chat.onReconnected.collect { onHistory(it.messages) } }
                val joined = chat.connect()
                onHistory(joined.messages)
                state = LinkState.Connected
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                state = LinkState.Failed
            }
        }
    }

    override fun onCleared() {
        client?.close()
        client = null
    }
}

class LoungeViewModel : ChatLinkModel() {
    /** Newest first. */
    val messages = mutableStateListOf<Bubble>()

    init {
        start()
    }

    override suspend fun open() = Api.loungeToken()

    private fun toBubble(m: ChatMessage): Bubble? {
        val me = Session.guestOrNull ?: return null
        val text = m.text
        if (m.type != "text" || text.isNullOrEmpty() || m.from in me.blocked) return null
        val meta = m.metadata ?: emptyMap()
        return Bubble(m.id, m.from == me.id, text, meta["name"] as? String ?: "Guest", meta["avatar"] as? String, m.from)
    }

    override fun onMessage(m: ChatMessage) {
        val b = toBubble(m) ?: return
        if (messages.any { it.id == b.id }) return
        messages.add(0, b)
        while (messages.size > 200) messages.removeAt(messages.lastIndex)
    }

    override fun onHistory(history: List<ChatMessage>) {
        val older = history.mapNotNull { toBubble(it) }.reversed()
        messages.clear()
        messages.addAll(older)
    }

    fun send(text: String) {
        val me = Session.guest
        viewModelScope.launch {
            try {
                client?.sendMessage(text, null, null, mapOf("name" to me.name, "avatar" to me.avatar, "frame" to me.frame), null, false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toast("Your message was not sent.", ToastKind.Error)
            }
        }
    }

    fun dropFrom(identity: String) {
        messages.removeAll { it.from == identity }
    }
}

private val sheetScope = MainScope()

@Composable
fun LoungeScreen() {
    val vm = viewModel { LoungeViewModel() }
    val nav = LocalNav.current

    fun message(b: Bubble) {
        sheetScope.launch {
            try {
                val c = Api.startConversation(b.from!!)
                nav.navigate(Routes.thread(c.id, c.peer))
            } catch (e: ApiError) {
                toast(e.message, ToastKind.Error)
            }
        }
    }

    fun block(b: Bubble) {
        sheetScope.launch {
            try {
                Api.block(b.from!!)
                Session.refresh()
                vm.dropFrom(b.from)
                toast("${b.name} is blocked", ToastKind.Success)
            } catch (e: ApiError) {
                toast("That did not work.", ToastKind.Error)
            }
        }
    }

    fun person(b: Bubble) {
        SheetHost.show("lounge-person", b.name) {
            Column {
                SheetRow(Ion.chatbubbleEllipsesOutline, "Send a message", { close(); message(b) }, id = "lounge-message")
                SheetRow(Ion.flagOutline, "Report", { close(); showReportSheet(b.from!!, b.name ?: "Guest") }, id = "lounge-report")
                SheetRow(Ion.banOutline, "Block", { close(); block(b) }, danger = true, id = "lounge-block")
            }
        }
    }

    Backdrop(modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ChatHeader("Public Lounge", { nav.popBackStack() }, "Chat by VoltKit RTC")
            when (vm.state) {
                LinkState.Connecting -> Box(Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) { Spinner() }
                LinkState.Failed -> Column(Modifier.fillMaxWidth().padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    T("Could not connect to the lounge.", color = C.textDim, id = "lounge-failed")
                    Spacer(Modifier.height(10.dp))
                    GhostButton("Back", { nav.popBackStack() })
                }
                LinkState.Connected -> Unit
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (vm.messages.isEmpty() && vm.state == LinkState.Connected) {
                    T("It is quiet in here. Say hello.", color = C.textDim, align = TextAlign.Center, modifier = Modifier.align(Alignment.Center))
                } else {
                    LazyColumn(
                        Modifier.fillMaxSize().testTag("lounge-list"),
                        reverseLayout = true,
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom),
                    ) {
                        items(vm.messages, key = { it.id }) { MessageBubble(it, showName = true) { b -> person(b) } }
                    }
                }
            }
            InputBar("lounge-input", disabled = vm.state != LinkState.Connected) { vm.send(it) }
        }
    }
}

class ThreadViewModel(private val conversationId: String) : ChatLinkModel() {
    /** Newest first, as the backend sends them. */
    val messages = mutableStateListOf<Bubble>()

    init {
        start()
    }

    override suspend fun open(): ChatChannel {
        val me = Session.guest
        val (history, chat) = Api.conversation(conversationId)
        messages.clear()
        messages.addAll(history.map { Bubble(it.id, it.from == me.id, it.text) })
        markRead()
        return chat
    }

    private fun markRead() {
        viewModelScope.launch { runCatching { Api.markRead(conversationId) } }
    }

    private fun add(b: Bubble) {
        if (messages.any { it.id == b.id }) return
        messages.add(0, b)
    }

    override fun onMessage(m: ChatMessage) {
        val text = m.text
        // Our own already show, from the send below.
        if (m.from == Session.guestOrNull?.id || text.isNullOrEmpty()) return
        // The backend relays a stored message: its own id is in the metadata, the same id the history uses.
        add(Bubble(m.metadata?.get("messageId") as? String ?: m.id, false, text))
        markRead()
    }

    fun send(text: String) {
        viewModelScope.launch {
            try {
                val message = Api.sendDirect(conversationId, text)
                add(Bubble(message.id, true, message.text))
            } catch (e: ApiError) {
                toast(e.message, ToastKind.Error)
            }
        }
    }
}

@Composable
fun ThreadScreen(conversationId: String, peer: GuestPublic) {
    val vm = viewModel(key = "thread-$conversationId") { ThreadViewModel(conversationId) }
    val nav = LocalNav.current

    fun block() {
        sheetScope.launch {
            try {
                Api.block(peer.id)
                Session.refresh()
                nav.popBackStack()
                toast("${peer.name} is blocked", ToastKind.Success)
            } catch (e: ApiError) {
                toast("That did not work.", ToastKind.Error)
            }
        }
    }

    fun menu() {
        SheetHost.show("thread-options", peer.name) {
            Column {
                SheetRow(Ion.flagOutline, "Report", { close(); showReportSheet(peer.id, peer.name) }, id = "thread-report")
                SheetRow(Ion.banOutline, "Block", { close(); block() }, danger = true, id = "thread-block")
            }
        }
    }

    Backdrop(modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ChatHeader(peer.name, { nav.popBackStack() }, "Direct message", peer.avatar) {
                IconBtn(Ion.ellipsisHorizontal, "Options", { menu() }, 38.dp, id = "thread-menu")
            }
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth().testTag("thread-list"),
                reverseLayout = true,
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.Bottom),
            ) {
                items(vm.messages, key = { it.id }) { MessageBubble(it, showName = false) }
            }
            InputBar("thread-input") { vm.send(it) }
        }
    }
}
