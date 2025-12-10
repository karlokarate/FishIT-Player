package com.fishit.player.pipeline.telegram.tdlib

import com.fishit.player.pipeline.telegram.mapper.TdlibTestFixtures
import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.AuthorizationStateReady
import dev.g000sha256.tdl.dto.AuthorizationStateWaitCode
import dev.g000sha256.tdl.dto.AuthorizationStateWaitPassword
import dev.g000sha256.tdl.dto.AuthorizationStateWaitPhoneNumber
import dev.g000sha256.tdl.dto.Chat
import dev.g000sha256.tdl.dto.ChatListMain
import dev.g000sha256.tdl.dto.ChatTypePrivate
import dev.g000sha256.tdl.dto.Chats
import dev.g000sha256.tdl.dto.File
import dev.g000sha256.tdl.dto.FormattedText
import dev.g000sha256.tdl.dto.LocalFile
import dev.g000sha256.tdl.dto.Message
import dev.g000sha256.tdl.dto.MessageSenderUser
import dev.g000sha256.tdl.dto.MessageVideo
import dev.g000sha256.tdl.dto.Messages
import dev.g000sha256.tdl.dto.RemoteFile
import dev.g000sha256.tdl.dto.Video
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

/**
 * Unit Tests für DefaultTelegramClient.
 *
 * Tests TDLib client wrapper functionality using MockK mocks. Verifies:
 * - Authorization flow handling
 * - Chat fetching
 * - Media message fetching
 * - File resolution and download requests
 * - Error handling and retries
 */
class DefaultTelegramClientTest {

    private lateinit var mockTdlClient: TdlClient
    private lateinit var mockProvider: TdlibClientProvider
    private lateinit var client: DefaultTelegramClient

    @Before
    fun setup() {
        mockTdlClient = mockk(relaxed = true)
        mockProvider = mockk {
            every { isInitialized } returns true
            every { getClient() } returns mockTdlClient
        }
        client = DefaultTelegramClient(mockProvider)
    }

    // ========== Test Helpers for TdlResult ==========
    
    /**
     * Creates a mocked TdlResult.Success wrapping the given value.
     * 
     * Uses mockk because TdlResult.Success has a private constructor.
     */
    private inline fun <reified T : Any> successResult(value: T): TdlResult<T> {
        return mockk<TdlResult.Success<T>> {
            every { result } returns value
        }
    }
    
    /**
     * Creates a mocked TdlResult.Failure with the given error code and message.
     */
    private fun failureResult(code: Int, message: String): TdlResult<Nothing> {
        return mockk<TdlResult.Failure> {
            every { this@mockk.code } returns code
            every { this@mockk.message } returns message
        }
    }

    // ========== Authorization Tests ==========

    @Test
    fun `ensureAuthorized succeeds when state is Ready`() = runTest {
        val readyState = mockk<AuthorizationStateReady>()
        coEvery { mockProvider.initialize() } returns Unit
        coEvery { mockTdlClient.getAuthorizationState() } returns successResult(readyState)

        client.ensureAuthorized()

        // Should complete without throwing
        val authState = client.authState.first()
        assertEquals(TelegramAuthState.Ready, authState)
    }

    @Test
    fun `ensureAuthorized throws when waiting for phone`() = runTest {
        val waitPhoneState = mockk<AuthorizationStateWaitPhoneNumber>()
        coEvery { mockProvider.initialize() } returns Unit
        coEvery { mockTdlClient.getAuthorizationState() } returns successResult(waitPhoneState)

        assertFailsWith<TelegramAuthException> { client.ensureAuthorized() }

        val authState = client.authState.first()
        assertEquals(TelegramAuthState.WaitingForPhone, authState)
    }

    @Test
    fun `ensureAuthorized throws when waiting for code`() = runTest {
        val waitCodeState = mockk<AuthorizationStateWaitCode>()
        coEvery { mockProvider.initialize() } returns Unit
        coEvery { mockTdlClient.getAuthorizationState() } returns successResult(waitCodeState)

        assertFailsWith<TelegramAuthException> { client.ensureAuthorized() }

        val authState = client.authState.first()
        assertEquals(TelegramAuthState.WaitingForCode, authState)
    }

