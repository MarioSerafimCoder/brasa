@file:androidx.media3.common.util.UnstableApi
package com.brasa.tv.feature.player

import android.app.Application
import androidx.media3.common.*
import com.brasa.tv.data.storage.AppSettings
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class TrackSelectionTest {
    @Test fun canTurnSubtitlesOffAndOnRepeatedly() {
        val group = TrackGroup(Format.Builder().setSampleMimeType(MimeTypes.TEXT_VTT).setLanguage("pt-BR").build())
        val track = PlaybackTrack(group, 0, "Português", "pt-BR", false, true)
        var parameters = TrackSelectionParameters.Builder().build()
        repeat(3) {
            parameters = selectPlaybackTrack(parameters, C.TRACK_TYPE_TEXT, track)
            assertFalse(parameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT))
            assertEquals(listOf(0), parameters.overrides[group]?.trackIndices)
            parameters = selectPlaybackTrack(parameters, C.TRACK_TYPE_TEXT, null)
            assertTrue(parameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT))
            assertFalse(parameters.overrides.containsKey(group))
        }
    }
    @Test fun twoTracksWithSameLanguageRemainIndividuallySelectable() {
        val dub = TrackGroup("dub", Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).setLanguage("pt").setLabel("Dublagem").build())
        val commentary = TrackGroup("commentary", Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).setLanguage("pt").setLabel("Comentário").build())
        val tracks = Tracks(listOf(dub, commentary).map { Tracks.Group(it, false, intArrayOf(C.FORMAT_HANDLED), booleanArrayOf(false)) })
        val options = playbackTracks(tracks, C.TRACK_TYPE_AUDIO)
        assertEquals(2, options.size)
        assertNotEquals(options[0].label, options[1].label)
        var parameters = selectPlaybackTrack(TrackSelectionParameters.Builder().build(), C.TRACK_TYPE_AUDIO, options[0])
        parameters = selectPlaybackTrack(parameters, C.TRACK_TYPE_AUDIO, options[1])
        assertEquals(setOf(commentary), parameters.overrides.keys)
    }
    @Test fun anotherProfileDoesNotInheritDisabledSubtitles() {
        val original = TrackSelectionParameters.Builder().build()
        val disabled = applyPlaybackPreferences(original, AppSettings(subtitleMode = "off"))
        assertTrue(disabled.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT))
        val normal = applyPlaybackPreferences(disabled, AppSettings(audioLanguage = "pt-BR", subtitleLanguage = "en"))
        assertFalse(normal.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT))
        assertEquals("pt-br", normal.preferredAudioLanguages.first())
        assertEquals("en", normal.preferredTextLanguages.first())
    }
    @Test fun newProfileClearsPreviousExplicitTrackOverride() {
        val group = TrackGroup(Format.Builder().setSampleMimeType(MimeTypes.TEXT_VTT).setLanguage("pt").build())
        val selected = selectPlaybackTrack(TrackSelectionParameters.Builder().build(), C.TRACK_TYPE_TEXT,
            PlaybackTrack(group, 0, "Português", "pt", true, true))
        val reset = applyPlaybackPreferences(selected, AppSettings(subtitleLanguage = "en"))
        assertTrue(reset.overrides.isEmpty())
        assertEquals("en", reset.preferredTextLanguages.first())
    }
}
