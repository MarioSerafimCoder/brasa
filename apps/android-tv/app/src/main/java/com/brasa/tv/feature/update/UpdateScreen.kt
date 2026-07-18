package com.brasa.tv.feature.update
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.brasa.tv.designsystem.*
import java.text.NumberFormat
@Composable fun UpdateScreen(state:UpdateUiState,onCheck:()->Unit,onDownload:(UpdateManifest)->Unit,onCancel:()->Unit,onInstall:()->Unit,onPermission:()->Unit,onBack:()->Unit){BackHandler{if(state is UpdateUiState.Downloading||state is UpdateUiState.Validating)onCancel()else onBack()};val focus=remember{FocusRequester()};LaunchedEffect(state){if(state!==UpdateUiState.Checking&&state !is UpdateUiState.Validating&&state !is UpdateUiState.Installing)runCatching{focus.requestFocus()}};Column(Modifier.fillMaxSize().padding(64.dp),verticalArrangement=Arrangement.Center){BrasaLogo();Spacer(Modifier.height(20.dp));when(state){
UpdateUiState.Idle,UpdateUiState.Checking->{Text("Verificando atualização…",fontSize=36.sp);if(state===UpdateUiState.Idle)BrasaButton("Verificar agora",onCheck,Modifier.focusRequester(focus))}
UpdateUiState.UpToDate->{Text("BRasa TV está atualizado",fontSize=38.sp);Spacer(Modifier.height(20.dp));BrasaButton("Voltar",onBack,Modifier.focusRequester(focus))}
is UpdateUiState.Available->{Text("Nova versão do BRasa disponível",fontSize=48.sp,color=Color.White);Text("Versão ${state.update.versionName} · ${formatBytes(state.update.sizeBytes)}",fontSize=23.sp,color=Color.LightGray);Spacer(Modifier.height(20.dp));Text("Novidades:",fontSize=24.sp,color=Color.White);state.update.releaseNotes.forEach{Text("• $it",fontSize=20.sp,color=Color.White)};Spacer(Modifier.height(24.dp));Row(horizontalArrangement=Arrangement.spacedBy(14.dp)){BrasaButton("Atualizar agora",{onDownload(state.update)},Modifier.focusRequester(focus),style=BrasaButtonStyle.Primary);BrasaButton("Lembrar depois",onBack)}}
is UpdateUiState.Downloading->{Text("Baixando atualização",fontSize=40.sp);Text("${state.percent}%",fontSize=56.sp,color=BrasaOrange);Spacer(Modifier.height(12.dp));Box(Modifier.width(620.dp).height(8.dp).background(Color.White.copy(alpha=.2f))){Box(Modifier.fillMaxWidth(state.percent/100f).fillMaxHeight().background(BrasaOrange))};Spacer(Modifier.height(20.dp));BrasaButton("Cancelar",onCancel,Modifier.focusRequester(focus))}
is UpdateUiState.Validating->{Text("Validando atualização…",fontSize=40.sp);Text("Verificando hash, pacote, versão e assinatura.",fontSize=20.sp,color=Color.LightGray)}
is UpdateUiState.Ready->{Text("Atualização pronta",fontSize=40.sp);Text("O Android solicitará sua confirmação para instalar.",fontSize=20.sp,color=Color.LightGray);Spacer(Modifier.height(24.dp));Row(horizontalArrangement=Arrangement.spacedBy(14.dp)){BrasaButton("Continuar",onInstall,Modifier.focusRequester(focus),style=BrasaButtonStyle.Primary);BrasaButton("Lembrar depois",onBack)}}
is UpdateUiState.PermissionRequired->{Text("Permissão necessária",fontSize=40.sp);Text("Para atualizar o BRasa diretamente pelo computador, permita que o BRasa instale esta atualização.",fontSize=20.sp);Text("O Android sempre pedirá sua confirmação antes de concluir.",fontSize=20.sp,color=Color.LightGray);Spacer(Modifier.height(24.dp));Row(horizontalArrangement=Arrangement.spacedBy(14.dp)){BrasaButton("Abrir configurações",onPermission,Modifier.focusRequester(focus));BrasaButton("Cancelar",onBack)}}
is UpdateUiState.Installing->{Text("Aguardando confirmação do Android",fontSize=40.sp);Text("Confirme ou cancele a instalação na tela do sistema.",fontSize=20.sp,color=Color.LightGray)}
is UpdateUiState.Installed->{Text("BRasa atualizado para ${state.versionName}",fontSize=40.sp);Spacer(Modifier.height(24.dp));BrasaButton("Continuar",onBack,Modifier.focusRequester(focus))}
is UpdateUiState.Error->{Text("Não foi possível atualizar",fontSize=40.sp);Text(state.message,fontSize=20.sp,color=BrasaRed);Spacer(Modifier.height(24.dp));Row(horizontalArrangement=Arrangement.spacedBy(14.dp)){BrasaButton("Tentar novamente",{state.update?.let(onDownload)?:onCheck()},Modifier.focusRequester(focus));BrasaButton("Voltar",onBack)}}
}}}
private fun formatBytes(bytes:Long)=NumberFormat.getNumberInstance(java.util.Locale.forLanguageTag("pt-BR")).apply{maximumFractionDigits=1}.format(bytes/1048576.0)+" MB"
