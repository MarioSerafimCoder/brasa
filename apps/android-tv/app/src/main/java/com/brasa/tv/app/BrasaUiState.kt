package com.brasa.tv.app

import com.brasa.tv.core.model.*

data class BrasaUiState(
    val operations: OperationStates = OperationStates(),
    val server: ServerInfo? = null,
    val pairing: PairingStatus? = null,
    val profiles: List<Profile> = emptyList(),
    val profile: Profile? = null,
    val home: HomeResponse? = null,
    val catalog: CatalogResponse? = null,
    val searchResults: List<CatalogItem> = emptyList(),
    val selected: CatalogItem? = null,
    val playbackItem: CatalogItem? = null,
    val playback: PlaybackInfo? = null,
    val selectedRow: HomeRow? = null,
    val cacheBytes: Long = 0,
    val paired: Boolean = false,
    val previewMode: Boolean = false,
    val libraryScanning: Boolean = false,
    val libraryScanMessage: String = "",
    val reconnecting: Boolean = false,
    val searching: Boolean = false,
    val searchError: String = "",
    val connectionProblem: com.brasa.tv.core.network.ConnectionProblem? = null,
)
