package `in`.voltkit.livedemo.kotlin.screens

import android.net.Uri
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import `in`.voltkit.livedemo.kotlin.R
import `in`.voltkit.livedemo.kotlin.Voltkit
import `in`.voltkit.livedemo.kotlin.data.GuestPublic
import `in`.voltkit.livedemo.kotlin.data.Session
import `in`.voltkit.livedemo.kotlin.data.SessionStatus
import `in`.voltkit.livedemo.kotlin.screens.room.RoomExit
import `in`.voltkit.livedemo.kotlin.screens.room.RoomScreen
import `in`.voltkit.livedemo.kotlin.ui.BuiltWith
import `in`.voltkit.livedemo.kotlin.ui.C
import `in`.voltkit.livedemo.kotlin.ui.G
import `in`.voltkit.livedemo.kotlin.ui.GradientButton
import `in`.voltkit.livedemo.kotlin.ui.Icon
import `in`.voltkit.livedemo.kotlin.ui.Ion
import `in`.voltkit.livedemo.kotlin.ui.Picture
import `in`.voltkit.livedemo.kotlin.ui.SheetLayer
import `in`.voltkit.livedemo.kotlin.ui.T
import `in`.voltkit.livedemo.kotlin.ui.ToastHost
import `in`.voltkit.livedemo.kotlin.ui.V
import `in`.voltkit.livedemo.kotlin.ui.Wordmark
import `in`.voltkit.livedemo.kotlin.ui.vertical
import kotlinx.coroutines.delay

val LocalNav = staticCompositionLocalOf<NavHostController> { error("no navigation") }

object Routes {
    const val TABS = "tabs"
    fun create(type: String = "video") = "create?type=$type"
    fun room(id: String) = "room/$id"
    const val MATCH = "match"
    fun thread(conversationId: String, peer: GuestPublic) =
        "thread/$conversationId?peer=${Uri.encode(peer.id)}&name=${Uri.encode(peer.name)}&avatar=${peer.avatar}&frame=${peer.frame}"
    const val LOUNGE = "lounge"
    const val VOLTKIT = "voltkit"
    const val BLOCKED = "blocked"
}

private const val LAUNCH_MS = 1600L

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AppRoot() {
    var launched by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(LAUNCH_MS)
        launched = true
    }
    // Test tags show up as resource ids, so UI Automator finds controls the way it does in the other two apps.
    Box(Modifier.fillMaxSize().background(C.bg).semantics { testTagsAsResourceId = true }) {
        when {
            Session.status == SessionStatus.Loading || !launched -> LaunchScreen()
            Session.status == SessionStatus.Offline -> OfflineScreen()
            Session.status == SessionStatus.GuestNeeded -> OnboardingScreen()
            else -> SignedIn()
        }
        SheetLayer()
        ToastHost()
    }
}

@Composable
private fun SignedIn() {
    val nav = rememberNavController()
    // Left from the notification: the room comes off the stack, with anything opened over it.
    val exitRoom = RoomExit.request
    LaunchedEffect(exitRoom) {
        if (exitRoom == null) return@LaunchedEffect
        RoomExit.request = null
        nav.popBackStack("room/{roomId}", inclusive = true)
    }
    val slide = tween<androidx.compose.ui.unit.IntOffset>(280, easing = FastOutSlowInEasing)
    CompositionLocalProvider(LocalNav provides nav) {
        NavHost(
            nav,
            startDestination = Routes.TABS,
            enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, slide) },
            exitTransition = { fadeOut(tween(280)) },
            popEnterTransition = { fadeIn(tween(200)) },
            popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, slide) },
        ) {
            composable(Routes.TABS) { TabsScreen() }
            composable(
                "create?type={type}",
                arguments = listOf(navArgument("type") { type = NavType.StringType; defaultValue = "video" }),
                enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Up, tween(300)) },
                popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Down, tween(300)) },
            ) { CreateScreen(it.arguments?.getString("type") ?: "video") }
            composable(
                "room/{roomId}",
                enterTransition = { fadeIn(tween(220)) },
                popExitTransition = { fadeOut(tween(220)) },
            ) { RoomScreen(it.arguments?.getString("roomId")!!) }
            composable(
                Routes.MATCH,
                enterTransition = { fadeIn(tween(220)) },
                popExitTransition = { fadeOut(tween(220)) },
            ) { MatchScreen() }
            composable(
                "thread/{id}?peer={peer}&name={name}&avatar={avatar}&frame={frame}",
                arguments = listOf("peer", "name", "avatar", "frame").map { a -> navArgument(a) { type = NavType.StringType; defaultValue = "" } },
            ) {
                val a = it.arguments!!
                ThreadScreen(
                    a.getString("id")!!,
                    GuestPublic(a.getString("peer")!!, a.getString("name")!!, a.getString("avatar")!!, a.getString("frame")!!),
                )
            }
            composable(Routes.LOUNGE) { LoungeScreen() }
            composable(Routes.VOLTKIT) { VoltKitScreen() }
            composable(Routes.BLOCKED) { BlockedScreen() }
        }
    }
}

/** Every start says what this is, long enough to read, before the app itself. */
@Composable
private fun LaunchScreen() {
    val shown = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) { shown.animateTo(1f, tween(420)) }
    Box(Modifier.fillMaxSize().background(vertical(G.launch)).testTag("launch-screen")) {
        Column(
            Modifier
                .align(Alignment.Center)
                .padding(horizontal = 24.dp)
                .fadeScale(0.92f + 0.08f * shown.value, shown.value),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Picture(R.drawable.brand_mark, 104.dp)
            Spacer(Modifier.height(18.dp))
            Wordmark(38)
            Spacer(Modifier.height(8.dp))
            T(Voltkit.DEMO_LINE, V.Heading, C.textDim)
            Spacer(Modifier.height(8.dp))
            T(Voltkit.TAGLINE, V.Small, C.gold)
        }
        Box(Modifier.align(Alignment.BottomCenter).padding(start = 16.dp, end = 16.dp, bottom = 56.dp).fadeScale(1f, shown.value)) { BuiltWith() }
    }
}

private fun Modifier.fadeScale(scale: Float, alpha: Float) = graphicsLayer {
    scaleX = scale
    scaleY = scale
    this.alpha = alpha
}

@Composable
private fun OfflineScreen() {
    Box(Modifier.fillMaxSize().background(C.bg), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Wordmark(32)
            Icon(Ion.cloudOfflineOutline, 40.dp, C.textDim)
            T("Could not reach the server.", color = C.textDim, align = TextAlign.Center)
            GradientButton("Try again", { Session.load() })
        }
    }
}

