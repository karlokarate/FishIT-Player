package com.chris.m3usuite.ui.common

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * TV-optimized empty state component with proper focus handling.
 * 
 * Problem: When a screen shows no content, users on TV can get stuck
 * without a clear way to navigate back. The lack of focusable elements
 * makes the screen feel broken.
 * 
 * Solution: Provide a clear, focusable empty state with:
 * - Visual feedback (message, icon)
 * - Focusable back button
 * - Automatic back handler integration
 * - Optional action button
 * 
 * Usage:
 *   TvEmptyState(
 *       message = "No channels available",
 *       onBack = { navController.popBackStack() }
 *   )
 */
@Composable
fun TvEmptyState(
    message: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = Icons.Default.Search,
    subMessage: String? = null,
    onBack: (() -> Unit)? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    enableAutoBackHandler: Boolean = true
) {
    val backFocusRequester = remember { FocusRequester() }
    
    // Handle back button press
    if (enableAutoBackHandler && onBack != null) {
        BackHandler(onBack = onBack)
    }
    
    // Request focus on back button when screen loads
    LaunchedEffect(Unit) {
        try {
            backFocusRequester.requestFocus()
        } catch (e: Exception) {
            // Focus request might fail if component is not yet composed
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(32.dp)
                .widthIn(max = 600.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Icon
            icon?.let {
                Icon(
                    imageVector = it,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
            
            // Main message
            Text(
                text = message,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            
            // Sub message
            subMessage?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Action buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Back button (always focusable)
                if (onBack != null) {
                    Button(
                        onClick = onBack,
                        modifier = Modifier
                            .focusRequester(backFocusRequester)
                            .focusable()
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Zurück")
                    }
                }
                
                // Optional action button
                if (actionLabel != null && onAction != null) {
                    Button(
                        onClick = onAction,
                        modifier = Modifier.focusable()
                    ) {
                        Text(actionLabel)
                    }
                }
            }
        }
    }
}

/**
 * Empty state for lists/grids on TV.
 * 
 * Shows when no items are available with guidance for users.
 */
@Composable
fun TvEmptyListState(
    emptyMessage: String = "Keine Einträge vorhanden",
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    showRefreshAction: Boolean = false,
    onRefresh: (() -> Unit)? = null
) {
    TvEmptyState(
        message = emptyMessage,
        subMessage = if (showRefreshAction) "Versuchen Sie, die Liste zu aktualisieren" else null,
        onBack = onBack,
        actionLabel = if (showRefreshAction) "Aktualisieren" else null,
        onAction = onRefresh,
        modifier = modifier
    )
}

/**
 * Loading state with optional cancellation for TV.
 * 
 * Shows a progress indicator with the ability to cancel/go back.
 */
@Composable
fun TvLoadingState(
    message: String = "Lädt...",
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    showBackButton: Boolean = true
) {
    val backFocusRequester = remember { FocusRequester() }
    
    // Handle back button press
    if (onBack != null) {
        BackHandler(onBack = onBack)
    }
    
    // Request focus on back button when screen loads
    LaunchedEffect(Unit) {
        if (showBackButton) {
            try {
                backFocusRequester.requestFocus()
            } catch (e: Exception) {
                // Ignore focus errors
            }
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(48.dp)
            )
            
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
            
            if (showBackButton && onBack != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onBack,
                    modifier = Modifier
                        .focusRequester(backFocusRequester)
                        .focusable()
                ) {
                    Text("Abbrechen")
                }
            }
        }
    }
}

/**
 * Error state with retry option for TV.
 * 
 * Shows an error message with options to retry or go back.
 */
@Composable
fun TvErrorState(
    errorMessage: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onRetry: (() -> Unit)? = null
) {
    TvEmptyState(
        message = "Ein Fehler ist aufgetreten",
        subMessage = errorMessage,
        onBack = onBack,
        actionLabel = if (onRetry != null) "Erneut versuchen" else null,
        onAction = onRetry,
        modifier = modifier
    )
}
