package `in`.voltkit.livedemo.kotlin.ui

import android.os.Handler
import android.os.Looper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

enum class ToastKind { Info, Error, Success }

private data class ToastItem(val text: String, val kind: ToastKind, val seq: Int)

private var current by mutableStateOf<ToastItem?>(null)
private var seq = 0
private val main = Handler(Looper.getMainLooper())

/** Shows a short message over every screen, sheets included. Safe to call from any thread. */
fun toast(text: String, kind: ToastKind = ToastKind.Info) {
    main.post { current = ToastItem(text, kind, ++seq) }
}

@Composable
fun ToastHost() {
    var shown by remember { mutableStateOf<ToastItem?>(null) }
    var visible by remember { mutableStateOf(false) }
    val item = current
    LaunchedEffect(item?.seq) {
        if (item == null) return@LaunchedEffect
        shown = item
        visible = true
        delay(2800)
        visible = false
    }
    val tint = when (shown?.kind) {
        ToastKind.Error -> C.danger
        ToastKind.Success -> C.success
        else -> C.purple
    }
    Box(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(top = 8.dp, start = 16.dp, end = 16.dp)) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(220)) + slideInVertically(tween(220)) { -it / 3 },
            exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { -it / 3 },
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .background(Color(0xF7170B2E), RoundedCornerShape(R2.md))
                    .border(1.dp, tint, RoundedCornerShape(R2.md))
                    .padding(horizontal = 16.dp, vertical = 11.dp),
            ) {
                T(shown?.text ?: "", V.Small, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
