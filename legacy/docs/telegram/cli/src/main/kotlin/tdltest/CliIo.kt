package tdltest

object CliIo {

    fun readInt(prompt: String): Int {
        while (true) {
            print(prompt)
            val line = readLine() ?: return 0
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
