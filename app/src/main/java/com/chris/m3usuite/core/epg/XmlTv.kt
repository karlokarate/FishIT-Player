package com.chris.m3usuite.core.epg

import android.content.Context
import android.util.Xml
import com.chris.m3usuite.core.http.HttpClientFactory
import com.chris.m3usuite.prefs.SettingsStore
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.flow.first

data class XmlTvProg(val title: String?, val startMs: Long, val stopMs: Long)

object XmlTv {
    private val fmt = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private fun parseTime(attr: String?): Long? = runCatching { if (attr.isNullOrBlank()) null else fmt.parse(attr)?.time }.getOrNull()

    /**
     * Stream-parse XMLTV and return current + next programme for the given channel id.
     * Stops parsing as soon as we have both entries to avoid loading entire file.
     */
    suspend fun currentNext(context: Context, settings: SettingsStore, channelId: String): Pair<XmlTvProg?, XmlTvProg?> {
        val url = settings.epgUrl.first()
        if (url.isBlank()) return null to null
        val client = HttpClientFactory.create(context, settings)
        val req = okhttp3.Request.Builder().url(url).build()
        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) return null to null
            val body = res.body ?: return null to null
            val parser: XmlPullParser = Xml.newPullParser()
            parser.setInput(body.byteStream(), null)
            var event = parser.eventType
            var inProgramme = false
            var curChan: String? = null
            var curTitle: String? = null
            var curStart: Long? = null
            var curStop: Long? = null
            val now = System.currentTimeMillis()
            var nowProg: XmlTvProg? = null
            var nextProg: XmlTvProg? = null
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "programme" -> {
                                inProgramme = true
                                curChan = parser.getAttributeValue(null, "channel")
                                curStart = parseTime(parser.getAttributeValue(null, "start"))
                                curStop = parseTime(parser.getAttributeValue(null, "stop"))
                                curTitle = null
                            }
                            "title" -> if (inProgramme) {
                                curTitle = try { parser.nextText() } catch (_: Throwable) { null }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "programme") {
                            if (curChan == channelId && curStart != null && curStop != null) {
                                val p = XmlTvProg(curTitle, curStart!!, curStop!!)
                                if (now in curStart!!..curStop!!) {
                                    nowProg = p
                                } else if (nowProg != null && nextProg == null && curStart!! > nowProg!!.stopMs) {
                                    nextProg = p
                                }
                                if (nowProg != null && nextProg != null) break
                            }
                            inProgramme = false
                            curChan = null; curTitle = null; curStart = null; curStop = null
                        }
                    }
                }
                event = parser.next()
            }
            return nowProg to nextProg
        }
    }

    /**
     * Build a Now/Next index for a set of channel ids from a single XMLTV scan.
     * Returns map channelId -> Pair(now, next). Stops early if all channels resolved.
     */
    suspend fun indexNowNext(context: Context, settings: SettingsStore, channelIds: Set<String>): Map<String, Pair<XmlTvProg?, XmlTvProg?>> {
        if (channelIds.isEmpty()) return emptyMap()
        val url = settings.epgUrl.first()
        if (url.isBlank()) return emptyMap()
        val client = HttpClientFactory.create(context, settings)
        val req = okhttp3.Request.Builder().url(url).build()
        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) return emptyMap()
            val body = res.body ?: return emptyMap()
            val parser: XmlPullParser = Xml.newPullParser()
            parser.setInput(body.byteStream(), null)
            val nowMs = System.currentTimeMillis()
            val nowMap = mutableMapOf<String, XmlTvProg?>()
            val nextMap = mutableMapOf<String, XmlTvProg?>()

            var event = parser.eventType
            var inProgramme = false
            var curChan: String? = null
            var curTitle: String? = null
            var curStart: Long? = null
            var curStop: Long? = null

            fun doneForAll(): Boolean {
                for (ch in channelIds) if (nowMap[ch] == null) return false
                return true
            }

            while (event != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                when (event) {
                    org.xmlpull.v1.XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "programme" -> {
                                inProgramme = true
                                curChan = parser.getAttributeValue(null, "channel")
                                if (curChan !in channelIds) curChan = null
                                curStart = parseTime(parser.getAttributeValue(null, "start"))
                                curStop = parseTime(parser.getAttributeValue(null, "stop"))
                                curTitle = null
                            }
                            "title" -> if (inProgramme && curChan != null) {
                                curTitle = try { parser.nextText() } catch (_: Throwable) { null }
                            }
                        }
                    }
                    org.xmlpull.v1.XmlPullParser.END_TAG -> {
                        if (parser.name == "programme") {
                            if (curChan != null && curStart != null && curStop != null) {
                                val ch = curChan!!
                                val p = XmlTvProg(curTitle, curStart!!, curStop!!)
                                if (nowMs in curStart!!..curStop!!) {
                                    nowMap[ch] = p
                                } else if (nowMap[ch] != null && nextMap[ch] == null && curStart!! > nowMap[ch]!!.stopMs) {
                                    nextMap[ch] = p
                                }
                            }
                            inProgramme = false
                            curChan = null; curTitle = null; curStart = null; curStop = null
                            if (doneForAll()) break
                        }
                    }
                }
                event = parser.next()
            }
            return channelIds.associateWith { ch -> nowMap[ch] to nextMap[ch] }
        }
    }
}
