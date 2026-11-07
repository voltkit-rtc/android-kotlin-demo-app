package `in`.voltkit.livedemo.kotlin.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import `in`.voltkit.livedemo.kotlin.R
import `in`.voltkit.livedemo.kotlin.Voltkit
import `in`.voltkit.livedemo.kotlin.data.Assets

fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        toast("No browser on this phone could open that link.", ToastKind.Error)
    }
}

/** Hairline borders, as StyleSheet.hairlineWidth. */
@Composable
fun hairline(): Dp = with(LocalDensity.current) { 1f.toDp() }

/** Text in the app's own type scale. */
@Composable
fun T(
    text: String,
    variant: V = V.Body,
    color: Color = C.text,
    modifier: Modifier = Modifier,
    align: TextAlign? = null,
    lines: Int = Int.MAX_VALUE,
    size: Int? = null,
    spacing: Float? = null,
    id: String? = null,
) {
    BasicText(
        text = text,
        modifier = if (id != null) modifier.testTag(id) else modifier,
        style = textStyle(variant, color, size, spacing).let { if (align != null) it.copy(textAlign = align) else it },
        maxLines = lines,
        overflow = if (lines == Int.MAX_VALUE) TextOverflow.Clip else TextOverflow.Ellipsis,
        softWrap = lines != 1,
    )
}

@Composable
fun AnnotatedT(text: AnnotatedString, style: TextStyle, modifier: Modifier = Modifier) {
    BasicText(text, modifier, style = style)
}

/** One of the Ionicons glyphs, as the React Native and Flutter apps draw them. */
@Composable
fun Icon(glyph: String, size: Dp, color: Color = C.white, modifier: Modifier = Modifier) {
    val fontSize = fixedSp(size)
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        BasicText(
            glyph,
            style = TextStyle(fontFamily = IonFont, fontSize = fontSize, lineHeight = fontSize, color = color, textAlign = TextAlign.Center),
            modifier = Modifier.requiredSize(size * 1.2f).offset(y = 0.dp),
        )
    }
}

@Composable
fun Wordmark(size: Int = 22) {
    val style = textStyle(V.Display, size = size).copy(fontWeight = FontWeight.ExtraBold, letterSpacing = fixedSp((-0.4).dp))
    BasicText(
        buildAnnotatedString {
            append("Volt")
            withStyle(SpanStyle(color = C.cyan)) { append("Kit") }
        },
        style = style,
    )
}

/**
 * Pressable: dims and shrinks a little under the finger, and names itself for tests and screen readers. With a [label], what the
 * screen reader says is the label, not the text inside.
 */
fun Modifier.tap(
    onClick: (() -> Unit)?,
    label: String? = null,
    id: String? = null,
    pressedScale: Float = 1f,
    pressedAlpha: Float = 1f,
    selected: Boolean? = null,
): Modifier = this.then(Modifier.tapInner(onClick, label, id, pressedScale, pressedAlpha, selected))

@Composable
private fun Modifier.tapInnerImpl(
    onClick: (() -> Unit)?,
    label: String?,
    id: String?,
    pressedScale: Float,
    pressedAlpha: Float,
    selected: Boolean?,
): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    var m = this
    if (id != null) m = m.testTag(id)
    m = if (label != null) {
        m.clearAndSetSemantics {
            contentDescription = label
            role = Role.Button
            if (selected != null) this.selected = selected
        }
    } else if (selected != null) {
        m.semantics { this.selected = selected }
    } else {
        m
    }
    if (pressedScale != 1f || pressedAlpha != 1f) {
        m = m.graphicsLayer {
            val s = if (pressed) pressedScale else 1f
            scaleX = s
            scaleY = s
            alpha = if (pressed) pressedAlpha else 1f
        }
    }
    return m.clickable(source, indication = null, enabled = onClick != null, onClick = { onClick?.invoke() })
}

private fun Modifier.tapInner(
    onClick: (() -> Unit)?,
    label: String?,
    id: String?,
    pressedScale: Float,
    pressedAlpha: Float,
    selected: Boolean?,
): Modifier = composed { tapInnerImpl(onClick, label, id, pressedScale, pressedAlpha, selected) }

@Composable
fun Backdrop(colors: List<Color> = G.night, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier.background(C.bg).background(vertical(colors))) { content() }
}

@Composable
fun Spinner(color: Color = C.magenta, size: Dp = 22.dp) {
    CircularProgressIndicator(Modifier.size(size), color = color, strokeWidth = 2.4.dp)
}

