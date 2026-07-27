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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import com.brasa.tv.app.BrasaUiState
import com.brasa.tv.core.model.Profile
import com.brasa.tv.designsystem.AmbientBackground
import com.brasa.tv.designsystem.BrasaBorder
import com.brasa.tv.designsystem.BrasaButton
import com.brasa.tv.designsystem.BrasaButtonStyle
import com.brasa.tv.designsystem.BrasaFocus
import com.brasa.tv.designsystem.BrasaLogo
import com.brasa.tv.designsystem.BrasaOrange
import com.brasa.tv.designsystem.BrasaRed
import com.brasa.tv.designsystem.BrasaSpacing
import com.brasa.tv.designsystem.BrasaSurfaceElevated
import com.brasa.tv.designsystem.BrasaText
import com.brasa.tv.designsystem.BrasaTextMuted
import com.brasa.tv.designsystem.BrasaType

@Composable
fun ProfileScreen(state: BrasaUiState, onLoad: () -> Unit, onSelect: (Profile) -> Unit, onExit: () -> Unit) {
    val firstFocus = remember { FocusRequester() }
    var confirmExit by remember { mutableStateOf(false) }
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
            Row(horizontalArrangement = Arrangement.spacedBy(BrasaSpacing.x4)) {
                state.profiles.forEachIndexed { index, profile ->
                    ProfileAvatar(profile, { onSelect(profile) }, if (index == 0) Modifier.focusRequester(firstFocus) else Modifier)
                }
            }
            if (state.loading) { Spacer(Modifier.height(BrasaSpacing.x3)); Text("Carregando perfis…", color = BrasaTextMuted, fontSize = BrasaType.metadata) }
            Spacer(Modifier.height(BrasaSpacing.x4))
            BrasaButton("Encerrar aplicativo", { confirmExit = true }, style = BrasaButtonStyle.Ghost, leading = "⏻")
        }
    }
    if (confirmExit) ExitConfirmationDialog(onDismiss = { confirmExit = false }, onConfirm = onExit)
}

@Composable
private fun ExitConfirmationDialog(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val cancelFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { cancelFocus.requestFocus() } }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .74f)), contentAlignment = Alignment.Center) {
            Column(
                Modifier.width(520.dp).background(BrasaSurfaceElevated, RoundedCornerShape(22.dp)).border(1.dp, BrasaBorder, RoundedCornerShape(22.dp)).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Encerrar o BRasa?", color = Color.White, fontSize = BrasaType.section, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(BrasaSpacing.x2))
                Text("A reprodução será encerrada e o aplicativo será fechado.", color = BrasaTextMuted, fontSize = BrasaType.body)
                Spacer(Modifier.height(BrasaSpacing.x4))
                Row(horizontalArrangement = Arrangement.spacedBy(BrasaSpacing.x2)) {
                    BrasaButton("Cancelar", onDismiss, Modifier.focusRequester(cancelFocus))
                    BrasaButton("Encerrar", onConfirm, style = BrasaButtonStyle.Primary)
                }
            }
        }
    }
}

@Composable
private fun ProfileAvatar(profile: Profile, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, tween(165), label = "profileScale")
    val accent = if (profile.kind == "kids") Color(0xFF36B8FF) else BrasaOrange
    Column(modifier.graphicsLayer { scaleX = scale; scaleY = scale }.onFocusChanged { focused = it.isFocused }.clickable(role = Role.Button, onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(112.dp).shadow(if (focused) 11.dp else 2.dp, CircleShape, spotColor = accent).background(Brush.linearGradient(listOf(accent, if (profile.kind == "kids") Color(0xFF654DFF) else BrasaRed)), CircleShape).border(if (focused) 3.dp else 1.dp, if (focused) BrasaFocus else BrasaBorder, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(profile.initials.ifBlank { profile.name.take(1).uppercase() }, color = Color.White, fontSize = BrasaType.page, fontWeight = FontWeight.Black)
            if (profile.hasPin) Text("PIN", modifier = Modifier.align(Alignment.BottomEnd).background(Color.Black.copy(alpha = .82f), CircleShape).padding(7.dp), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(BrasaSpacing.x2))
        Text(profile.name, color = if (focused) BrasaText else BrasaTextMuted, fontSize = BrasaType.body, fontWeight = FontWeight.Bold)
        if (profile.kind == "kids") Text("KIDS", color = accent, fontSize = BrasaType.metadata, fontWeight = FontWeight.ExtraBold)
    }
}
