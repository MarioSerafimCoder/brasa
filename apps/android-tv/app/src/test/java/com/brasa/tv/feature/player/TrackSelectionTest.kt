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
    @Test fun translatedAndUnaccentedLanguageLabelsAreNotRepeated() {
        listOf("en" to "English", "pt-BR" to "Portugues (Brasil)").forEach { (language, label) ->
            val group = TrackGroup(Format.Builder().setSampleMimeType(MimeTypes.TEXT_VTT).setLanguage(language).setLabel(label).build())
            val tracks = Tracks(listOf(Tracks.Group(group, false, intArrayOf(C.FORMAT_HANDLED), booleanArrayOf(false))))
            assertEquals(if (language == "en") "Inglês" else "Português (Brasil)", playbackTracks(tracks, C.TRACK_TYPE_TEXT).single().label)
        }
    }
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
    @Test fun trackLabelsPreferReadableLanguageAndChannelNames() {
        val audio = TrackGroup(Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).setLanguage("pt-BR").setLabel("Audio Track 1").setChannelCount(6).build())
        val subtitle = TrackGroup(Format.Builder().setSampleMimeType(MimeTypes.TEXT_VTT).setLanguage("en").setLabel("Subtitle 2").build())
        val audioTracks = Tracks(listOf(Tracks.Group(audio, false, intArrayOf(C.FORMAT_HANDLED), booleanArrayOf(false))))
        val subtitleTracks = Tracks(listOf(Tracks.Group(subtitle, false, intArrayOf(C.FORMAT_HANDLED), booleanArrayOf(false))))
        assertEquals("Português (Brasil) · 5.1", playbackTracks(audioTracks, C.TRACK_TYPE_AUDIO).single().label)
        assertEquals("Inglês", playbackTracks(subtitleTracks, C.TRACK_TYPE_TEXT).single().label)
    }
    @Test fun subtitleLabelsDoNotRepeatLanguageOrEnglishQualifiers() {
        val forced = Format.Builder().setSampleMimeType(MimeTypes.TEXT_VTT).setLanguage("pt-BR")
            .setLabel("Português · Portuguese (Brazil) [Forced]").build()
        val accessible = Format.Builder().setSampleMimeType(MimeTypes.TEXT_VTT).setLanguage("pt")
            .setLabel("Português SDH").build()
        val named = Format.Builder().setSampleMimeType(MimeTypes.TEXT_VTT).setLanguage("en")
            .setLabel("Diretor").build()
        assertEquals("Português (Brasil) · Forçada", trackLabel(forced, C.TRACK_TYPE_TEXT))
        assertEquals("Português · SDH", trackLabel(accessible, C.TRACK_TYPE_TEXT))
        assertEquals("Inglês · Diretor", trackLabel(named, C.TRACK_TYPE_TEXT))
    }
}
