package com.brasa.tv.feature.profiles

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.brasa.tv.app.BrasaUiState
import com.brasa.tv.core.model.Profile
import com.brasa.tv.designsystem.AmbientBackground
import com.brasa.tv.designsystem.BrasaBorder
import com.brasa.tv.designsystem.BrasaFocus
import com.brasa.tv.designsystem.BrasaLogo
import com.brasa.tv.designsystem.BrasaOrange
import com.brasa.tv.designsystem.BrasaRed
import com.brasa.tv.designsystem.BrasaSpacing
import com.brasa.tv.designsystem.BrasaText
import com.brasa.tv.designsystem.BrasaTextMuted
import com.brasa.tv.designsystem.BrasaType

@Composable
fun ProfileScreen(state: BrasaUiState, onLoad: () -> Unit, onSelect: (Profile) -> Unit) {
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { onLoad() }
    LaunchedEffect(state.profiles) { if (state.profiles.isNotEmpty()) runCatching { firstFocus.requestFocus() } }
    AmbientBackground {
        Column(Modifier.fillMaxSize().padding(horizontal = BrasaSpacing.safe), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            BrasaLogo()
            Spacer(Modifier.height(BrasaSpacing.x4))
            Text("Quem está assistindo?", color = Color.White, fontSize = BrasaType.page, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(BrasaSpacing.x1))
            Text("Escolha um perfil para personalizar sua biblioteca.", color = BrasaTextMuted, fontSize = BrasaType.body)
            Spacer(Modifier.height(BrasaSpacing.x6))
            Row(horizontalArrangement = Arrangement.spacedBy(BrasaSpacing.x6)) {
                state.profiles.forEachIndexed { index, profile ->
                    ProfileAvatar(profile, { onSelect(profile) }, if (index == 0) Modifier.focusRequester(firstFocus) else Modifier)
                }
            }
            if (state.loading) { Spacer(Modifier.height(BrasaSpacing.x3)); Text("Carregando perfis…", color = BrasaTextMuted, fontSize = BrasaType.metadata) }
        }
    }
}

@Composable
private fun ProfileAvatar(profile: Profile, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, tween(165), label = "profileScale")
    val accent = if (profile.kind == "kids") Color(0xFF36B8FF) else BrasaOrange
    Column(modifier.graphicsLayer { scaleX = scale; scaleY = scale }.onFocusChanged { focused = it.isFocused }.clickable(role = Role.Button, onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(142.dp).shadow(if (focused) 14.dp else 2.dp, CircleShape, spotColor = accent).background(Brush.linearGradient(listOf(accent, if (profile.kind == "kids") Color(0xFF654DFF) else BrasaRed)), CircleShape).border(if (focused) 4.dp else 1.dp, if (focused) BrasaFocus else BrasaBorder, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(profile.initials.ifBlank { profile.name.take(1).uppercase() }, color = Color.White, fontSize = BrasaType.page, fontWeight = FontWeight.Black)
            if (profile.hasPin) Text("PIN", modifier = Modifier.align(Alignment.BottomEnd).background(Color.Black.copy(alpha = .82f), CircleShape).padding(9.dp), color = Color.White, fontSize = BrasaType.metadata, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(BrasaSpacing.x2))
        Text(profile.name, color = if (focused) BrasaText else BrasaTextMuted, fontSize = BrasaType.body, fontWeight = FontWeight.Bold)
        if (profile.kind == "kids") Text("KIDS", color = accent, fontSize = BrasaType.metadata, fontWeight = FontWeight.ExtraBold)
    }
}
