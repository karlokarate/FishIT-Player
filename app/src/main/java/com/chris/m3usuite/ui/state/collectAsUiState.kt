package com.chris.m3usuite.ui.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.State
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect

@Composable
fun <T> collectAsUiState(flow: Flow<T>, emptyWhen: (T) -> Boolean = { false }): State<UiState<T>> {
    val state: MutableState<UiState<T>> = remember { mutableStateOf<UiState<T>>(UiState.Loading) }
    LaunchedEffect(flow) {
        flow
            .catch { t -> state.value = UiState.Error(t.message ?: "Fehler", t) }
            .collect { value ->
                state.value = if (emptyWhen(value)) UiState.Empty else UiState.Success(value)
            }
    }
    return state
}

