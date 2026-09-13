package com.brasa.tv.designsystem

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/** Store the selected key, not the position: catalog refreshes can reorder cards. */
class CatalogFocusMemory(private val savedKey: MutableState<String>, private val pending: MutableState<Boolean>) {
    val selectedKey: String get() = savedKey.value
    val needsRestore: Boolean get() = pending.value
    fun select(key: String) { savedKey.value = key }
    fun restore(key: String) { savedKey.value = key; pending.value = true }

    /** Scroll before requesting focus: off-screen lazy cards do not exist yet. */
    @Composable
    fun RestoreItems(keys: List<String>, fallback: Boolean = true, scroll: suspend (Int) -> Unit) {
        LaunchedEffect(keys, pending.value) {
            if (!pending.value || keys.isEmpty()) return@LaunchedEffect
            var index = keys.indexOf(savedKey.value)
            if (index < 0 && fallback) { index = 0; savedKey.value = keys.first() }
            if (index >= 0) { scroll(index); withFrameNanos { } }
        }
    }

    @Composable
    fun modifier(key: String): Modifier {
        val requester = remember(key) { FocusRequester() }
        LaunchedEffect(key, pending.value) {
            if (pending.value && savedKey.value == key) {
                withFrameNanos { }
                if (runCatching { requester.requestFocus() }.getOrDefault(false)) pending.value = false
            }
        }
        return Modifier.focusRequester(requester).onFocusChanged { if (it.isFocused && (!pending.value || savedKey.value == key)) { savedKey.value = key; pending.value = false } }
    }
}

@Composable
fun rememberCatalogFocus(scope: String): CatalogFocusMemory {
    val savedKey = rememberSaveable(scope) { mutableStateOf("") }
    val pending = remember(scope) { mutableStateOf(savedKey.value.isNotBlank()) }
    val memory = remember(scope) { CatalogFocusMemory(savedKey, pending) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, memory) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME && memory.selectedKey.isNotBlank()) memory.restore(memory.selectedKey) }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return memory
}
