package `in`.voltkit.livedemo.kotlin.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import `in`.voltkit.livedemo.kotlin.R
import `in`.voltkit.livedemo.kotlin.Voltkit
import `in`.voltkit.livedemo.kotlin.data.Api
import `in`.voltkit.livedemo.kotlin.data.Rates
import `in`.voltkit.livedemo.kotlin.ui.Backdrop
import `in`.voltkit.livedemo.kotlin.ui.BuiltWith
import `in`.voltkit.livedemo.kotlin.ui.C
import `in`.voltkit.livedemo.kotlin.ui.G
import `in`.voltkit.livedemo.kotlin.ui.GhostButton
import `in`.voltkit.livedemo.kotlin.ui.GradientButton
import `in`.voltkit.livedemo.kotlin.ui.Icon
import `in`.voltkit.livedemo.kotlin.ui.IconBtn
import `in`.voltkit.livedemo.kotlin.ui.Ion
import `in`.voltkit.livedemo.kotlin.ui.Panel
import `in`.voltkit.livedemo.kotlin.ui.Picture
import `in`.voltkit.livedemo.kotlin.ui.R2
import `in`.voltkit.livedemo.kotlin.ui.T
import `in`.voltkit.livedemo.kotlin.ui.V
import `in`.voltkit.livedemo.kotlin.ui.Wordmark
import `in`.voltkit.livedemo.kotlin.ui.diagonal
import `in`.voltkit.livedemo.kotlin.ui.openUrl

private val FEATURES = listOf(
    Triple(Ion.videocam, "Video rooms", "Host and co-host cards with adaptive video, up to 1080p"),
    Triple(Ion.mic, "Audio rooms", "Seats kept by the server, with live speaking indicators"),
    Triple(Ion.shuffle, "1v1 calls", "Face-to-face video that reconnects on its own"),
    Triple(Ion.shieldCheckmark, "Roles and moderation", "Host, co-host, moderator and viewer, enforced by the service"),
    Triple(Ion.chatbubbles, "Realtime chat", "In every room, in the lounge and in direct messages"),
    Triple(Ion.sparkles, "SVGA gifts", "Animated gifts and entrance effects, sent through chat"),
)

private fun rupees(value: String): String {
    val n = value.toDoubleOrNull() ?: return value
    return if (n == Math.floor(n)) n.toLong().toString() else n.toString()
}

@Composable
fun VoltKitScreen() {
    val nav = LocalNav.current
    val context = LocalContext.current
    var rates by remember { mutableStateOf<Rates?>(null) }
    LaunchedEffect(Unit) { rates = runCatching { Api.rates() }.getOrNull() }
    Backdrop(G.launch, Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = topInset() + 8.dp, bottom = bottomInset() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            IconBtn(Ion.chevronBack, "Back", { nav.popBackStack() }, 40.dp)
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Picture(R.drawable.brand_mark, 84.dp)
                Wordmark(40)
                T(Voltkit.TAGLINE, V.Heading, C.gold, align = TextAlign.Center)
                T("${Voltkit.BUILD}.", color = C.textDim, align = TextAlign.Center)
                Spacer(Modifier.height(6.dp))
                BuiltWith()
            }
            Panel {
                Column {
                    T(Voltkit.DEMO_LINE, V.Small, C.gold)
                    Spacer(Modifier.height(4.dp))
                    T(
                        "This app is built with Kotlin, and everything live in it runs on VoltKit RTC. Want the same experience in your own app? Start with an API key and the Android SDK.",
                        color = C.textDim,
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((icon, title, text) in FEATURES) {
                    Row(Modifier.fillMaxWidth().background(C.glass, RoundedCornerShape(R2.md)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(38.dp).background(diagonal(G.video), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) { Icon(icon, 19.dp) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            T(title, V.Medium)
                            T(text, V.Small, C.textDim)
                        }
                    }
                }
            }
            val r = rates
            if (r?.audio != null && r.video != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for ((label, value) in listOf("Audio from" to r.audio, "Video" to r.video)) {
                        Box(Modifier.weight(1f)) {
                            Panel {
                                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                    T(label, V.Small, C.textDim)
                                    T("₹${rupees(value)}", V.Display, C.gold)
                                }
                            }
                        }
                    }
                }
                T(r.unit, V.Tiny, C.textFaint, Modifier.fillMaxWidth(), align = TextAlign.Center)
            }
            GradientButton("Build this into your app", { openUrl(context, Voltkit.KOTLIN_PAGE) }, Modifier.fillMaxWidth(), icon = Ion.arrowForward, dark = true, colors = G.gold, id = "voltkit-site")
            GhostButton("Go to voltkit.in", { openUrl(context, Voltkit.SITE) }, Modifier.fillMaxWidth(), icon = Ion.globeOutline)
            GhostButton("Android SDK docs", { openUrl(context, Voltkit.KOTLIN_DOCS) }, Modifier.fillMaxWidth(), icon = Ion.flashOutline)
        }
    }
}
