package com.fishit.player.core.model

import com.fishit.player.core.model.ids.PipelineItemId

/**
 * Uniquely identifies a media source within a specific pipeline.
 *
 * Combines the pipeline identifier with a pipeline-local source ID to create a globally unique
 * reference to a specific variant of a media item.
 *
 * Examples:
 * - `SourceKey(TELEGRAM, "12345:67890")` - Telegram message with remoteId or chatId:messageId
 * - `SourceKey(XTREAM, "vod:12345")` - Xtream VOD stream
 * - `SourceKey(XTREAM, "episode:123:456")` - Xtream series episode
 *
 * @property pipeline The pipeline that produced this source
 * @property sourceId Pipeline-local unique identifier (format varies by pipeline)
 */
data class SourceKey(
    val pipeline: PipelineIdTag,
    val sourceId: PipelineItemId,
) {
    /**
     * Serialized form for storage/comparison.
     *
     * Format: `<pipeline_code>:<sourceId>` Example: `"tg:12345:67890"`, `"xc:vod:12345"`
     */
    fun toSerializedString(): String = "${pipeline.code}:${sourceId.value}"

    companion object {
        /**
         * Parse a serialized SourceKey string.
         *
         * @param serialized String in format `<pipeline_code>:<sourceId>`
         * @return Parsed SourceKey or null if invalid format
         */
        fun fromSerializedString(serialized: String): SourceKey? {
            val firstColon = serialized.indexOf(':')
            if (firstColon <= 0 || firstColon >= serialized.length - 1) return null

            val pipelineCode = serialized.substring(0, firstColon)
            val sourceId = serialized.substring(firstColon + 1)

            return SourceKey(
                pipeline = PipelineIdTag.fromCode(pipelineCode),
                sourceId = PipelineItemId(sourceId),
            )
        }
    }
}
