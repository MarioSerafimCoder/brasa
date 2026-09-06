package com.brasa.tv.feature.update

import org.junit.Assert.*
import org.junit.Test

class UpdatePolicyTest {
    @Test fun checkFailureDoesNotBlockWatching() {
        assertFalse(UpdateUiState.Error("Servidor de atualização indisponível").requiresInstallation())
        assertFalse(UpdateUiState.Checking.requiresInstallation())
    }
    @Test fun ordinaryUpdateCanBeDeferredEvenAfterDownloadFailure() {
        val update = UpdateManifest(versionCode = 30, mandatory = false)
        assertFalse(UpdateUiState.Available(update).requiresInstallation())
        assertFalse(UpdateUiState.Ready(update, "test.apk").requiresInstallation())
        assertFalse(UpdateUiState.Error("Download falhou", update).requiresInstallation())
    }
    @Test fun explicitMandatoryUpdateRemainsRequiredThroughoutInstall() {
        val update = UpdateManifest(versionCode = 30, mandatory = true)
        val states = listOf(UpdateUiState.Available(update), UpdateUiState.Downloading(update, 20),
            UpdateUiState.Validating(update), UpdateUiState.Ready(update, "test.apk"),
            UpdateUiState.PermissionRequired(update, "test.apk"), UpdateUiState.Installing(update),
            UpdateUiState.Error("Falha", update))
        assertTrue(states.all { it.requiresInstallation() })
        assertFalse(UpdateUiState.Installed("1.0.29").requiresInstallation())
    }
}
