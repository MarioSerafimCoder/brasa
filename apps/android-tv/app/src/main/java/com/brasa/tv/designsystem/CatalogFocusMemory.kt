package com.brasa.tv.designsystem

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester

/** Store the selected key, not the position: catalog refreshes can reorder cards. */
class CatalogFocusMemory(private val savedKey: MutableState<String>, private val pending: MutableState<Boolean>) {
    fun select(key: String) { savedKey.value = key }
    fun restore(key: String) { savedKey.value = key; pending.value = true }

    @Composable
    fun modifier(key: String): Modifier {
        val requester = remember(key) { FocusRequester() }
        LaunchedEffect(key, pending.value) {
            if (pending.value && savedKey.value == key) {
                withFrameNanos { }
                if (runCatching { requester.requestFocus() }.getOrDefault(false)) pending.value = false
            }
        }
        return Modifier.focusRequester(requester)
    }
}

@Composable
fun rememberCatalogFocus(scope: String): CatalogFocusMemory {
    val savedKey = rememberSaveable(scope) { mutableStateOf("") }
    val pending = remember(scope) { mutableStateOf(savedKey.value.isNotBlank()) }
    return remember(scope) { CatalogFocusMemory(savedKey, pending) }
}
