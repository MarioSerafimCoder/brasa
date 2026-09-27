package com.brasa.tv.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.brasa.tv.core.model.CatalogItem
import com.brasa.tv.core.model.isWatched
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.view.KeyEvent

enum class BrasaButtonStyle { Primary, Secondary, Ghost }
enum class MediaCardFormat { Landscape, Poster, CompactPoster }
enum class BrasaIcon { Search, Settings, Play, Pause, Replay, Forward, Power, Info, Mic, Back, Add, Check, More }

@Composable
fun BrasaVectorIcon(icon: BrasaIcon, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(19.dp)) {
        val stroke = size.minDimension * .105f
        val center = androidx.compose.ui.geometry.Offset(size.width / 2, size.height / 2)
        when (icon) {
            BrasaIcon.Search -> {
                drawCircle(color, size.minDimension * .29f, center.copy(x = size.width * .43f, y = size.height * .43f), style = Stroke(stroke, cap = StrokeCap.Round))
                drawLine(color, androidx.compose.ui.geometry.Offset(size.width * .64f, size.height * .64f), androidx.compose.ui.geometry.Offset(size.width * .88f, size.height * .88f), stroke, StrokeCap.Round)
            }
            BrasaIcon.Settings -> {
                drawCircle(color, size.minDimension * .2f, center, style = Stroke(stroke))
                repeat(8) { index ->
                    val angle = Math.toRadians(index * 45.0)
                    val a = androidx.compose.ui.geometry.Offset(center.x + kotlin.math.cos(angle).toFloat() * size.width * .31f, center.y + kotlin.math.sin(angle).toFloat() * size.height * .31f)
                    val b = androidx.compose.ui.geometry.Offset(center.x + kotlin.math.cos(angle).toFloat() * size.width * .44f, center.y + kotlin.math.sin(angle).toFloat() * size.height * .44f)
                    drawLine(color, a, b, stroke, StrokeCap.Round)
                }
            }
            BrasaIcon.Play -> drawPath(Path().apply { moveTo(size.width * .27f, size.height * .16f); lineTo(size.width * .84f, size.height * .5f); lineTo(size.width * .27f, size.height * .84f); close() }, color)
            BrasaIcon.Pause -> { drawRoundRect(color, androidx.compose.ui.geometry.Offset(size.width * .22f, size.height * .14f), androidx.compose.ui.geometry.Size(size.width * .2f, size.height * .72f)); drawRoundRect(color, androidx.compose.ui.geometry.Offset(size.width * .58f, size.height * .14f), androidx.compose.ui.geometry.Size(size.width * .2f, size.height * .72f)) }
            BrasaIcon.Replay, BrasaIcon.Forward -> {
                drawArc(color, if (icon == BrasaIcon.Replay) 55f else 125f, 245f, false, style = Stroke(stroke, cap = StrokeCap.Round))
                val x = if (icon == BrasaIcon.Replay) size.width * .13f else size.width * .87f
                val path = Path().apply { moveTo(x, size.height * .2f); lineTo(if (icon == BrasaIcon.Replay) size.width * .38f else size.width * .62f, size.height * .22f); lineTo(x, size.height * .43f); close() }
                drawPath(path, color)
            }
            BrasaIcon.Power -> { drawArc(color, -48f, 276f, false, style = Stroke(stroke, cap = StrokeCap.Round)); drawLine(color, center.copy(y = size.height * .08f), center.copy(y = size.height * .48f), stroke, StrokeCap.Round) }
            BrasaIcon.Info -> { drawCircle(color, size.minDimension * .42f, center, style = Stroke(stroke)); drawCircle(color, stroke * .55f, center.copy(y = size.height * .29f)); drawLine(color, center.copy(y = size.height * .45f), center.copy(y = size.height * .72f), stroke, StrokeCap.Round) }
            BrasaIcon.Mic -> { drawRoundRect(color, androidx.compose.ui.geometry.Offset(size.width * .34f, size.height * .08f), androidx.compose.ui.geometry.Size(size.width * .32f, size.height * .54f)); drawArc(color, 0f, 180f, false, androidx.compose.ui.geometry.Offset(size.width * .2f, size.height * .23f), androidx.compose.ui.geometry.Size(size.width * .6f, size.height * .5f), style = Stroke(stroke)); drawLine(color, center.copy(y = size.height * .72f), center.copy(y = size.height * .9f), stroke, StrokeCap.Round) }
            BrasaIcon.Back -> { drawLine(color, androidx.compose.ui.geometry.Offset(size.width * .78f, size.height * .18f), androidx.compose.ui.geometry.Offset(size.width * .27f, size.height * .5f), stroke, StrokeCap.Round); drawLine(color, androidx.compose.ui.geometry.Offset(size.width * .27f, size.height * .5f), androidx.compose.ui.geometry.Offset(size.width * .78f, size.height * .82f), stroke, StrokeCap.Round) }
            BrasaIcon.Add -> { drawLine(color, center.copy(x = size.width * .18f), center.copy(x = size.width * .82f), stroke, StrokeCap.Round); drawLine(color, center.copy(y = size.height * .18f), center.copy(y = size.height * .82f), stroke, StrokeCap.Round) }
            BrasaIcon.Check -> drawPath(Path().apply { moveTo(size.width * .12f, size.height * .52f); lineTo(size.width * .4f, size.height * .78f); lineTo(size.width * .88f, size.height * .22f) }, color, style = Stroke(stroke, cap = StrokeCap.Round))
            BrasaIcon.More -> repeat(3) { drawCircle(color, stroke * .7f, androidx.compose.ui.geometry.Offset(size.width * (.25f + it * .25f), center.y)) }
        }
    }
}

