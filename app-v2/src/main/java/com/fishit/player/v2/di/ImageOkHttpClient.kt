package com.fishit.player.v2.di

import javax.inject.Qualifier

/**
 * Qualifier for the OkHttpClient used specifically for image loading.
 *
 * Separates the image-loading OkHttpClient from other OkHttpClients
 * (e.g., XtreamApiClient, general network) to allow different configurations:
 * - Image: longer timeouts, more concurrent requests per host
 * - API: stricter timeouts, retry policies
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ImageOkHttpClient
