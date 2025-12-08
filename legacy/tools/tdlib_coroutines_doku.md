

0. Kontext & Ziel

Dieses Dokument beschreibt den aktuellen Stand eines Telegram-Backends auf Basis von:

TDLib 1.8.56 (bereits nativ gebaut und vorhanden),

dev.g000sha256:tdl-coroutines-jvm:5.0.0 (Coroutine-basierter TDLib-Client),

einem Flow-basierten Login (Auth-Flow über authorizationStateUpdates),

einem Chat-Browser mit Paging (20 Nachrichten pro Page),

einem MediaParser, der Filme/Serien/RAR/Adult/Invites/Sub-Chats erkennt.


Dieses Backend lief erfolgreich in einem Linux/Termux-ptroot-Setup.
Die gleichen Module sollen später in die Android-/TV-App FishIT Player übernommen bzw. adaptiert werden.


---

1. Projekt-/Modul-Struktur (JVM-Testclient)

Aktuelle Struktur (JVM CLI-Projekt):

build.gradle.kts

src/main/kotlin/tdltest/
  CliIo.kt
  Config.kt
  TelegramSession.kt
  MessagePrinter.kt
  ChatBrowser.kt
  MediaModels.kt
  MediaParser.kt
  Main.kt

Hinweis:
Im JVM-Testclient wird zusätzlich LD_LIBRARY_PATH auf den Ordner mit libtdjson.so gesetzt. Für Android-FishIT wird später das Android-AAR tdl-coroutines-android verwendet → kein eigener nativer TDLib-Build nötig.


---

2. build.gradle.kts (JVM-Testprojekt)

Datei: build.gradle.kts

import org.gradle.api.tasks.JavaExec

plugins {
    kotlin("jvm") version "2.1.0"
    application
}

repositories {
    mavenCentral()
}

dependencies {
    // TDLib Kotlin Coroutines Client (JVM)
    implementation("dev.g000sha256:tdl-coroutines-jvm:5.0.0")

    // Coroutines Runtime
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
}

application {
    mainClass.set("tdltest.MainKt")
}

kotlin {
    jvmToolchain(21)
}

// WICHTIG für Termux/proot: stdin an den run-Task durchreichen,
// damit readLine() tatsächlich Eingaben bekommt.
tasks.withType<JavaExec>().configureEach {
    standardInput = System.`in`
}

Für FishIT-Android später:

Statt tdl-coroutines-jvm → implementation("dev.g000sha256:tdl-coroutines-android:5.0.0")

application-Block entfällt, das wird ein Android-Library-/App-Modul.

standardInput-Hack ist nur für CLI nötig.



---

3. CliIo.kt – CLI I/O Helper

Datei: src/main/kotlin/tdltest/CliIo.kt

package tdltest

object CliIo {

    fun readInt(prompt: String): Int {
        while (true) {
            print(prompt)
            val line = readLine() ?: return 0 // falls stdin geschlossen
            val trimmed = line.trim()
            if (trimmed.isEmpty()) {
                println("Bitte eine Zahl eingeben.")
                continue
            }
            val value = trimmed.toIntOrNull()
            if (value != null) return value
            println("Keine gültige Zahl: '$trimmed'")
        }
    }

    fun readNonEmptyString(prompt: String): String {
        while (true) {
            print(prompt)
            val line = readLine() ?: return ""
            val trimmed = line.trim()
            if (trimmed.isNotEmpty()) return trimmed
            println("Eingabe darf nicht leer sein.")
        }
    }

    fun readChoice(prompt: String, valid: Set<String>): String {
        while (true) {
            print(prompt)
            val line = (readLine() ?: "").trim().lowercase()
            if (line in valid) return line
            println("Ungültige Auswahl: '$line'. Erlaubt: ${valid.joinToString(", ")}")
        }
    }
}

FishIT-Integration:
Dieses Modul ist nur für CLI.
In der Android-App wird das durch ViewModel/Compose-UI ersetzt (z.B. stateFlow/events statt readLine()).


---

4. Config.kt – Konfigurations-Layer (Properties-Datei)

Datei: src/main/kotlin/tdltest/Config.kt

package tdltest

import java.io.File
import java.util.Properties

data class AppConfig(
    val apiId: Int,
    val apiHash: String,
    val phoneNumber: String,
    val dbDir: String,
    val filesDir: String
)

object ConfigLoader {

    private const val CONFIG_FILE_NAME = "telegram-cli.properties"

