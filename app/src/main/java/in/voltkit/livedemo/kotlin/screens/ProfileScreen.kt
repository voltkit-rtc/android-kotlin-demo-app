package `in`.voltkit.livedemo.kotlin.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.voltkit.rtc.VoltKitRtc
import `in`.voltkit.livedemo.kotlin.BuildConfig
import `in`.voltkit.livedemo.kotlin.EchoSetting
import `in`.voltkit.livedemo.kotlin.Voltkit
import `in`.voltkit.livedemo.kotlin.data.Api
import `in`.voltkit.livedemo.kotlin.data.ApiError
import `in`.voltkit.livedemo.kotlin.data.Assets
import `in`.voltkit.livedemo.kotlin.data.GuestPublic
import `in`.voltkit.livedemo.kotlin.data.Session
import `in`.voltkit.livedemo.kotlin.ui.Avatar
import `in`.voltkit.livedemo.kotlin.ui.Backdrop
import `in`.voltkit.livedemo.kotlin.ui.BuiltWith
import `in`.voltkit.livedemo.kotlin.ui.C
import `in`.voltkit.livedemo.kotlin.ui.Field
import `in`.voltkit.livedemo.kotlin.ui.G
import `in`.voltkit.livedemo.kotlin.ui.GhostButton
import `in`.voltkit.livedemo.kotlin.ui.GradientButton
import `in`.voltkit.livedemo.kotlin.ui.Icon
import `in`.voltkit.livedemo.kotlin.ui.IconBtn
import `in`.voltkit.livedemo.kotlin.ui.Ion
import `in`.voltkit.livedemo.kotlin.ui.Panel
import `in`.voltkit.livedemo.kotlin.ui.R2
import `in`.voltkit.livedemo.kotlin.ui.SheetHost
import `in`.voltkit.livedemo.kotlin.ui.SheetRow
import `in`.voltkit.livedemo.kotlin.ui.T
import `in`.voltkit.livedemo.kotlin.ui.ToastKind
import `in`.voltkit.livedemo.kotlin.ui.V
import `in`.voltkit.livedemo.kotlin.ui.openUrl
import `in`.voltkit.livedemo.kotlin.ui.tap
import `in`.voltkit.livedemo.kotlin.ui.toast
import kotlinx.coroutines.launch

