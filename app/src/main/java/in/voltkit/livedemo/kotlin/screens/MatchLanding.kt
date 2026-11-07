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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import `in`.voltkit.livedemo.kotlin.data.Api
import `in`.voltkit.livedemo.kotlin.data.Session
import `in`.voltkit.livedemo.kotlin.ui.Backdrop
import `in`.voltkit.livedemo.kotlin.ui.C
import `in`.voltkit.livedemo.kotlin.ui.G
import `in`.voltkit.livedemo.kotlin.ui.GradientButton
import `in`.voltkit.livedemo.kotlin.ui.Icon
import `in`.voltkit.livedemo.kotlin.ui.Ion
import `in`.voltkit.livedemo.kotlin.ui.Panel
import `in`.voltkit.livedemo.kotlin.ui.PoweredBy
import `in`.voltkit.livedemo.kotlin.ui.T
import `in`.voltkit.livedemo.kotlin.ui.V
import `in`.voltkit.livedemo.kotlin.ui.vertical

private val RULES = listOf("You are matched with someone at random", "Tap Next at any time to meet someone new", "Report or block anyone from the call")

@Composable
fun MatchLanding(focus: Int) {
    val nav = LocalNav.current
    var waiting by remember { mutableIntStateOf(0) }
    LaunchedEffect(focus) { runCatching { waiting = Api.stats().waiting } }
    Backdrop(modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(start = 16.dp, end = 16.dp, top = topInset() + 12.dp, bottom = 110.dp)) {
            T("1v1 Face Live", V.Display)
            Spacer(Modifier.height(4.dp))
            T("Face to face with someone new, in one tap.", color = C.textDim)
            Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(132.dp).background(vertical(G.match), CircleShape), contentAlignment = Alignment.Center) { Icon(Ion.videocam, 52.dp) }
                }
                Panel {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (rule in RULES) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Ion.checkmarkCircle, 17.dp, C.success)
                                Spacer(Modifier.width(8.dp))
                                T(rule, V.Small, C.textDim, Modifier.weight(1f))
                            }
                        }
                    }
                }
                if (waiting > 0) {
                    T("$waiting ${if (waiting == 1) "person is" else "people are"} waiting now", V.Small, C.gold, Modifier.fillMaxWidth(), align = TextAlign.Center)
                }
            }
            GradientButton(
                "Start matching",
                { nav.navigate(Routes.MATCH) },
                Modifier.fillMaxWidth(),
                icon = Ion.shuffle,
                disabled = Session.guest.restricted,
                colors = G.match,
                id = "match-start",
            )
            Spacer(Modifier.height(12.dp))
            PoweredBy()
        }
    }
}
