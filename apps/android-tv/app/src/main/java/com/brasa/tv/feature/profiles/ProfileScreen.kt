package com.brasa.tv.feature.profiles

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.brasa.tv.app.BrasaUiState
import com.brasa.tv.core.model.Profile
import com.brasa.tv.designsystem.*

@Composable fun ProfileScreen(state:BrasaUiState,onLoad:()->Unit,onSelect:(Profile)->Unit){LaunchedEffect(Unit){onLoad()};Column(Modifier.fillMaxSize().padding(64.dp),verticalArrangement=Arrangement.Center){BrasaLogo();Text("Quem está assistindo?",fontSize=44.sp);Spacer(Modifier.height(28.dp));Row(horizontalArrangement=Arrangement.spacedBy(20.dp)){state.profiles.forEach{profile->BrasaButton("${profile.initials}  ${profile.name}",{onSelect(profile)},Modifier.width(220.dp).height(90.dp))}};if(state.loading)Text("Carregando perfis…")}}
