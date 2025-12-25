package com.fishit.player.infra.transport.telegram

/**
 * Public facade for the Telegram transport implementation.
 *
 * This interface unifies all typed transport clients and the compatibility
 * [TelegramTransportClient] facade behind a single API surface.
 */
interface TelegramClient :
    TelegramAuthClient,
    TelegramHistoryClient,
    TelegramFileClient,
    TelegramThumbFetcher,
    TelegramTransportClient
