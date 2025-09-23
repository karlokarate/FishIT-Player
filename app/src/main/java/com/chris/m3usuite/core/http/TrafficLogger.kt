package com.chris.m3usuite.core.http

import android.content.Context
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

object TrafficLogger {
    private val enabled = AtomicBoolean(false)
    private val dateFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)
    private const val MAX_BODY_BYTES = 262144 // 256 KiB per side

    fun setEnabled(value: Boolean) { enabled.set(value) }
    fun isEnabled(): Boolean = enabled.get()

    fun tryLog(appContext: Context, request: Request, response: Response, startedNs: Long) {
        if (!enabled.get()) return
        runCatching {
            val durMs = (System.nanoTime() - startedNs) / 1_000_000
            val obj = JSONObject()
            obj.put("ts", dateFmt.format(Date()))
            obj.put("durationMs", durMs)

            val req = JSONObject()
            req.put("method", request.method)
            req.put("url", request.url.toString())
            val rh = JSONObject()
            for ((n, v) in request.headers) rh.put(n, v)
            req.put("headers", rh)
            // Request body (peek up to MAX)
            val reqBody = request.body
            if (reqBody != null) {
                val buf = Buffer()
                try {
                    reqBody.writeTo(buf)
                    val bytes = buf.readByteArray()
                    val len = bytes.size
                    val slice = if (len > MAX_BODY_BYTES) bytes.copyOf(MAX_BODY_BYTES) else bytes
                    req.put("bodyBase64", android.util.Base64.encodeToString(slice, android.util.Base64.NO_WRAP))
                    req.put("bodyLength", len)
                    req.put("bodyTruncated", len > MAX_BODY_BYTES)
                } catch (_: Throwable) {
                    req.put("bodyError", true)
                }
            }
            obj.put("request", req)

            val res = JSONObject()
            res.put("code", response.code)
            val sh = JSONObject()
            for ((n, v) in response.headers) sh.put(n, v)
            res.put("headers", sh)
            // Response body peek (clone) up to MAX
            try {
                val peek = response.peekBody(MAX_BODY_BYTES.toLong())
                val bytes = peek.bytes()
                res.put("bodyBase64", android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP))
                res.put("bodyLength", response.body?.contentLength() ?: -1)
                res.put("bodyTruncated", (response.body?.contentLength() ?: -1L) > MAX_BODY_BYTES)
            } catch (_: Throwable) {
                res.put("bodyError", true)
            }
            obj.put("response", res)

            writeJsonLine(appContext, obj)
        }
    }

    private fun writeJsonLine(ctx: Context, obj: JSONObject) {
        val dir = File(ctx.filesDir, "http-logs")
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "traffic-${SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())}.jsonl")
        file.appendText(obj.toString() + "\n")
    }
}

