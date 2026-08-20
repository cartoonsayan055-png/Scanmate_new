package com.synthbyte.scanmate.utils

import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import java.util.Locale

data class QrPayloadInfo(
    val typeLabel: String,
    val summary: String,
    val actionLabel: String? = null,
    val actionUri: Uri? = null
)

object QrPayloadParser {
    fun parse(rawValue: String): QrPayloadInfo {
        val value = rawValue.trim()
        val upper = value.uppercase(Locale.US)
        return when {
            upper.startsWith("BEGIN:VCARD") -> QrPayloadInfo("Business card", parseVCard(value), null, null)
            upper.startsWith("MECARD:") -> QrPayloadInfo("Business card", parseMeCard(value), null, null)
            upper.startsWith("WIFI:") -> QrPayloadInfo("Wi‑Fi QR", parseWifi(value), null, null)
            value.startsWith("mailto:", ignoreCase = true) -> QrPayloadInfo("Email", value.removePrefixIgnoreCase("mailto:"), "Open email", Uri.parse(value))
            value.startsWith("tel:", ignoreCase = true) -> QrPayloadInfo("Phone", value.removePrefixIgnoreCase("tel:"), "Call", Uri.parse(value))
            value.startsWith("sms:", ignoreCase = true) -> QrPayloadInfo("SMS", value.removePrefixIgnoreCase("sms:"), "Message", Uri.parse(value))
            isSafeHttpUrl(value) -> QrPayloadInfo("Website", value, "Open link", Uri.parse(value))
            else -> QrPayloadInfo("Text", value.take(180), null, null)
        }
    }

    fun contactInsertIntent(rawValue: String): Intent? {
        val value = rawValue.trim()
        val upper = value.uppercase(Locale.US)
        if (!upper.startsWith("BEGIN:VCARD") && !upper.startsWith("MECARD:")) return null
        val name = extractContactField(value, listOf("FN", "N"))?.replace(';', ' ')?.replace(',', ' ')
        val phone = extractContactField(value, listOf("TEL"))
        val email = extractContactField(value, listOf("EMAIL"))
        if (name.isNullOrBlank() && phone.isNullOrBlank() && email.isNullOrBlank()) return null
        return Intent(ContactsContract.Intents.Insert.ACTION).apply {
            type = ContactsContract.RawContacts.CONTENT_TYPE
            putExtra(ContactsContract.Intents.Insert.NAME, name.orEmpty())
            putExtra(ContactsContract.Intents.Insert.PHONE, phone.orEmpty())
            putExtra(ContactsContract.Intents.Insert.EMAIL, email.orEmpty())
        }
    }

    private fun extractContactField(value: String, keys: List<String>): String? {
        if (value.startsWith("MECARD:", ignoreCase = true)) {
            val body = value.removePrefixIgnoreCase("MECARD:")
            body.split(';').forEach { part ->
                val key = part.substringBefore(':', missingDelimiterValue = "").uppercase(Locale.US)
                val data = part.substringAfter(':', missingDelimiterValue = "").trim()
                if (key in keys && data.isNotBlank()) return data
            }
        }
        value.lines().forEach { line ->
            val key = line.substringBefore(':', missingDelimiterValue = "").substringBefore(';').uppercase(Locale.US)
            val data = line.substringAfter(':', missingDelimiterValue = "").trim()
            if (key in keys && data.isNotBlank()) return data
        }
        return null
    }

    private fun parseVCard(value: String): String {
        val lines = value.lines().map { it.trim() }
        val name = lines.firstOrNull { it.startsWith("FN:", ignoreCase = true) }?.substringAfter(':')
            ?: lines.firstOrNull { it.startsWith("N:", ignoreCase = true) }?.substringAfter(':')?.replace(';', ' ')
        val phone = lines.firstOrNull { it.startsWith("TEL", ignoreCase = true) }?.substringAfter(':')
        val email = lines.firstOrNull { it.startsWith("EMAIL", ignoreCase = true) }?.substringAfter(':')
        return listOfNotNull(name?.takeIf { it.isNotBlank() }, phone?.takeIf { it.isNotBlank() }, email?.takeIf { it.isNotBlank() })
            .joinToString(" • ")
            .ifBlank { "Contact details detected" }
    }

    private fun parseMeCard(value: String): String {
        val body = value.removePrefixIgnoreCase("MECARD:")
        val parts = body.split(';').mapNotNull { part ->
            val key = part.substringBefore(':', missingDelimiterValue = "").uppercase(Locale.US)
            val data = part.substringAfter(':', missingDelimiterValue = "").trim()
            when (key) {
                "N" -> data.replace(',', ' ')
                "TEL", "EMAIL" -> data
                else -> null
            }?.takeIf { it.isNotBlank() }
        }
        return parts.take(4).joinToString(" • ").ifBlank { "Contact details detected" }
    }

    private fun parseWifi(value: String): String {
        fun field(name: String): String? = Regex("$name:([^;]*)", RegexOption.IGNORE_CASE).find(value)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }
        val ssid = field("S") ?: "Hidden network"
        val security = field("T") ?: "Unknown security"
        return "$ssid • $security"
    }

    private fun isSafeHttpUrl(value: String): Boolean {
        val uri = runCatching { Uri.parse(value) }.getOrNull() ?: return false
        return uri.scheme == "http" || uri.scheme == "https"
    }

    private fun String.removePrefixIgnoreCase(prefix: String): String =
        if (startsWith(prefix, ignoreCase = true)) substring(prefix.length) else this
}