    fun load(): AppConfig {
        val file = File(CONFIG_FILE_NAME)
        if (!file.exists()) {
            error(
                """
                Konfigurationsdatei '$CONFIG_FILE_NAME' wurde nicht gefunden.

                Lege sie im Projektordner an, z.B. mit folgendem Inhalt:

                    apiId=1234567
                    apiHash=dein_api_hash_von_my_telegram_org
                    phoneNumber=+491701234567
                    dbDir=td-db
                    filesDir=td-files

                dbDir/filesDir sind optional; Standard ist td-db / td-files im Projekt.
                """.trimIndent()
            )
        }

        val props = Properties()
        file.inputStream().use { props.load(it) }

        val apiId = props.getProperty("apiId")?.trim()?.toIntOrNull()
            ?: error("In '$CONFIG_FILE_NAME' fehlt 'apiId' oder ist keine gültige Zahl.")

        val apiHash = props.getProperty("apiHash")?.trim().orEmpty()
        if (apiHash.isEmpty()) {
            error("In '$CONFIG_FILE_NAME' fehlt 'apiHash'.")
        }

        val phoneNumber = props.getProperty("phoneNumber")?.trim().orEmpty()
        if (phoneNumber.isEmpty()) {
            error("In '$CONFIG_FILE_NAME' fehlt 'phoneNumber'.")
        }

        val dbDirRaw = props.getProperty("dbDir")?.trim().takeUnless { it.isNullOrEmpty() } ?: "td-db"
        val filesDirRaw = props.getProperty("filesDir")?.trim().takeUnless { it.isNullOrEmpty() } ?: "td-files"

        val dbDir = File(dbDirRaw).apply { mkdirs() }.absolutePath
        val filesDir = File(filesDirRaw).apply { mkdirs() }.absolutePath

        println("\n[CONFIG] apiId      = $apiId")
        println("[CONFIG] phone      = $phoneNumber")
        println("[CONFIG] dbDir      = $dbDir")
        println("[CONFIG] filesDir   = $filesDir\n")

        return AppConfig(
            apiId = apiId,
            apiHash = apiHash,
            phoneNumber = phoneNumber,
            dbDir = dbDir,
            filesDir = filesDir
        )
    }
}

FishIT-Integration:

In Android: Konfig aus BuildConfig, EncryptedSharedPreferences oder RemoteConfig ziehen.

Das Interface AppConfig kann 1:1 bleiben, der Loader wird Android-spezifisch.



---

5. TelegramSession.kt – Flow-basierter Auth-Flow (State-Machine)

Datei: src/main/kotlin/tdltest/TelegramSession.kt

package tdltest

import dev.g000sha256.tdl.TdlClient
import dev.g000sha256.tdl.TdlResult
import dev.g000sha256.tdl.dto.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Hilfs-Erweiterung für TdlResult -> Erfolgswert oder Exception
fun <T> TdlResult<T>.getOrThrow(): T = when (this) {
    is TdlResult.Success -> result
    is TdlResult.Failure -> throw RuntimeException("TDLib error $code: $message")
}

