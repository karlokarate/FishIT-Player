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
        client.newCall(Request.Builder().url(url).build()).execute().use { res ->
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
}

