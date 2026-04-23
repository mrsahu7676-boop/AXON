package com.axon.assistant

import android.content.Context
import android.provider.ContactsContract

/**
 * Resolves a contact name (full or partial) to a phone number using the device contacts.
 */
class ContactResolver(private val context: Context) {

    /**
     * Find a phone number for a contact name (case-insensitive, partial match).
     * @return Raw phone number string, or null if not found.
     */
    fun resolveNumber(name: String): String? {
        if (name.isBlank()) return null

        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
        )
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%${name.trim()}%")

        return try {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    cursor.getString(idx)
                } else null
            }
        } catch (e: SecurityException) {
            null // READ_CONTACTS permission not granted
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Resolve number for WhatsApp — strips formatting and prepends India country code if needed.
     */
    fun resolveWhatsAppNumber(name: String): String? {
        val raw = resolveNumber(name) ?: return null
        val digits = raw.replace("[^0-9+]".toRegex(), "")
        return when {
            digits.startsWith("+")    -> digits.removePrefix("+")
            digits.startsWith("91")   -> digits
            digits.length == 10       -> "91$digits"
            else                      -> digits
        }
    }

    /**
     * Strip number to dialable format (remove spaces, dashes etc.).
     */
    fun toDialable(number: String): String =
        number.replace("[^0-9+]".toRegex(), "")
}