class TelegramSession(
    val client: TdlClient,
    val config: AppConfig,
    private val scope: CoroutineScope
) {

    @Volatile
    private var currentState: AuthorizationState? = null

    private var collectorStarted = false
    private var tdParamsSet = false

    // Flow-basiert: Updates abonnieren, dann Requests ausführen
    suspend fun login() {
        println("[AUTH] Login-Flow wird gestartet (Flow-basiert)...")

        startAuthCollectorIfNeeded()

        // Nur zum Debuggen einmal aktuellen State holen – KEINE Aktionen hier auslösen,
        // sonst würden wir setTdlibParameters doppelt senden.
        val initial = client.getAuthorizationState().getOrThrow()
        println("[AUTH] Initialer State (getAuthorizationState): ${initial::class.simpleName}")
        currentState = initial

        // Jetzt NUR noch auf den Flow reagieren
        while (true) {
            when (val s = currentState) {
                is AuthorizationStateReady -> {
                    println("[AUTH] Fertig: AuthorizationStateReady ✅")
                    return
                }

                is AuthorizationStateClosing,
                is AuthorizationStateClosed,
                is AuthorizationStateLoggingOut -> {
                    error("[AUTH] Fataler State: ${s::class.simpleName}")
                }

                else -> {
                    delay(200)
                }
            }
        }
    }

    private fun startAuthCollectorIfNeeded() {
        if (collectorStarted) return
        collectorStarted = true

        println("[AUTH] Starte Flow-Collector für authorizationStateUpdates...")

        scope.launch {
            try {
                client.authorizationStateUpdates.collect { update ->
                    val state = update.authorizationState
                    println("[AUTH] UpdateAuthorizationState: ${state::class.simpleName}")
                    currentState = state
                    handleAuthState(state)
                }
            } catch (t: Throwable) {
                println("[AUTH] Fehler im authorizationStateUpdates-Flow: ${t.message}")
                t.printStackTrace()
            }
        }
    }

    // modulare Behandlung der einzelnen Auth-States + Debug-Logs
    private suspend fun handleAuthState(state: AuthorizationState) {
        println("[AUTH] handleAuthState(${state::class.simpleName})")

        when (state) {
            is AuthorizationStateWaitTdlibParameters -> onWaitTdlibParameters()
            is AuthorizationStateWaitPhoneNumber     -> onWaitPhoneNumber()
            is AuthorizationStateWaitCode            -> onWaitCode()
            is AuthorizationStateWaitPassword        -> onWaitPassword()
            is AuthorizationStateReady               -> {
                println("[AUTH] State READY empfangen.")
            }

            is AuthorizationStateLoggingOut          -> {
                println("[AUTH] State LOGGING_OUT")
            }

            is AuthorizationStateClosing             -> {
                println("[AUTH] State CLOSING")
            }

            is AuthorizationStateClosed              -> {
                println("[AUTH] State CLOSED")
            }

            else -> {
                println("[AUTH] Unbehandelter Auth-State: $state")
            }
        }
    }

    private suspend fun onWaitTdlibParameters() {
        println("[AUTH] → AuthorizationStateWaitTdlibParameters")

        // Schutz: Nicht mehrfach TdlibParameters setzen
        if (tdParamsSet) {
            println("[AUTH] TdlibParameters wurden bereits gesetzt – überspringe.")
            return
        }
        tdParamsSet = true

        val ok = client.setTdlibParameters(
            /* useTestDc             */ false,
            /* databaseDirectory     */ config.dbDir,
            /* filesDirectory        */ config.filesDir,
            /* databaseEncryptionKey */ ByteArray(0),
            /* useFileDatabase       */ true,
            /* useChatInfoDatabase   */ true,
            /* useMessageDatabase    */ true,
            /* useSecretChats        */ false,
            /* apiId                 */ config.apiId,
            /* apiHash               */ config.apiHash,
            /* systemLanguageCode    */ "de",
            /* deviceModel           */ "ptroot-ROG",
            /* systemVersion         */ System.getProperty("os.name") ?: "Linux",
            /* applicationVersion    */ "tdl-cli-test-0.1"
        ).getOrThrow()

        println("[AUTH] TdlibParameters gesetzt: $ok")
    }

    private suspend fun onWaitPhoneNumber() {
        println("[AUTH] → AuthorizationStateWaitPhoneNumber")

        val settings = PhoneNumberAuthenticationSettings(
            /* allowFlashCall                 */ false,
            /* allowMissedCall                */ false,
            /* allowSmsRetrieverApi           */ false,
            /* hasUnknownPhoneNumber          */ false,
            /* isCurrentPhoneNumber           */ false,
            /* firebaseAuthenticationSettings */ null,
            /* authenticationTokens           */ emptyArray()
        )

        client.setAuthenticationPhoneNumber(config.phoneNumber, settings).getOrThrow()
        println("[AUTH] Telefonnummer an TDLib übermittelt: ${config.phoneNumber}")
    }

    private suspend fun onWaitCode() {
        println("[AUTH] → AuthorizationStateWaitCode")

        val code = CliIo.readNonEmptyString(
            "[AUTH] Bitte den per SMS/Telegram gesendeten Code eingeben: "
        )

        // In dieser lib-Version erwartet checkAuthenticationCode einen String-Code.
        client.checkAuthenticationCode(code).getOrThrow()

        println("[AUTH] Code an TDLib übermittelt.")
    }

    private suspend fun onWaitPassword() {
        println("[AUTH] → AuthorizationStateWaitPassword (2FA aktiv)")
        println("[AUTH] 2FA-Login (Passwort) ist in diesem CLI noch nicht implementiert.")
        error("[AUTH] 2FA nicht unterstützt – bitte Account ohne 2FA verwenden.")
    }
}

FishIT-Integration:

TelegramSession wird in einem ViewModel oder einem :telegram-core-Modul laufen.

scope wird dann z.B. viewModelScope sein.

authorizationStateUpdates.collect bleibt identisch (Flow-based).

CliIo.readNonEmptyString wird ersetzt durch UI-Eingaben (z. B. Dialog/Screen für Code/Passwort).



---

6. MessagePrinter.kt

Datei: src/main/kotlin/tdltest/MessagePrinter.kt

package tdltest

import dev.g000sha256.tdl.dto.*

object MessagePrinter {

