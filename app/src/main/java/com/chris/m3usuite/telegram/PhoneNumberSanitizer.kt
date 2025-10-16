package com.chris.m3usuite.telegram

import android.content.Context
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import java.util.Locale

/**
 * Normalizes phone numbers for Telegram login flows.
 * Removes formatting characters, translates leading "00" to "+"
 * and, if needed, converts the number to E.164 using the best matching
 * country ISO derived from network, SIM or device locale.
 */
object PhoneNumberSanitizer {
    private val trimRegex = Regex("[\\s\\-()]+")

    fun sanitize(context: Context, raw: String): String {
        var value = raw.replace(trimRegex, "").trim()
        if (value.startsWith("00")) {
            value = "+" + value.drop(2)
        }
        if (!value.startsWith("+")) {
            val iso = detectDefaultCountryIso(context)
            if (!iso.isNullOrBlank()) {
                val normalized = runCatching { PhoneNumberUtils.formatNumberToE164(value, iso) }.getOrNull()
                if (!normalized.isNullOrBlank()) {
                    value = normalized
                }
            }
        }
        return value
    }

    private fun detectDefaultCountryIso(context: Context): String? {
        val tm = context.applicationContext.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val candidates = listOfNotNull(
            tm?.networkCountryIso,
            tm?.simCountryIso,
            Locale.getDefault().country
        )
        val iso = candidates.firstOrNull { !it.isNullOrBlank() }
        return iso?.uppercase(Locale.US)
    }
}

