package com.fishit.player.pipeline.telegram.mapper

import com.fishit.player.infra.transport.telegram.api.TgContent
import com.fishit.player.infra.transport.telegram.api.TgFile
import com.fishit.player.infra.transport.telegram.api.TgMessage
import com.fishit.player.infra.transport.telegram.api.TgMinithumbnail
import com.fishit.player.infra.transport.telegram.api.TgPhotoSize
import com.fishit.player.infra.transport.telegram.api.TgThumbnail
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Pipeline-layer test to verify transport-layer wrapper DTOs are instantiable.
 *
 * Contract:
 * - Pipeline must NOT import TDLib / g000sha256 DTOs directly.
 * - Transport is the only layer that sees TDLib.
 */
class DtoConstructorTest {

    @Test
    fun `transport wrapper DTOs can be constructed`() {
        val file = TgFile(id = 1, remoteId = "remote:1")
        assertEquals(1, file.fileId)

        val content =
            TgContent.Video(
                fileId = 1,
                remoteId = "remote:1",
                fileName = "video.mp4",
                mimeType = "video/mp4",
                duration = 120,
                width = 1920,
                height = 1080,
                fileSize = 123_456L,
                supportsStreaming = true,
                caption = "test",
                thumbnail = TgThumbnail(fileId = 2, remoteId = "remote:2", width = 320, height = 180, fileSize = 2_000),
                minithumbnail = TgMinithumbnail(width = 40, height = 40, data = byteArrayOf(1, 2, 3)),
            )

        val message =
            TgMessage(
                messageId = 100,
                chatId = 200,
                date = 1_700_000_000,
                content = content,
            )

        assertNotNull(message)
        assertEquals(100, message.id)
        assertNotNull((message.content as TgContent.Video).thumbnail)

        val photo = TgContent.Photo(
            sizes = listOf(TgPhotoSize(fileId = 3, remoteId = "remote:3", width = 1280, height = 720, fileSize = 10_000)),
            caption = null,
            minithumbnail = null,
        )
        assertEquals(1, photo.sizes.size)
    }
}