private val FRAMES = listOf("none" to "None", "lion" to "Golden Lion", "wolf" to "Silver Wolf")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileScreen() {
    val nav = LocalNav.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val guest = Session.guestOrNull ?: return
    var phoneCanceller by remember { mutableStateOf(EchoSetting.phoneCanceller(context)) }

    fun update(name: String? = null, avatar: String? = null, frame: String? = null) = scope.launch {
        try {
            Session.setGuest(Api.updateMe(name, avatar, frame))
        } catch (e: ApiError) {
            toast(e.message, ToastKind.Error)
        }
    }

    fun topUp() = scope.launch {
        try {
            Session.setGuest(Api.topUp())
            toast("Demo coins added", ToastKind.Success)
        } catch (e: ApiError) {
            toast(e.message, ToastKind.Error)
        }
    }

    fun pickAvatar() = SheetHost.show(title = "Pick an avatar") {
        FlowRow(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            for (key in Assets.avatarKeys) {
                Box(Modifier.tap({ close(); update(avatar = key) }, "Avatar $key")) { Avatar(key, size = 68.dp) }
            }
        }
    }

    fun editName() = SheetHost.show(title = "Your name") {
        var name by remember { mutableStateOf(TextFieldValue(guest.name, TextRange(guest.name.length))) }
        var saving by remember { mutableStateOf(false) }
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { focus.requestFocus() }
        Column {
            Field(name, { name = it }, Modifier.fillMaxWidth(), id = "profile-name-input", maxLength = 24, focus = focus)
            Spacer(Modifier.height(12.dp))
            GradientButton(
                "Save",
                {
                    saving = true
                    scope.launch {
                        update(name = name.text.trim()).join()
                        close()
                    }
                },
                Modifier.fillMaxWidth(),
                loading = saving,
                disabled = name.text.trim().length < 2,
                id = "profile-name-save",
            )
        }
    }

    fun confirmDelete() = SheetHost.show {
        Column {
            T("Delete everything?", V.Title)
            Spacer(Modifier.height(10.dp))
            T("This guest, their coins and their direct messages are removed for good. Live rooms you host end.", color = C.textDim)
            Spacer(Modifier.height(16.dp))
            GradientButton(
                "Delete my data",
                {
                    close()
                    scope.launch {
                        try {
                            Api.deleteMe()
                            Session.forget()
                        } catch (e: ApiError) {
                            toast("That did not work. Try again.", ToastKind.Error)
                        }
                    }
                },
                Modifier.fillMaxWidth(),
                colors = G.danger,
                id = "profile-delete-yes",
            )
            Spacer(Modifier.height(10.dp))
            GhostButton("Keep it", { close() }, Modifier.fillMaxWidth())
        }
    }

    fun chooseEcho() = SheetHost.show(title = "Echo cancellation") {
        Column {
            T(
                "If the other person hears their own voice from your phone on the loudspeaker, try the other canceller. It applies the next time the app starts.",
                V.Small,
                C.textDim,
            )
            Spacer(Modifier.height(8.dp))
            for ((phone, label, hint) in listOf(
                Triple(true, "The phone's canceller", "The Android SDK's default"),
                Triple(false, "WebRTC's canceller", "For a phone whose own one lets echo through. Weaker on the loudspeaker"),
            )) {
                SheetRow(if (phoneCanceller == phone) Ion.checkmarkCircle else Ion.ellipsisHorizontal, label, {
                    close()
                    if (phone != phoneCanceller) {
                        phoneCanceller = phone
                        EchoSetting.set(context, phone)
                        toast("Applies the next time the app starts", ToastKind.Success)
                    }
                }, hint = hint, gold = phoneCanceller == phone)
            }
        }
    }

    Backdrop(modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, top = topInset() + 12.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.tap(::pickAvatar, "Change avatar", "profile-avatar").padding(vertical = 26.dp)) { Avatar(guest.avatar, guest.frame, 92.dp) }
                Spacer(Modifier.height(4.dp))
                Row(Modifier.tap(::editName, "Change name", "profile-name"), verticalAlignment = Alignment.CenterVertically) {
                    T(guest.name, V.Title)
                    Spacer(Modifier.width(7.dp))
                    Icon(Ion.pencil, 15.dp, C.textDim)
                }
                Spacer(Modifier.height(4.dp))
                T("Guest on this phone", V.Tiny, C.textFaint)
            }
            Panel {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            T("Demo coins", V.Small, C.textDim)
                            T(grouped(guest.coins), V.Display, C.gold, id = "coin-balance")
                        }
                        Spacer(Modifier.width(10.dp))
                        GradientButton("Get more", { topUp() }, icon = Ion.add, dark = true, colors = G.gold, id = "profile-topup")
                    }
                    Spacer(Modifier.height(6.dp))
                    T("Free and fictional. For trying gifts only: they have no value and cannot be bought or cashed out.", V.Tiny, C.textFaint)
                }
            }
            Panel {
                Column {
                    T("Avatar frame", V.Small, C.textDim)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for ((id, label) in FRAMES) {
                            Column(
                                Modifier
                                    .weight(1f)
                                    .tap({ update(frame = id) }, "$label frame", "frame-$id", selected = guest.frame == id)
                                    .clip(RoundedCornerShape(R2.md))
                                    .background(C.glass)
                                    .border(1.dp, if (guest.frame == id) C.gold else Color.Transparent, RoundedCornerShape(R2.md))
                                    .padding(vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Box(Modifier.height(66.dp), contentAlignment = Alignment.Center) { Avatar(guest.avatar, id, 36.dp) }
                                T(label, V.Tiny, lines = 1)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    T("A frame shows on your seat, and plays an entrance effect when you join a room.", V.Tiny, C.textFaint)
                }
            }
            T("Settings", V.Small, C.textDim, Modifier.padding(top = 6.dp))
            Panel(padding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                Column {
                    SheetRow(Ion.banOutline, "Blocked people", { nav.navigate(Routes.BLOCKED) }, id = "profile-blocked")
                    SheetRow(
                        Ion.pulseOutline,
                        "Echo cancellation",
                        ::chooseEcho,
                        hint = if (phoneCanceller) "The phone's canceller" else "WebRTC's canceller",
                        id = "profile-echo",
                    )
                    SheetRow(Ion.trashOutline, "Delete my data", ::confirmDelete, hint = "Removes this guest and their messages", danger = true, id = "profile-delete")
                }
            }
            T("About this app", V.Small, C.textDim, Modifier.padding(top = 6.dp))
            Panel {
                Column {
                    T(Voltkit.DEMO_LINE, V.Heading)
                    Spacer(Modifier.height(4.dp))
                    T(
                        "Everything live in here runs on VoltKit RTC: rooms, calls, chat and gifts. Want the same experience in your own app? You can build it with VoltKit.",
                        V.Small,
                        C.textDim,
                    )
                    Spacer(Modifier.height(12.dp))
                    BuiltWith(start = true)
                }
            }
            Panel(padding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)) {
                Column {
                    SheetRow(Ion.rocketOutline, "Build this into your app", { openUrl(context, Voltkit.KOTLIN_PAGE) }, hint = "Video, voice, chat and gifts with Kotlin", gold = true, id = "about-build")
                    SheetRow(Ion.globeOutline, "Go to voltkit.in", { openUrl(context, Voltkit.SITE) }, hint = "All of VoltKit RTC", id = "about-site")
                    SheetRow(Ion.flashOutline, "Android SDK docs", { openUrl(context, Voltkit.KOTLIN_DOCS) }, hint = "Calls, audio rooms, routing and errors", id = "about-docs")
                    SheetRow(Ion.flashOutline, "Quickstart", { openUrl(context, Voltkit.QUICKSTART) }, hint = "From sign-up to a working call", id = "about-quickstart")
                    SheetRow(Ion.informationCircleOutline, "What this demo shows", { nav.navigate(Routes.VOLTKIT) }, id = "profile-voltkit")
                }
            }
            T(
                "Version ${BuildConfig.VERSION_NAME}  ·  Kotlin ${KotlinVersion.CURRENT}  ·  VoltKit SDK ${VoltKitRtc.VERSION}",
                V.Tiny,
                C.textFaint,
                Modifier.fillMaxWidth(),
                align = TextAlign.Center,
                id = "about-versions",
            )
        }
    }
}

