package tdltest

import dev.g000sha256.tdl.dto.Chat
import dev.g000sha256.tdl.dto.ChatListMain
import dev.g000sha256.tdl.dto.Message
import dev.g000sha256.tdl.dto.SearchMessagesFilterEmpty
import dev.g000sha256.tdl.dto.MessageDocument
import dev.g000sha256.tdl.dto.MessagePhoto
import dev.g000sha256.tdl.dto.MessageText
import dev.g000sha256.tdl.dto.MessageVideo
import dev.g000sha256.tdl.dto.AuthorizationStateReady
import dev.g000sha256.tdl.dto.AuthorizationStateWaitTdlibParameters
import dev.g000sha256.tdl.dto.AuthorizationStateWaitPhoneNumber
import dev.g000sha256.tdl.dto.AuthorizationStateWaitCode
import dev.g000sha256.tdl.dto.AuthorizationStateWaitPassword
import dev.g000sha256.tdl.dto.AuthorizationStateWaitEmailAddress
import dev.g000sha256.tdl.dto.AuthorizationStateWaitEmailCode
import dev.g000sha256.tdl.dto.AuthorizationStateWaitOtherDeviceConfirmation
import dev.g000sha256.tdl.dto.AuthorizationStateWaitPremiumPurchase
import dev.g000sha256.tdl.dto.AuthorizationStateWaitRegistration
import dev.g000sha256.tdl.dto.AuthorizationStateLoggingOut
import dev.g000sha256.tdl.dto.AuthorizationStateClosing
import dev.g000sha256.tdl.dto.AuthorizationStateClosed
import dev.g000sha256.tdl.dto.TextEntityTypeTextUrl
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.delay
import java.util.Locale
import java.time.Instant
import java.io.File

/**
 * Minimaler Dump der Chats/Nachrichten ohne UI-Filter oder Paging-Interaktion.
 * Zeigt Messages so, wie sie direkt aus TDLib kommen.
 */
