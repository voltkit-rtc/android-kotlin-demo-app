package `in`.voltkit.livedemo.kotlin.screens

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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import `in`.voltkit.livedemo.kotlin.R
import `in`.voltkit.livedemo.kotlin.Voltkit
import `in`.voltkit.livedemo.kotlin.data.Api
import `in`.voltkit.livedemo.kotlin.data.ApiError
import `in`.voltkit.livedemo.kotlin.data.Assets
import `in`.voltkit.livedemo.kotlin.data.LiveSocket
import `in`.voltkit.livedemo.kotlin.data.Room
import `in`.voltkit.livedemo.kotlin.data.Session
import `in`.voltkit.livedemo.kotlin.ui.Avatar
import `in`.voltkit.livedemo.kotlin.ui.Backdrop
import `in`.voltkit.livedemo.kotlin.ui.BuiltWith
import `in`.voltkit.livedemo.kotlin.ui.C
import `in`.voltkit.livedemo.kotlin.ui.G
import `in`.voltkit.livedemo.kotlin.ui.GradientButton
import `in`.voltkit.livedemo.kotlin.ui.Icon
import `in`.voltkit.livedemo.kotlin.ui.Ion
import `in`.voltkit.livedemo.kotlin.ui.LivePill
import `in`.voltkit.livedemo.kotlin.ui.Picture
import `in`.voltkit.livedemo.kotlin.ui.R2
import `in`.voltkit.livedemo.kotlin.ui.T
import `in`.voltkit.livedemo.kotlin.ui.TagChip
import `in`.voltkit.livedemo.kotlin.ui.V
import `in`.voltkit.livedemo.kotlin.ui.Wordmark
import `in`.voltkit.livedemo.kotlin.ui.diagonal
import `in`.voltkit.livedemo.kotlin.ui.hairline
import `in`.voltkit.livedemo.kotlin.ui.tap
import kotlinx.coroutines.launch

private val FILTERS = listOf(Triple("all", "All live", Ion.flame), Triple("video", "Video", Ion.videocam), Triple("audio", "Audio", Ion.mic))

@Composable
private fun Equalizer() {
    val t = rememberInfiniteTransition(label = "eq")
    Row(Modifier.height(11.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
        for (i in 0..2) {
            val scale by t.animateFloat(0.3f, 1f, infiniteRepeatable(tween(300 + i * 120), RepeatMode.Reverse), label = "bar$i")
            Box(
                Modifier
                    .width(2.5.dp)
                    .height(11.dp)
                    .graphicsLayer { scaleY = scale; transformOrigin = TransformOrigin(0.5f, 1f) }
                    .background(C.cyan, RoundedCornerShape(1.dp)),
            )
        }
    }
}

@Composable
private fun RoomCard(room: Room, onOpen: (Room) -> Unit) {
    Box(
        Modifier
            .aspectRatio(0.74f)
            .tap({ onOpen(room) }, "${room.type} room, ${room.title}, hosted by ${room.host.name}, ${room.viewers} people", "room-card-${room.type}", pressedScale = 0.97f)
            .clip(RoundedCornerShape(R2.lg))
            .background(C.plum)
            .border(hairline(), C.glassBorder, RoundedCornerShape(R2.lg)),
    ) {
        Picture(Assets.cover(room.theme), 400.dp, Modifier.fillMaxSize(), crop = true)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color(0x59050309), 0.4f to Color(0x00050309), 1f to Color(0xF2050309))))
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth().padding(9.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                LivePill()
                TagChip(compact(room.viewers), Ion.people)
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { Avatar(room.host.avatar, room.host.frame, 58.dp) }
            Column(Modifier.padding(11.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (room.isVideo) Ion.videocam else Ion.mic, 13.dp, if (room.isVideo) C.pink else C.cyan)
                    Spacer(Modifier.width(5.dp))
                    if (!room.isVideo) {
                        Equalizer()
                        Spacer(Modifier.width(5.dp))
                    }
                    T(if (room.isVideo) "Video room" else "Audio room", V.Tiny, C.textDim)
                }
                Spacer(Modifier.height(3.dp))
                T(room.title, V.Heading, lines = 1)
                Spacer(Modifier.height(1.dp))
                T(room.host.name, V.Small, C.textDim, lines = 1)
            }
        }
    }
}

@Composable
private fun QuickAction(modifier: Modifier, icon: String, title: String, hint: String, tint: List<Color>, id: String, onClick: () -> Unit) {
    Column(
        modifier
            .tap(onClick, title, id, pressedScale = 0.97f)
            .background(C.glass, RoundedCornerShape(R2.md))
            .border(hairline(), C.glassBorder, RoundedCornerShape(R2.md))
            .padding(12.dp),
    ) {
        Box(Modifier.padding(bottom = 8.dp).size(40.dp).background(diagonal(tint), RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, 22.dp)
        }
        T(title, V.Small, lines = 1)
        Spacer(Modifier.height(3.dp))
        T(hint, V.Tiny, C.textFaint, lines = 1)
    }
}

