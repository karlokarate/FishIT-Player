"""
Telethon HTTP Proxy for FishIT-Player.

Runs a lightweight HTTP server on localhost using Python's built-in asyncio.
Bridges Kotlin ↔ Telegram via typed JSON endpoints.

No aiohttp dependency — pure asyncio.start_server() + manual HTTP parsing.

Endpoints:
  GET  /health           → Health check
  GET  /auth/status      → Auth state
  POST /auth/phone       → Send phone number
  POST /auth/code        → Send verification code
  POST /auth/password    → Send 2FA password
  POST /auth/logout      → Logout
  GET  /chats            → Chat list
  GET  /chat?id=X        → Single chat
  GET  /messages?chat=X&limit=Y&offset=Z → Paginated messages
  GET  /messages/search?chat=X&q=Y       → Search messages
  GET  /file?chat=X&id=Y                 → File streaming (Range support!)
  GET  /file/info?chat=X&id=Y            → File metadata
  GET  /thumb?chat=X&id=Y                → Thumbnail bytes
  GET  /me                               → Current user ID
"""

import asyncio
import json
import logging
import os
import threading
from urllib.parse import urlparse, parse_qs

from telethon import TelegramClient
from telethon.errors import (
    FloodWaitError,
    SessionRevokedError,
    ChatAdminRequiredError,
)
from telethon.tl.types import (
    MessageMediaDocument,
    MessageMediaPhoto,
    DocumentAttributeVideo,
    DocumentAttributeAudio,
    DocumentAttributeAnimated,
    DocumentAttributeFilename,
)

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("tg_proxy")

# ── Configuration from environment ──────────────────────────────────────────

API_ID = int(os.environ.get("TG_API_ID", "0"))
API_HASH = os.environ.get("TG_API_HASH", "")
SESSION_PATH = os.environ.get(
    "TG_SESSION_PATH", "/data/data/com.fishit.player/files/telethon")
PROXY_PORT = int(os.environ.get("TG_PROXY_PORT", "8089"))
PROXY_HOST = "127.0.0.1"

# Global Telethon client (initialized in main())
_client: TelegramClient | None = None


# ── HTTP helpers ────────────────────────────────────────────────────────────

def _json_response(writer, data, status=200, status_text="OK"):
    """Write a JSON HTTP response."""
    body = json.dumps(data, ensure_ascii=False).encode("utf-8")
    writer.write(
        f"HTTP/1.1 {status} {status_text}\r\n"
        f"Content-Type: application/json; charset=utf-8\r\n"
        f"Content-Length: {len(body)}\r\n"
        f"Connection: close\r\n"
        f"\r\n".encode()
    )
    writer.write(body)


def _error_response(writer, status, message):
    """Write an error JSON response."""
    _json_response(writer, {"error": message},
                   status=status, status_text=message[:50])


def _parse_range(range_header, file_size):
    """Parse Range: bytes=X-Y header. Returns (start, end) inclusive."""
    if not range_header or not range_header.startswith("bytes="):
        return 0, file_size - 1
    range_spec = range_header[6:]  # strip "bytes="
    parts = range_spec.split("-")
    start = int(parts[0]) if parts[0] else 0
    end = int(parts[1]) if len(parts) > 1 and parts[1] else file_size - 1
    return max(0, start), min(end, file_size - 1)


# ── Content type extraction helpers ─────────────────────────────────────────

def _extract_content_info(message):
    """Extract content type info from a Telethon message for JSON serialization."""
    media = message.media
    if media is None:
        return None

    if isinstance(media, MessageMediaDocument) and media.document:
        doc = media.document
        attrs = doc.attributes or []

        # Determine content type from attributes
        video_attr = next(
            (a for a in attrs if isinstance(a, DocumentAttributeVideo)), None)
        audio_attr = next(
            (a for a in attrs if isinstance(a, DocumentAttributeAudio)), None)
        anim_attr = next((a for a in attrs if isinstance(
            a, DocumentAttributeAnimated)), None)
        fname_attr = next((a for a in attrs if isinstance(
            a, DocumentAttributeFilename)), None)

        result = {
            "remoteId": str(doc.id),
            "mimeType": doc.mime_type,
            "fileSize": doc.size,
            "fileName": fname_attr.file_name if fname_attr else None,
        }

        if video_attr:
            result["type"] = "video"
            result["duration"] = video_attr.duration
            result["width"] = video_attr.w
            result["height"] = video_attr.h
            result["supportsStreaming"] = getattr(
                video_attr, "supports_streaming", False)
        elif anim_attr:
            result["type"] = "animation"
            result["duration"] = video_attr.duration if video_attr else 0
            result["width"] = video_attr.w if video_attr else 0
            result["height"] = video_attr.h if video_attr else 0
        elif audio_attr:
            result["type"] = "audio"
            result["duration"] = audio_attr.duration
        else:
            result["type"] = "document"

        # Thumbnail info
        if doc.thumbs:
            best_thumb = max(
                doc.thumbs, key=lambda t: getattr(t, "size", 0) or 0)
            result["thumbnail"] = {
                "width": getattr(best_thumb, "w", 0),
                "height": getattr(best_thumb, "h", 0),
            }

        # Caption
        result["caption"] = message.text or ""

        return result

    if isinstance(media, MessageMediaPhoto):
        return {
            "type": "photo",
            "remoteId": str(media.photo.id) if media.photo else None,
            "caption": message.text or "",
        }

    return None