class ChatBrowser(
    private val session: TelegramSession
) {

    private val client get() = session.client
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    suspend fun dumpChatsWithMessages(maxChats: Int = 500, maxMessagesPerChat: Int = 200, raw: Boolean = true) {
        println("\n[CHAT] Lade Chats (ungefiltert, max $maxChats)...")

        val chatsResult = client.getChats(ChatListMain(), maxChats).getOrThrow()
        val chatIds: LongArray = chatsResult.chatIds ?: LongArray(0)
        DebugLog.log("dumpChatsWithMessages: requested=$maxChats receivedIds=${chatIds.size}")

        val chats = mutableListOf<Chat>()
        for (id in chatIds) {
            try {
                val chat = client.getChat(id).getOrThrow()
                chats += chat
                DebugLog.log("getChat ok id=$id title='${chat.title}'")
            } catch (t: Throwable) {
                println("[CHAT] Fehler beim Laden von Chat $id: ${t.message}")
                DebugLog.log("getChat fail id=$id error='${t.message}'")
            }
        }

        if (chats.isEmpty()) {
            println("[CHAT] Keine Chats vorhanden.")
            return
        }

        println("[CHAT] ${chats.size} Chats geladen. Dump bis zu $maxMessagesPerChat Nachrichten je Chat.")

        // Sammle Übersicht (id + title) und schreibe später in Datei
        val summary = mutableListOf<String>()
        for (chat in chats) {
            summary += "${chat.id} | ${chat.title}"
            dumpChat(chat, maxMessagesPerChat, raw)
        }

        val summaryFile = java.io.File("/tmp/tdl-cli/chat_summary.txt")
        val summaryText = summary.joinToString(separator = "\n")
        summaryFile.writeText(summaryText)
        println("[CHAT] Übersicht geschrieben nach ${summaryFile.absolutePath}")
        println("[CHAT] Übersicht (Konsole):")
        summary.forEach { println(it) }
    }

    private suspend fun dumpChat(chat: Chat, maxMessages: Int, raw: Boolean) {
        println("\n=== Chat: ${chat.title} [id=${chat.id}] ===")
        DebugLog.log("dumpChat start chatId=${chat.id} title='${chat.title}' max=$maxMessages")

        val all = mutableListOf<Message>()
        val seen = mutableSetOf<Long>()
        var fromMessageId: Long = 0L
        val limit = 100

        while (all.size < maxMessages) {
            val batch = try {
                loadHistoryBlock(chat.id, fromMessageId, limit)
            } catch (t: Throwable) {
                DebugLog.log("dumpChat error chatId=${chat.id} from=$fromMessageId: ${t.message}")
                println("[CHAT] Fehler beim Laden von Nachrichten: ${t.message}")
                break
            }
            val unique = batch.filter { seen.add(it.id) }
            if (unique.isEmpty()) break
            all += unique
            val oldest = unique.minByOrNull { it.id } ?: break
            if (oldest.id == fromMessageId) break
            fromMessageId = oldest.id
            delay(200) // TDLib-friendly pacing
        }

        val slice = all.take(maxMessages)
        for (msg in slice) {
            if (raw) {
                println(renderRawMessage(msg))
            } else {
                println(renderCompact(msg))
            }
        }
    }

    /**
     * Interaktiver Browser: listet Chats, lässt Auswahl zu und zeigt Nachrichten (raw oder kompakt).
     */
    suspend fun browseChatsInteractive(maxChats: Int = 500, maxMessagesPerChat: Int = 200, rawDefault: Boolean = true) {
        println("\n[CHAT] Lade Chats für Browser (max $maxChats)...")
        val chatsResult = client.getChats(ChatListMain(), maxChats).getOrThrow()
        val chatIds: LongArray = chatsResult.chatIds ?: LongArray(0)
        DebugLog.log("browseChatsInteractive: requested=$maxChats receivedIds=${chatIds.size}")

        val chats = mutableListOf<Chat>()
        for (id in chatIds) {
            try {
                chats += client.getChat(id).getOrThrow()
                DebugLog.log("getChat ok id=$id")
            } catch (t: Throwable) {
                println("[CHAT] Fehler beim Laden von Chat $id: ${t.message}")
                DebugLog.log("getChat fail id=$id error='${t.message}'")
            }
        }
        if (chats.isEmpty()) {
            println("[CHAT] Keine Chats vorhanden.")
            return
        }

        var rawMode = rawDefault

        while (true) {
            println("\n=== Chat Browser (max $maxChats) ===")
            chats.forEachIndexed { index, chat ->
                println("${index + 1}) ${chat.title} [${chat.id}]")
            }
            println("0) Beenden, t) Toggle View (aktuell ${if (rawMode) "raw" else "compact"}), p<idx,...> Export (JSON, 25 Nachrichten), pa=alle exportieren")
            val choiceRaw = CliIo.readNonEmptyString("Auswahl: ").trim()
            val choice = choiceRaw.lowercase()
            if (choice == "t") {
                rawMode = !rawMode
                println("View gewechselt auf ${if (rawMode) "raw" else "compact"}.")
                continue
            }
            if (choice == "pa") {
                val all = (1..chats.size).toList()
                exportChatsByIndices(chats, all, maxMessagesPerChat = 25)
                continue
            }
            if (choice.startsWith("p")) {
                val parts = choice.removePrefix("p").split(",").mapNotNull { it.trim().toIntOrNull() }
                if (parts.isEmpty()) {
                    println("[EXPORT] Keine gültigen Indizes.")
                    continue
                }
                exportChatsByIndices(chats, parts, maxMessagesPerChat = 25)
                continue
            }
            val idx = choice.toIntOrNull()
            if (idx == null) {
                println("Ungültige Eingabe.")
                continue
            }
            if (idx == 0) return
            if (idx !in 1..chats.size) {
                println("Ungültige Auswahl.")
                continue
            }
            viewChatInteractive(chats[idx - 1], maxMessagesPerChat, rawMode)
        }
    }

    private suspend fun viewChatInteractive(chat: Chat, maxMessagesPerChat: Int, rawDefault: Boolean) {
        println("\n=== Chat: ${chat.title} [id=${chat.id}] (max $maxMessagesPerChat Messages) ===")

        val pages = mutableListOf<List<Message>>()
        val seen = mutableSetOf<Long>()
        var hasMore = true
        var currentPage = 0
        var rawMode = rawDefault
        var fromMessageId: Long = 0L
        val pageSize = 100
        val totalCount: Int? = try {
            // Best-effort Gesamtanzahl ermitteln (ohne Filter, serverseitig)
            client.getChatMessageCount(
                chatId = chat.id,
                filter = SearchMessagesFilterEmpty(),
                returnLocal = false
            ).getOrThrow().count
        } catch (_: Throwable) {
            null
        }
        totalCount?.let { println("[CHAT] TDLib totalCount (server-side): $it") }
        DebugLog.log("viewChatInteractive start chatId=${chat.id} title='${chat.title}' totalCount=${totalCount ?: -1} pageSize=$pageSize max=$maxMessagesPerChat")

        fun printPageHeader() {
            val totalLoaded = pages.size
            val totalPages = totalCount?.let { cnt ->
                val capped = cnt.coerceAtMost(maxMessagesPerChat)
                (capped + pageSize - 1) / pageSize
            }
            val totalInfo = when {
                totalPages != null -> "$totalPages"
                hasMore -> "$totalLoaded+"
                else -> "$totalLoaded"
            }
            println("\n--- Page ${currentPage + 1}/$totalInfo (mode=${if (rawMode) "raw" else "compact"}) ---")
            val state = session.authState.value
            val (color, label) = when (state) {
                is AuthorizationStateReady -> "\u001B[32m" to "READY"
                is AuthorizationStateWaitTdlibParameters,
                is AuthorizationStateWaitPhoneNumber,
                is AuthorizationStateWaitCode,
                is AuthorizationStateWaitPassword,
                is AuthorizationStateWaitEmailAddress,
                is AuthorizationStateWaitEmailCode,
                is AuthorizationStateWaitOtherDeviceConfirmation,
                is AuthorizationStateWaitPremiumPurchase,
                is AuthorizationStateWaitRegistration -> "\u001B[33m" to (state::class.simpleName ?: "WAIT")
                is AuthorizationStateLoggingOut,
                is AuthorizationStateClosing,
                is AuthorizationStateClosed -> "\u001B[31m" to (state::class.simpleName ?: "ERROR")
                else -> "\u001B[33m" to (state?.let { it::class.simpleName } ?: "UNKNOWN")
            }
            val reset = "\u001B[0m"
            println("AuthState: $color$label$reset")
        }

        suspend fun loadNextPage(): Boolean {
            if (!hasMore) return false
            val limit = pageSize
            val msgs = try {
                loadHistoryBlock(chat.id, fromMessageId, limit)
            } catch (t: Throwable) {
                hasMore = false
                DebugLog.log("loadNextPage error chatId=${chat.id} from=$fromMessageId limit=$limit: ${t.message}")
                println("[CHAT] Fehler beim Nachladen: ${t.message}")
                return false
            }
            val unique = msgs.filter { seen.add(it.id) }
            if (unique.isEmpty()) {
                hasMore = false
                DebugLog.log("loadNextPage chatId=${chat.id} from=$fromMessageId limit=$limit -> 0 msgs (hasMore=false)")
                return false
            }
            pages += unique
            val oldest = unique.minByOrNull { it.id }!!
            fromMessageId = oldest.id
            DebugLog.log(
                "loadNextPage chatId=${chat.id} from=$fromMessageId(limitAnchor) limit=$limit msgs=${unique.size} oldest=${unique.minOf { it.id }} newest=${unique.maxOf { it.id }} hasMore=$hasMore"
            )
            delay(200)
            return true
        }

        suspend fun ensurePage(index: Int): Boolean {
            while (pages.size <= index) {
                if (!loadNextPage()) return false
            }
            return true
        }

        // Load first page
        if (!ensurePage(0)) {
            println("[CHAT] Keine Nachrichten vorhanden.")
            return
        }

        while (true) {
            printPageHeader()
            val msgs = pages[currentPage]
            msgs.forEach { msg ->
                if (rawMode) {
                    println(renderRawMessage(msg))
                } else {
                    println(renderCompact(msg))
                }
            }
            println("\nCommands: n=next, p=prev, g<number>=goto page, t=toggle view, q=back")
            val cmd = CliIo.readNonEmptyString("> ").trim().lowercase()
            when {
                cmd == "n" -> {
                    val target = currentPage + 1
                    if (ensurePage(target)) {
                        currentPage = target
                    } else {
                        println("[CHAT] Keine weiteren Seiten.")
                    }
                }
                cmd == "p" -> {
                    if (currentPage > 0) {
                        currentPage--
                    } else {
                        println("[CHAT] Bereits auf der ersten Seite.")
                    }
                }
                cmd == "t" -> {
                    rawMode = !rawMode
                    println("[CHAT] View gewechselt auf ${if (rawMode) "raw" else "compact"}.")
                }
                cmd == "q" -> return
                cmd.startsWith("g") -> {
                    val num = cmd.removePrefix("g").toIntOrNull()
                    if (num == null || num <= 0) {
                        println("[CHAT] Ungültige Seitenzahl.")
                    } else {
                        val target = num - 1
                        if (ensurePage(target)) {
                            currentPage = target
                        } else {
                            println("[CHAT] Seite $num nicht verfügbar (Ende erreicht).")
                        }
                    }
                }
                else -> println("[CHAT] Unbekannter Befehl.")
            }
        }
    }

    private fun renderCompact(msg: Message): String {
        val type = msg.content::class.simpleName ?: "Unknown"
        val meta = when (val c = msg.content) {
            is MessageVideo -> {
                val name = c.video.fileName ?: ""
                val dur = c.video.duration
                val sizeMb = c.video.video?.size?.let { String.format("%.1f MB", it / 1024.0 / 1024.0) } ?: "n/a"
                "video name='$name' dur=${dur}s size=$sizeMb mime=${c.video.mimeType}"
            }
            is MessageDocument -> {
                val name = c.document.fileName ?: ""
                val sizeMb = c.document.document?.size?.let { String.format("%.1f MB", it / 1024.0 / 1024.0) } ?: "n/a"
                "document name='$name' size=$sizeMb mime=${c.document.mimeType}"
            }
            is MessagePhoto -> {
                val sizes = c.photo.sizes?.maxByOrNull { it.width * it.height }
                val sizeMb = sizes?.photo?.size?.let { String.format("%.1f MB", it / 1024.0 / 1024.0) } ?: "n/a"
                "photo ${sizes?.width}x${sizes?.height} size=$sizeMb"
            }
            is MessageText -> {
                val preview = c.text?.text?.take(120)?.replace("\n", " ") ?: ""
                "text '$preview'"
            }
            else -> ""
        }
        return "#${msg.id} [${msg.date}] sender=${msg.senderId} type=$type ${if (meta.isNotEmpty()) "meta=$meta" else ""}".trim()
    }

    private fun renderRawMessage(msg: Message): String =
        when (val content = msg.content) {
            is MessageVideo -> gson.toJson(toSimpleMessageVideo(msg, content))
            is MessagePhoto -> gson.toJson(toSimpleMessagePhoto(msg, content))
            is MessageText -> gson.toJson(toSimpleMessageText(msg, content))
            else -> gson.toJson(msg)
        }

    private suspend fun loadHistoryBlock(
        chatId: Long,
        fromMessageId: Long,
        limit: Int,
    ): List<Message> {
        val cappedLimit = limit.coerceAtMost(100)
        // TDLib-Empfehlung: Erste Seite from=0, offset=0; Folgepages from=ältesteVorher, offset=-1
        repeat(5) { attempt ->
            val offset = if (fromMessageId == 0L) 0 else -1
            DebugLog.log("getChatHistory attempt=${attempt + 1} chatId=$chatId from=$fromMessageId offset=$offset limit=$cappedLimit")
            val history = client.getChatHistory(
                chatId = chatId,
                fromMessageId = fromMessageId,
                offset = offset,
                limit = cappedLimit,
                onlyLocal = false,
            )
            when (history) {
                is dev.g000sha256.tdl.TdlResult.Failure -> {
                    DebugLog.log("getChatHistory error chatId=$chatId from=$fromMessageId offset=$offset code=${history.code} msg=${history.message}")
                    throw RuntimeException("TDLib error ${history.code}: ${history.message}")
                }
                is dev.g000sha256.tdl.TdlResult.Success -> {
                    val msgs = (history.result.messages ?: emptyArray()).filterNotNull()
                    if (msgs.isNotEmpty()) {
                        val ids = msgs.map { it.id }
                        DebugLog.log("getChatHistory ok chatId=$chatId from=$fromMessageId offset=$offset size=${msgs.size} oldest=${ids.minOrNull()} newest=${ids.maxOrNull()}")
                        return msgs
                    }
                }
            }
            if (attempt < 4) {
                val waitMs = 200L + attempt * 100L
                println("[CHAT] Keine Nachrichten erhalten (Attempt ${attempt + 1}/5), warte ${waitMs}ms …")
                DebugLog.log("getChatHistory empty chatId=$chatId from=$fromMessageId offset=$offset retry in ${waitMs}ms")
                delay(waitMs)
            }
        }
        DebugLog.log("getChatHistory failed chatId=$chatId from=$fromMessageId (no messages after retries)")
        return emptyList()
    }

    private fun toSimpleMessageVideo(msg: Message, content: MessageVideo): SimpleMessageVideo {
        val videoFile = content.video.video
        val thumbnail = content.video.thumbnail
        return SimpleMessageVideo(
            id = msg.id,
            chatId = msg.chatId,
            date = msg.date,
            dateIso = msg.date?.let { epoch -> java.time.Instant.ofEpochSecond(epoch.toLong()).toString() },
            content = SimpleVideoContent(
                duration = content.video.duration,
                width = content.video.width,
                height = content.video.height,
                fileName = content.video.fileName,
                mimeType = content.video.mimeType,
                supportsStreaming = content.video.supportsStreaming,
                file = videoFile?.let {
                    SimpleRemoteFile(
                        id = it.id?.toLong(),
                        size = it.size?.toLong(),
                        remoteId = it.remote?.id,
                        uniqueId = it.remote?.uniqueId,
                    )
                },
                thumbnail = thumbnail?.let { thumb ->
                    SimpleThumbnail(
                        width = thumb.width,
                        height = thumb.height,
                        file = thumb.file?.let { tf ->
                            SimpleRemoteFile(
                                id = tf.id?.toLong(),
                                size = tf.size?.toLong(),
                                remoteId = tf.remote?.id,
                                uniqueId = tf.remote?.uniqueId,
                            )
                        }
                    )
                },
                caption = content.caption?.text
            )
        )
    }

    private fun toSimpleMessagePhoto(msg: Message, content: MessagePhoto): SimpleMessagePhoto {
        val sizes = content.photo.sizes.orEmpty().filterNotNull().map {
            SimplePhotoSize(
                width = it.width,
                height = it.height,
                file = it.photo?.let { pf ->
                    SimpleRemoteFile(
                        id = pf.id?.toLong(),
                        size = pf.size?.toLong(),
                        remoteId = pf.remote?.id,
                        uniqueId = pf.remote?.uniqueId,
                    )
                }
            )
        }
        // Optional: größte Größe first
        val sortedSizes = sizes.sortedByDescending { (it.width ?: 0) * (it.height ?: 0) }
        return SimpleMessagePhoto(
            id = msg.id,
            chatId = msg.chatId,
            date = msg.date,
            dateIso = msg.date?.let { epoch -> java.time.Instant.ofEpochSecond(epoch.toLong()).toString() },
            content = SimplePhotoContent(
                type = "photo",
                sizes = sortedSizes,
            )
        )
    }

    private fun toSimpleMessageText(msg: Message, content: MessageText): SimpleMessageText {
        val raw = content.text?.text.orEmpty()
        val lines = raw.split("\n")
        fun findValue(prefix: String): String? =
            lines.firstOrNull { it.startsWith(prefix, ignoreCase = true) }
                ?.substringAfter(prefix)
                ?.trim()
                ?.ifEmpty { null }

        val title = findValue("Titel:")
        val year = findValue("Erscheinungsjahr:")?.toIntOrNull()
        val lengthMinutes = findValue("Länge:")?.replace("Minuten", "", ignoreCase = true)?.trim()?.toIntOrNull()
        val fsk = findValue("FSK:")?.toIntOrNull()
        val collection = findValue("Filmreihe:")
        val originalTitle = findValue("Originaltitel:")
        val productionCountry = findValue("Produktionsland:")
        val director = findValue("Regie:")
        val tmdbRating = findValue("TMDbRating:")?.toDoubleOrNull()
        val genres = findValue("Genres:")?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()

        val tmdbUrl = content.text?.entities
            ?.mapNotNull { entity ->
                when (val t = entity.type) {
                    is TextEntityTypeTextUrl -> t.url
                    else -> null
                }
            }
            ?.firstOrNull { it.contains("/movie/", ignoreCase = true) }

        return SimpleMessageText(
            id = msg.id,
            chatId = msg.chatId,
            date = msg.date,
            dateIso = msg.date?.let { epoch -> java.time.Instant.ofEpochSecond(epoch.toLong()).toString() },
            text = raw,
            title = title,
            year = year,
            lengthMinutes = lengthMinutes,
            fsk = fsk,
            collection = collection,
            originalTitle = originalTitle,
            productionCountry = productionCountry,
            director = director,
            tmdbRating = tmdbRating,
            genres = genres,
            tmdbUrl = tmdbUrl
        )
    }

    private suspend fun exportChatsByIndices(chats: List<Chat>, indices: List<Int>, maxMessagesPerChat: Int) {
        val exportDir = File("exports").apply { mkdirs() }
        println("[EXPORT] Starte Export für Indizes: $indices (je $maxMessagesPerChat Nachrichten).")
        for (idx in indices) {
            if (idx !in 1..chats.size) {
                println("[EXPORT] Index $idx ungültig, übersprungen.")
                continue
            }
            val chat = chats[idx - 1]
            try {
                val messages = loadLatestMessages(chat.id, maxMessagesPerChat)
                val jsonMessages = messages.map { msg ->
                    JsonParser.parseString(renderRawMessage(msg))
                }
                val obj = JsonObject().apply {
                    addProperty("chatId", chat.id)
                    addProperty("title", chat.title)
                    addProperty("exportedAt", Instant.now().toString())
                    addProperty("count", jsonMessages.size)
                    add("messages", gson.toJsonTree(jsonMessages))
                }
                val file = File(exportDir, "${chat.id}.json")
                file.writeText(gson.toJson(obj))
                println("[EXPORT] Chat '${chat.title}' -> ${file.absolutePath} (${jsonMessages.size} Nachrichten).")
            } catch (t: Throwable) {
                println("[EXPORT] Fehler bei Chat '${chat.title}': ${t.message}")
                DebugLog.log("exportChats error chatId=${chat.id}: ${t.message}")
            }
            delay(200)
        }
        println("[EXPORT] Fertig.")
    }

    private suspend fun loadLatestMessages(chatId: Long, max: Int): List<Message> {
        val all = mutableListOf<Message>()
        val seen = mutableSetOf<Long>()
        var from: Long = 0
        val limit = 100.coerceAtMost(max.coerceAtLeast(1))
        var attempts = 0
        while (all.size < max && attempts < 5) {
            val batch = loadHistoryBlock(chatId, from, limit)
            val unique = batch.filter { seen.add(it.id) }
            if (unique.isEmpty()) {
                attempts++
                val waitMs = 200L * attempts
                DebugLog.log("loadLatestMessages chatId=$chatId attempt=$attempts no-data -> retry in ${waitMs}ms")
                delay(waitMs)
                continue
            }
            all += unique
            val oldest = unique.minByOrNull { it.id }!!
            from = oldest.id
            attempts = 0
            delay(100)
        }
        return all.take(max)
    }
}

