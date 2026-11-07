package `in`.voltkit.livedemo.kotlin.gifts

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import `in`.voltkit.livedemo.kotlin.data.Api
import `in`.voltkit.livedemo.kotlin.data.ApiError
import `in`.voltkit.livedemo.kotlin.data.Assets
import `in`.voltkit.livedemo.kotlin.data.Gift
import `in`.voltkit.livedemo.kotlin.data.Room
import `in`.voltkit.livedemo.kotlin.data.Session
import `in`.voltkit.livedemo.kotlin.rtc.Member
import `in`.voltkit.livedemo.kotlin.screens.compact
import `in`.voltkit.livedemo.kotlin.ui.Avatar
import `in`.voltkit.livedemo.kotlin.ui.C
import `in`.voltkit.livedemo.kotlin.ui.GradientButton
import `in`.voltkit.livedemo.kotlin.ui.Icon
import `in`.voltkit.livedemo.kotlin.ui.Ion
import `in`.voltkit.livedemo.kotlin.ui.Picture
import `in`.voltkit.livedemo.kotlin.ui.R2
import `in`.voltkit.livedemo.kotlin.ui.SheetHost
import `in`.voltkit.livedemo.kotlin.ui.SheetScope
import `in`.voltkit.livedemo.kotlin.ui.T
import `in`.voltkit.livedemo.kotlin.ui.ToastKind
import `in`.voltkit.livedemo.kotlin.ui.V
import `in`.voltkit.livedemo.kotlin.ui.tap
import `in`.voltkit.livedemo.kotlin.ui.toast
import kotlinx.coroutines.launch
import kotlin.math.floor

private val COUNTS = listOf(1, 5, 10)

fun showGiftSheet(room: Room, people: List<Member>, initialTo: String?) = SheetHost.show("gift-sheet") { GiftSheet(room, people, initialTo) }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SheetScope.GiftSheet(room: Room, people: List<Member>, initialTo: String?) {
    val scope = rememberCoroutineScope()
    val guest = Session.guest
    var gifts by remember { mutableStateOf(emptyList<Gift>()) }
    var selected by remember { mutableStateOf<String?>(null) }
    var to by remember { mutableStateOf(initialTo) }
    var count by remember { mutableIntStateOf(1) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        try {
            gifts = GiftCatalog.load()
        } catch (e: ApiError) {
            toast("Could not load the gifts", ToastKind.Error)
        }
    }
    val target = people.firstOrNull { it.identity == to } ?: people.firstOrNull()
    val gift = gifts.firstOrNull { it.id == selected } ?: gifts.firstOrNull()
    val cost = (gift?.price ?: 0) * count
    val short = guest.coins < cost
    // Whole numbers, inside the sheet's own border: four must always fit on a row.
    val cell = floor((LocalConfiguration.current.screenWidthDp - 36f - 2f - 8f * 3) / 4f).dp

    Column(Modifier.verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            T("Send a gift", V.Heading, modifier = Modifier.weight(1f))
            Row(
                Modifier
                    .tap({
                        scope.launch {
                            try {
                                Session.setGuest(Api.topUp())
                                toast("Demo coins added", ToastKind.Success)
                            } catch (e: ApiError) {
                                toast(e.message, ToastKind.Error)
                            }
                        }
                    }, "Get more demo coins", "topup")
                    .background(C.glass, RoundedCornerShape(R2.pill))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(Ion.logoBitcoin, 15.dp, C.gold)
                T(compact(guest.coins), V.Small, C.gold)
                Icon(Ion.addCircle, 17.dp, C.gold)
            }
        }
        Spacer(Modifier.height(4.dp))
        T("Demo coins only. They are free, have no value and cannot be bought or cashed out.", V.Tiny, C.textFaint)
        Spacer(Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(0.dp)) {
            items(people, key = { it.identity }) { p ->
                val mine = p.identity == guest.id
                val on = target?.identity == p.identity
                Row(
                    Modifier
                        .tap({ to = p.identity }, "Send to ${if (mine) "yourself" else p.name}", if (mine) "gift-to-me" else "gift-to-${p.identity}")
                        .height(34.dp)
                        .background(if (on) Color(0x33D946EF) else C.glass, RoundedCornerShape(R2.pill))
                        .border(1.dp, if (on) C.magenta else Color.Transparent, RoundedCornerShape(R2.pill))
                        .padding(start = 4.dp, end = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(p.avatar, size = 24.dp)
                    Spacer(Modifier.width(6.dp))
                    T(if (mine) "${p.name} (you)" else p.name, V.Tiny, lines = 1, modifier = Modifier.widthIn(max = 90.dp))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (g in gifts) {
                val on = gift?.id == g.id
                Column(
                    Modifier
                        .width(cell)
                        .tap({ selected = g.id }, "${g.name}, ${g.price} demo coins", "gift-${g.id}")
                        .background(if (on) Color(0x1AFFD765) else C.glass, RoundedCornerShape(R2.md))
                        .border(1.dp, if (on) C.gold else Color.Transparent, RoundedCornerShape(R2.md))
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Picture(Assets.gift(g.icon), 54.dp)
                    T(g.name, V.Tiny, lines = 1)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Ion.logoBitcoin, 10.dp, C.gold)
                        Spacer(Modifier.width(2.dp))
                        T(compact(g.price), V.Tiny, C.gold)
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            for (c in COUNTS) {
                Box(
                    Modifier.padding(end = 4.dp).size(42.dp).tap({ count = c }, "Send $c").background(if (count == c) C.purple else C.glass, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { T("×$c", V.Small) }
            }
            Spacer(Modifier.width(6.dp))
            GradientButton(
                if (short) "Not enough coins" else "Send · ${compact(cost)}",
                {
                    val g = gift ?: return@GradientButton
                    val t = target ?: return@GradientButton
                    busy = true
                    scope.launch {
                        try {
                            Session.setGuest(Api.sendGift(room.id, g.id, t.identity, count))
                            close()
                        } catch (e: ApiError) {
                            toast(e.message, ToastKind.Error)
                        } finally {
                            busy = false
                        }
                    }
                },
                Modifier.weight(1f),
                disabled = short || target == null || gift == null,
                loading = busy,
                id = "gift-send",
            )
        }
    }
}