    fun printMessageLine(msg: Message) {
        val sender = when (val s = msg.senderId) {
            is MessageSenderUser -> "User(${s.userId})"
            is MessageSenderChat -> "Chat(${s.chatId})"
            else                 -> "Sender(?)"
        }

        val preview = when (val content = msg.content) {
            is MessageText     -> content.text.text
            is MessageDocument -> "[Dokument] ${content.document.fileName}"
            is MessageVideo    -> "[Video] ${content.video.fileName}"
            is MessagePhoto    -> "[Foto]"
            else               -> "[${content::class.simpleName}]"
        }

        println(
            String.format(
                "#%-12d [%s] %s",
                msg.id,
                sender,
                preview.replace("\n", " ")
            )
        )
    }
}

FishIT-Integration:
Dieses Modul ist ein Presenter – kann 1:1 in Android-Logik übernommen werden (UI-specific Mapping).


---

7. ChatBrowser.kt – Chatliste + Nachrichten-Paging (20er-Blöcke)

Datei: src/main/kotlin/tdltest/ChatBrowser.kt

package tdltest

import dev.g000sha256.tdl.dto.*

class ChatBrowser(
    private val session: TelegramSession
) {

    private val client get() = session.client

    suspend fun run() {
        println("\n[CHAT] Lade Chats...")

        val chatsResult = client.getChats(ChatListMain(), 200).getOrThrow()
        val chatIds: LongArray = chatsResult.chatIds ?: LongArray(0)

        val chats = mutableListOf<Chat>()
        for (id in chatIds) {
            try {
                val chat = client.getChat(id).getOrThrow()
                chats += chat
            } catch (t: Throwable) {
                println("[CHAT] Fehler beim Laden von Chat $id: ${t.message}")
            }
        }

        if (chats.isEmpty()) {
            println("[CHAT] Keine Chats vorhanden.")
            return
        }

        var page = 0
        val pageSize = 20

        while (true) {
            val totalPages = (chats.size - 1) / pageSize + 1
            if (page < 0) page = 0
            if (page >= totalPages) page = totalPages - 1

            val start = page * pageSize
            val end = (start + pageSize).coerceAtMost(chats.size)

            println("\n=== Chats (Seite ${page + 1}/$totalPages) ===")
            for (index in start until end) {
                val chat = chats[index]
                println(String.format("%2d) %-30s [id=%d]", index - start, chat.title, chat.id))
            }

            val input = CliIo.readNonEmptyString(
                "\n[n] nächste Seite, [p] vorherige Seite, [0-${end - start - 1}] Chat öffnen, [q] beenden\n> "
            ).lowercase()

            when {
                input == "q" -> return
                input == "n" -> page++
                input == "p" -> page--
                input.matches(Regex("\\d+")) -> {
                    val localIndex = input.toInt()
                    val globalIndex = start + localIndex
                    if (globalIndex in chats.indices) {
                        showChat(chats[globalIndex])
                    } else {
                        println("[CHAT] Ungültiger Index.")
                    }
                }
                else -> println("[CHAT] Unbekannte Eingabe.")
            }
        }
    }

    private suspend fun showChat(chat: Chat) {
        println("\n[CHAT] Anzeige von Chat: ${chat.title} [id=${chat.id}]")

        var fromMessageId: Long = 0L
        val pageSize = 20

        chatLoop@ while (true) {
            val history = client.getChatHistory(
                chatId = chat.id,
                fromMessageId = fromMessageId,
                offset = 0,
                limit = pageSize,
                onlyLocal = false
            ).getOrThrow()

            val msgsArray: Array<Message?> = history.messages ?: emptyArray()
            val messages = msgsArray.filterNotNull()

            if (messages.isEmpty()) {
                if (fromMessageId == 0L) {
                    println("[CHAT] Keine Nachrichten vorhanden.")
                } else {
                    println("[CHAT] Keine weiteren Nachrichten.")
                }
            } else {
                println("\n--- Nachrichten (max $pageSize) ---")
                for (msg in messages) {
                    MessagePrinter.printMessageLine(msg)
                }
                fromMessageId = messages.last().id
            }

            val choice = CliIo.readChoice(
                "\n[n] nächste $pageSize, [b] zurück zu Chats\n> ",
                setOf("n", "b")
            )
            when (choice) {
                "n" -> continue@chatLoop
                "b" -> break@chatLoop
            }
        }
    }
}

FishIT-Integration:

In Android UI wird ChatBrowser.run() durch UseCases/ViewModels ersetzt.

Kernlogik (Paging per GetChatHistory) bleibt identisch.



---

8. MediaModels.kt – Datenmodelle für Parser/Library

Datei: src/main/kotlin/tdltest/MediaModels.kt

package tdltest