@Composable
fun GradientButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: String? = null,
    disabled: Boolean = false,
    loading: Boolean = false,
    colors: List<Color> = G.accent,
    dark: Boolean = false,
    id: String? = null,
) {
    val ink = if (dark) C.ink else C.white
    Row(
        modifier
            .alpha(if (disabled) 0.45f else 1f)
            .tap(if (disabled || loading) null else onClick, label, id, pressedScale = 0.98f, pressedAlpha = 0.85f)
            .height(52.dp)
            .clip(RoundedCornerShape(R2.pill))
            .background(diagonal(colors))
            .padding(horizontal = 22.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            Spinner(ink)
        } else {
            if (icon != null) {
                Icon(icon, 19.dp, ink)
                Spacer(Modifier.width(8.dp))
            }
            T(label, V.Heading, ink, lines = 1)
        }
    }
}

@Composable
fun GhostButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: String? = null, danger: Boolean = false, id: String? = null) {
    val tint = if (danger) C.danger else C.white
    Row(
        modifier
            .tap(onClick, label, id, pressedAlpha = 0.7f)
            .height(48.dp)
            .clip(RoundedCornerShape(R2.pill))
            .background(C.glass)
            .border(hairline(), C.glassBorder, RoundedCornerShape(R2.pill))
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, 18.dp, tint)
            Spacer(Modifier.width(8.dp))
        }
        T(label, V.Medium, tint)
    }
}

@Composable
fun IconBtn(
    icon: String,
    label: String,
    onClick: () -> Unit,
    size: Dp = 44.dp,
    active: Boolean = false,
    danger: Boolean = false,
    tint: Color = C.white,
    badge: Int = 0,
    disabled: Boolean = false,
    turns: Float = 0f,
    id: String? = null,
) {
    Box(Modifier.size(size).alpha(if (disabled) 0.4f else 1f).tap(if (disabled) null else onClick, label, id, pressedAlpha = 0.75f)) {
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(if (danger) C.danger else if (active) C.purple else C.glassStrong)
                .border(hairline(), C.glassBorder, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, size * 0.5f, tint, Modifier.rotate(turns * 360f))
        }
        if (badge > 0) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(3.dp, (-3).dp)
                    .height(17.dp)
                    .widthIn(min = 17.dp)
                    .background(C.pink, RoundedCornerShape(9.dp))
                    .padding(horizontal = 3.dp),
                contentAlignment = Alignment.Center,
            ) {
                T(if (badge > 9) "9+" else "$badge", V.Tiny, size = 10)
            }
        }
    }
}

@Composable
fun Panel(modifier: Modifier = Modifier, padding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues(16.dp), content: @Composable () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(R2.lg))
            .background(diagonal(G.panel))
            .border(hairline(), C.glassBorder, RoundedCornerShape(R2.lg))
            .padding(padding),
    ) { content() }
}

@Composable
fun Picture(@DrawableRes res: Int, size: Dp, modifier: Modifier = Modifier, height: Dp = size, crop: Boolean = false) {
    Image(
        painterResource(res),
        contentDescription = null,
        modifier = modifier.width(size).height(height),
        contentScale = if (crop) ContentScale.Crop else ContentScale.Fit,
    )
}

@Composable
fun Avatar(avatar: String?, frame: String = "none", size: Dp = 44.dp) {
    Box(Modifier.size(size)) {
        Image(
            painterResource(Assets.avatar(avatar)),
            contentDescription = null,
            modifier = Modifier.size(size).clip(CircleShape),
            contentScale = ContentScale.Crop,
        )
        val framed = Assets.frame(frame)
        if (framed != null) {
            Image(
                painterResource(framed),
                contentDescription = null,
                modifier = Modifier.requiredSize(size * 1.92f).offset(x = 0.dp, y = 0.dp).align(Alignment.Center),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

@Composable
fun TagChip(label: String, icon: String? = null, tint: Color = C.white) {
    Row(
        Modifier.background(Color(0x73000000), RoundedCornerShape(R2.pill)).padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, 12.dp, tint)
            Spacer(Modifier.width(4.dp))
        }
        T(label, V.Tiny, tint)
    }
}

@Composable
fun RoleBadge(role: String) {
    if (role == "viewer") return
    val (label, bg, ink) = when (role) {
        "host" -> Triple("HOST", C.gold, C.ink)
        "moderator" -> Triple("MOD", C.success, Color(0xFF032016))
        else -> Triple("CO-HOST", C.purple, C.white)
    }
    Box(Modifier.background(bg, RoundedCornerShape(5.dp)).padding(horizontal = 5.dp, vertical = 1.dp)) {
        T(label, V.Tiny, ink, size = 9)
    }
}

@Composable
fun LiveDot(size: Dp = 7.dp) {
    val pulse by rememberInfiniteTransition(label = "live").animateFloat(
        0.35f,
        1f,
        infiniteRepeatable(tween(650), RepeatMode.Reverse),
        label = "pulse",
    )
    Box(Modifier.size(size).graphicsLayer { alpha = pulse }.background(C.white, CircleShape))
}

@Composable
fun LivePill() {
    Row(
        Modifier.background(horizontal(G.danger), RoundedCornerShape(R2.pill)).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LiveDot(6.dp)
        Spacer(Modifier.width(4.dp))
        T("LIVE", V.Tiny)
    }
}

/** The Kotlin mark, drawn: there is no Kotlin glyph in Ionicons. */
@Composable
fun KotlinMark(size: Dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(w, 0f)
            lineTo(w / 2f, w / 2f)
            lineTo(w, w)
            lineTo(0f, w)
            close()
        }
        drawPath(path, Brush.linearGradient(listOf(Color(0xFF7F52FF), Color(0xFFC811E2), Color(0xFFE54857)), Offset(w, 0f), Offset(0f, w)))
    }
}

