package com.brasa.tv.core.model

import kotlinx.serialization.Serializable

@Serializable
data class LibraryScanStatus(
    val id: String = "",
    val state: String = "idle",
    val message: String = "",
    val progress: Int = 0,
)