enum class MediaKind {
    MOVIE,          // Film
    SERIES,         // komplette Serie (Metadaten ohne einzelne Episode)
    EPISODE,        // einzelne Folge einer Serie
    CLIP,           // kurzer Clip / unbekannte Länge
    RAR_ARCHIVE,    // .rar / .zip / .7z usw.
    PHOTO,
    TEXT_ONLY,
    ADULT,
    OTHER
}

data class MediaInfo(
    val chatId: Long,
    val messageId: Long,
    val kind: MediaKind,
    val chatTitle: String? = null,

    val fileName: String? = null,
    val mimeType: String? = null,
    val sizeBytes: Long? = null,

    val title: String? = null,
    val originalTitle: String? = null,
    val year: Int? = null,
    val durationMinutes: Int? = null,
    val country: String? = null,
    val fsk: Int? = null,
    val collection: String? = null,
    val genres: List<String> = emptyList(),
    val director: String? = null,
    val tmdbRating: Double? = null,
    val tmdbVotes: Int? = null,

    val totalEpisodes: Int? = null,
    val totalSeasons: Int? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,

    val extraInfo: String? = null
)

data class SubChatRef(
    val parentChatId: Long,
    val parentChatTitle: String?,
    val parentMessageId: Long,
    val label: String,
    val linkedChatId: Long? = null,
    val inviteLinks: List<String> = emptyList()
)

data class InviteLink(
    val chatId: Long,
    val messageId: Long,
    val url: String
)

sealed class ParsedItem {
    data class Media(val info: MediaInfo) : ParsedItem()
    data class SubChat(val ref: SubChatRef) : ParsedItem()
    data class Invite(val invite: InviteLink) : ParsedItem()
    data class None(val chatId: Long, val messageId: Long) : ParsedItem()
}


---

9. MediaParser.kt – Parser für Filme/Serien/RAR/Adult/Sub-Chats/Invites

Datei: src/main/kotlin/tdltest/MediaParser.kt

package tdltest

import dev.g000sha256.tdl.dto.*
import kotlin.text.Regex

object MediaParser {

    private val reTitle = Regex(
        """Titel:\s*([^T\n]+?)(?=(?:Originaltitel:|Erscheinungsjahr:|L[aä]nge:|Produktionsland:|FSK:|Filmreihe:|Regie:|TMDbRating:|Genres:|Episoden:|$))""",
        RegexOption.IGNORE_CASE
    )

    private val reOriginalTitle = Regex(
        """Originaltitel:\s*([^O\n]+?)(?=(?:Erscheinungsjahr:|L[aä]nge:|Produktionsland:|FSK:|Filmreihe:|Regie:|TMDbRating:|Genres:|Episoden:|$))""",
        RegexOption.IGNORE_CASE
    )

    private val reYear = Regex("""Erscheinungsjahr:\s*(\d{4})""", RegexOption.IGNORE_CASE)
    private val reLengthMinutes = Regex("""L[aä]nge:\s*(\d+)\s*Minuten""", RegexOption.IGNORE_CASE)
    private val reCountry = Regex(
        """Produktionsland:\s*([^F\n]+?)(?=(?:FSK:|Filmreihe:|Regie:|TMDbRating:|Genres:|Episoden:|$))""",
        RegexOption.IGNORE_CASE
    )
    private val reFsk = Regex("""FSK:\s*(\d{1,2})""", RegexOption.IGNORE_CASE)
    private val reCollection = Regex(
        """Filmreihe:\s*([^F\n]+?)(?=(?:Regie:|TMDbRating:|Genres:|Episoden:|$))""",
        RegexOption.IGNORE_CASE
    )
    private val reDirector = Regex(
        """Regie:\s*([^T\n]+?)(?=(?:TMDbRating:|Genres:|Episoden:|$))""",
        RegexOption.IGNORE_CASE
    )
    private val reTmdbRating = Regex(
        """TMDbRating:\s*([\d.,]+)(?:\s+bei\s+(\d+)\s+Stimmen)?""",
        RegexOption.IGNORE_CASE
    )
    private val reGenres = Regex("""Genres:\s*(.+)$""", RegexOption.IGNORE_CASE)
    private val reEpisodes = Regex(
        """Episoden:\s*(\d+)\s+Episoden(?:\s+in\s+(\d+)\s+Staffeln)?""",
        RegexOption.IGNORE_CASE
    )
    private val reInvite = Regex("""https?://t\.me/\S+|t\.me/\S+""", RegexOption.IGNORE_CASE)
    private val reSeasonEpisode = Regex("""[Ss](\d{1,2})\s*[Ee](\d{1,2})""")
    private val reAdultWords = Regex("""\b(porn|sex|xxx|18\+|🔞|creampie|anal|bds[mn])\b""", RegexOption.IGNORE_CASE)

