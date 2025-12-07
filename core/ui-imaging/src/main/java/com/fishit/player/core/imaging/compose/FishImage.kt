package com.fishit.player.core.imaging.compose

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.size.Size
import com.fishit.player.core.model.ImageRef

/**
 * Composable wrapper for loading [ImageRef] via Coil 3.
 *
 * **Purpose:**
 * - Unified image loading API for all ImageRef variants
 * - Handles loading/error states with custom composables
 * - Respects preferred size hints
 *
 * **Usage:**
 * ```kotlin
 * FishImage(
 *     imageRef = metadata.poster,
 *     contentDescription = "Movie poster",
 *     modifier = Modifier.fillMaxSize(),
 * )
 * ```
 *
 * @param imageRef Image reference to load (null shows placeholder/error)
 * @param contentDescription Accessibility description
 * @param modifier Compose modifier
 * @param contentScale How to scale the image (default Crop)
 * @param alignment Image alignment within bounds
 * @param colorFilter Optional color filter
 * @param filterQuality Quality for scaling (default Low for performance)
 * @param placeholder Content to show while loading
 * @param error Content to show on error (also shown for null imageRef)
 * @param onState Callback for loading state changes
 */
@Composable
fun FishImage(
        imageRef: ImageRef?,
        contentDescription: String?,
        modifier: Modifier = Modifier,
        contentScale: ContentScale = ContentScale.Crop,
        alignment: Alignment = Alignment.Center,
        colorFilter: ColorFilter? = null,
        filterQuality: FilterQuality = FilterQuality.Low,
        placeholder: @Composable (() -> Unit)? = null,
        error: @Composable (() -> Unit)? = null,
        onState: ((AsyncImagePainter.State) -> Unit)? = null,
) {
    if (imageRef == null) {
        // Show error or placeholder for null refs
        Box(modifier = modifier) {
            when {
                error != null -> error()
                placeholder != null -> placeholder()
            }
        }
        return
    }

    val context = LocalPlatformContext.current

    // Track loading state for showing placeholder/error composables
    var imageState by remember {
        mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty)
    }

    val request =
            ImageRequest.Builder(context)
                    .data(imageRef)
                    .apply {
                        // Apply preferred size if specified
                        val width = imageRef.preferredWidth
                        val height = imageRef.preferredHeight
                        if (width != null && height != null) {
                            size(Size(width, height))
                        }
                        // If only one dimension specified, let Coil determine the other
                        // (avoiding explicit Dimension.Undefined usage which has API issues)
                    }
                    .build()

    Box(modifier = modifier) {
        AsyncImage(
                model = request,
                contentDescription = contentDescription,
                modifier = Modifier.matchParentSize(),
                contentScale = contentScale,
                alignment = alignment,
                colorFilter = colorFilter,
                filterQuality = filterQuality,
                onState = { state ->
                    imageState = state
                    onState?.invoke(state)
                },
        )

        // Show placeholder while loading
        if (imageState is AsyncImagePainter.State.Loading && placeholder != null) {
            placeholder()
        }

        // Show error composable on failure
        if (imageState is AsyncImagePainter.State.Error && error != null) {
            error()
        }
    }
}

/**
 * Simplified FishImage for common poster/thumbnail use cases.
 *
 * @param imageRef Image reference to load
 * @param contentDescription Accessibility description
 * @param modifier Compose modifier
 * @param contentScale How to scale the image (default Crop)
 */
@Composable
fun FishImage(
        imageRef: ImageRef?,
        contentDescription: String?,
        modifier: Modifier = Modifier,
        contentScale: ContentScale = ContentScale.Crop,
) {
    FishImage(
            imageRef = imageRef,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
            alignment = Alignment.Center,
            colorFilter = null,
            filterQuality = FilterQuality.Low,
            placeholder = null,
            error = null,
            onState = null,
    )
}

/**
 * FishImage with loading/error composables.
 *
 * @param imageRef Image reference to load
 * @param contentDescription Accessibility description
 * @param modifier Compose modifier
 * @param contentScale How to scale the image (default Crop)
 * @param loading Content to show while loading
 * @param error Content to show on error
 */