def _message_to_dict(msg):
    """Convert a Telethon Message to a JSON-serializable dict."""
    content = _extract_content_info(msg)
    return {
        "id": msg.id,
        "chatId": msg.chat_id,
        "date": msg.date.isoformat() if msg.date else None,
        "content": content,
        "text": msg.text or "",
    }


def _chat_to_dict(dialog):
    """Convert a Telethon Dialog to a JSON-serializable dict."""
    entity = dialog.entity
    return {
        "id": dialog.id,
        "title": dialog.title or getattr(entity, "first_name", "") or str(dialog.id),
        "memberCount": getattr(entity, "participants_count", None),
        "lastMessageDate": dialog.date.isoformat() if dialog.date else None,
    }


# ── Request handler ─────────────────────────────────────────────────────────

async def _handle_request(reader, writer):
    """Handle a single HTTP connection."""
    global _client
    client = _client
    if client is None:
        _error_response(writer, 503, "Telethon client not initialized")
        await writer.drain()
        writer.close()
        return

    try:
        # Read request line
        request_line = await asyncio.wait_for(reader.readline(), timeout=10)
        if not request_line:
            writer.close()
            return

        parts = request_line.decode(
            "utf-8", errors="replace").strip().split(" ", 2)
        if len(parts) < 2:
            writer.close()
            return

        method, raw_path = parts[0], parts[1]

        # Read headers
        headers = {}
        while True:
            line = await asyncio.wait_for(reader.readline(), timeout=5)
            if line == b"\r\n" or line == b"\n" or not line:
                break
            decoded = line.decode("utf-8", errors="replace").strip()
            if ": " in decoded:
                key, value = decoded.split(": ", 1)
                headers[key.lower()] = value

        # Read body for POST requests
        body = b""
        if method == "POST":
            content_length = int(headers.get("content-length", "0"))
            if content_length > 0:
                body = await asyncio.wait_for(reader.readexactly(content_length), timeout=10)

        # Parse URL
        parsed = urlparse(raw_path)
        path = parsed.path
        params = parse_qs(parsed.query)

        logger.info(f"{method} {path}")

        # ── Route ──

        if path == "/health":
            me = await client.get_me()
            _json_response(
                writer, {"status": "ok", "userId": me.id if me else None})

        elif path == "/auth/status":
            authorized = await client.is_user_authorized()
            _json_response(writer, {"authorized": authorized})

        elif path == "/auth/phone" and method == "POST":
            data = json.loads(body) if body else {}
            phone = data.get("phone", "")
            result = await client.send_code_request(phone)
            _json_response(writer, {"phoneCodeHash": result.phone_code_hash})

        elif path == "/auth/code" and method == "POST":
            data = json.loads(body) if body else {}
            phone = data.get("phone", "")
            code = data.get("code", "")
            phone_code_hash = data.get("phoneCodeHash", "")
            await client.sign_in(phone, code, phone_code_hash=phone_code_hash)
            _json_response(writer, {"authorized": True})

        elif path == "/auth/password" and method == "POST":
            data = json.loads(body) if body else {}
            password = data.get("password", "")
            await client.sign_in(password=password)
            _json_response(writer, {"authorized": True})

        elif path == "/auth/logout" and method == "POST":
            await client.log_out()
            _json_response(writer, {"loggedOut": True})

        elif path == "/chats":
            limit = int(params.get("limit", ["100"])[0])
            dialogs = []
            async for d in client.iter_dialogs(limit=limit):
                dialogs.append(_chat_to_dict(d))
            _json_response(writer, dialogs)

        elif path == "/chat":
            chat_id = int(params["id"][0])
            entity = await client.get_entity(chat_id)
            _json_response(writer, {
                "id": chat_id,
                "title": getattr(entity, "title", None) or getattr(entity, "first_name", str(chat_id)),
                "memberCount": getattr(entity, "participants_count", None),
            })

        elif path == "/messages":
            chat_id = int(params["chat"][0])
            limit = int(params.get("limit", ["100"])[0])
            offset_id = int(params.get("offset", ["0"])[0])
            messages = []
            async for msg in client.iter_messages(chat_id, limit=limit, offset_id=offset_id):
                messages.append(_message_to_dict(msg))
            _json_response(writer, messages)

        elif path == "/messages/search":
            chat_id = int(params["chat"][0])
            query = params.get("q", [""])[0]
            limit = int(params.get("limit", ["100"])[0])
            messages = []
            async for msg in client.iter_messages(chat_id, search=query, limit=limit):
                messages.append(_message_to_dict(msg))
            _json_response(writer, messages)

        elif path == "/file/info":
            chat_id = int(params["chat"][0])
            msg_id = int(params["id"][0])
            message = await client.get_messages(chat_id, ids=msg_id)
            if not message or not message.media:
                _error_response(writer, 404, "Message or media not found")
            else:
                info = _extract_content_info(message)
                _json_response(writer, info or {
                               "error": "No extractable media"})

        elif path == "/file":
            await _handle_file_stream(writer, client, params, headers)

        elif path == "/thumb":
            chat_id = int(params["chat"][0])
            msg_id = int(params["id"][0])
            message = await client.get_messages(chat_id, ids=msg_id)
            if not message or not message.media:
                _error_response(writer, 404, "No media for thumbnail")
            else:
                thumb_bytes = await client.download_media(message, thumb=-1, file=bytes)
                if thumb_bytes:
                    writer.write(
                        f"HTTP/1.1 200 OK\r\n"
                        f"Content-Type: image/jpeg\r\n"
                        f"Content-Length: {len(thumb_bytes)}\r\n"
                        f"Connection: close\r\n"
                        f"\r\n".encode()
                    )
                    writer.write(thumb_bytes)
                else:
                    _error_response(writer, 404, "Thumbnail not available")

        elif path == "/me":
            me = await client.get_me()
            _json_response(
                writer, {"id": me.id, "username": me.username} if me else {"id": None})

        else:
            _error_response(writer, 404, f"Unknown endpoint: {path}")

    except FloodWaitError as e:
        logger.warning(f"FloodWait: {e.seconds}s — waiting...")
        await asyncio.sleep(e.seconds)
        _error_response(writer, 429, f"FloodWait: retry after {e.seconds}s")
    except SessionRevokedError:
        _error_response(writer, 401, "session_revoked")
    except ChatAdminRequiredError:
        _error_response(writer, 403, "admin_required")
    except Exception as e:
        logger.exception(f"Error handling request: {e}")
        _error_response(writer, 500, str(e))
    finally:
        await writer.drain()
        writer.close()


