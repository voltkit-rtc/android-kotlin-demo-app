package `in`.voltkit.livedemo.kotlin.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import `in`.voltkit.livedemo.kotlin.R
import `in`.voltkit.livedemo.kotlin.Voltkit
import `in`.voltkit.livedemo.kotlin.data.ApiError
import `in`.voltkit.livedemo.kotlin.data.Assets
import `in`.voltkit.livedemo.kotlin.data.Session
import `in`.voltkit.livedemo.kotlin.ui.Avatar
import `in`.voltkit.livedemo.kotlin.ui.Backdrop
import `in`.voltkit.livedemo.kotlin.ui.BuiltWith
import `in`.voltkit.livedemo.kotlin.ui.C
import `in`.voltkit.livedemo.kotlin.ui.Field
import `in`.voltkit.livedemo.kotlin.ui.G
import `in`.voltkit.livedemo.kotlin.ui.GradientButton
import `in`.voltkit.livedemo.kotlin.ui.Icon
import `in`.voltkit.livedemo.kotlin.ui.Ion
import `in`.voltkit.livedemo.kotlin.ui.Picture
import `in`.voltkit.livedemo.kotlin.ui.T
import `in`.voltkit.livedemo.kotlin.ui.V
import `in`.voltkit.livedemo.kotlin.ui.Wordmark
import `in`.voltkit.livedemo.kotlin.ui.tap
import kotlinx.coroutines.launch

private val SUGGESTIONS = listOf("Star", "Nova", "Echo", "Blaze", "Luna", "Orbit", "Pixel", "Comet")
private fun suggestName() = "${SUGGESTIONS.random()}${(100..999).random()}"

@Composable
fun OnboardingScreen() {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(TextFieldValue(suggestName())) }
    var avatar by remember { mutableStateOf(Assets.avatarKeys.random()) }
    var adult by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val ok = name.text.trim().length >= 2 && adult

    fun enter() {
        busy = true
        error = null
        scope.launch {
            try {
                Session.start(name.text.trim(), avatar)
            } catch (e: ApiError) {
                error = e.message
                busy = false
            }
        }
    }

    Backdrop(G.launch, Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val top = topInset()
            val bottom = keyboardOrBottom()
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .padding(start = 20.dp, end = 20.dp, top = top + 14.dp, bottom = bottom + 14.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Picture(R.drawable.brand_mark, 88.dp)
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Wordmark(34)
                        Spacer(Modifier.width(6.dp))
                        T(Voltkit.APP_SUFFIX, V.Title, C.textDim, Modifier.padding(bottom = 3.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                    T(Voltkit.DEMO_LINE, V.Medium, align = TextAlign.Center)
                    Spacer(Modifier.height(6.dp))
                    T(Voltkit.TAGLINE, color = C.textDim, align = TextAlign.Center)
                }
                Spacer(Modifier.height(16.dp))
                Column(Modifier.fillMaxWidth()) {
                    T("Pick an avatar", V.Small, C.textDim)
                    Spacer(Modifier.height(6.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                        items(Assets.avatarKeys) { key ->
                            Box(
                                Modifier
                                    .tap({ avatar = key }, "Avatar $key", selected = avatar == key)
                                    .border(2.dp, if (avatar == key) C.magenta else Color.Transparent, CircleShape)
                                    .padding(3.dp),
                            ) { Avatar(key, size = 56.dp) }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    T("Your name", V.Small, C.textDim)
                    Spacer(Modifier.height(6.dp))
                    Field(name, { name = it }, Modifier.fillMaxWidth(), id = "onboarding-name", hint = "What should people call you?", maxLength = 24, border = true)
                    Spacer(Modifier.height(6.dp))
                    Row(
                        Modifier.fillMaxWidth().tap({ adult = !adult }, "I am 18 or older", "onboarding-age", selected = adult).padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(24.dp)
                                .background(if (adult) C.purple else Color.Transparent, RoundedCornerShape(7.dp))
                                .border(1.5.dp, if (adult) C.purple else C.textDim, RoundedCornerShape(7.dp)),
                            contentAlignment = Alignment.Center,
                        ) { if (adult) Icon(Ion.checkmark, 16.dp) }
                        Spacer(Modifier.width(10.dp))
                        T("I am 18 or older, and I will be kind. People who break the rules get removed.", V.Small, C.textDim, Modifier.weight(1f))
                    }
                    error?.let { T(it, V.Small, C.danger) }
                    Spacer(Modifier.height(14.dp))
                    GradientButton("Enter", ::enter, Modifier.fillMaxWidth(), icon = Ion.arrowForward, disabled = !ok, loading = busy, id = "onboarding-enter")
                    Spacer(Modifier.height(6.dp))
                    T("No account, no password. You are a guest on this phone.", V.Tiny, C.textFaint, Modifier.fillMaxWidth(), align = TextAlign.Center)
                }
                Spacer(Modifier.height(16.dp))
                BuiltWith()
            }
        }
    }
}
