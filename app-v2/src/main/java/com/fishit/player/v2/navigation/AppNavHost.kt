package com.fishit.player.v2.navigation

import com.fishit.player.v2.BuildConfig
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.fishit.player.core.model.MediaType
import com.fishit.player.core.model.SourceType
import com.fishit.player.core.sourceactivation.SourceActivationStore
import com.fishit.player.core.ui.theme.FishTheme
import com.fishit.player.feature.detail.ui.DetailScreen
import com.fishit.player.feature.home.HomeScreen
import com.fishit.player.feature.home.debug.DebugPlaybackScreen
import com.fishit.player.feature.library.LibraryScreen
import com.fishit.player.feature.onboarding.StartScreen
import com.fishit.player.feature.settings.DebugScreen
import com.fishit.player.feature.settings.CategorySelectionScreen
import com.fishit.player.feature.settings.SettingsScreen
import com.fishit.player.feature.settings.dbinspector.DbInspectorDetailScreen
import com.fishit.player.feature.settings.dbinspector.DbInspectorEntityTypesScreen
import com.fishit.player.feature.settings.dbinspector.DbInspectorNavArgs
import com.fishit.player.feature.settings.dbinspector.DbInspectorRowsScreen
import com.fishit.player.ui.PlayerScreen
import com.fishit.player.v2.ui.debug.DebugSkeletonScreen
import kotlinx.coroutines.delay

/**
 * Top-level navigation host for FishIT Player v2.
 *
 * Navigation flow:
 * Start -> Home -> Detail -> Player
 *               -> Debug -> DebugPlayback (test player)
 *               -> Settings
 *
 * Contract S-3: Bootstraps are started in Application.onCreate() ONLY.
 * No bootstrap triggers in navigation or UI layers.
 *
 * **Playback Flow (via Detail Screen):**
 * 1. DetailScreen emits StartPlayback event with full context
 * 2. AppNavHost stores context in PlaybackPendingState
 * 3. Navigation to Player route
 * 4. PlayerNavViewModel consumes pending state for full playback data
 *
 * **Conditional Start Destination:**
 * - If user has active sources (Xtream/Telegram logged in), skip StartScreen
 * - Otherwise, show StartScreen for onboarding
 */
