package com.chris.m3usuite.player.internal.live

import com.chris.m3usuite.data.repo.EpgRepository

/**
 * Default implementation of [LiveEpgRepository] that bridges to the existing [EpgRepository].
 *
 * This implementation wraps the existing [EpgRepository] class and delegates EPG queries
 * to it, converting the results to the domain model expected by [LivePlaybackController].
 *
 * **Phase 3 Step 3.B**: This repository is instantiated in [rememberInternalPlayerSession]
 * when [PlaybackContext.type] == [PlaybackType.LIVE].
 *
 * @param epgRepository The existing EPG repository from the data layer.
 */
class DefaultLiveEpgRepository(
    private val epgRepository: EpgRepository,
) : LiveEpgRepository {
    /**
     * Fetches the now/next program titles for a channel.
     *
     * **Implementation notes:**
     * - Delegates to [EpgRepository.nowNext]
     * - [streamId] is Int (matching [ObxLive.streamId] field)
     * - Returns pair of (nowTitle, nextTitle) with nulls for unavailable data
     * - Extracts titles from the first two [XtShortEPGProgramme] entries
     *
     * @param streamId The channel's stream ID (Int, matching ObxLive.streamId).
     * @return Pair of (nowTitle, nextTitle), with nulls for unavailable data.
     */
    override suspend fun getNowNext(streamId: Int): Pair<String?, String?> =
        try {
            val programs = epgRepository.nowNext(streamId, limit = 2)
            val nowTitle = programs.getOrNull(0)?.title
            val nextTitle = programs.getOrNull(1)?.title
            nowTitle to nextTitle
        } catch (e: Throwable) {
            // Fail-safe: Return null pair on any error
            null to null
        }
}
