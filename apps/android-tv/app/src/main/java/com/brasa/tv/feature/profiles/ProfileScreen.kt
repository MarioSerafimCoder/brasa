package com.brasa.tv.feature.profiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.brasa.tv.app.BrasaUiState
import com.brasa.tv.core.model.Profile
import com.brasa.tv.designsystem.AmbientBackground
import com.brasa.tv.designsystem.BrasaButton
import com.brasa.tv.designsystem.BrasaButtonStyle
import com.brasa.tv.designsystem.BrasaLogo
import com.brasa.tv.designsystem.BrasaTextMuted

@Composable
fun ProfileScreen(state: BrasaUiState, onLoad: () -> Unit, onSelect: (Profile) -> Unit) {
    LaunchedEffect(Unit) { onLoad() }
    AmbientBackground {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            BrasaLogo()
            Spacer(Modifier.height(28.dp))
            Text("Quem está assistindo?", color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(8.dp))
            Text("Escolha um perfil para personalizar sua biblioteca.", color = BrasaTextMuted, fontSize = 17.sp)
            Spacer(Modifier.height(30.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                state.profiles.forEachIndexed { index, profile ->
                    BrasaButton(
                        profile.name,
                        { onSelect(profile) },
                        Modifier.width(210.dp).height(76.dp),
                        style = if (index == 0) BrasaButtonStyle.Primary else BrasaButtonStyle.Secondary,
                        leading = profile.initials.ifBlank { profile.name.take(1).uppercase() },
                    )
                }
            }
            if (state.loading) {
                Spacer(Modifier.height(20.dp))
                Text("Carregando perfis…", color = BrasaTextMuted, fontSize = 16.sp)
            }
        }
    }
}