@Composable
private fun Hero(onLearn: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .tap(onLearn, Voltkit.POWERED_BY)
            .clip(RoundedCornerShape(R2.lg))
            .background(diagonal(listOf(Color(0xFF2A1159), Color(0xFF5B1FA8), Color(0xFFC026D3))))
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Picture(R.drawable.art_crown, 124.dp, Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-14).dp))
        Column(Modifier.padding(end = 118.dp)) {
            T("Your room.", V.Title)
            Spacer(Modifier.height(2.dp))
            T("Your layout.", V.Title)
            Spacer(Modifier.height(6.dp))
            T(Voltkit.DEMO_LINE, V.Small, Color(0xCCFFFFFF), lines = 1)
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                T("Build yours at voltkit.in", V.Tiny, C.gold)
                Spacer(Modifier.width(4.dp))
                Icon(Ion.arrowForward, 12.dp, C.gold)
            }
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun HomeScreen(focus: Int) {
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val guest = Session.guest
    var filter by rememberSaveable { mutableStateOf("all") }
    var rooms by remember { mutableStateOf(emptyList<Room>()) }
    var online by remember { mutableStateOf<Int?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshing by remember { mutableStateOf(false) }

    suspend fun load() {
        try {
            rooms = Api.rooms()
            online = Api.stats().online
            error = null
        } catch (e: ApiError) {
            error = e.message
        }
        loaded = true
    }

    LaunchedEffect(focus) { load() }
    DisposableEffect(Unit) {
        val offs = listOf(
            LiveSocket.on("rooms:changed") { scope.launch { load() } },
            // Once the live connection is up, and after each reconnect: the count includes this phone, and rooms missed while
            // offline appear.
            LiveSocket.on("connect") { scope.launch { load() } },
        )
        onDispose { offs.forEach { it() } }
    }
    val refresh = rememberPullRefreshState(refreshing, {
        scope.launch {
            refreshing = true
            load()
            refreshing = false
        }
    })
    val shown = if (filter == "all") rooms else rooms.filter { it.type == filter }
    val create = { type: String -> nav.navigate(Routes.create(type)) }

    Backdrop(modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().pullRefresh(refresh)) {
            LazyVerticalGrid(
                GridCells.Fixed(2),
                Modifier.fillMaxSize().testTag("home-list"),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = topInset() + 8.dp, bottom = 110.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Picture(R.drawable.brand_mark, 32.dp)
                            Spacer(Modifier.width(7.dp))
                            Wordmark(23)
                            Spacer(Modifier.width(7.dp))
                            T(Voltkit.APP_SUFFIX, V.Heading, C.textDim)
                            Spacer(Modifier.weight(1f))
                            online?.let {
                                Row(
                                    Modifier.height(28.dp).background(C.glass, RoundedCornerShape(R2.pill)).padding(horizontal = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(Modifier.size(6.dp).background(C.success, CircleShape))
                                    Spacer(Modifier.width(5.dp))
                                    T("${compact(it)} online", V.Tiny)
                                }
                                Spacer(Modifier.width(7.dp))
                            }
                            Row(
                                Modifier.height(28.dp).background(Color(0x1FFFD765), RoundedCornerShape(R2.pill)).padding(horizontal = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Ion.logoBitcoin, 14.dp, C.gold)
                                Spacer(Modifier.width(4.dp))
                                T(compact(guest.coins), V.Tiny, C.gold)
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Hero { nav.navigate(Routes.VOLTKIT) }
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            QuickAction(Modifier.weight(1f), Ion.videocam, "Go live", "Video room", G.video, "go-video") { create("video") }
                            QuickAction(Modifier.weight(1f), Ion.mic, "Audio room", "Seats and voices", G.audio, "go-audio") { create("audio") }
                            QuickAction(Modifier.weight(1f), Ion.shuffle, "1v1 Face", "Meet someone", G.match, "go-match") { nav.navigate(Routes.MATCH) }
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            for ((id, label, icon) in FILTERS) {
                                val on = filter == id
                                Row(
                                    Modifier
                                        .tap({ filter = id }, label, "filter-$id", selected = on)
                                        .height(34.dp)
                                        .background(if (on) C.purple else C.glass, RoundedCornerShape(R2.pill))
                                        .padding(horizontal = 13.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(icon, 13.dp, if (on) C.white else C.textDim)
                                    Spacer(Modifier.width(5.dp))
                                    T(label, V.Small, if (on) C.white else C.textDim)
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }
                if (shown.isEmpty() && loaded) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 24.dp).testTag("home-empty"),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            if (error != null) {
                                Icon(Ion.cloudOfflineOutline, 40.dp, C.textDim)
                                T(error!!, color = C.textDim, align = TextAlign.Center)
                                GradientButton("Try again", { scope.launch { load() } })
                            } else {
                                Icon(Ion.radioOutline, 42.dp, C.magenta)
                                T("Nobody is live right now", V.Heading)
                                T("Start the first room and be the one others find.", color = C.textDim, align = TextAlign.Center)
                                GradientButton("Go live", { create(if (filter == "audio") "audio" else "video") }, icon = Ion.radio, id = "home-empty-create")
                            }
                        }
                    }
                }
                items(shown, key = { it.id }) { room -> RoomCard(room) { nav.navigate(Routes.room(it.id)) } }
                item(span = { GridItemSpan(maxLineSpan) }) { BuiltWith(Modifier.padding(top = 18.dp)) }
            }
            PullRefreshIndicator(
                refreshing,
                refresh,
                Modifier.align(Alignment.TopCenter).padding(top = topInset()),
                backgroundColor = C.plum,
                contentColor = C.magenta,
            )
        }
    }
}
