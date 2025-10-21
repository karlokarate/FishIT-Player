package com.chris.m3usuite.ui.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.chris.m3usuite.ui.focus.FocusKit
import com.chris.m3usuite.ui.focus.run
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class TvKeyboard {
    Default,
    Uri,
    Number,
    Password,
    Email
}

@Composable
fun FishFormSection(
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val horizontal = LocalFishDimens.current.contentPaddingHorizontalDp
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontal, vertical = 4.dp)
        )
        if (!description.isNullOrBlank()) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontal)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Column(
            modifier = FocusKit.run { Modifier.focusGroup() },
            content = content
        )
    }
}

@Composable
fun FishFormSwitch(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    helperText: String? = null,
    errorText: String? = null
) {
    val horizontal = LocalFishDimens.current.contentPaddingHorizontalDp
    Column(modifier = modifier.fillMaxWidth()) {
        val rowModifier = FocusKit.run {
            Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontal, vertical = 6.dp)
                .tvClickable(
                    enabled = enabled,
                    role = androidx.compose.ui.semantics.Role.Switch
                ) { onCheckedChange(!checked) }
                .onDpadAdjustLeftRight(
                    onLeft = { if (enabled && checked) onCheckedChange(false) },
                    onRight = { if (enabled && !checked) onCheckedChange(true) }
                )
        }
        Row(
            modifier = rowModifier,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = checked,
                onCheckedChange = { onCheckedChange(it) },
                enabled = enabled
            )
        }
        FormSupportingText(helperText, errorText, enabled, horizontal)
    }
}

@Composable
fun <T> FishFormSelect(
    label: String,
    options: List<T>,
    selected: T,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    helperText: String? = null,
    errorText: String? = null,
    optionLabel: (T) -> String = { it.toString() },
    placeholder: String = "—"
) {
    val horizontal = LocalFishDimens.current.contentPaddingHorizontalDp
    val safeOptions = if (options.isEmpty()) emptyList() else options
    val currentIndex = safeOptions.indexOfFirst { it == selected }.takeIf { it >= 0 } ?: 0
    val displayValue = safeOptions.getOrNull(currentIndex)?.let(optionLabel) ?: placeholder
    Column(modifier = modifier.fillMaxWidth()) {
        val rowModifier = FocusKit.run {
            Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontal, vertical = 6.dp)
                .tvClickable(enabled = enabled, role = androidx.compose.ui.semantics.Role.DropdownList) {
                    if (enabled && safeOptions.isNotEmpty()) {
                        val nextIndex = (currentIndex + 1) % safeOptions.size
                        onSelected(safeOptions[nextIndex])
                    }
                }
                .onDpadAdjustLeftRight(
                    onLeft = {
                        if (enabled && safeOptions.isNotEmpty()) {
                            val next = if (currentIndex <= 0) safeOptions.lastIndex else currentIndex - 1
                            onSelected(safeOptions[next])
                        }
                    },
                    onRight = {
                        if (enabled && safeOptions.isNotEmpty()) {
                            val next = if (currentIndex >= safeOptions.lastIndex) 0 else currentIndex + 1
                            onSelected(safeOptions[next])
                        }
                    }
                )
        }
        Row(
            modifier = rowModifier,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                modifier = Modifier.weight(1f)
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    tint = if (enabled && safeOptions.isNotEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
                Text(
                    text = displayValue,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = if (enabled && safeOptions.isNotEmpty()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            }
        }
        FormSupportingText(helperText, errorText, enabled, horizontal)
    }
}

@Composable
fun FishFormSlider(
    label: String,
    value: Int,
    range: IntRange,
    step: Int = 1,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    helperText: String? = null,
    errorText: String? = null,
    valueFormatter: (Int) -> String = { it.toString() }
) {
    val horizontal = LocalFishDimens.current.contentPaddingHorizontalDp
    val context = LocalContext.current
    val isTv = FocusKit.isTvDevice(context)
    val clamped = value.coerceIn(range.first, range.last)
    val ratio = if (range.isEmpty()) 0f else (clamped - range.first).toFloat() / max(1, range.last - range.first)
    val sliderSteps = if (step <= 0) 0 else max(0, ((range.last - range.first) / step) - 1)
    Column(modifier = modifier.fillMaxWidth()) {
        val rowModifier = FocusKit.run {
            Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontal, vertical = 6.dp)
                .tvClickable(enabled = enabled) {}
                .onDpadAdjustLeftRight(
                    onLeft = {
                        if (enabled && !range.isEmpty()) {
                            val next = clamped - step
                            onValueChange(max(range.first, next))
                        }
                    },
                    onRight = {
                        if (enabled && !range.isEmpty()) {
                            val next = clamped + step
                            onValueChange(min(range.last, next))
                        }
                    }
                )
        }
        Row(
            modifier = rowModifier,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                modifier = Modifier.weight(1f)
            )
            Text(
                text = valueFormatter(clamped),
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }
        if (isTv) {
            LinearProgressIndicator(
                progress = { ratio },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontal)
                    .height(6.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(999.dp)
                    ),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.Transparent
            )
        } else {
            Slider(
                value = clamped.toFloat(),
                onValueChange = { raw ->
                    if (!range.isEmpty()) {
                        val snapped = snapToStep(raw, range.first, range.last, step)
                        onValueChange(snapped)
                    }
                },
                valueRange = range.first.toFloat()..range.last.toFloat(),
                steps = sliderSteps,
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontal)
            )
        }
        FormSupportingText(helperText, errorText, enabled, horizontal)
    }
}

