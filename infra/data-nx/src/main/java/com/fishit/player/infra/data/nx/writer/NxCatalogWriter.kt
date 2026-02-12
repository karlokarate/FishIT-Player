private fun buildSourceKey(raw: RawMediaMetadata, accountKey: String): String {
    val identifier = accountKey.removePrefix("${raw.sourceType.name.lowercase()}:" )
    // FIX: Strip sourceType prefix from sourceId too.
    // XtreamIdCodec produces "xtream:series:1418", but SourceKeyParser.buildSourceKey already prepends "src:xtream:", resulting in "src:xtream:account:xtream:series:1418".
    // We need just the kind:key part, e.g. "series:1418".
    val cleanSourceId = raw.sourceId.removePrefix("${raw.sourceType.name.lowercase()}:" )
    return SourceKeyParser.buildSourceKey(raw.sourceType, identifier, cleanSourceId)
}