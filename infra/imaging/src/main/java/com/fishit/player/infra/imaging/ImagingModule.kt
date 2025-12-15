package com.fishit.player.infra.imaging

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * RESERVED MODULE: Imaging Infrastructure
 *
 * Purpose: Global Coil/ImageLoader/OkHttp cache + provisioning (source-agnostic)
 *
 * TODO: Implement when needed:
 * - [ ] Global Coil ImageLoader configuration
 * - [ ] OkHttp cache setup for images
 * - [ ] Source-specific fetcher integration via narrow interfaces
 * - [ ] ImageLoader DI provisioning
 *
 * Contract Rules:
 * - Transport layers MUST NOT own Coil/ImageLoader configuration
 * - May accept source-specific fetchers via narrow interfaces
 * - Remains source-agnostic (no TDLib/Xtream direct dependencies)
 *
 * See: docs/v2/FROZEN_MODULE_MANIFEST.md
 */
@Module
@InstallIn(SingletonComponent::class)
object ImagingModule {
    // TODO: Provide @Singleton ImageLoader when ready
    // TODO: Provide @Singleton OkHttpClient for imaging when ready
}