    @Test
    fun `ensureAuthorized throws when waiting for password`() = runTest {
        val waitPasswordState = mockk<AuthorizationStateWaitPassword>()
        coEvery { mockProvider.initialize() } returns Unit
        coEvery { mockTdlClient.getAuthorizationState() } returns
                successResult(waitPasswordState)

        assertFailsWith<TelegramAuthException> { client.ensureAuthorized() }

        val authState = client.authState.first()
        assertEquals(TelegramAuthState.WaitingForPassword, authState)
    }

    @Test
    fun `ensureAuthorized throws on TDLib error`() = runTest {
        coEvery { mockProvider.initialize() } returns Unit
        coEvery { mockTdlClient.getAuthorizationState() } returns
                failureResult(401, "Unauthorized")

        assertFailsWith<TelegramAuthException> { client.ensureAuthorized() }
    }

    @Test
    fun `ensureAuthorized initializes provider when not initialized`() = runTest {
        every { mockProvider.isInitialized } returns false
        val readyState = mockk<AuthorizationStateReady>()
        coEvery { mockProvider.initialize() } returns Unit
        coEvery { mockTdlClient.getAuthorizationState() } returns successResult(readyState)

        client.ensureAuthorized()

        coVerify { mockProvider.initialize() }
    }

    // ========== getChats Tests ==========

    @Test
    fun `getChats returns list of chat info`() = runTest {
        val chatIds = longArrayOf(1001L, 1002L)
        val chatsResult = mockk<Chats> { every { this@mockk.chatIds } returns chatIds }

        val chat1 = createMockChat(1001L, "Chat One", ChatTypePrivate(123L))
        val chat2 = createMockChat(1002L, "Chat Two", ChatTypePrivate(456L))

        coEvery { mockTdlClient.getChats(any<ChatListMain>(), any()) } returns
                successResult(chatsResult)
        coEvery { mockTdlClient.getChat(1001L) } returns successResult(chat1)
        coEvery { mockTdlClient.getChat(1002L) } returns successResult(chat2)

        val result = client.getChats(limit = 10)

        assertEquals(2, result.size)
        assertEquals(1001L, result[0].chatId)
        assertEquals("Chat One", result[0].title)
        assertEquals("private", result[0].type)
        assertEquals(1002L, result[1].chatId)
        assertEquals("Chat Two", result[1].title)
    }

    @Test
    fun `getChats handles individual chat fetch failure gracefully`() = runTest {
        val chatIds = longArrayOf(1001L, 1002L)
        val chatsResult = mockk<Chats> { every { this@mockk.chatIds } returns chatIds }

        val chat1 = createMockChat(1001L, "Chat One", ChatTypePrivate(123L))

        coEvery { mockTdlClient.getChats(any<ChatListMain>(), any()) } returns
                successResult(chatsResult)
        coEvery { mockTdlClient.getChat(1001L) } returns successResult(chat1)
        coEvery { mockTdlClient.getChat(1002L) } returns failureResult(404, "Not found")

        val result = client.getChats(limit = 10)

        // Should still return chat1, even though chat2 failed
        assertEquals(1, result.size)
        assertEquals(1001L, result[0].chatId)
    }

    // ========== fetchMediaMessages Tests ==========

    @Test
    fun `fetchMediaMessages returns media items from chat`() = runTest {
        val messages =
                createMockVideoMessages(chatId = 1001L, messageIds = listOf(100L, 101L, 102L))
        val messagesResult =
                mockk<Messages> { every { this@mockk.messages } returns messages.toTypedArray() }

        coEvery {
            mockTdlClient.getChatHistory(
                    chatId = 1001L,
                    fromMessageId = 0L,
                    offset = 0,
                    limit = 100,
                    onlyLocal = false
            )
        } returns successResult(messagesResult)

        val result = client.fetchMediaMessages(chatId = 1001L, limit = 100, offsetMessageId = 0)

        assertEquals(3, result.size)
        assertTrue(result.all { it.chatId == 1001L })
    }

