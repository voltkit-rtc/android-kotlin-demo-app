package `in`.voltkit.livedemo.kotlin.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import `in`.voltkit.livedemo.kotlin.data.Api
import `in`.voltkit.livedemo.kotlin.data.ApiError
import `in`.voltkit.livedemo.kotlin.data.Assets
import `in`.voltkit.livedemo.kotlin.data.Session
import `in`.voltkit.livedemo.kotlin.rtc.askMediaConsent
import `in`.voltkit.livedemo.kotlin.screens.room.PendingRoom
import `in`.voltkit.livedemo.kotlin.ui.Backdrop
import `in`.voltkit.livedemo.kotlin.ui.C
import `in`.voltkit.livedemo.kotlin.ui.Field
import `in`.voltkit.livedemo.kotlin.ui.G
import `in`.voltkit.livedemo.kotlin.ui.GradientButton
import `in`.voltkit.livedemo.kotlin.ui.Icon
import `in`.voltkit.livedemo.kotlin.ui.IconBtn
import `in`.voltkit.livedemo.kotlin.ui.Ion
import `in`.voltkit.livedemo.kotlin.ui.Picture
import `in`.voltkit.livedemo.kotlin.ui.PoweredBy
import `in`.voltkit.livedemo.kotlin.ui.R2
import `in`.voltkit.livedemo.kotlin.ui.T
import `in`.voltkit.livedemo.kotlin.ui.ToastKind
import `in`.voltkit.livedemo.kotlin.ui.V
import `in`.voltkit.livedemo.kotlin.ui.diagonal
import `in`.voltkit.livedemo.kotlin.ui.hairline
import `in`.voltkit.livedemo.kotlin.ui.tap
import `in`.voltkit.livedemo.kotlin.ui.toast
import kotlinx.coroutines.launch

private val SEATS = mapOf("video" to listOf(4, 6, 9), "audio" to listOf(8, 10, 15))

@Composable
private fun Option(label: String, on: Boolean, id: String? = null, onClick: () -> Unit) {
    Box(
        Modifier
            .tap(onClick, label, id, selected = on)
            .height(40.dp)
            .background(if (on) C.purple else C.glass, RoundedCornerShape(R2.pill))
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) { T(label, V.Small, if (on) C.white else C.textDim) }
}

@Composable
private fun Label(text: String) = T(text, V.Small, C.textDim, Modifier.padding(top = 18.dp, bottom = 8.dp))

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CreateScreen(initialType: String) {
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    var type by remember { mutableStateOf(initialType) }
    var title by remember { mutableStateOf(TextFieldValue("${Session.guest.name}'s room")) }
    var theme by remember { mutableStateOf("royal") }
    var seats by remember { mutableIntStateOf(SEATS.getValue(initialType)[1]) }
    var seatMode by remember { mutableStateOf("request") }
    var layout by remember { mutableStateOf("stage") }
    var busy by remember { mutableStateOf(false) }

    fun goLive() {
        val name = title.text.trim()
        if (name.length < 3) return toast("Give your room a title of at least 3 characters.", ToastKind.Error)
        busy = true
        scope.launch {
            try {
                if (!askMediaConsent(type)) return@launch
                val session = Api.createRoom(type, name, theme, seats, seatMode, layout)
                PendingRoom.put(session)
                nav.navigate(Routes.room(session.room.id)) { popUpTo(Routes.TABS) }
            } catch (e: ApiError) {
                toast(e.message, ToastKind.Error)
            } finally {
                busy = false
            }
        }
    }

    Backdrop(modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = topInset() + 8.dp, bottom = keyboardOrBottom() + 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBtn(Ion.chevronBack, "Back", { nav.popBackStack() }, 40.dp, id = "create-close")
                Spacer(Modifier.width(10.dp))
                T("Start a room", V.Title)
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (t in listOf("video", "audio")) {
                    val on = type == t
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .heightIn(min = 112.dp)
                            .tap({ type = t; seats = SEATS.getValue(t)[1] }, "$t room", "create-type-$t", selected = on)
                            .clip(RoundedCornerShape(R2.lg))
                            .background(diagonal(if (on) (if (t == "video") G.video else G.audio) else G.panel))
                            .border(hairline(), C.glassBorder, RoundedCornerShape(R2.lg))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Icon(if (t == "video") Ion.videocam else Ion.mic, 26.dp)
                        T(if (t == "video") "Video room" else "Audio room", V.Heading)
                        T(if (t == "video") "Cards for you and co-hosts" else "A throne and numbered seats", V.Tiny, Color(0xBFFFFFFF))
                    }
                }
            }
            Label("Title")
            Field(title, { title = it }, Modifier.fillMaxWidth(), id = "create-title", hint = "What is this room about?", maxLength = 48)
            Label("Backdrop")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Assets.themeKeys) { key ->
                    Box(
                        Modifier
                            .tap({ theme = key }, "$key backdrop", selected = theme == key)
                            .size(76.dp, 100.dp)
                            .clip(RoundedCornerShape(R2.md))
                            .border(2.dp, if (theme == key) C.gold else Color.Transparent, RoundedCornerShape(R2.md)),
                    ) { Picture(Assets.cover(key), 76.dp, height = 100.dp, crop = true) }
                }
            }
            Label(if (type == "video") "Video cards, including yours" else "Seats on stage")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (n in SEATS.getValue(type)) Option("$n", seats == n, "create-seats-$n") { seats = n }
            }
            if (type == "video") {
                Label("Layout")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Option("Stage", layout == "stage") { layout = "stage" }
                    Option("Grid", layout == "grid") { layout = "grid" }
                }
            }
            Label("Who gets on stage")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Option("I approve each person", seatMode == "request", "create-mode-request") { seatMode = "request" }
                Option("Anyone can take a seat", seatMode == "open", "create-mode-open") { seatMode = "open" }
            }
            Spacer(Modifier.height(22.dp))
            GradientButton(if (type == "video") "Go live" else "Open the room", ::goLive, Modifier.fillMaxWidth(), icon = Ion.radio, loading = busy, id = "create-go")
            Spacer(Modifier.height(18.dp))
            PoweredBy()
        }
    }
}
