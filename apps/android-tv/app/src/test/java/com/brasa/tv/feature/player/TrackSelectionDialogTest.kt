@file:androidx.media3.common.util.UnstableApi
package com.brasa.tv.feature.player

import android.app.Application
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.media3.common.*
import com.brasa.tv.data.storage.AppSettings
import com.brasa.tv.designsystem.BrasaTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w1280dp-h720dp-land-mdpi", application = Application::class)
class TrackSelectionDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test fun menuExposesIndividualTracksOffAndAppearanceControls() {
        val group = TrackGroup(Format.Builder().setSampleMimeType(MimeTypes.TEXT_VTT).setLanguage("pt").build())
        val track = PlaybackTrack(group, 0, "Português — Completa", "pt", false, true)
        var selected: PlaybackTrack? = track
        var size = 1f
        var style = "outline"
        var dismissed = false
        compose.setContent {
            BrasaTheme {
                TrackSelectionDialog(C.TRACK_TYPE_TEXT, listOf(track), true, AppSettings(),
                    onSelect = { selected = it }, onAutomaticAudio = {}, onSize = { size = it },
                    onStyle = { style = it }, onDismiss = { dismissed = true })
            }
        }
        compose.onNodeWithText("✓ Sem legenda").assertIsFocused().performClick()
        compose.runOnIdle { assertNull(selected) }
        compose.onNodeWithText("1. Português — Completa").performClick()
        compose.runOnIdle { assertEquals(track, selected) }
        compose.onNodeWithText("Tamanho: 100%").performClick()
        compose.onNodeWithText("Contorno").performClick()
        compose.onNodeWithText("Concluir").performClick()
        compose.runOnIdle { assertEquals(1.2f, size); assertEquals("background", style); assertTrue(dismissed) }
    }
}
