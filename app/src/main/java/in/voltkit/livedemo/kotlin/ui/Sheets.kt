package `in`.voltkit.livedemo.kotlin.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CompletableDeferred
import kotlin.math.max

/** What a sheet's content can do: close itself, with an answer for whoever is waiting on it. */
class SheetScope internal constructor(private val entry: SheetEntry) {
    fun close(result: Any? = null) = SheetHost.dismiss(entry, result)
}

class SheetEntry internal constructor(val id: String?, val title: String?, val content: @Composable SheetScope.() -> Unit) {
    internal var closing by mutableStateOf(false)
    internal val result = CompletableDeferred<Any?>()
    internal val scope = SheetScope(this)
}

/** Sheets over the whole app, as the React Native and Flutter apps have them. The newest is on top, and back closes it. */
object SheetHost {
    internal val entries = mutableStateListOf<SheetEntry>()

    fun show(id: String? = null, title: String? = null, content: @Composable SheetScope.() -> Unit): SheetEntry =
        SheetEntry(id, title, content).also { entries.add(it) }

    /** Shows a sheet and waits for its answer: null when it was dismissed without one. */
    @Suppress("UNCHECKED_CAST")
    suspend fun <R> ask(id: String? = null, title: String? = null, content: @Composable SheetScope.() -> Unit): R? =
        show(id, title, content).result.await() as R?

    fun dismiss(entry: SheetEntry, result: Any? = null) {
        if (!entry.result.isCompleted) entry.result.complete(result)
        entry.closing = true
    }

    /** Closes everything, as when the screen under the sheets goes. */
    fun closeAll() = entries.toList().forEach { dismiss(it) }

    internal fun remove(entry: SheetEntry) {
        entries.remove(entry)
    }
}

@Composable
fun SheetLayer() {
    for (entry in SheetHost.entries.toList()) {
        androidx.compose.runtime.key(entry) { SheetFrame(entry, isTop = entry === SheetHost.entries.lastOrNull()) }
    }
}

@Composable
private fun SheetFrame(entry: SheetEntry, isTop: Boolean) {
    val shown = remember { Animatable(0f) }
    var panelHeight by remember { mutableStateOf(600f) }
    LaunchedEffect(entry.closing) {
        if (entry.closing) {
            shown.animateTo(0f, tween(170))
            SheetHost.remove(entry)
        } else {
            shown.animateTo(1f, tween(230, easing = FastOutSlowInEasing))
        }
    }
    BackHandler(enabled = isTop && !entry.closing) { SheetHost.dismiss(entry) }
    val density = LocalDensity.current
    val ime = WindowInsets.ime.getBottom(density)
    val nav = WindowInsets.navigationBars.getBottom(density)
    val bottom = with(density) { (if (ime > 0) ime else max(nav, 14.dp.roundToPx())).toDp() } + 8.dp
    BoxWithConstraints(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = shown.value }
                .background(Color(0x99000000))
                .semantics { contentDescription = "Close" }
                .clickable(remember { MutableInteractionSource() }, indication = null) { SheetHost.dismiss(entry) },
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(max = maxHeight * 0.88f)
                .onSizeChanged { panelHeight = it.height.toFloat() }
                .graphicsLayer { translationY = (1f - shown.value) * panelHeight }
                .background(C.plum, RoundedCornerShape(topStart = R2.xl, topEnd = R2.xl))
                .border(hairline(), C.glassBorder, RoundedCornerShape(topStart = R2.xl, topEnd = R2.xl))
                .clickable(remember { MutableInteractionSource() }, indication = null) {}
                .then(if (entry.id != null) Modifier.testTag(entry.id) else Modifier)
                .padding(start = 18.dp, end = 18.dp, top = 10.dp, bottom = bottom),
        ) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(Modifier.size(40.dp, 4.dp).background(C.glassStrong, RoundedCornerShape(2.dp)))
            }
            Spacer(Modifier.height(12.dp))
            if (entry.title != null) {
                T(entry.title, V.Heading)
                Spacer(Modifier.height(12.dp))
            }
            Box(Modifier.weight(1f, fill = false)) { entry.content(entry.scope) }
        }
    }
}