@Suppress("ktlint:standard:function-naming")
@Composable
fun AppNavHost(
    playbackPendingState: PlaybackPendingState,
    sourceActivationStore: SourceActivationStore,
) {
    val navController = rememberNavController()
    val coroutineScope = rememberCoroutineScope()

    // Observe source activation state to determine start destination
    val activationSnapshot by sourceActivationStore.observeStates().collectAsState(
        initial = sourceActivationStore.getCurrentSnapshot()
    )
    
    // Track if initial navigation has been determined
    var initialCheckComplete by remember { mutableStateOf(false) }
    var shouldStartAtHome by remember { mutableStateOf(false) }
    
    // Determine start destination based on source activation
    // Wait a brief moment to allow XtreamSessionBootstrap to complete
    LaunchedEffect(Unit) {
        // Give bootstraps a moment to restore state (bootstrap has 2s delay)
        delay(200) // Small delay, bootstrap persists state immediately on success
        
        // Check if user has active sources
        val snapshot = sourceActivationStore.getCurrentSnapshot()
        shouldStartAtHome = snapshot.hasActiveSources
        initialCheckComplete = true
    }

    FishTheme {
        // Show loading indicator while checking auth state
        if (!initialCheckComplete) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@FishTheme
        }

        // Contract S-3: Removed LaunchedEffect bootstrap trigger
        // Sync is managed by CatalogSyncBootstrap in Application.onCreate()

        NavHost(
            navController = navController,
            startDestination = if (shouldStartAtHome) Routes.HOME else Routes.START,
        ) {
            // Start/Onboarding Screen
            composable(Routes.START) {
                StartScreen(
                    onContinue = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.START) { inclusive = true }
                        }
                    },
                )
            }

            // Home Screen
            composable(Routes.HOME) {
                HomeScreen(
                    onItemClick = { item ->
                        // Only LIVE content goes directly to player
                        // All other content (including Movies) goes to DetailScreen first
                        if (item.mediaType == MediaType.LIVE) {
                            navController.navigate(
                                Routes.player(
                                    mediaId = item.navigationId,
                                    sourceType = item.navigationSource.name,
                                ),
                            )
                        } else {
                            navController.navigate(
                                Routes.detail(
                                    mediaId = item.navigationId,
                                    sourceType = item.navigationSource.name,
                                ),
                            )
                        }
                    },
                    onSettingsClick = {
                        navController.navigate(Routes.SETTINGS)
                    },
                    onDebugClick = {
                        navController.navigate(Routes.DEBUG)
                    },
                    onLibraryClick = {
                        navController.navigate(Routes.LIBRARY)
                    },
                )
            }

            // Library Screen
            composable(Routes.LIBRARY) {
                LibraryScreen(
                    onItemClick = { item ->
                        navController.navigate(
                            Routes.detail(
                                mediaId = item.id,
                                sourceType = item.sourceType.name,
                            ),
                        )
                    },
                    onBack = { navController.popBackStack() },
                )
            }

            // Detail Screen
            composable(
                route = Routes.DETAIL_PATTERN,
                arguments =
                    listOf(
                        navArgument(Routes.ARG_MEDIA_ID) { type = NavType.StringType },
                        navArgument(Routes.ARG_SOURCE_TYPE) { type = NavType.StringType },
                    ),
            ) { backStackEntry ->
                val mediaId = backStackEntry.arguments?.getString(Routes.ARG_MEDIA_ID) ?: return@composable
                val sourceTypeName = backStackEntry.arguments?.getString(Routes.ARG_SOURCE_TYPE) ?: return@composable
                val sourceType =
                    try {
                        SourceType.valueOf(sourceTypeName)
                    } catch (e: Exception) {
                        SourceType.UNKNOWN
                    }

                DetailScreen(
                    mediaId = mediaId,
                    sourceType = sourceType,
                    onBack = { navController.popBackStack() },
                    onPlayback = { event ->
                        // Store full playback context in pending state
                        playbackPendingState.setPendingPlayback(event)

                        // Navigate to player with minimal route params (full context in pending state)
                        navController.navigate(
                            Routes.player(
                                mediaId = event.source.sourceId.value,
                                sourceType = event.source.sourceType.name,
                            ),
                        )
                    },
                )
            }

            composable(
                route = Routes.PLAYER_PATTERN,
                arguments =
                    listOf(
                        navArgument(Routes.ARG_MEDIA_ID) { type = NavType.StringType },
                        navArgument(Routes.ARG_SOURCE_TYPE) { type = NavType.StringType },
                    ),
            ) { backStackEntry ->
                val mediaId = backStackEntry.arguments?.getString(Routes.ARG_MEDIA_ID) ?: return@composable
                val sourceTypeName = backStackEntry.arguments?.getString(Routes.ARG_SOURCE_TYPE) ?: return@composable
                val sourceType =
                    try {
                        SourceType.valueOf(sourceTypeName)
                    } catch (e: Exception) {
                        SourceType.UNKNOWN
                    }

                PlayerNavScreen(
                    mediaId = mediaId,
                    sourceType = sourceType,
                    onBack = { navController.popBackStack() },
                )
            }

            // Debug Screen (settings/diagnostics) - only in debug builds
            if (BuildConfig.DEBUG) {
                composable(Routes.DEBUG) {
                    DebugScreen(
                        onBack = { navController.popBackStack() },
                        onDebugPlayback = { navController.navigate(Routes.DEBUG_PLAYBACK) },
                        onDatabaseInspector = { navController.navigate(Routes.DB_INSPECTOR) },
                    )
                }
            }

            // ObjectBox DB Inspector (power-user tool, available in all builds)
            composable(Routes.DB_INSPECTOR) {
                DbInspectorEntityTypesScreen(
                    onBack = { navController.popBackStack() },
                    onOpenEntity = { entityTypeId -> navController.navigate(Routes.dbInspectorEntity(entityTypeId)) },
                )
            }

            composable(
                route = Routes.DB_INSPECTOR_ENTITY_PATTERN,
                arguments =
                    listOf(
                        navArgument(DbInspectorNavArgs.ARG_ENTITY_TYPE) { type = NavType.StringType },
                    ),
            ) { backStackEntry ->
                val entityTypeId = backStackEntry.arguments?.getString(DbInspectorNavArgs.ARG_ENTITY_TYPE) ?: return@composable
                DbInspectorRowsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenRow = { rowId -> navController.navigate(Routes.dbInspectorEntityDetail(entityTypeId, rowId)) },
                )
            }

            composable(
                route = Routes.DB_INSPECTOR_ENTITY_DETAIL_PATTERN,
                arguments =
                    listOf(
                        navArgument(DbInspectorNavArgs.ARG_ENTITY_TYPE) { type = NavType.StringType },
                        navArgument(DbInspectorNavArgs.ARG_ROW_ID) { type = NavType.LongType },
                    ),
            ) {
                DbInspectorDetailScreen(
                    onBack = { navController.popBackStack() },
                )
            }

            // Debug Playback Screen (test player with Big Buck Bunny) - only in debug builds
            if (BuildConfig.DEBUG) {
                composable(Routes.DEBUG_PLAYBACK) {
                    DebugPlaybackScreen(
                        onBack = { navController.popBackStack() },
                    )
                }
            }

            // Settings Screen
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onDatabaseInspector = { navController.navigate(Routes.DB_INSPECTOR) },
                    onCategorySelection = { navController.navigate(Routes.CATEGORY_SELECTION) },
                )
            }

            // Category Selection Screen (Xtream categories)
            composable(Routes.CATEGORY_SELECTION) {
                CategorySelectionScreen(
                    onBack = { navController.popBackStack() },
                )
            }

            // Legacy Debug Skeleton (for reference) - only in debug builds
            if (BuildConfig.DEBUG) {
                composable(Routes.DEBUG_SKELETON) {
                    DebugSkeletonScreen()
                }
            }
        }
    }
}

