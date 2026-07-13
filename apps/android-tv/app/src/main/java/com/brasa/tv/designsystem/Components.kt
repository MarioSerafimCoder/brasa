package com.brasa.tv.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.brasa.tv.core.model.CatalogItem
import java.util.Locale
import kotlinx.coroutines.delay

enum class BrasaButtonStyle { Primary, Secondary, Ghost }
enum class MediaCardFormat { Landscape, Poster }

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
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.04f else 1f, spring(stiffness = 700f), label = "buttonScale")
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
        focused -> BrasaOrange
        style == BrasaButtonStyle.Ghost -> BrasaBorder.copy(alpha = .45f)
        else -> BrasaBorder
    }

    Row(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(if (focused) 8.dp else 0.dp, RoundedCornerShape(9.dp), ambientColor = BrasaOrange, spotColor = BrasaOrange)
            .background(background, RoundedCornerShape(9.dp))
            .border(if (focused) 2.dp else 1.dp, border, RoundedCornerShape(9.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = 46.dp)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Text(leading, color = foreground, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(9.dp))
        }
        Text(text, color = foreground, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
fun BrasaTopBar(
    modifier: Modifier = Modifier,
    active: String = "Início",
    onHome: (() -> Unit)? = null,
    onSearch: (() -> Unit)? = null,
    onProfiles: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(BrasaBackground.copy(alpha = .88f), RoundedCornerShape(13.dp))
            .border(1.dp, BrasaBorder.copy(alpha = .7f), RoundedCornerShape(13.dp))
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BrasaLogo()
        Spacer(Modifier.width(32.dp))
        if (onHome != null) NavItem("Início", active == "Início", onHome)
        Spacer(Modifier.weight(1f))
        if (onSearch != null) NavItem("⌕  Buscar", active == "Buscar", onSearch)
        if (onProfiles != null) NavItem("Perfis", active == "Perfis", onProfiles)
        if (onSettings != null) NavItem("Configurações", active == "Configurações", onSettings)
    }
}

@Composable
private fun NavItem(text: String, active: Boolean, onClick: () -> Unit) {
    BrasaButton(
        text = text,
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 4.dp),
        style = if (active) BrasaButtonStyle.Secondary else BrasaButtonStyle.Ghost,
    )
}

@Composable
fun SectionHeading(title: String, modifier: Modifier = Modifier, action: String? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = BrasaText, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        if (action != null) Text(action, color = BrasaTextMuted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
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
    val scale by animateFloatAsState(if (focused) 1.04f else 1f, spring(stiffness = 650f), label = "cardScale")
    val width = if (format == MediaCardFormat.Poster) 164.dp else 278.dp
    val imageRatio = if (format == MediaCardFormat.Poster) 2f / 3f else 16f / 9f
    LaunchedEffect(focused, item.mediaKey) { if (focused) { delay(180); onFocused() } }
    Column(
        modifier
            .width(width)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(if (focused) 10.dp else 1.dp, RoundedCornerShape(11.dp), ambientColor = Color.Black, spotColor = BrasaOrange)
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
                model = if (format == MediaCardFormat.Poster) item.poster.ifBlank { item.backdrop } else item.backdrop.ifBlank { item.poster },
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
                    Text(item.title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(metadata(item), color = BrasaTextMuted, fontSize = 13.sp, maxLines = 1)
                }
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
        if (format == MediaCardFormat.Poster) {
            Spacer(Modifier.height(9.dp))
            Text(item.title, color = BrasaText, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(metadata(item), color = if (focused) BrasaText else BrasaTextMuted, fontSize = 13.sp, maxLines = 1)
        }
    }
}

fun metadata(item: CatalogItem): String = buildList {
    item.year?.let { add(it.toString()) }
    item.rating?.let { add("★ ${String.format(Locale.ROOT, "%.1f", it)}") }
    if (item.duration.isNotBlank()) add(item.duration)
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
        fontSize = 13.sp,
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
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        visualTransformation = visualTransformation,
        textStyle = androidx.compose.ui.text.TextStyle(color = BrasaText, fontSize = 20.sp, fontWeight = FontWeight.Medium),
        modifier = modifier
            .background(BrasaSurfaceElevated, RoundedCornerShape(9.dp))
            .border(if (focused) 2.dp else 1.dp, if (focused) BrasaOrange else BrasaBorder, RoundedCornerShape(9.dp))
            .onFocusChanged { focused = it.isFocused }
            .padding(horizontal = 18.dp, vertical = 14.dp),
        decorationBox = { inner ->
            if (value.isEmpty()) Text(placeholder, color = BrasaTextMuted, fontSize = 20.sp)
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
            Modifier.fillMaxSize().padding(horizontal = 72.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            BrasaLogo()
            Spacer(Modifier.height(30.dp))
            Text(title, color = BrasaText, fontSize = 40.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text(message, modifier = Modifier.widthIn(max = 700.dp), color = BrasaTextMuted, fontSize = 19.sp)
            if (action != null) {
                Spacer(Modifier.height(26.dp))
                BrasaButton(action, onAction, style = BrasaButtonStyle.Primary)
            }
        }
    }
}