private fun snapToStep(value: Float, min: Int, max: Int, step: Int): Int {
    if (step <= 0) return value.roundToInt().coerceIn(min, max)
    val base = min.toFloat()
    val normalized = (value - base) / step.toFloat()
    val snapped = base + normalized.roundToInt() * step
    return snapped.roundToInt().coerceIn(min, max)
}

@Composable
fun FishFormTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    placeholder: String? = null,
    helperText: String? = null,
    errorText: String? = null,
    keyboard: TvKeyboard = TvKeyboard.Default,
    trailingContent: (@Composable RowScope.() -> Unit)? = null
) {
    val horizontal = LocalFishDimens.current.contentPaddingHorizontalDp
    var showDialog by remember { mutableStateOf(false) }
    Column(modifier = modifier.fillMaxWidth()) {
        val rowModifier = FocusKit.run {
            Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontal, vertical = 6.dp)
                .tvClickable(enabled = enabled) { if (enabled) showDialog = true }
        }
        Row(
            modifier = rowModifier,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                modifier = Modifier.weight(1f)
            )
            if (trailingContent != null) {
                trailingContent()
            } else {
                val display = when {
                    value.isNotBlank() && keyboard == TvKeyboard.Password -> "\u2022".repeat(value.length).ifEmpty { "\u2022" }
                    value.isNotBlank() -> value
                    !placeholder.isNullOrBlank() -> placeholder
                    else -> "—"
                }
                val color = when {
                    value.isNotBlank() -> MaterialTheme.colorScheme.onSurface
                    !placeholder.isNullOrBlank() -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }.let { if (enabled) it else it.copy(alpha = 0.4f) }
                Text(
                    text = display,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = color
                )
            }
        }
        FormSupportingText(helperText, errorText, enabled, horizontal)
    }

    if (showDialog) {
        var draft by remember(value) { mutableStateOf(value) }
        val keyboardOptions = keyboard.toKeyboardOptions()
        val transformation = if (keyboard == TvKeyboard.Password) PasswordVisualTransformation() else VisualTransformation.None
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(text = label) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        keyboardOptions = keyboardOptions,
                        singleLine = true,
                        visualTransformation = transformation,
                        placeholder = placeholder?.let { ph -> { Text(ph) } }
                    )
                    if (!errorText.isNullOrBlank()) {
                        Text(
                            text = errorText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else if (!helperText.isNullOrBlank()) {
                        Text(
                            text = helperText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showDialog = false
                    if (draft != value) onValueChange(draft)
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Abbrechen")
                }
            }
        )
    }
}

@Composable
fun FishFormButtonRow(
    primaryText: String,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    primaryEnabled: Boolean = true,
    secondaryText: String? = null,
    onSecondary: (() -> Unit)? = null,
    secondaryEnabled: Boolean = true,
    isBusy: Boolean = false
) {
    val horizontal = LocalFishDimens.current.contentPaddingHorizontalDp
    val focusModifier = FocusKit.run { Modifier.focusGroup() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontal, vertical = 8.dp)
            .then(focusModifier),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FocusKit.TvButton(
            onClick = onPrimary,
            enabled = primaryEnabled && !isBusy,
            modifier = Modifier.weight(1f)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
                Text(primaryText)
            }
        }
        if (secondaryText != null && onSecondary != null) {
            FocusKit.TvOutlinedButton(
                onClick = onSecondary,
                enabled = secondaryEnabled
            ) {
                Text(secondaryText)
            }
        }
    }
}

@Composable
private fun FormSupportingText(
    helperText: String?,
    errorText: String?,
    enabled: Boolean,
    horizontal: Dp
) {
    val text = when {
        !errorText.isNullOrBlank() -> errorText
        !helperText.isNullOrBlank() -> helperText
        else -> null
    }
    if (text.isNullOrBlank()) return
    val color = if (!errorText.isNullOrBlank()) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }.let { if (enabled) it else it.copy(alpha = 0.5f) }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontal, vertical = 2.dp)
    )
}

private fun TvKeyboard.toKeyboardOptions(): KeyboardOptions = when (this) {
    TvKeyboard.Default -> KeyboardOptions.Default.copy(imeAction = ImeAction.Done)
    TvKeyboard.Uri -> KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done)
    TvKeyboard.Number -> KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done)
    TvKeyboard.Password -> KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done)
    TvKeyboard.Email -> KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done)
}

// Use Kotlin's built-in IntRange.isEmpty()