    @Test
    fun `fetchMediaMessages uses correct offset for pagination`() = runTest {
        val emptyMessages = mockk<Messages> { every { messages } returns emptyArray() }

        coEvery {
            mockTdlClient.getChatHistory(
                    chatId = 1001L,
                    fromMessageId = 500L,
                    offset = -1, // Should be -1 for subsequent pages
                    limit = 50,
                    onlyLocal = false
            )
        } returns successResult(emptyMessages)

        client.fetchMediaMessages(chatId = 1001L, limit = 50, offsetMessageId = 500L)

        coVerify {
            mockTdlClient.getChatHistory(
                    chatId = 1001L,
                    fromMessageId = 500L,
                    offset = -1,
                    limit = 50,
                    onlyLocal = false
            )
        }
    }

    @Test
    fun `fetchMediaMessages limits to max 100 per TDLib`() = runTest {
        val emptyMessages = mockk<Messages> { every { messages } returns emptyArray() }

        coEvery {
            mockTdlClient.getChatHistory(
                    chatId = 1001L,
                    fromMessageId = 0L,
                    offset = 0,
                    limit = 100, // Should be capped at 100
                    onlyLocal = false
            )
        } returns successResult(emptyMessages)

        client.fetchMediaMessages(chatId = 1001L, limit = 500, offsetMessageId = 0)

        coVerify {
            mockTdlClient.getChatHistory(
                    chatId = 1001L,
                    fromMessageId = 0L,
                    offset = 0,
                    limit = 100, // Verify it was capped
                    onlyLocal = false
            )
        }
    }

    // ========== fetchAllMediaMessages Tests ==========

    @Test
    fun `fetchAllMediaMessages aggregates from multiple chats`() = runTest {
        val messages1 = createMockVideoMessages(chatId = 1001L, messageIds = listOf(100L))
        val messages2 = createMockVideoMessages(chatId = 1002L, messageIds = listOf(200L, 201L))

        val messagesResult1 =
                mockk<Messages> { every { messages } returns messages1.toTypedArray() }
        val messagesResult2 =
                mockk<Messages> { every { messages } returns messages2.toTypedArray() }

        coEvery { mockTdlClient.getChatHistory(chatId = 1001L, any(), any(), any(), any()) } returns
                successResult(messagesResult1)
        coEvery { mockTdlClient.getChatHistory(chatId = 1002L, any(), any(), any(), any()) } returns
                successResult(messagesResult2)

        val result = client.fetchAllMediaMessages(chatIds = listOf(1001L, 1002L), limit = 100)

        assertEquals(3, result.size)
    }

    @Test
    fun `fetchAllMediaMessages handles chat failure gracefully`() = runTest {
        val messages1 = createMockVideoMessages(chatId = 1001L, messageIds = listOf(100L))
        val messagesResult1 =
                mockk<Messages> { every { messages } returns messages1.toTypedArray() }

        coEvery { mockTdlClient.getChatHistory(chatId = 1001L, any(), any(), any(), any()) } returns
                successResult(messagesResult1)
        coEvery { mockTdlClient.getChatHistory(chatId = 1002L, any(), any(), any(), any()) } returns
                failureResult(500, "Server error")

        val result = client.fetchAllMediaMessages(chatIds = listOf(1001L, 1002L), limit = 100)

        // Should still return messages from chat1
        assertEquals(1, result.size)
        assertEquals(1001L, result[0].chatId)
    }

    // ========== File Resolution Tests ==========

    @Test
    fun `resolveFileLocation returns file details`() = runTest {
        val file =
                createMockFile(
                        fileId = 999,
                        remoteId = "remote_abc",
                        uniqueId = "unique_xyz",
                        size = 1500000000L,
                        downloadedSize = 500000000L,
                        isDownloadingActive = true,
                        isDownloadingCompleted = false,
                        localPath = "/storage/telegram/temp"
                )

        coEvery { mockTdlClient.getFile(999) } returns successResult(file)

        val result = client.resolveFileLocation(fileId = 999)

        assertEquals(999, result.fileId)
        assertEquals("remote_abc", result.remoteId)
        assertEquals("unique_xyz", result.uniqueId)
        assertEquals(1500000000L, result.size)
        assertEquals(500000000L, result.downloadedSize)
        assertTrue(result.isDownloadingActive)
    }