    fun parseMessage(
        chatId: Long,
        chatTitle: String?,
        message: Message,
        recentMessages: List<Message> = emptyList()
    ): ParsedItem {
        parseMedia(chatId, chatTitle, message, recentMessages)?.let { return ParsedItem.Media(it) }
        parseSubChatRef(chatId, chatTitle, message)?.let { return ParsedItem.SubChat(it) }
        parseInvite(chatId, message)?.let { return ParsedItem.Invite(it) }
        return ParsedItem.None(chatId, message.id)
    }

    private fun parseMedia(
        chatId: Long,
        chatTitle: String?,
        message: Message,
        recentMessages: List<Message>
    ): MediaInfo? {
        val content = message.content

        return when (content) {
            is MessageVideo -> {
                val video = content.video
                val name = video.fileName?.takeIf { it.isNotBlank() }
                val metaFromName = parseMediaFromFileName(name)
                val kind = if (isAdultChannel(chatTitle, content.caption?.text)) MediaKind.ADULT else {
                    if (metaFromName?.seasonNumber != null) MediaKind.EPISODE else MediaKind.MOVIE
                }

                val metaFromText = parseMetaFromText(content.caption?.text.orEmpty())
                val base = metaFromName?.copy(
                    kind = kind,
                    chatId = chatId,
                    messageId = message.id,
                    chatTitle = chatTitle
                ) ?: MediaInfo(
                    chatId = chatId,
                    messageId = message.id,
                    kind = kind,
                    chatTitle = chatTitle,
                    fileName = name,
                    mimeType = video.mimeType,
                    sizeBytes = video.video?.size?.toLong()
                )

                mergeMeta(base, metaFromText)
            }

            is MessageDocument -> {
                val doc = content.document
                val name = doc.fileName?.takeIf { it.isNotBlank() }
                val isArchive = name?.matches(Regex(""".*\.(rar|zip|7z|tar|gz|bz2)$""", RegexOption.IGNORE_CASE)) == true
                val kind = when {
                    isArchive -> MediaKind.RAR_ARCHIVE
                    isAdultChannel(chatTitle, content.caption?.text) -> MediaKind.ADULT
                    else -> MediaKind.MOVIE
                }

                val metaFromName = parseMediaFromFileName(name)
                val metaFromText = parseMetaFromText(content.caption?.text.orEmpty())
                val base = metaFromName?.copy(
                    kind = if (isArchive) MediaKind.RAR_ARCHIVE else kind,
                    chatId = chatId,
                    messageId = message.id,
                    chatTitle = chatTitle
                ) ?: MediaInfo(
                    chatId = chatId,
                    messageId = message.id,
                    kind = kind,
                    chatTitle = chatTitle,
                    fileName = name,
                    mimeType = doc.mimeType,
                    sizeBytes = doc.document?.size?.toLong()
                )

                mergeMeta(base, metaFromText)
            }

            is MessagePhoto -> {
                val photo = content.photo
                MediaInfo(
                    chatId = chatId,
                    messageId = message.id,
                    kind = if (isAdultChannel(chatTitle, content.caption?.text)) MediaKind.ADULT else MediaKind.PHOTO,
                    chatTitle = chatTitle,
                    fileName = null,
                    mimeType = null,
                    sizeBytes = photo.sizes?.maxByOrNull { it.width }?.photo?.size?.toLong()
                )
            }

            is MessageText -> {
                val text = content.text?.text.orEmpty()
                val meta = parseMetaFromText(text) ?: return null

                val kind = when {
                    meta.totalEpisodes != null || meta.totalSeasons != null -> MediaKind.SERIES
                    isAdultChannel(chatTitle, text) -> MediaKind.ADULT
                    else -> MediaKind.TEXT_ONLY
                }

                meta.copy(
                    chatId = chatId,
                    messageId = message.id,
                    kind = kind,
                    chatTitle = chatTitle
                )
            }

            else -> null
        }
    }

