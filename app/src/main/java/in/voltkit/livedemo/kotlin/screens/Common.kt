package `in`.voltkit.livedemo.kotlin.screens

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import kotlin.math.max

/** The status bar's height. */
@Composable
fun topInset(): Dp = with(LocalDensity.current) { WindowInsets.statusBars.getTop(this).toDp() }

/** The navigation bar's height. */
@Composable
fun bottomInset(): Dp = with(LocalDensity.current) { WindowInsets.navigationBars.getBottom(this).toDp() }

/** How much of the screen the keyboard covers, navigation bar included; 0 while it is down. */
@Composable
fun keyboardHeight(): Dp = with(LocalDensity.current) { WindowInsets.ime.getBottom(this).toDp() }

/** The keyboard when it is up, else the navigation bar. */
@Composable
fun keyboardOrBottom(): Dp = with(LocalDensity.current) {
    max(WindowInsets.ime.getBottom(this), WindowInsets.navigationBars.getBottom(this)).toDp()
}

fun compact(n: Int): String {
    fun trim(s: String) = s.removeSuffix(".0")
    return when {
        n < 1000 -> "$n"
        n < 1_000_000 -> trim("%.${if (n < 10_000) 1 else 0}f".format(n / 1000.0)) + "K"
        else -> trim("%.1f".format(n / 1_000_000.0)) + "M"
    }
}

fun clock(seconds: Int) = "%02d:%02d".format(seconds / 60, seconds % 60)

fun grouped(n: Int): String = "%,d".format(n)

fun shortTime(iso: String): String = try {
    val at = java.time.Instant.parse(iso).atZone(java.time.ZoneId.systemDefault())
    val now = java.time.LocalDate.now()
    if (at.toLocalDate() == now) "%02d:%02d".format(at.hour, at.minute) else "${at.dayOfMonth}/${at.monthValue}"
} catch (e: Exception) {
    ""
}
