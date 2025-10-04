package com.chris.m3usuite.ui.forms

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.text.input.ImeAction

/**
 * TV/DPAD/IME helper: attach to text inputs or containers in TV forms so that:
 * - DPAD_CENTER, Enter, NumpadEnter on KeyUp invoke onOk()
 * - Use [rememberOkKeyboardActions] to also map IME action Done → onOk()
 *
 * Usage (Compose):
 *   val (opts, acts) = rememberOkKeyboardActions { onConfirm() }
 *   OutlinedTextField(
 *     value = name,
 *     onValueChange = setName,
 *     keyboardOptions = opts,
 *     keyboardActions = acts,
 *     modifier = Modifier.okOnImeOrDpad { onConfirm() }
 *   )
 *
 * On TV, pair this with a TvButton "OK" that also calls onConfirm().
 */
fun Modifier.okOnImeOrDpad(onOk: () -> Unit): Modifier = composed {
    val onOkUpdated by rememberUpdatedState(newValue = onOk)
    this.onKeyEvent { keyEvent ->
        if (keyEvent.type == KeyEventType.KeyUp) {
            when (keyEvent.key) {
                Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                    onOkUpdated()
                    true
                }
                else -> false
            }
        } else {
            false
        }
    }
}

/**
 * Returns KeyboardOptions/KeyboardActions pair that maps IME action Done → onOk().
 * Combine with [okOnImeOrDpad] to cover both soft-keyboard and DPAD inputs.
 */
@Composable
fun rememberOkKeyboardActions(onOk: () -> Unit): Pair<KeyboardOptions, KeyboardActions> {
    val onOkUpdated by rememberUpdatedState(newValue = onOk)
    val options = KeyboardOptions(imeAction = ImeAction.Done)
    val actions = KeyboardActions(
        onDone = {
            onOkUpdated()
        }
    )
    return options to actions
}

/**
 * Optional: attach to a parent container to capture DPAD/Enter even when the TextField