    private fun parseMetaFromText(text: String): MediaInfo? {
        if (!text.contains("Titel:", ignoreCase = true) &&
            !text.contains("Erscheinungsjahr:", ignoreCase = true) &&
            !text.contains("Länge:", ignoreCase = true) &&
            !text.contains("FSK:", ignoreCase = true) &&
            !text.contains("TMDbRating:", ignoreCase = true)
        ) {
            return null
        }

        val t = text.replace('\u202f', ' ').replace('\u00A0', ' ')

        fun grab(re: Regex): String? = re.find(t)?.groupValues?.getOrNull(1)?.trim()

        val title = grab(reTitle)
        val orig = grab(reOriginalTitle)
        val year = grab(reYear)?.toIntOrNull()
        val length = grab(reLengthMinutes)?.toIntOrNull()
        val country = grab(reCountry)
        val fsk = grab(reFsk)?.toIntOrNull()
        val collection = grab(reCollection)
        val director = grab(reDirector)

        val ratingMatch = reTmdbRating.find(t)
        val rating = ratingMatch?.groupValues?.getOrNull(1)?.replace(',', '.')?.toDoubleOrNull()
        val votes = ratingMatch?.groupValues?.getOrNull(2)?.toIntOrNull()

        val genresText = grab(reGenres)
        val genres = genresText
            ?.split(',', '/', '|')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?: emptyList()

        val epsMatch = reEpisodes.find(t)
        val totalEps = epsMatch?.groupValues?.getOrNull(1)?.toIntOrNull()
        val totalSeasons = epsMatch?.groupValues?.getOrNull(2)?.toIntOrNull()

        if (title == null && year == null && length == null && country == null && fsk == null && rating == null && genres.isEmpty()) {
            return null
        }

        return MediaInfo(
            chatId = 0L,
            messageId = 0L,
            kind = MediaKind.OTHER, // wird später überschrieben
            title = title,
            originalTitle = orig,
            year = year,
            durationMinutes = length,
            country = country,
            fsk = fsk,
            collection = collection,
            genres = genres,
            director = director,
            tmdbRating = rating,
            tmdbVotes = votes,
            totalEpisodes = totalEps,
            totalSeasons = totalSeasons,
            extraInfo = if (t.contains("Weitere Infos", ignoreCase = true)) {
                t.substringAfter("Weitere Infos", "").trim().ifEmpty { null } else t.substringAfter("Weitere Infos", "").trim()
            } else null
        )
    }

    private fun parseMediaFromFileName(fileName: String?): MediaInfo? {
        if (fileName.isNullOrBlank()) return null

        val base = fileName.substringBeforeLast('.')
        var title: String? = base
        var year: Int? = null
        var season: Int? = null
        var episode: Int? = null

        val yearMatch = Regex("""(19|20)\d{2}""").find(base)
        if (yearMatch != null) {
            year = yearMatch.value.toInt()
            title = base.substring(0, yearMatch.range.first).trim().trim('-', '.', '_')
        }

        val seMatch = reSeasonEpisode.find(base)
        if (seMatch != null) {
            season = seMatch.groupValues[1].toIntOrNull()
            episode = seMatch.groupValues[2].toIntOrNull()
            title = base.substring(0, seMatch.range.first).trim().trim('-', '.', '_')
        }

        return MediaInfo(
            chatId = 0L,
            messageId = 0L,
            kind = MediaKind.OTHER,
            title = title,
            year = year,
            seasonNumber = season,
            episodeNumber = episode,
            fileName = fileName
        )
    }

    private fun mergeMeta(base: MediaInfo, add: MediaInfo?): MediaInfo {
        if (add == null) return base
        return base.copy(
            title = add.title ?: base.title,
            originalTitle = add.originalTitle ?: base.originalTitle,
            year = add.year ?: base.year,
            durationMinutes = add.durationMinutes ?: base.durationMinutes,
            country = add.country ?: base.country,
            fsk = add.fsk ?: base.fsk,
            collection = add.collection ?: base.collection,
            genres = if (add.genres.isNotEmpty()) add.genres else base.genres,
            director = add.director ?: base.director,
            tmdbRating = add.tmdbRating ?: base.tmdbRating,
            tmdbVotes = add.tmdbVotes ?: base.tmdbVotes,
            totalEpisodes = add.totalEpisodes ?: base.totalEpisodes,
            totalSeasons = add.totalSeasons ?: base.totalSeasons,
            seasonNumber = add.seasonNumber ?: base.seasonNumber,
            episodeNumber = add.episodeNumber ?: base.episodeNumber,
            extraInfo = add.extraInfo ?: base.extraInfo
        )
    }

    private fun isAdultChannel(chatTitle: String?, text: String?): Boolean {
        val t = (chatTitle.orEmpty() + " " + text.orEmpty())
        return reAdultWords.containsMatchIn(t)
    }

    private fun parseSubChatRef(
        chatId: Long,
        chatTitle: String?,
        message: Message
    ): SubChatRef? {
        val content = message.content

        if (content is MessageText) {
            val text = content.text?.text?.trim().orElseNull()
            if (!text.isNullOrEmpty()) {
                val isDirectoryLike = chatTitle?.contains("Serien", ignoreCase = true) == true ||
                        chatTitle?.contains("Staffel", ignoreCase = true) == true

                if (isDirectoryLike && !text.contains("Titel:", ignoreCase = true)) {
                    return SubChatRef(
                        parentChatId = chatId,
                        parentChatTitle = chatTitle,
                        parentMessageId = message.id,
                        label = text,
                        linkedChatId = null,
                        inviteLinks = extractInviteLinksFromText(text)
                    )
                }
            }
        }

        val invite = parseInvite(chatId, message)
        if (invite != null) {
            val label = when (val c = content) {
                is MessageText -> c.text?.text?.trim().orEmpty()
                else -> "(Invite)"
            }
            return SubChatRef(
                parentChatId = chatId,
                parentChatTitle = chatTitle,
                parentMessageId = message.id,
                label = label,
                linkedChatId = null,
                inviteLinks = listOf(invite.url)
            )
        }

        return null
    }