@Composable
private fun BrandPill(label: String, url: String, lead: String, name: String, leading: @Composable () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Row(
        Modifier
            .tap({ openUrl(context, url) }, label)
            .background(Color(0x61000000), RoundedCornerShape(R2.pill))
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(Modifier.width(4.dp))
        T(lead, V.Tiny, C.textDim)
        T(name, V.Tiny)
    }
}

@Composable
fun PoweredBy(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        BrandPill(Voltkit.POWERED_BY, Voltkit.SITE, "Powered by ", "VoltKit RTC") { Picture(R.drawable.brand_mark, 14.dp) }
    }
}

/** Says what this app is: a Kotlin demo, running on VoltKit RTC. Both open the page that explains how. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BuiltWith(modifier: Modifier = Modifier, start: Boolean = false) {
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp, if (start) Alignment.Start else Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BrandPill(Voltkit.POWERED_BY, Voltkit.SITE, "Powered by ", "VoltKit RTC") { Picture(R.drawable.brand_mark, 14.dp) }
        BrandPill(Voltkit.MADE_WITH, Voltkit.KOTLIN_PAGE, "Made with ", "Kotlin") { KotlinMark(12.dp) }
    }
}

@Composable
fun SheetRow(
    icon: String,
    label: String,
    onClick: () -> Unit,
    hint: String? = null,
    danger: Boolean = false,
    gold: Boolean = false,
    id: String? = null,
) {
    val tint = if (danger) C.danger else if (gold) C.gold else C.white
    Row(
        Modifier.fillMaxWidth().tap(onClick, label, id, pressedAlpha = 0.6f).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).background(C.glass, CircleShape), contentAlignment = Alignment.Center) { Icon(icon, 19.dp, tint) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            T(label, V.Medium, tint)
            if (hint != null) T(hint, V.Small, C.textFaint)
        }
    }
}

/** A single line input in the app's look. */
@Composable
fun Field(
    value: TextFieldValue,
    onChange: (TextFieldValue) -> Unit,
    modifier: Modifier = Modifier,
    id: String? = null,
    hint: String? = null,
    maxLength: Int = 1000,
    height: Dp = 50.dp,
    radius: Dp = R2.md,
    fontSize: Int = 16,
    weight: FontWeight = FontWeight.Medium,
    fill: Color = C.glass,
    border: Boolean = false,
    enabled: Boolean = true,
    action: ImeAction = ImeAction.Done,
    onSubmit: (() -> Unit)? = null,
    focus: FocusRequester? = null,
) {
    val style = textStyle(V.Body, size = fontSize).copy(fontWeight = weight)
    val shape = RoundedCornerShape(radius)
    Box(
        modifier
            .height(height)
            .clip(shape)
            .background(fill)
            .then(if (border) Modifier.border(BorderStroke(hairline(), C.glassBorder), shape) else Modifier)
            .padding(horizontal = 15.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = value,
            onValueChange = { if (it.text.length <= maxLength) onChange(it) },
            enabled = enabled,
            singleLine = true,
            textStyle = style,
            cursorBrush = SolidColor(C.magenta),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = action),
            keyboardActions = KeyboardActions(onAny = { onSubmit?.invoke() }),
            modifier = Modifier
                .fillMaxWidth()
                .then(if (id != null) Modifier.testTag(id) else Modifier)
                .then(if (focus != null) Modifier.focusRequester(focus) else Modifier),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.text.isEmpty() && hint != null) BasicText(hint, style = style.copy(color = C.textFaint), maxLines = 1)
                    inner()
                }
            },
        )
    }
}

fun Modifier.circle(color: Color) = this.clip(CircleShape).background(color)
