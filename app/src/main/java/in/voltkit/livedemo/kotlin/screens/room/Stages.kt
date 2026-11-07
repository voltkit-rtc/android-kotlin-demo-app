package `in`.voltkit.livedemo.kotlin.screens.room

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.voltkit.rtc.RemoteTrack
import com.voltkit.rtc.Role
import `in`.voltkit.livedemo.kotlin.R
import `in`.voltkit.livedemo.kotlin.data.Assets
import `in`.voltkit.livedemo.kotlin.rtc.Layout
import `in`.voltkit.livedemo.kotlin.rtc.LocalVideo
import `in`.voltkit.livedemo.kotlin.rtc.RemoteVideo
import `in`.voltkit.livedemo.kotlin.rtc.RoomEngine
import `in`.voltkit.livedemo.kotlin.rtc.Seats
import `in`.voltkit.livedemo.kotlin.ui.Avatar
import `in`.voltkit.livedemo.kotlin.ui.C
import `in`.voltkit.livedemo.kotlin.ui.G
import `in`.voltkit.livedemo.kotlin.ui.Icon
import `in`.voltkit.livedemo.kotlin.ui.Ion
import `in`.voltkit.livedemo.kotlin.ui.Picture
import `in`.voltkit.livedemo.kotlin.ui.R2
import `in`.voltkit.livedemo.kotlin.ui.RoleBadge
import `in`.voltkit.livedemo.kotlin.ui.T
import `in`.voltkit.livedemo.kotlin.ui.V
import `in`.voltkit.livedemo.kotlin.ui.tap
import `in`.voltkit.livedemo.kotlin.ui.vertical
import kotlin.math.floor

@Composable
fun SpeakingRing(engine: RoomEngine, identity: String, size: Dp) {
    if (identity !in engine.speaking) return
    val pulse = remember { Animatable(0f) }
    LaunchedEffect(Unit) { pulse.animateTo(1f, infiniteRepeatable(tween(1000, easing = LinearEasing))) }
    Box(
        Modifier
            .size(size)
            .graphicsLayer {
                val s = 1f + 0.45f * pulse.value
                scaleX = s
                scaleY = s
                alpha = 0.95f * (1f - pulse.value)
            }
            .border(2.5.dp, C.cyan, CircleShape),
    )
}