# ── File streaming with Range support ───────────────────────────────────────

async def _handle_file_stream(writer, client, params, headers):
    """
    Stream a Telegram file with HTTP Range support.

    This is the critical endpoint — maps HTTP Range headers 1:1 to
    Telethon's iter_download(offset=BYTES, limit=BYTES).
    """
    chat_id = int(params["chat"][0])
    msg_id = int(params["id"][0])

    message = await client.get_messages(chat_id, ids=msg_id)
    if not message or not message.media:
        _error_response(writer, 404, "Message or media not found")
        return

    # Determine file size and MIME type
    file_size = message.file.size if message.file else 0
    mime_type = (
        message.file.mime_type if message.file else None) or "application/octet-stream"

    if not file_size:
        _error_response(writer, 404, "Cannot determine file size")
        return

    # Parse Range header
    range_header = headers.get("range")
    if range_header:
        start, end = _parse_range(range_header, file_size)
        content_length = end - start + 1
        status_line = "206 Partial Content"
        range_info = f"Content-Range: bytes {start}-{end}/{file_size}\r\n"
    else:
        start, end = 0, file_size - 1
        content_length = file_size
        status_line = "200 OK"
        range_info = ""

    logger.info(
        f"File stream: chat={chat_id} msg={msg_id} range={start}-{end}/{file_size}")

    # Write HTTP response headers
    writer.write(
        f"HTTP/1.1 {status_line}\r\n"
        f"Content-Type: {mime_type}\r\n"
        f"Content-Length: {content_length}\r\n"
        f"Accept-Ranges: bytes\r\n"
        f"{range_info}"
        f"Connection: close\r\n"
        f"\r\n".encode()
    )
    await writer.drain()

    # Stream via Telethon iter_download with byte-level offset
    bytes_sent = 0
    async for chunk in client.iter_download(
        message.media,
        offset=start,
        request_size=512 * 1024,  # 512 KB chunks
    ):
        remaining = content_length - bytes_sent
        if remaining <= 0:
            break
        to_write = chunk[:remaining] if len(chunk) > remaining else chunk
        writer.write(to_write)
        await writer.drain()
        bytes_sent += len(to_write)


# ── Server entry point ──────────────────────────────────────────────────────

async def _main():
    """Initialize Telethon client and start HTTP server."""
    global _client

    logger.info(f"Starting Telethon proxy on {PROXY_HOST}:{PROXY_PORT}")
    logger.info(f"Session path: {SESSION_PATH}")

    _client = TelegramClient(SESSION_PATH, API_ID, API_HASH)
    await _client.connect()

    logger.info("Telethon client connected")

    server = await asyncio.start_server(_handle_request, PROXY_HOST, PROXY_PORT)
    logger.info(f"HTTP proxy listening on {PROXY_HOST}:{PROXY_PORT}")

    async with server:
        await server.serve_forever()


def start_server():
    """
    Called from Kotlin via Chaquopy.

    Starts the Telethon proxy in a daemon thread so Chaquopy's callAttr()
    returns immediately.
    """
    def _run():
        asyncio.run(_main())

    thread = threading.Thread(target=_run, daemon=True, name="TelethonProxy")
    thread.start()
    logger.info("Telethon proxy thread started")
