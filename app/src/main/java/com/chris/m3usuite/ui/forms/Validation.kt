package com.chris.m3usuite.ui.forms

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow

sealed interface ValidationState {
    data object Ok : ValidationState
    data class Error(val message: String) : ValidationState
}

typealias Validator<T> = (T) -> ValidationState

@Composable
fun ValidationHint(
    helperText: String? = null,
    errorText: String? = null,
    state: ValidationState? = null,
    modifier: Modifier = Modifier
) {
    val err = when (state) {
        is ValidationState.Error -> state.message
        else -> errorText
    }
    when {
        !err.isNullOrBlank() -> Text(
            err,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier
        )
        !helperText.isNullOrBlank() -> Text(
            helperText,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier
        )
    }
}

