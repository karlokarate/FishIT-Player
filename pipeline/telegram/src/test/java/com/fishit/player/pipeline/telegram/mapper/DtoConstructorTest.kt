package com.fishit.player.pipeline.telegram.mapper

import dev.g000sha256.tdl.dto.*
import org.junit.Test
import kotlin.test.assertNotNull

/**
 * Test to verify g000sha256 DTO constructor availability.
 */
class DtoConstructorTest {

    @Test
    fun `print all relevant DTO constructors`() {
        val classes = listOf(
            LocalFile::class.java,
            RemoteFile::class.java,
            File::class.java,
            Video::class.java,
            Audio::class.java,
            Document::class.java,
            Photo::class.java,
            PhotoSize::class.java,
            FormattedText::class.java,
            Message::class.java,
            MessageVideo::class.java,
            MessageAudio::class.java,
            MessageDocument::class.java,
            MessagePhoto::class.java,
            MessageSenderUser::class.java,
        )
        
        classes.forEach { clazz ->
            println("\n${clazz.simpleName} constructors:")
            clazz.constructors.forEach { c ->
                println("  ${c.parameterCount} params: ${c.parameterTypes.map { it.simpleName }}")
            }
        }
    }
}
