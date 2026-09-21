package com.brasa.tv.feature.server

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Text
import com.brasa.tv.core.network.ConnectionProblem
import com.brasa.tv.designsystem.*

@Composable
fun ConnectionRecoveryScreen(problem: ConnectionProblem, onRetry: () -> Unit, onServer: () -> Unit, onPair: () -> Unit) {
    val focus = remember { FocusRequester() }
    AmbientBackground {
        Column(Modifier.fillMaxSize().padding(BrasaSpacing.safe), verticalArrangement = Arrangement.Center) {
            BrasaLogo()
            Spacer(Modifier.height(24.dp))
            Text(problem.title, color = BrasaText, fontSize = BrasaType.page)
            Spacer(Modifier.height(12.dp))
            Text(problem.explanation, Modifier.widthIn(max = 760.dp), color = BrasaTextMuted, fontSize = BrasaType.body)
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BrasaButton(if (problem == ConnectionProblem.REVOKED) "Parear novamente" else "Tentar novamente",
                    if (problem == ConnectionProblem.REVOKED) onPair else onRetry,
                    Modifier.focusRequester(focus), style = BrasaButtonStyle.Primary)
                BrasaButton("Endereço do computador", onServer)
            }
        }
    }
    LaunchedEffect(problem) { focus.requestFocus() }
}
