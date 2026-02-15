package com.fishit.player.core.model

/**
 * Marks a class as a pipeline/transport/normalizer component for
 * automatic documentation generation.
 *
 * The doc-generator tool scans source files for this annotation and
 * extracts layer contracts, naming patterns, and responsibilities.
 *
 * ## Usage
 *
 * ```kotlin
 * /**
 *  * @responsibility Fetch streams via Xtream API
 *  * @responsibility Handle authentication
 *  */
 * @PipelineComponent(
 *     layer = Layer.TRANSPORT,
 *     sourceType = "Xtream",
 *     genericPattern = "{Source}ApiClient"
 * )
 * class DefaultXtreamApiClient(...) : XtreamApiClient { ... }
 * ```
 *
 * ## Supported Layers
 *
 * - **TRANSPORT** — `infra/transport-xtream`, `infra/transport-telegram`
 * - **PIPELINE** — `pipeline/xtream`, `pipeline/telegram`
 * - **NORMALIZER** — `core/metadata-normalizer`
 * - **PERSISTENCE** — `core/persistence`, `infra/data-nx`
 *
 * @property layer The architectural layer this component belongs to
 * @property sourceType The source type (e.g., "Xtream", "Telegram")
 * @property genericPattern The generic naming pattern using `{Source}` placeholder
 *
 * @see com.fishit.player.tools.docgen.ContractExtractor
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class PipelineComponent(
    val layer: Layer,
    val sourceType: String,
    val genericPattern: String,
)

/**
 * Architectural layers for pipeline component classification.
 *
 * Used with [PipelineComponent] to categorize components by their
 * position in the architecture hierarchy.
 */
enum class Layer {
    /** Network/API integration (TDLib, OkHttp, Xtream API) */
    TRANSPORT,

    /** Catalog sync pipelines (scan, map, emit) */
    PIPELINE,

    /** Metadata normalization (title cleanup, TMDB lookup) */
    NORMALIZER,

    /** Data persistence (ObjectBox, repositories) */
    PERSISTENCE,
}
