@file:androidx.media3.common.util.UnstableApi

package com.brasa.tv.feature.player

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.TrackGroup
import androidx.media3.common.Tracks
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import com.brasa.tv.data.storage.AppSettings
import java.util.Locale

data class PlaybackTrack(val group: TrackGroup, val index: Int, val label: String, val language: String, val selected: Boolean, val supported: Boolean)

fun playbackTracks(tracks: Tracks, type: Int): List<PlaybackTrack> = tracks.groups.filter { it.type == type }.flatMap { group ->
    (0 until group.length).map { index ->
        val format = group.getTrackFormat(index)
        PlaybackTrack(group.mediaTrackGroup, index, trackLabel(format, type), format.language.orEmpty(), group.isTrackSelected(index), group.isTrackSupported(index))
    }
}

private fun trackLabel(format: Format, type: Int): String {
    val language = format.language?.takeUnless { it == "und" }?.let { Locale.forLanguageTag(it).getDisplayLanguage(Locale.forLanguageTag("pt-BR")) }.orEmpty()
    val parts = mutableListOf(language.ifBlank { "Idioma não informado" })
    format.label?.takeIf { it.isNotBlank() && !it.equals(language, true) }?.let(parts::add)
    if (type == C.TRACK_TYPE_AUDIO) {
        if (format.channelCount > 0) parts += "${format.channelCount} canais"
        format.sampleMimeType?.substringAfterLast('/')?.uppercase(Locale.ROOT)?.let(parts::add)
    }
    if (format.selectionFlags and C.SELECTION_FLAG_FORCED != 0) parts += "Forçada"
    if (format.roleFlags and C.ROLE_FLAG_COMMENTARY != 0) parts += "Comentário"
    return parts.joinToString(" · ")
}

fun selectPlaybackTrack(parameters: TrackSelectionParameters, type: Int, track: PlaybackTrack?): TrackSelectionParameters {
    val builder = parameters.buildUpon().clearOverridesOfType(type)
        .setTrackTypeDisabled(type, track == null && type == C.TRACK_TYPE_TEXT)
    if (track != null) builder.setOverrideForType(TrackSelectionOverride(track.group, track.index))
    return builder.build()
}

fun applyPlaybackPreferences(parameters: TrackSelectionParameters, settings: AppSettings): TrackSelectionParameters = parameters.buildUpon()
    .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
    .clearOverridesOfType(C.TRACK_TYPE_TEXT)
    .setPreferredAudioLanguage(settings.audioLanguage.ifBlank { null })
    .setPreferredTextLanguage(settings.subtitleLanguage.ifBlank { null })
    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, settings.subtitleMode == "off")
    .build()