@Suppress("ktlint:standard:function-naming")
@Composable
private fun PlayerNavScreen(
    mediaId: String,
    sourceType: SourceType,
    onBack: () -> Unit,
    viewModel: PlayerNavViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(mediaId, sourceType) {
        viewModel.load(mediaId, sourceType)
    }

    when {
        state.context != null ->
            state.context?.let { context ->
                PlayerScreen(
                    context = context,
                    onExit = onBack,
                )
            }

        state.error != null -> {
            // Show error with retry option instead of auto-navigating back
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = state.error ?: "Unable to start playback",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { viewModel.load(mediaId, sourceType) }) {
                            Text("Retry")
                        }
                        Button(onClick = onBack) {
                            Text("Back")
                        }
                    }
                }
            }
        }

        else -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
    }
}

/**
 * Route constants for navigation.
 */
object Routes {
    // Main routes
    const val START = "start"
    const val HOME = "home"
    const val LIBRARY = "library"
    const val DEBUG = "debug"
    const val DEBUG_PLAYBACK = "debug_playback"
    const val DEBUG_SKELETON = "debug_skeleton"
    const val SETTINGS = "settings"
    const val CATEGORY_SELECTION = "category_selection"

    // Database Inspector (ObjectBox) - debug/power-user
    const val DB_INSPECTOR = "db_inspector"
    const val DB_INSPECTOR_ENTITY_PATTERN = "$DB_INSPECTOR/{${DbInspectorNavArgs.ARG_ENTITY_TYPE}}"
    const val DB_INSPECTOR_ENTITY_DETAIL_PATTERN =
        "$DB_INSPECTOR/{${DbInspectorNavArgs.ARG_ENTITY_TYPE}}/{${DbInspectorNavArgs.ARG_ROW_ID}}"

    // Detail route with arguments
    const val ARG_MEDIA_ID = "mediaId"
    const val ARG_SOURCE_TYPE = "sourceType"
    const val DETAIL_PATTERN = "detail/{$ARG_MEDIA_ID}/{$ARG_SOURCE_TYPE}"
    const val PLAYER_PATTERN = "player/{$ARG_MEDIA_ID}/{$ARG_SOURCE_TYPE}"

    fun detail(
        mediaId: String,
        sourceType: String,
    ): String = "detail/$mediaId/$sourceType"

    fun player(
        mediaId: String,
        sourceType: String,
    ): String = "player/$mediaId/$sourceType"

    fun dbInspectorEntity(entityTypeId: String): String = "$DB_INSPECTOR/$entityTypeId"

    fun dbInspectorEntityDetail(
        entityTypeId: String,
        rowId: Long,
    ): String = "$DB_INSPECTOR/$entityTypeId/$rowId"
}
