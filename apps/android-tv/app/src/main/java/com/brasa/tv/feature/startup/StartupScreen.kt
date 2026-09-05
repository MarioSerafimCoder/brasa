package com.brasa.tv.feature.startup

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import com.brasa.tv.designsystem.AmbientBackground
import com.brasa.tv.designsystem.BrasaLogo
import com.brasa.tv.designsystem.BrasaOrange
import com.brasa.tv.designsystem.BrasaRed
import com.brasa.tv.designsystem.BrasaTextMuted

@Composable
fun StartupScreen(message: String = "Conectando à sua biblioteca…") {
    val transition = rememberInfiniteTransition(label = "startup")
    val glow by transition.animateFloat(
        initialValue = .28f,
        targetValue = .72f,
        animationSpec = infiniteRepeatable(tween(1_150), RepeatMode.Reverse),
        label = "startupGlow",
    )

    AmbientBackground {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(300.dp)
                    .alpha(glow)
                    .background(
                        Brush.radialGradient(
                            listOf(BrasaOrange.copy(alpha = .22f), BrasaRed.copy(alpha = .06f), androidx.compose.ui.graphics.Color.Transparent),
                        ),
                        CircleShape,
                    ),
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                BrasaLogo()
                Spacer(Modifier.height(22.dp))
                Text(message, color = BrasaTextMuted, fontSize = 17.sp)
            }
        }
    }
}
