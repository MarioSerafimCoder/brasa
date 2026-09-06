package com.brasa.tv.feature.update

/** A failed check is not evidence that the working client must be replaced. */
fun UpdateUiState.requiresInstallation(): Boolean = when (this) {
    is UpdateUiState.Available -> update.mandatory
    is UpdateUiState.Downloading -> update.mandatory
    is UpdateUiState.Validating -> update.mandatory
    is UpdateUiState.Ready -> update.mandatory
    is UpdateUiState.PermissionRequired -> update.mandatory
    is UpdateUiState.Installing -> update.mandatory
    is UpdateUiState.Error -> update?.mandatory == true
    else -> false
}