@Composable
private fun MicBadge(on: Boolean, modifier: Modifier) {
    Box(
        modifier.size(18.dp).background(if (on) C.blue else C.danger, CircleShape).border(1.5.dp, C.plum, CircleShape),
        contentAlignment = Alignment.Center,
    ) { Icon(if (on) Ion.mic else Ion.micOff, 10.dp) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AudioStage(engine: RoomEngine, onMember: (String) -> Unit, onSeat: (String) -> Unit) {
    val width = LocalConfiguration.current.screenWidthDp.toFloat()
    val room = engine.room
    val cols = Layout.audioColumns(engine.seats)
    // Whole numbers: cells that add up to the exact width can round over it and wrap a seat onto a new row.
    val cellW = floor((width - 20f) / cols).dp
    val size = minOf(cellW - 18.dp, 58.dp)
    val host = engine.memberById[room.host.id]
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(180.dp, 142.dp).tap({ onMember(room.host.id) }, "Host ${room.host.name}", "audio-host"), contentAlignment = Alignment.TopCenter) {
            Picture(R.drawable.art_throne, 128.dp)
            Box(Modifier.padding(top = 19.dp).size(60.dp), contentAlignment = Alignment.Center) {
                SpeakingRing(engine, room.host.id, 60.dp)
                Avatar(host?.avatar ?: room.host.avatar, host?.frame ?: room.host.frame, 60.dp)
                MicBadge(engine.micIsOn(room.host.id), Modifier.align(Alignment.BottomEnd).offset(3.dp, 3.dp))
            }
            T(host?.name ?: room.host.name, V.Small, C.gold, Modifier.padding(top = 120.dp).widthIn(max = 180.dp), lines = 1)
        }
        FlowRow(
            Modifier.padding(start = 10.dp, end = 10.dp, top = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            for (key in Seats.all(engine.seats)) {
                val claim = engine.claims[key]
                val m = claim?.let { engine.memberById[it.identity] }
                val locked = key in engine.locked
                val label = "No.${Seats.number(key)}"
                Column(
                    Modifier
                        .width(cellW)
                        .tap(
                            { if (claim != null) onMember(claim.identity) else onSeat(key) },
                            if (claim != null) "${m?.name ?: "Guest"}, seat $label" else if (locked) "Seat $label, locked" else "Seat $label, empty",
                            "audio-$key",
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.size(size).background(C.glass, CircleShape).border(1.dp, Color(0x5978AAFF), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (claim != null) {
                            SpeakingRing(engine, claim.identity, size)
                            Box(Modifier.graphicsLayer { alpha = if (claim.away) 0.45f else 1f }) { Avatar(m?.avatar, m?.frame ?: "none", size) }
                            MicBadge(engine.micIsOn(claim.identity), Modifier.align(Alignment.BottomEnd).offset(3.dp, 3.dp))
                        } else {
                            Box(Modifier.size(size * 0.66f).background(Color(0x17FFFFFF), CircleShape), contentAlignment = Alignment.Center) {
                                Icon(if (locked) Ion.lockClosed else Ion.mic, size * 0.3f, C.textDim)
                            }
                        }
                    }
                    Spacer(Modifier.height(5.dp))
                    T(
                        if (claim != null) (if (claim.away) "Reconnecting" else m?.name ?: "Guest") else label,
                        V.Tiny,
                        if (claim != null) C.white else C.textDim,
                        Modifier.widthIn(max = 66.dp),
                        lines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoCard(
    engine: RoomEngine,
    x: Float, y: Float, w: Float, h: Float,
    id: String,
    seatLabel: String,
    onClick: () -> Unit,
    identity: String? = null,
    name: String? = null,
    avatar: String? = null,
    frame: String = "none",
    role: Role? = null,
    host: Boolean = false,
    locked: Boolean = false,
    away: Boolean = false,
) {
    val compact = w < 150f
    val mine = identity == engine.me.id
    val remote: RemoteTrack? = if (identity != null && !mine) engine.videoOf(identity) else null
    val localTrack = if (mine && engine.local.camEnabled) engine.localVideo else null
    val showVideo = remote != null || localTrack != null
    val micOn = identity != null && engine.micIsOn(identity)
    val speaking = identity != null && identity in engine.speaking
    Box(
        Modifier
            .offset(x.dp, y.dp)
            .size(w.dp, h.dp)
            .tap(onClick, if (identity != null) "$name, ${role?.name ?: "viewer"}" else if (locked) "$seatLabel, locked" else "$seatLabel, empty", id),
    ) {
        if (identity == null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(R2.sm))
                    .background(Color(0x0DFFFFFF))
                    .drawBehind {
                        drawRoundRect(
                            Color(0x21FFFFFF),
                            cornerRadius = CornerRadius(R2.sm.toPx()),
                            style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))),
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(if (locked) Ion.lockClosed else Ion.add, if (compact) 22.dp else 28.dp, C.textDim)
                    Spacer(Modifier.height(4.dp))
                    T(seatLabel, V.Tiny, C.textFaint)
                }
            }
            return@Box
        }
        val edge = when {
            speaking -> Modifier.shadow(14.dp, RoundedCornerShape(R2.sm), ambientColor = C.cyan, spotColor = C.cyan)
            else -> Modifier
        }
        Box(edge.fillMaxSize().clip(RoundedCornerShape(R2.sm))) {
            Box(Modifier.fillMaxSize().background(vertical(if (host) listOf(Color(0xFF3B2A08), Color(0xFF170B2E)) else listOf(Color(0xFF241243), Color(0xFF0D0720)))))
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Avatar(avatar, size = (minOf(w, h) * if (compact) 0.42f else 0.34f).dp) }
            if (remote != null) RemoteVideo(engine.client, remote, Modifier.fillMaxSize())
            if (localTrack != null) LocalVideo(localTrack, engine.local.frontCamera, Modifier.fillMaxSize())
            if (remote != null && !compact && remote.producerId in engine.held) {
                Box(Modifier.align(Alignment.Center).background(Color(0x99000000), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                    T("Weak connection", V.Tiny)
                }
            }
            if (away) Box(Modifier.fillMaxSize().background(Color(0x8C000000)))
            Box(
                Modifier
                    .fillMaxSize()
                    .border(
                        if (speaking) 2.5.dp else 1.dp,
                        if (speaking) C.cyan else if (host) Color(0xBFFFD765) else Color(0x38FFFFFF),
                        RoundedCornerShape(R2.sm),
                    ),
            )
            val crest = Assets.crest(frame)
            if (crest != null && showVideo) {
                Picture(crest, if (compact) 28.dp else 42.dp, Modifier.align(Alignment.TopEnd).padding(if (compact) 2.dp else 4.dp))
            }
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(44.dp).background(vertical(G.scrimBottom)))
            Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(18.dp).background(if (micOn) Color(0x38FFFFFF) else C.danger, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(if (micOn) Ion.mic else Ion.micOff, 10.dp)
                }
                Spacer(Modifier.width(5.dp))
                T(if (away) "Reconnecting…" else name ?: "", V.Tiny, lines = 1, modifier = Modifier.weight(1f))
            }
            if (role != null) Box(Modifier.align(Alignment.TopStart).padding(6.dp)) { RoleBadge(role.name) }
        }
    }
}

@Composable
fun VideoStage(engine: RoomEngine, width: Float, height: Float, onMember: (String) -> Unit, onSeat: (String) -> Unit) {
    val room = engine.room
    val cells = Layout.video(room.seats, engine.layout, width, height)
    Box(Modifier.size(width.dp, height.dp)) {
        val hostMember = engine.memberById[room.host.id]
        val hostMine = room.host.id == engine.me.id
        val c0 = cells[0]
        VideoCard(
            engine, c0.x, c0.y, c0.w, c0.h, "video-card-host", "Host", { onMember(room.host.id) },
            identity = room.host.id,
            name = if (hostMine) "${hostMember?.name ?: engine.me.name} (you)" else hostMember?.name ?: room.host.name,
            avatar = hostMember?.avatar ?: room.host.avatar,
            frame = hostMember?.frame ?: room.host.frame,
            role = Role.host,
            host = true,
        )
        Seats.all(engine.seats).forEachIndexed { i, key ->
            val c = cells[i + 1]
            val claim = engine.claims[key]
            val m = claim?.let { engine.memberById[it.identity] }
            val mine = claim?.identity == engine.me.id
            VideoCard(
                engine, c.x, c.y, c.w, c.h, "video-card-$key", "No.${Seats.number(key)}",
                { if (claim != null) onMember(claim.identity) else onSeat(key) },
                identity = claim?.identity,
                name = if (mine) "${m?.name ?: engine.me.name} (you)" else m?.name ?: "Guest",
                avatar = m?.avatar ?: "a01",
                frame = m?.frame ?: "none",
                role = m?.role,
                locked = key in engine.locked,
                away = claim?.away ?: false,
            )
        }
    }
}