    private fun parseInvite(chatId: Long, message: Message): InviteLink? {
        val text = when (val c = message.content) {
            is MessageText -> c.text?.text
            is MessageVideo -> c.caption?.text
            is MessageDocument -> c.caption?.text
            is MessagePhoto -> c.caption?.text
            else -> null
        } ?: return null

        val m = reInvite.find(text) ?: return null
        val url = m.value
        return InviteLink(
            chatId = chatId,
            messageId = message.id,
            url = url
        )
    }

    private fun extractInviteLinksFromText(text: String?): List<String> {
        if (text.isNullOrEmpty()) return emptyList()
        return reInvite.findAll(text).map { it.value }.toList()
    }

    private fun String?.orElseNull(): String? = this?.takeIf { it.isNotBlank() }
}


---
10. Main.kt – Einstiegspunkt (JVM CLI)

Datei: src/main/kotlin/tdltest/Main.kt

package tdltest

import dev.g000sha256.tdl.TdlClient
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    println("=== Telegram CLI (tdl-coroutines-jvm, TDLib 1.8.56) ===")

    // 1. Konfiguration aus Datei laden
    val config = ConfigLoader.load()

    // 2. Client & Session bauen
    val client = TdlClient.create()
    val session = TelegramSession(client, config, this)

    // 3. Login (Flow-basiert, Debug-Logs)
    session.login()

    // 4. Chat-Browser starten
    val browser = ChatBrowser(session)
    browser.run()

    println("Auf Wiedersehen 👋")
}


---

11. Integration in FishIT-Player-Repo (für Agents/Codex)

Empfohlene Zielstruktur in FishIT:

FishIT-Player/
  app/                  // Android App (Compose/TV)
  telegram-core/        // NEUES Modul
    src/main/kotlin/    // hier landen TelegramSession, MediaParser etc.
  ...

In telegram-core/build.gradle.kts (Android):

Statt tdl-coroutines-jvm:


dependencies {
    implementation("dev.g000sha256:tdl-coroutines-android:5.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    // evtl. kotlinx-serialization etc.
}

minSdk/targetSdk gemäß FishIT-Projekt.


Mapping JVM → Android:

TelegramSession bleibt fast identisch, aber:

scope: CoroutineScope wird z.B. viewModelScope.

CliIo.readNonEmptyString(...) wird ersetzt durch UI-Dialog / Screen für code/password.


ChatBrowser & MediaParser bleiben auf DTO-Ebene identisch.

Statt println → UI-Render in Compose.


ConfigLoader:

In App: apiId/apiHash aus BuildConfig oder Secrets.

dbDir/filesDir über Android-Kontext (context.filesDir/context.noBackupFilesDir etc.).


MediaModels & MediaParser (Kotlin-only)
→ 1:1 in telegram-core nutzen, später mit ObjectBox/Room persistieren und im UI anzeigen.



---

12. Next Steps für Agent/Codex

1. Neues Android-Modul telegram-core erzeugen

Build-Gradle auf tdl-coroutines-android:5.0.0 umstellen.

Obige Dateien (ohne CliIo.kt / CLI-spezifisch) übernehmen.



2. Android-spezifische Konfiguration implementieren

AppConfig-Instanz aus BuildConfig/Secrets/SharedPrefs aufbauen.

ConfigLoader-Variante für Android schreiben.



3. Session-Integration in ViewModel

TelegramSession in einem TelegramViewModel nutzen.

authorizationStateUpdates & Chat-Liste über StateFlow/LiveData an Compose-TV-UI binden.



4. MediaParser-Ausgaben persistieren

MediaInfo, SubChatRef, InviteLink in DB schreiben.

Index für:

Filme

Serien

Sub-Chats (z.B. „Serien zum Streamen“ → „Inside Man“, „Slow Horses“, „Dexter Wiedererwachen“ etc.)




5. UI in FishIT-Player bauen

Startscreen: Telegram-Konten / „Telegram Library“.

Views:

Chatliste

Media-Library (Filme, Serien, Kids, Adult gefiltert)

Serien-Unterchats / Sub-Chats drill-down.
