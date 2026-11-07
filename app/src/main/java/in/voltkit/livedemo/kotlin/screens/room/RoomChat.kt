package `in`.voltkit.livedemo.kotlin.screens.room

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import `in`.voltkit.livedemo.kotlin.rtc.ChatLine
import `in`.voltkit.livedemo.kotlin.rtc.RoomEngine
import `in`.voltkit.livedemo.kotlin.ui.AnnotatedT
import `in`.voltkit.livedemo.kotlin.ui.Avatar
import `in`.voltkit.livedemo.kotlin.ui.C
import `in`.voltkit.livedemo.kotlin.ui.Icon
import `in`.voltkit.livedemo.kotlin.ui.Ion
import `in`.voltkit.livedemo.kotlin.ui.R2
import `in`.voltkit.livedemo.kotlin.ui.T
import `in`.voltkit.livedemo.kotlin.ui.V
import `in`.voltkit.livedemo.kotlin.ui.tap
import `in`.voltkit.livedemo.kotlin.ui.textStyle
import kotlin.random.Random

/** Chat lives on the room's own connection, so there is no second socket and no second token. */
@Composable
fun RoomChat(engine: RoomEngine, modifier: Modifier, onLine: (ChatLine) -> Unit) {
    BoxWithConstraints(modifier) {
        val maxW = maxWidth - 20.dp
        LazyColumn(
            Modifier.fillMaxSize().testTag("room-chat"),
            reverseLayout = true,
            contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = 6.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.Bottom),
        ) {
            items(engine.chat, key = { it.id }) { line ->
                when (line.kind) {
                    "notice" -> Box(
                        Modifier.widthIn(max = maxW * 0.92f).background(Color(0x6B000000), RoundedCornerShape(R2.sm)).padding(horizontal = 10.dp, vertical = 5.dp),
                    ) { T(line.text, V.Tiny, C.cyan) }
                    "gift" -> Box(
                        Modifier.widthIn(max = maxW * 0.88f).background(Color(0x73000000), RoundedCornerShape(R2.md)).padding(horizontal = 8.dp, vertical = 5.dp),
                    ) { T(line.text, V.Small, C.gold) }
                    else -> Row(
                        Modifier
                            .widthIn(max = maxW * 0.88f)
                            .tap({ onLine(line) }, "Message from ${line.name}: ${line.text}")
                            .background(Color(0x73000000), RoundedCornerShape(R2.md))
                            .padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(line.avatar, size = 20.dp)
                        Spacer(Modifier.width(6.dp))
                        AnnotatedT(
                            buildAnnotatedString {
                                withStyle(SpanStyle(color = C.gold)) { append("${line.name}  ") }
                                append(line.text)
                            },
                            textStyle(V.Small),
                        )
                    }
                }
            }
        }
    }
}

private const val MAX_HEARTS = 12
private val TINTS = listOf(C.pink, C.magenta, C.purple, C.gold)
private val EASE_OUT_QUAD = CubicBezierEasing(0.25f, 0.46f, 0.45f, 0.94f)

private fun between(p: Float, at: FloatArray, values: FloatArray): Float {
    for (i in 1 until at.size) {
        if (p <= at[i]) return values[i - 1] + (values[i] - values[i - 1]) * ((p - at[i - 1]) / (at[i] - at[i - 1]))
    }
    return values.last()
}

/** Hearts from everyone. They travel as ephemeral chat messages, which are delivered and never stored. */
@Composable
fun Reactions(engine: RoomEngine, modifier: Modifier) {
    val hearts = remember { mutableStateListOf<Int>() }
    LaunchedEffect(engine) {
        var seq = 0
        engine.client.onMessage.collect { m ->
            if (m.type == "reaction" && hearts.size < MAX_HEARTS) hearts.add(++seq)
        }
    }
    Box(modifier.size(40.dp, 30.dp)) {
        for (seq in hearts.toList()) {
            androidx.compose.runtime.key(seq) {
                val p = remember { Animatable(0f) }
                val sway = remember { 10f + Random.nextFloat() * 30f }
                val side = remember { if (Random.nextBoolean()) 1f else -1f }
                LaunchedEffect(Unit) {
                    p.animateTo(1f, tween(2200, easing = EASE_OUT_QUAD))
                    hearts.remove(seq)
                }
                val v = p.value
                Box(
                    Modifier
                        .offset(between(v, floatArrayOf(0f, 0.5f, 1f), floatArrayOf(0f, side * sway, -side * sway * 0.5f)).dp, (-280f * v).dp)
                        .graphicsLayer {
                            val s = between(v, floatArrayOf(0f, 0.15f, 1f), floatArrayOf(0.4f, 1.15f, 0.9f))
                            scaleX = s
                            scaleY = s
                            alpha = between(v, floatArrayOf(0f, 0.1f, 0.7f, 1f), floatArrayOf(0f, 1f, 0.9f, 0f))
                        },
                ) { Icon(Ion.heart, 30.dp, TINTS[seq % TINTS.size]) }
            }
        }
    }
}
