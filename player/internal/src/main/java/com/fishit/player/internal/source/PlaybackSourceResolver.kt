package com.fishit.player.internal.source

import com.fishit.player.core.playermodel.PlaybackContext
import com.fishit.player.core.playermodel.SourceType
import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.playback.domain.DataSourceType
import com.fishit.player.playback.domain.PlaybackSource
import com.fishit.player.playback.domain.PlaybackSourceException
import com.fishit.player.playback.domain.PlaybackSourceFactory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves playback sources using registered factories.
 *
 * This resolver holds all [PlaybackSourceFactory] implementations (injected via Hilt)
 * and selects the appropriate factory based on [PlaybackContext.sourceType].
 *
 * **Architecture:**
 * - player:internal depends on playback:domain (interfaces)
 * - playback:telegram and playback:xtream provide implementations
 * - Hilt wires everything together in app-v2
 *
 * **Resolution Flow:**
 * 1. Find factory that supports the context's sourceType
 * 2. Call factory.createSource(context)
 * 3. Return PlaybackSource for MediaItem construction
 *
 * **Fallback:**
 * If no factory supports the sourceType but the context has a URI,
 * create a basic HTTP PlaybackSource.
 */
@Singleton
class PlaybackSourceResolver
    @Inject
    constructor(
        private val factories: Set<@JvmSuppressWildcards PlaybackSourceFactory>,
    ) {
        companion object {
            private const val TAG = "PlaybackSourceResolver"


        }

        /**
         * Resolves a [PlaybackContext] to a [PlaybackSource].
         *
         * @param context The playback context to resolve
         * @return A PlaybackSource ready for MediaItem construction
         * @throws PlaybackSourceException if resolution fails
         */
        suspend fun resolve(context: PlaybackContext): PlaybackSource {
            UnifiedLog.d(TAG, "Resolving source for: ${context.canonicalId} (${context.sourceType})")

            // Find a factory that supports this source type
            val factory = factories.find { it.supports(context.sourceType) }

            return if (factory != null) {
                UnifiedLog.d(TAG, "Using factory: ${factory::class.simpleName}")
                try {
                    val source = factory.createSource(context)
                    UnifiedLog.d(TAG) {
                        "Selected dataSourceType=${source.dataSourceType} for canonicalId=${context.canonicalId}"
                    }
                    source
                } catch (e: Exception) {
                    UnifiedLog.e(TAG, "Factory failed to create source", e)
                    throw PlaybackSourceException(
                        "Failed to create source: ${e.message}",
                        context.sourceType,
                        e,
                    )
                }
            } else {
                // No factory found - try fallback
                resolveFallback(context)
            }
        }

        /**
         * Fallback resolution when no factory is available.
         *
         * Uses the URI directly if it's an HTTP(S) URL.
         * Throws explicit error instead of falling back to demo stream.
         */
        private fun resolveFallback(context: PlaybackContext): PlaybackSource {
            val uri = context.uri

            return when {
                uri != null && (uri.startsWith("http://") || uri.startsWith("https://")) -> {
                    UnifiedLog.w(TAG, "No factory for ${context.sourceType}, using URI directly: $uri")
                    PlaybackSource(
                        uri = uri,
                        headers = context.headers,
                        dataSourceType = DataSourceType.DEFAULT,
                    )
                }
                uri != null && uri.startsWith("file://") -> {
                    UnifiedLog.w(TAG, "No factory for ${context.sourceType}, using local file: $uri")
                    PlaybackSource(
                        uri = uri,
                        dataSourceType = DataSourceType.LOCAL_FILE,
                    )
                }
                else -> {
                    // Explicit error instead of demo stream fallback
                    UnifiedLog.e(TAG, "No factory and no valid URI for ${context.sourceType}")
                    throw PlaybackSourceException(
                        "No playback source available for ${context.sourceType}. " +
                            "Please ensure the source is configured correctly.",
                        context.sourceType,
                    )
                }
            }
        }

        /**
         * Checks if any factory can handle the given source type.
         */
        fun canResolve(sourceType: SourceType): Boolean = factories.any { it.supports(sourceType) }

        /**
         * Gets the number of registered factories.
         * Useful for diagnostics.
         */
        fun factoryCount(): Int = factories.size
    }
