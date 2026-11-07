package `in`.voltkit.livedemo.kotlin.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import `in`.voltkit.livedemo.kotlin.ui.C
import `in`.voltkit.livedemo.kotlin.ui.G
import `in`.voltkit.livedemo.kotlin.ui.Icon
import `in`.voltkit.livedemo.kotlin.ui.Ion
import `in`.voltkit.livedemo.kotlin.ui.T
import `in`.voltkit.livedemo.kotlin.ui.V
import `in`.voltkit.livedemo.kotlin.ui.diagonal
import `in`.voltkit.livedemo.kotlin.ui.tap
import kotlin.math.max

/**
 * The four tabs and the button in the middle. [focus] changes each time a tab comes into view, or the app comes back to the
 * tabs from another screen, so a tab can load again as the React Native app's useFocusEffect does.
 */
@Composable
fun TabsScreen() {
    val nav = LocalNav.current
    var selected by rememberSaveable { mutableIntStateOf(0) }
    var resumed by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumed++
        onPauseOrDispose { }
    }
    val holder = rememberSaveableStateHolder()
    Box(Modifier.fillMaxSize()) {
        holder.SaveableStateProvider(selected) {
            when (selected) {
                0 -> HomeScreen(resumed)
                1 -> MatchLanding(resumed)
                2 -> InboxScreen(resumed)
                else -> ProfileScreen()
            }
        }
        TabBar(Modifier.align(Alignment.BottomCenter), selected, onSelect = { selected = it }, onCreate = { nav.navigate(Routes.create()) })
    }
}

@Composable
private fun TabBar(modifier: Modifier, selected: Int, onSelect: (Int) -> Unit, onCreate: () -> Unit) {
    val bottom = bottomInset()
    Box(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .padding(top = 24.dp)
                .fillMaxWidth()
                .background(Color(0xF7100920))
                .drawBehind { drawLine(C.glassBorder, Offset(0f, 0f), Offset(size.width, 0f), 1f) }
                .padding(top = 8.dp, bottom = if (bottom > 8.dp) bottom else 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Tab(0, "tab-Home", Ion.homeOutline, Ion.home, "Home", selected, onSelect)
            Tab(1, "tab-Face", Ion.shuffleOutline, Ion.shuffle, "1v1", selected, onSelect)
            Spacer(Modifier.weight(1f))
            Tab(2, "tab-Chat", Ion.chatbubblesOutline, Ion.chatbubbles, "Chat", selected, onSelect)
            Tab(3, "tab-Me", Ion.personOutline, Ion.person, "Me", selected, onSelect)
        }
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .tap(onCreate, "Start a room", "tab-create")
                .shadow(18.dp, CircleShape, ambientColor = C.magenta, spotColor = C.magenta)
                .size(52.dp)
                .background(diagonal(G.accent), CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(Ion.add, 30.dp) }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Tab(
    index: Int,
    id: String,
    icon: String,
    on: String,
    label: String,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    val active = selected == index
    Column(
        Modifier.weight(1f).tap({ onSelect(index) }, label, id, selected = active),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(if (active) on else icon, 22.dp, if (active) C.magenta else C.textDim)
        Spacer(Modifier.height(2.dp))
        T(label, V.Tiny, if (active) C.white else C.textDim)
    }
}

@Suppress("unused")
private fun unused() = max(1, 2)
