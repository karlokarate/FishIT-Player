package tdltest

import java.io.File
import java.time.Instant

object DebugLog {
    private val file = File("chat_debug.log")

    @Synchronized
    fun log(message: String) {
        val line = "${Instant.now()} | $message\n"
        file.appendText(line)
    }
}
