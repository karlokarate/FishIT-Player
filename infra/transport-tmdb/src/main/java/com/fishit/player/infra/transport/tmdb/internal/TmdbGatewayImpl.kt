package com.fishit.player.infra.transport.tmdb.internal

import com.fishit.player.infra.logging.UnifiedLog
import com.fishit.player.infra.transport.tmdb.api.TmdbError
import com.fishit.player.infra.transport.tmdb.api.TmdbGateway
import com.fishit.player.infra.transport.tmdb.api.TmdbImages
import com.fishit.player.infra.transport.tmdb.api.TmdbMovieDetails
import com.fishit.player.infra.transport.tmdb.api.TmdbRequestParams
import com.fishit.player.infra.transport.tmdb.api.TmdbResult
import com.fishit.player.infra.transport.tmdb.api.TmdbTvDetails
import com.uwetrottmann.tmdb2.Tmdb
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of TmdbGateway using tmdb-java library.
 *
 * All tmdb-java types and exceptions are contained within this class.
 * Public API only exposes internal DTOs and typed errors.
 *
 * Error Handling:
 * - All exceptions caught and converted to TmdbResult.Err
 * - Network errors: TmdbError.Network
 * - Timeouts: TmdbError.Timeout
 * - 401: TmdbError.Unauthorized
 * - 404: TmdbError.NotFound
 * - 429: TmdbError.RateLimited
 * - 5xx/other: TmdbError.Unknown
 *
 * Retry Policy:
 * - Network errors and 5xx: retryable (handled by caller)
 * - 401/404: never retry
 * - 429: surface retryAfter header
 */
@Singleton
class TmdbGatewayImpl
    @Inject
    constructor(
        private val tmdb: Tmdb,
    ) : TmdbGateway {
        override suspend fun getMovieDetails(
            movieId: Int,
            params: TmdbRequestParams,
        ): TmdbResult<TmdbMovieDetails> =
            executeRequest("getMovieDetails") {
                val response =
                    tmdb
                        .moviesService()
                        .summary(movieId, params.language, null)
                        .execute()

                if (!response.isSuccessful) {
                    return@executeRequest TmdbResult.Err(mapHttpError(response.code(), response.headers()))
                }

                val movie = response.body() ?: return@executeRequest TmdbResult.Err(TmdbError.Unknown("Empty response body"))

                TmdbResult.Ok(movie.toTmdbMovieDetails())
            }

        override suspend fun getTvDetails(
            tvId: Int,
            params: TmdbRequestParams,
        ): TmdbResult<TmdbTvDetails> =
            executeRequest("getTvDetails") {
                val response =
                    tmdb
                        .tvService()
                        .tv(tvId, params.language, null)
                        .execute()

                if (!response.isSuccessful) {
                    return@executeRequest TmdbResult.Err(mapHttpError(response.code(), response.headers()))
                }

                val tv = response.body() ?: return@executeRequest TmdbResult.Err(TmdbError.Unknown("Empty response body"))

                TmdbResult.Ok(tv.toTmdbTvDetails())
            }

        override suspend fun getMovieImages(
            movieId: Int,
            params: TmdbRequestParams,
        ): TmdbResult<TmdbImages> =
            executeRequest("getMovieImages") {
                val response =
                    tmdb
                        .moviesService()
                        .images(movieId, params.language)
                        .execute()

                if (!response.isSuccessful) {
                    return@executeRequest TmdbResult.Err(mapHttpError(response.code(), response.headers()))
                }

                val images = response.body() ?: return@executeRequest TmdbResult.Err(TmdbError.Unknown("Empty response body"))

                TmdbResult.Ok(images.toTmdbImages(movieId))
            }

        override suspend fun getTvImages(
            tvId: Int,
            params: TmdbRequestParams,
        ): TmdbResult<TmdbImages> =
            executeRequest("getTvImages") {
                val response =
                    tmdb
                        .tvService()
                        .images(tvId, params.language)
                        .execute()

                if (!response.isSuccessful) {
                    return@executeRequest TmdbResult.Err(mapHttpError(response.code(), response.headers()))
                }

                val images = response.body() ?: return@executeRequest TmdbResult.Err(TmdbError.Unknown("Empty response body"))

                TmdbResult.Ok(images.toTmdbImages(tvId))
            }

        /**
         * Execute a TMDB request with exception handling.
         *
         * All exceptions are caught and converted to typed TmdbError variants.
         */
        private inline fun <T> executeRequest(
            operation: String,
            block: () -> TmdbResult<T>,
        ): TmdbResult<T> =
            try {
                block()
            } catch (e: UnknownHostException) {
                UnifiedLog.w("TmdbGateway", "$operation failed: Network error (DNS/host)", e)
                TmdbResult.Err(TmdbError.Network)
            } catch (e: SocketTimeoutException) {
                UnifiedLog.w("TmdbGateway", "$operation failed: Timeout", e)
                TmdbResult.Err(TmdbError.Timeout)
            } catch (e: IOException) {
                UnifiedLog.w("TmdbGateway", "$operation failed: Network I/O error", e)
                TmdbResult.Err(TmdbError.Network)
            } catch (e: HttpException) {
                UnifiedLog.w("TmdbGateway", "$operation failed: HTTP ${e.code()}", e)
                TmdbResult.Err(mapHttpError(e.code(), null))
            } catch (e: Exception) {
                UnifiedLog.e("TmdbGateway", "$operation failed: Unknown error", e)
                TmdbResult.Err(TmdbError.Unknown(e.message))
            }

        /**
         * Map HTTP status codes to typed errors.
         */
        private fun mapHttpError(
            code: Int,
            headers: okhttp3.Headers?,
        ): TmdbError =
            when (code) {
                401 -> TmdbError.Unauthorized
                404 -> TmdbError.NotFound
                429 -> {
                    val retryAfter = headers?.get("Retry-After")?.toLongOrNull()
                    TmdbError.RateLimited(retryAfter)
                }
                in 500..599 -> TmdbError.Unknown("Server error: $code")
                else -> TmdbError.Unknown("HTTP error: $code")
            }
    }
