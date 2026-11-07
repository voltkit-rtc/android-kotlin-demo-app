package `in`.voltkit.livedemo.kotlin.gifts

import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.opensource.svgaplayer.SVGACallback
import com.opensource.svgaplayer.SVGAImageView
import com.opensource.svgaplayer.SVGAParser
import com.opensource.svgaplayer.SVGAVideoEntity
import com.voltkit.rtc.ChatMessage
import `in`.voltkit.livedemo.kotlin.Config
import `in`.voltkit.livedemo.kotlin.data.Assets
import `in`.voltkit.livedemo.kotlin.rtc.RoomEngine
import `in`.voltkit.livedemo.kotlin.ui.Avatar
import `in`.voltkit.livedemo.kotlin.ui.C
import `in`.voltkit.livedemo.kotlin.ui.Picture
import `in`.voltkit.livedemo.kotlin.ui.R2
import `in`.voltkit.livedemo.kotlin.ui.T
import `in`.voltkit.livedemo.kotlin.ui.V
import `in`.voltkit.livedemo.kotlin.ui.horizontal
import `in`.voltkit.livedemo.kotlin.ui.textStyle
import kotlinx.coroutines.delay
import java.io.FileInputStream

private fun itemOf(m: ChatMessage): StageItem? {
    val meta = m.metadata ?: return null
    val url = meta["svgaUrl"] as? String
    if (url.isNullOrEmpty()) return null
    return when (m.type) {
        "gift" -> GiftItem(
            m.id, Config.assetUrl(url),
            meta["icon"] as? String ?: meta["giftId"] as? String ?: "heart",
            meta["fromName"] as? String ?: "Someone",
            meta["toName"] as? String ?: "the host",
            meta["giftName"] as? String ?: "a gift",
            (meta["count"] as? Number)?.toInt() ?: 1,
            meta["tier"] as? String ?: "basic",
        )
        "entrance" -> EntranceItem(m.id, Config.assetUrl(url), meta["name"] as? String ?: "Guest", meta["frame"] as? String ?: "none")
        else -> null
    }
}

/**
 * Plays gifts and entrances one at a time over the room. Everything arrives as a VoltKit chat message sent by the backend. A
 * file that cannot be played falls back to the gift's icon, and a stall skips ahead, so a bad animation can never hold up the
 * queue.
 */
@Composable
fun GiftStage(engine: RoomEngine) {
    val context = LocalContext.current
    val queue = remember { GiftQueue() }
    var item by remember { mutableStateOf<StageItem?>(null) }
    var movie by remember { mutableStateOf<SVGAVideoEntity?>(null) }
    var failed by remember { mutableStateOf(false) }
    var play by remember { mutableIntStateOf(0) }
    val parser = remember { SVGAParser(context) }

    fun next() {
        item = queue.next()
        movie = null
        failed = false
        play++
    }

    LaunchedEffect(engine) {
        engine.client.onMessage.collect { m ->
            val entry = itemOf(m) ?: return@collect
            queue.push(entry)
            if (item == null) next()
        }
    }
    // The player stops in the background, so anything queued is stale by the time the app returns.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        queue.clear()
        if (item != null) next()
    }
    val current = item
    LaunchedEffect(play) {
        if (current == null) return@LaunchedEffect
        val mine = play
        try {
            val file = SvgaFiles.get(context, current.url)
            parser.decodeFromInputStream(FileInputStream(file), file.name, object : SVGAParser.ParseCompletion {
                override fun onComplete(videoItem: SVGAVideoEntity) {
                    if (mine == play) movie = videoItem
                }

                override fun onError() {
                    if (mine != play) return
                    if (current is GiftItem) failed = true else next()
                }
            }, true, null, null)
        } catch (e: Exception) {
            Log.w("GiftStage", "svga failed: ${e.message}")
            if (current is GiftItem) failed = true else next()
        }
        // The player can stall, and then its end never comes.
        delay(9000)
        if (mine == play) next()
    }
    if (current == null) return

    BoxWithConstraints(Modifier.fillMaxSize().testTag("gift-stage")) {
        val w = maxWidth
        val h = maxHeight
        val video = movie
        when (current) {
            is GiftItem -> if (failed) {
                Fallback(current.icon, Modifier.align(Alignment.Center)) { next() }
            } else if (video != null) {
                Player(video, Modifier.align(Alignment.Center).size(w, w)) { next() }
            }
            is EntranceItem -> if (video != null) {
                Player(video, Modifier.offset(y = h * 0.30f).fillMaxWidth().height(w * 320f / 750f)) { next() }
            }
        }
        when (current) {
            is GiftItem -> Banner(current, Modifier.offset(x = 10.dp, y = h * 0.42f).widthIn(max = w * 0.76f))
            is EntranceItem -> Column(Modifier.offset(y = h * 0.37f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Avatar(null, if (current.frame == "none") "lion" else current.frame, 40.dp)
                Spacer(Modifier.height(10.dp))
                androidx.compose.foundation.text.BasicText(
                    current.name,
                    style = textStyle(V.Heading).copy(shadow = Shadow(Color.Black, blurRadius = 6f)),
                    maxLines = 1,
                )
                Spacer(Modifier.height(2.dp))
                T("has arrived", V.Tiny, C.gold)
            }
        }
    }
}

@Composable
private fun Player(video: SVGAVideoEntity, modifier: Modifier, onDone: () -> Unit) {
    AndroidView(
        factory = { ctx ->
            SVGAImageView(ctx).apply {
                loops = 1
                fillMode = SVGAImageView.FillMode.Clear
            }
        },
        modifier = modifier,
        update = { view ->
            view.callback = object : SVGACallback {
                override fun onPause() = Unit
                override fun onFinished() = onDone()
                override fun onRepeat() = Unit
                override fun onStep(frame: Int, percentage: Double) = Unit
            }
            view.setVideoItem(video)
            view.startAnimation()
        },
        onRelease = { it.stopAnimation(true) },
    )
}

@Composable
private fun Fallback(icon: String, modifier: Modifier, onDone: () -> Unit) {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        p.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 300f))
        delay(1000)
        p.animateTo(0f, tween(220))
        onDone()
    }
    Box(modifier.graphicsLayer { alpha = p.value.coerceIn(0f, 1f); scaleX = p.value; scaleY = p.value }) { Picture(Assets.gift(icon), 200.dp) }
}

@Composable
private fun Banner(item: GiftItem, modifier: Modifier) {
    val x = remember(item.id) { Animatable(0f) }
    LaunchedEffect(item.id) { x.animateTo(1f, tween(320, easing = EaseOutCubic)) }
    Row(
        modifier
            .graphicsLayer {
                alpha = x.value
                translationX = -220.dp.toPx() * (1f - x.value)
            }
            .background(horizontal(listOf(Color(0xF28B5CF6), Color(0xD1EC4899))), RoundedCornerShape(R2.pill))
            .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f, fill = false)) {
            T(item.fromName, V.Small, lines = 1)
            T("sent ${item.giftName} to ${item.toName}", V.Tiny, C.gold, lines = 1)
        }
        Spacer(Modifier.width(8.dp))
        Picture(Assets.gift(item.icon), 34.dp)
        if (item.count > 1) {
            Spacer(Modifier.width(8.dp))
            T("×${item.count}", V.Heading, C.gold)
        }
    }
}