@Composable
fun FishImageWithStates(
        imageRef: ImageRef?,
        contentDescription: String?,
        modifier: Modifier = Modifier,
        contentScale: ContentScale = ContentScale.Crop,
        loading: @Composable () -> Unit = {},
        error: @Composable () -> Unit = {},
) {
    FishImage(
            imageRef = imageRef,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
            placeholder = loading,
            error = error,
    )
}

/**
 * FishImage with tiered loading using minithumbnail as blur placeholder.
 *
 * **Netflix-style progressive loading:**
 * 1. Instantly shows blurred minithumbnail (~40x40px inline JPEG) from memory
 * 2. Loads full thumbnail (~320px) in background
 * 3. Crossfades from blur to full when loaded
 *
 * **Usage:**
 * ```kotlin
 * FishImageTiered(
 *     placeholderRef = metadata.placeholderThumbnail,  // InlineBytes from minithumbnail
 *     imageRef = metadata.poster,                       // Full thumbnail/poster
 *     contentDescription = "Movie poster",
 *     modifier = Modifier.fillMaxSize(),
 * )
 * ```
 *
 * @param placeholderRef Minithumbnail for instant blur placeholder (typically InlineBytes)
 * @param imageRef Full image to load
 * @param contentDescription Accessibility description
 * @param modifier Compose modifier
 * @param contentScale How to scale the image (default Crop)
 * @param blurRadius Blur radius for placeholder (default 8dp for natural blur-up effect)
 * @param error Content to show on error (when both placeholder and main fail)
 */
@Composable
fun FishImageTiered(
        placeholderRef: ImageRef?,
        imageRef: ImageRef?,
        contentDescription: String?,
        modifier: Modifier = Modifier,
        contentScale: ContentScale = ContentScale.Crop,
        blurRadius: Int = 8,
        error: @Composable (() -> Unit)? = null,
) {
    // Track main image loading state
    var mainImageState by remember {
        mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty)
    }

    val mainImageLoaded = mainImageState is AsyncImagePainter.State.Success
    val mainImageFailed = mainImageState is AsyncImagePainter.State.Error

    val context = LocalPlatformContext.current

    Box(modifier = modifier) {
        // Layer 1: Blurred placeholder (shown instantly from memory, fades out when main loads)
        AnimatedVisibility(
                visible = !mainImageLoaded && placeholderRef != null,
                enter = fadeIn(),
                exit = fadeOut(),
        ) {
            if (placeholderRef != null) {
                val placeholderRequest = ImageRequest.Builder(context).data(placeholderRef).build()

                AsyncImage(
                        model = placeholderRequest,
                        contentDescription = null, // Decorative placeholder
                        modifier = Modifier.matchParentSize().blur(blurRadius.dp),
                        contentScale = contentScale,
                        filterQuality = FilterQuality.Low,
                )
            }
        }

        // Layer 2: Full image (single loader with crossfade built-in)
        if (imageRef != null) {
            val mainRequest =
                    ImageRequest.Builder(context)
                            .data(imageRef)
                            .apply {
                                val width = imageRef.preferredWidth
                                val height = imageRef.preferredHeight
                                if (width != null && height != null) {
                                    size(Size(width, height))
                                }
                            }
                            .build()

            // Use alpha to crossfade from placeholder to loaded image
            val alpha = if (mainImageLoaded) 1f else 0f

            AsyncImage(
                    model = mainRequest,
                    contentDescription = contentDescription,
                    modifier = Modifier.matchParentSize().graphicsLayer { this.alpha = alpha },
                    contentScale = contentScale,
                    filterQuality = FilterQuality.Low,
                    onState = { state -> mainImageState = state },
            )
        }

        // Layer 3: Error fallback (when both fail or main is null)
        if ((imageRef == null || mainImageFailed) && placeholderRef == null && error != null) {
            error()
        }
    }
}