@Composable
fun BrasaLogo(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(28.dp)
                .background(
                    Brush.radialGradient(listOf(BrasaOrangeSoft, BrasaOrange, BrasaRed)),
                    RoundedCornerShape(topStart = 18.dp, topEnd = 6.dp, bottomEnd = 18.dp, bottomStart = 8.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text("B", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.width(9.dp))
        Text("BRasa", color = BrasaText, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
fun BrasaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: BrasaButtonStyle = BrasaButtonStyle.Secondary,
    leading: String? = null,
    leadingIcon: BrasaIcon? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.04f else 1f, tween(165), label = "buttonScale")
    val background by animateColorAsState(
        when {
            !enabled -> BrasaSurface.copy(alpha = .55f)
            style == BrasaButtonStyle.Primary -> if (focused) BrasaOrangeSoft else BrasaOrange
            focused -> Color.White
            style == BrasaButtonStyle.Ghost -> Color.Transparent
            else -> BrasaSurfaceElevated.copy(alpha = .94f)
        },
        label = "buttonColor",
    )
    val foreground = when {
        !enabled -> BrasaTextMuted.copy(alpha = .55f)
        focused && style != BrasaButtonStyle.Primary -> BrasaBackground
        else -> Color.White
    }
    val border = when {
        focused && style == BrasaButtonStyle.Primary -> Color.White.copy(alpha = .75f)
        focused -> Color.White
        style == BrasaButtonStyle.Ghost -> BrasaBorder.copy(alpha = .45f)
        else -> BrasaBorder
    }

    Row(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(if (focused) 8.dp else 0.dp, RoundedCornerShape(10.dp), ambientColor = BrasaOrange, spotColor = BrasaOrange)
            .background(background, RoundedCornerShape(10.dp))
            .border(if (focused) 2.dp else 1.dp, border, RoundedCornerShape(10.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = 46.dp)
            .padding(horizontal = 19.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            BrasaVectorIcon(leadingIcon, foreground)
            Spacer(Modifier.width(8.dp))
        } else if (leading != null) {
            Text(leading, color = foreground, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = foreground, fontSize = BrasaType.button, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
fun BrasaTopBar(
    modifier: Modifier = Modifier,
    active: String = "Início",
    onHome: (() -> Unit)? = null,
    onMovies: (() -> Unit)? = null,
    onSeries: (() -> Unit)? = null,
    onCollections: (() -> Unit)? = null,
    onMyList: (() -> Unit)? = null,
    onSearch: (() -> Unit)? = null,
    onProfiles: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
    profileInitials: String = "",
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(BrasaBackground.copy(alpha = .82f), RoundedCornerShape(13.dp))
            .border(1.dp, BrasaBorder.copy(alpha = .55f), RoundedCornerShape(13.dp))
            .padding(horizontal = 18.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            BrasaLogo()
            Spacer(Modifier.width(26.dp))
            if (onHome != null) NavItem("Início", active == "Início", onHome)
            if (onMovies != null) NavItem("Filmes", active == "Filmes", onMovies)
            if (onSeries != null) NavItem("Séries", active == "Séries", onSeries)
            if (onCollections != null) NavItem("Coleções", active == "Coleções", onCollections)
            if (onMyList != null) NavItem("Minha lista", active == "Minha lista", onMyList)
            if (onSearch != null) NavItem("Buscar", active == "Buscar", onSearch, BrasaIcon.Search)
            if (onSettings != null) NavItem("Configurações", active == "Configurações", onSettings, BrasaIcon.Settings)
            if (onProfiles != null) NavItem(profileInitials.ifBlank { "Perfil" }, active == "Perfis", onProfiles)
        }
    }
}

@Composable
private fun NavItem(text: String, active: Boolean, onClick: () -> Unit, icon: BrasaIcon? = null) {
    Column(Modifier.padding(horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        BrasaButton(text = text, onClick = onClick, style = BrasaButtonStyle.Ghost, leadingIcon = icon)
        Spacer(Modifier.height(2.dp))
        Box(Modifier.fillMaxWidth().height(3.dp).background(
            if (active) BrasaOrange else Color.Transparent, RoundedCornerShape(50)))
    }
}

@Composable
fun SectionHeading(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null, actionModifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = BrasaText, fontSize = BrasaType.section, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        if (action != null && onAction != null) BrasaButton(action, onAction, modifier = actionModifier, style = BrasaButtonStyle.Ghost)
    }
}

@Composable
fun MediaCard(
    item: CatalogItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    format: MediaCardFormat = if (item.type == "movie") MediaCardFormat.Poster else MediaCardFormat.Landscape,
    onFocused: () -> Unit = {},
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.04f else 1f, tween(165), label = "cardScale")
    val density = LocalCardDensity.current
    val poster = format != MediaCardFormat.Landscape
    val compact = format == MediaCardFormat.CompactPoster
    val width = (if (poster) 164.dp else 278.dp) * density
    val imageRatio = if (poster) 2f / 3f else 16f / 9f
    LaunchedEffect(focused, item.mediaKey) { if (focused) { delay(180); onFocused() } }
    Column(
        modifier
            .then(if (compact) Modifier.fillMaxWidth() else Modifier.width(width))
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(if (focused) 10.dp else 1.dp, RoundedCornerShape(11.dp), ambientColor = Color.Black, spotColor = BrasaOrange)
            .background(BrasaSurface, RoundedCornerShape(11.dp))
            .border(if (focused) 2.dp else 1.dp, if (focused) BrasaFocus else BrasaBorder, RoundedCornerShape(11.dp))
            .clip(RoundedCornerShape(11.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(imageRatio)
                .clip(RoundedCornerShape(11.dp))
                .background(Brush.linearGradient(listOf(BrasaSurfaceElevated, BrasaBackground)))
                .border(if (focused) 3.dp else 1.dp, if (focused) BrasaOrange else BrasaBorder, RoundedCornerShape(11.dp)),
        ) {
            AsyncImage(
                model = if (poster) item.poster.ifBlank { item.backdrop } else item.backdrop.ifBlank { item.poster },
                contentDescription = item.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, BrasaBackground.copy(alpha = .94f)))),
            )
            if (format == MediaCardFormat.Landscape) {
                Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
                    Text(item.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(metadata(item), color = BrasaTextMuted, fontSize = 14.sp, maxLines = 1)
                }
            }
            val badge = when {
                item.newEpisode -> "NOVO EPISÓDIO"
                item.isWatched() -> "ASSISTIDO"
                item.type == "series" && item.actionLabel.isNotBlank() -> item.actionLabel.uppercase()
                item.remainingMinutes != null -> "FALTAM ${item.remainingMinutes} MIN"
                else -> ""
            }
            if (badge.isNotBlank()) Text(badge, modifier = Modifier.align(Alignment.TopStart).padding(9.dp).background(BrasaBackground.copy(alpha = .88f), RoundedCornerShape(50)).padding(horizontal = 9.dp, vertical = 5.dp), color = if (item.newEpisode) BrasaOrange else BrasaText, fontSize = 11.sp, fontWeight = FontWeight.Black)
            if (item.favorite || item.inMyList) Row(
                modifier = Modifier.align(Alignment.TopEnd).padding(9.dp).background(BrasaOrange.copy(alpha = .92f), RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BrasaVectorIcon(BrasaIcon.Check, Color.Black, Modifier.size(12.dp))
                Spacer(Modifier.width(4.dp))
                Text("LISTA", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Black)
            }
            item.progress?.takeIf { it.percentage > 0 }?.let { progress ->
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(4.dp).background(Color.White.copy(alpha = .18f))) {
                    Box(
                        Modifier
                            .fillMaxWidth((progress.percentage / 100).toFloat().coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(Brush.horizontalGradient(listOf(BrasaOrange, BrasaRed))),
                    )
                }
            }
        }
        if (poster) {
            Column(Modifier.fillMaxWidth().heightIn(min = if (compact) 50.dp else 64.dp).padding(horizontal = if (compact) 7.dp else 11.dp, vertical = if (compact) 6.dp else 8.dp)) {
                Text(item.title, color = BrasaText, fontSize = if (compact) 14.sp else 17.sp, lineHeight = if (compact) 17.sp else 20.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(if (compact) 2.dp else 3.dp))
                Text(metadata(item), color = if (focused) BrasaText else BrasaTextMuted, fontSize = if (compact) 11.sp else 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

fun metadata(item: CatalogItem): String = buildList {
    item.year?.let { add(it.toString()) }
    item.rating?.let { add("★ ${String.format(Locale.ROOT, "%.1f", it)}") }
    if (item.duration.isNotBlank()) add(item.duration)
    if (item.contentRating.isNotBlank()) add(item.contentRating)
}.joinToString("  ·  ")

@Composable
fun GenreChip(text: String) {
    Text(
        text,
        modifier = Modifier
            .background(BrasaSurfaceElevated.copy(alpha = .82f), RoundedCornerShape(50))
            .border(1.dp, BrasaBorder, RoundedCornerShape(50))
            .padding(horizontal = 13.dp, vertical = 7.dp),
        color = BrasaText,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
fun BrasaTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    var focused by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    DisposableEffect(Unit) { onDispose { keyboard?.hide() } }
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        readOnly = !editing,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { editing = false; keyboard?.hide() }),
        visualTransformation = visualTransformation,
        textStyle = androidx.compose.ui.text.TextStyle(color = BrasaText, fontSize = BrasaType.body, fontWeight = FontWeight.Medium),
        modifier = modifier
            .background(BrasaSurfaceElevated, RoundedCornerShape(10.dp))
            .border(if (focused) 2.dp else 1.dp, if (focused) BrasaFocus else BrasaBorder, RoundedCornerShape(10.dp))
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                when {
                    event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_DOWN || event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_UP -> {
                        editing = false
                        keyboard?.hide()
                        focusManager.moveFocus(if (event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) FocusDirection.Down else FocusDirection.Up)
                        true
                    }
                    editing && event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BACK -> {
                        editing = false
                        keyboard?.hide()
                        true
                    }
                    !editing && event.nativeKeyEvent.keyCode in setOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER) -> {
                        editing = true
                        scope.launch { delay(80); keyboard?.show() }
                        true
                    }
                    else -> false
                }
            }
            .onFocusChanged {
                focused = it.isFocused
                if (!it.isFocused) {
                    editing = false
                    keyboard?.hide()
                }
            }
            .padding(horizontal = 16.dp, vertical = 11.dp),
        decorationBox = { inner ->
            if (value.isEmpty()) Text(placeholder, color = BrasaTextMuted, fontSize = BrasaType.body)
            inner()
        },
    )
}

@Composable
fun AmbientBackground(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF152033), BrasaBackground, BrasaBackground),
                    radius = 1100f,
                ),
            ),
    ) { content() }
}

@Composable
fun MessagePanel(title: String, message: String, action: String? = null, onAction: () -> Unit = {}) {
    AmbientBackground {
        Column(
            Modifier.fillMaxSize().padding(horizontal = BrasaSpacing.safe),
            verticalArrangement = Arrangement.Center,
        ) {
            BrasaLogo()
            Spacer(Modifier.height(24.dp))
            Text(title, color = BrasaText, fontSize = BrasaType.page, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text(message, modifier = Modifier.widthIn(max = 700.dp), color = BrasaTextMuted, fontSize = BrasaType.body)
            if (action != null) {
                Spacer(Modifier.height(26.dp))
                BrasaButton(action, onAction, style = BrasaButtonStyle.Primary)
            }
        }
    }
}
