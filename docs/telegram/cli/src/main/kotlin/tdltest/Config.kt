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
