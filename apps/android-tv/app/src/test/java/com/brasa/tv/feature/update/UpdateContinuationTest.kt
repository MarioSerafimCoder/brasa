package com.brasa.tv.feature.update

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.brasa.tv.designsystem.BrasaTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1280dp-h720dp-land-mdpi", application = Application::class)
class UpdateContinuationTest {
    @get:Rule val compose = createComposeRule()
    @Test fun failedCheckOffersWorkingExit() {
        var continued = false
        val state = UpdateUiState.Error("Falha temporária")
        compose.setContent { BrasaTheme { UpdateScreen(state, {}, {}, {}, {}, {}, {continued=true}, state.requiresInstallation()) } }
        compose.onNodeWithText("Voltar").performClick()
        compose.runOnIdle { assertTrue(continued) }
    }
    @Test fun optionalUpdateOffersDefer() {
        val state = UpdateUiState.Available(UpdateManifest(versionName="1.0.29"))
        compose.setContent { BrasaTheme { UpdateScreen(state, {}, {}, {}, {}, {}, {}, state.requiresInstallation()) } }
        compose.onNodeWithText("Lembrar depois").assertExists()
    }
    @Test fun explicitlyMandatoryUpdateKeepsItsGate() {
        val state = UpdateUiState.Available(UpdateManifest(versionName="1.0.29", mandatory=true))
        compose.setContent { BrasaTheme { UpdateScreen(state, {}, {}, {}, {}, {}, {}, state.requiresInstallation()) } }
        compose.onNodeWithText("Lembrar depois").assertDoesNotExist()
        compose.onNodeWithText("Atualizar agora").assertExists()
    }
}
