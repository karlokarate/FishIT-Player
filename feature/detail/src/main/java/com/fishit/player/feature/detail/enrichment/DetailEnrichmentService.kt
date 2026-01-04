package com.fishit.player.feature.detail.enrichment

@Deprecated(
    message =
        "Moved behind core/detail-domain interface. Inject com.fishit.player.core.detail.domain.DetailEnrichmentService instead.",
    replaceWith = ReplaceWith("com.fishit.player.core.detail.domain.DetailEnrichmentService"),
)
typealias DetailEnrichmentService = com.fishit.player.core.detail.domain.DetailEnrichmentService
