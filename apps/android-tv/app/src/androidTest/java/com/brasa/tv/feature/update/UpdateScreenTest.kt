package com.brasa.tv.feature.update
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.brasa.tv.designsystem.BrasaTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
class UpdateScreenTest{@get:Rule val compose=createComposeRule()
@Test fun availableUpdateStartsOnUpdateAndAllowsLater(){var deferred=false;compose.setContent{BrasaTheme{UpdateScreen(UpdateUiState.Available(UpdateManifest(versionName="1.0.1",sizeBytes=13_000_000,releaseNotes=listOf("Melhorias"))),{},{},{},{},{},{deferred=true})}};compose.onNodeWithText("Atualizar agora").assertIsFocused();compose.onNodeWithText("Depois").performClick();compose.runOnIdle{assertTrue(deferred)}}
@Test fun downloadShowsProgressAndCancel(){var cancelled=false;compose.setContent{BrasaTheme{UpdateScreen(UpdateUiState.Downloading(UpdateManifest(),47),{},{},{cancelled=true},{},{},{})}};compose.onNodeWithText("47%").assertExists();compose.onNodeWithText("Cancelar").performClick();compose.runOnIdle{assertTrue(cancelled)}}
@Test fun permissionExplanationRequiresExplicitAction(){var opened=false;compose.setContent{BrasaTheme{UpdateScreen(UpdateUiState.PermissionRequired(UpdateManifest(),"apk"),{},{},{},{},{opened=true},{})}};compose.onNodeWithText("Abrir configurações").assertIsFocused().performClick();compose.runOnIdle{assertTrue(opened)}}}