    @Test
    fun `resolveFileByRemoteId returns file ID`() = runTest {
        val file = createMockFile(fileId = 888)

        coEvery { mockTdlClient.getRemoteFile("remote_abc", null) } returns successResult(file)

        val result = client.resolveFileByRemoteId("remote_abc")

        assertEquals(888, result)
    }

    // ========== Download Request Tests ==========

    @Test
    fun `requestFileDownload returns file location`() = runTest {
        val file =
                createMockFile(
                        fileId = 777,
                        remoteId = "download_remote",
                        uniqueId = "download_unique",
                        size = 2000000000L,
                        downloadedSize = 0L,
                        isDownloadingActive = true,
                        isDownloadingCompleted = false
                )

        coEvery {
            mockTdlClient.downloadFile(
                    fileId = 777,
                    priority = 16,
                    offset = 0,
                    limit = 0,
                    synchronous = false
            )
        } returns successResult(file)

        val result = client.requestFileDownload(fileId = 777, priority = 16)

        assertEquals(777, result.fileId)
        assertEquals("download_remote", result.remoteId)
        assertTrue(result.isDownloadingActive)
    }

    // ========== Lifecycle Tests ==========

    @Test
    fun `close resets state`() = runTest {
        // First set up some state
        val readyState = mockk<AuthorizationStateReady>()
        coEvery { mockProvider.initialize() } returns Unit
        coEvery { mockTdlClient.getAuthorizationState() } returns successResult(readyState)
        client.ensureAuthorized()

        // Now close
        client.close()

        val authState = client.authState.first()
        val connectionState = client.connectionState.first()

        assertEquals(TelegramAuthState.Idle, authState)
        assertEquals(TelegramConnectionState.Disconnected, connectionState)
    }

    // ========== Helper Factory Methods ==========

    private fun createMockChat(
            chatId: Long,
            title: String,
            type: dev.g000sha256.tdl.dto.ChatType
    ): Chat {
        return mockk {
            every { id } returns chatId
            every { this@mockk.title } returns title
            every { this@mockk.type } returns type
            every { photo } returns null
        }
    }

    /**
     * Create real Video Messages using TdlibTestFixtures.
     * 
     * Uses real g000sha256 DTOs instead of mocks since they are final data classes.
     */
    private fun createMockVideoMessages(chatId: Long, messageIds: List<Long>): List<Message> {
        return messageIds.map { msgId ->
            TdlibTestFixtures.createVideoMessage(
                messageId = msgId,
                chatId = chatId,
                fileName = "video_$msgId.mp4",
                caption = "Caption for $msgId",
                remoteId = "remote_$msgId",
                uniqueId = "unique_$msgId"
            )
        }
    }

    /**
     * Create a real File DTO using TdlibTestFixtures.
     */
    private fun createMockFile(
            fileId: Int,
            remoteId: String = "remote_default",
            uniqueId: String = "unique_default",
            size: Long = 1000000L,
            downloadedSize: Long = 0L,
            isDownloadingActive: Boolean = false,
            isDownloadingCompleted: Boolean = false,
            localPath: String = ""
    ): File {
        val localFile = TdlibTestFixtures.createLocalFile(
            path = localPath,
            isDownloadingActive = isDownloadingActive,
            isDownloadingCompleted = isDownloadingCompleted,
            downloadedSize = downloadedSize
        )
        val remoteFile = TdlibTestFixtures.createRemoteFile(
            id = remoteId,
            uniqueId = uniqueId
        )
        return TdlibTestFixtures.createFile(
            id = fileId,
            size = size,
            expectedSize = size,
            local = localFile,
            remote = remoteFile
        )
    }
}