private data class SimpleMessageVideo(
    val id: Long?,
    val chatId: Long?,
    val date: Int?,
    val dateIso: String?,
    val content: SimpleVideoContent
)

private data class SimpleVideoContent(
    val duration: Int?,
    val width: Int?,
    val height: Int?,
    val fileName: String?,
    val mimeType: String?,
    val supportsStreaming: Boolean?,
    val file: SimpleRemoteFile?,
    val thumbnail: SimpleThumbnail?,
    val caption: String?
)

private data class SimpleThumbnail(
    val width: Int?,
    val height: Int?,
    val file: SimpleRemoteFile?
)

private data class SimpleRemoteFile(
    val id: Long?,
    val size: Long?,
    val remoteId: String?,
    val uniqueId: String?
)

private data class SimpleMessagePhoto(
    val id: Long?,
    val chatId: Long?,
    val date: Int?,
    val dateIso: String?,
    val content: SimplePhotoContent
)

private data class SimplePhotoContent(
    val type: String,
    val sizes: List<SimplePhotoSize>
)

private data class SimplePhotoSize(
    val width: Int?,
    val height: Int?,
    val file: SimpleRemoteFile?
)

private data class SimpleMessageText(
    val id: Long?,
    val chatId: Long?,
    val date: Int?,
    val dateIso: String?,
    val text: String,
    val title: String?,
    val year: Int?,
    val lengthMinutes: Int?,
    val fsk: Int?,
    val collection: String?,
    val originalTitle: String?,
    val productionCountry: String?,
    val director: String?,
    val tmdbRating: Double?,
    val genres: List<String>,
    val tmdbUrl: String?
)