@Composable
fun BlockedScreen() {
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    var people by remember { mutableStateOf<List<GuestPublic>?>(null) }
    LaunchedEffect(Unit) { people = try { Api.blocks() } catch (e: ApiError) { emptyList() } }

    fun unblock(id: String) = scope.launch {
        try {
            Api.unblock(id)
            Session.refresh()
        } catch (e: ApiError) {
            // Still listed next time, if it did not go through.
        }
        people = people?.filter { it.id != id }
    }

    Backdrop(modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.padding(start = 14.dp, end = 14.dp, top = topInset() + 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBtn(Ion.chevronBack, "Back", { nav.popBackStack() }, 38.dp)
                Spacer(Modifier.width(10.dp))
                T("Blocked people", V.Title)
            }
            val list = people
            if (list != null && list.isEmpty()) {
                T("Nobody is blocked.", color = C.textDim, modifier = Modifier.fillMaxWidth().padding(top = 46.dp), align = TextAlign.Center)
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(list ?: emptyList(), key = { it.id }) { item ->
                        Row(Modifier.fillMaxWidth().background(C.glass, RoundedCornerShape(R2.md)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(item.avatar, size = 42.dp)
                            Spacer(Modifier.width(10.dp))
                            T(item.name, V.Medium, lines = 1, modifier = Modifier.weight(1f))
                            Spacer(Modifier.width(10.dp))
                            Box(
                                Modifier.tap({ unblock(item.id) }, "Unblock ${item.name}").background(C.glassStrong, RoundedCornerShape(R2.pill)).padding(horizontal = 12.dp, vertical = 7.dp),
                            ) { T("Unblock", V.Tiny) }
                        }
                    }
                }
            }
        }
    }
}
