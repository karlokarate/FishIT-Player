package tdltest

import dev.g000sha256.tdl.TdlClient
import kotlinx.coroutines.runBlocking
import java.io.File

fun main() = runBlocking {
    println("=== Telegram CLI (tdl-coroutines-jvm, TDLib 1.8.56) ===")
    // Logge Pfade für native Libs, um die geladene TDLib-Quelle sichtbar zu machen
    println("[ENV] java.library.path = ${System.getProperty("java.library.path")}")
    println("[ENV] LD_LIBRARY_PATH   = ${System.getenv("LD_LIBRARY_PATH") ?: ""}")

    val config = ConfigLoader.load()

    // Immer clean starten: DB/Files löschen und neu anlegen
    File(config.dbDir).deleteRecursively()
    File(config.filesDir).deleteRecursively()
    File(config.dbDir).mkdirs()
    File(config.filesDir).mkdirs()
    println("[INIT] TDLib Cache/AuthKey verworfen (db/files frisch angelegt).")

    // Allow re-login if session was closed/logged out
    lateinit var session: TelegramSession
    while (true) {
        val client = TdlClient.create()
        val s = TelegramSession(client, config, this)
        when (val result = s.login()) {
            is TelegramSession.LoginResult.Ready -> {
                session = s
                break
            }
            is TelegramSession.LoginResult.Restart -> {
                println(result.reason)
                val wipe = CliIo.readChoice("TD-DB/Files löschen für Neu-Login (y/n)? ", setOf("y", "n"))
                if (wipe == "y") {
                    File(config.dbDir).deleteRecursively()
                    File(config.filesDir).deleteRecursively()
                    println("Cache/DB gelöscht.")
                }
                // Loop neu starten → neuer Client & frische Session
                continue
            }
        }
    }

    val browser = ChatBrowser(session)

    // Modus wählen: raw dump vs kompakte Ausgabe; danach optional interaktiver Browser
    val rawDump = CliIo.readChoice(
        "Ansicht wählen: [r] raw (TDLib-objekte), [c] compact (nur ids/content-typen): ",
        setOf("r", "c")
    ) == "r"

    val dumpChoice = CliIo.readChoice(
        "Dump aller Chats + Messages (y/n)? ",
        setOf("y", "n")
    )
    if (dumpChoice == "y") {
        browser.dumpChatsWithMessages(raw = rawDump)
    }

    val browseChoice = CliIo.readChoice(
        "Interaktiven Chat-Browser starten (y/n)? ",
        setOf("y", "n")
    )
    if (browseChoice == "y") {
        browser.browseChatsInteractive(rawDefault = rawDump)
    }

    println("Auf Wiedersehen 👋")
}
