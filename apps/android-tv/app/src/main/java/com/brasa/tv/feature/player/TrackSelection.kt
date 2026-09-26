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
import java.text.Normalizer

data class PlaybackTrack(val group: TrackGroup, val index: Int, val label: String, val language: String, val selected: Boolean, val supported: Boolean)

fun playbackTracks(tracks: Tracks, type: Int): List<PlaybackTrack> = tracks.groups.filter { it.type == type }.flatMap { group ->
    (0 until group.length).map { index ->
        val format = group.getTrackFormat(index)
        PlaybackTrack(group.mediaTrackGroup, index, trackLabel(format, type), format.language.orEmpty(), group.isTrackSelected(index), group.isTrackSupported(index))
    }
}

private fun trackLabel(format: Format, type: Int): String {
    val language = format.language?.takeUnless { it == "und" }?.let { tag ->
        val locale = Locale.forLanguageTag(tag)
        val displayLocale = Locale.forLanguageTag("pt-BR")
        val name = locale.getDisplayLanguage(displayLocale).replaceFirstChar { it.uppercase() }
        val country = locale.getDisplayCountry(displayLocale)
        if (country.isBlank()) name else "$name ($country)"
    }.orEmpty()
    fun identity(value: String) = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT).trim()
    val locale = Locale.forLanguageTag(format.language.orEmpty())
    val languageNames = listOf(language, locale.getDisplayName(Locale.ENGLISH), locale.getDisplayName(locale),
        locale.getDisplayName(Locale.forLanguageTag("pt-BR"))).map(::identity)
    val parts = mutableListOf(language.ifBlank { "Idioma não informado" })
    format.label?.trim()?.takeIf {
        it.isNotBlank() && identity(it) !in languageNames &&
            !it.matches(Regex("(?i)(audio|subtitle|legenda|track|faixa)(?:[ _-]*(track|faixa))?[ _-]*\\d*"))
    }?.let(parts::add)
    if (type == C.TRACK_TYPE_AUDIO) {
        when (format.channelCount) {
            1 -> parts += "Mono"
            2 -> parts += "Estéreo"
            6 -> parts += "5.1"
            8 -> parts += "7.1"
            in 3..Int.MAX_VALUE -> parts += "${format.channelCount} canais"
        }
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
