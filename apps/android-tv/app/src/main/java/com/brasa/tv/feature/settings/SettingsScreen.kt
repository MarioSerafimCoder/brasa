package com.brasa.tv.feature.settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.brasa.tv.BuildConfig
import com.brasa.tv.app.BrasaUiState
import com.brasa.tv.designsystem.*
import java.text.DateFormat
import java.util.Date
@Composable fun SettingsScreen(state:BrasaUiState,lastUpdateCheckAt:Long,onUpdates:()->Unit,onProfiles:()->Unit,onForget:()->Unit,onBack:()->Unit){BackHandler(onBack=onBack);Column(Modifier.fillMaxSize().padding(64.dp),verticalArrangement=Arrangement.Center){BrasaLogo();Text("Configurações",fontSize=42.sp);Spacer(Modifier.height(18.dp));Text("Servidor: ${state.server?.name?:"BRasa"}",fontSize=20.sp);Text("API: ${state.server?.apiVersion?:1}",fontSize=20.sp);Text("Perfil: ${state.profile?.name?:"Nenhum"}",fontSize=20.sp);Text("Dispositivo autorizado: ${if(state.paired)"Sim" else "Não"}",fontSize=20.sp);Spacer(Modifier.height(18.dp));Text("Versão instalada: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",fontSize=20.sp);Text("Última verificação: ${if(lastUpdateCheckAt>0)DateFormat.getDateTimeInstance().format(Date(lastUpdateCheckAt))else"Nunca"}",fontSize=18.sp);Spacer(Modifier.height(24.dp));Row(horizontalArrangement=Arrangement.spacedBy(14.dp)){BrasaButton("Verificar atualização",onUpdates);BrasaButton("Trocar perfil",onProfiles);BrasaButton("Esquecer servidor",onForget);BrasaButton("Voltar",onBack)}}}
