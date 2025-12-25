package com.fishit.player.infra.transport.telegram.api

/** Exception thrown during Telegram file operations. */
class TelegramFileException(message: String, cause: Throwable? = null) : Exception(message, cause)
