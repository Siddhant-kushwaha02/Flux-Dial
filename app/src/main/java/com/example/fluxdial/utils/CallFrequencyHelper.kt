package com.example.fluxdial.utils

import android.content.Context
import android.provider.CallLog

data class FrequentContact(
    val name: String,
    val phoneNumber: String,
    val callCount: Int,
    val photoUri: String?
)

object CallFrequencyHelper {

    fun getFrequentContacts(
        context: Context,
        limit: Int = 5
    ): List<FrequentContact> {
        val frequencyMap = mutableMapOf<String, Int>()
        val numberToName = mutableMapOf<String, String>()
        val numberToPhoto = mutableMapOf<String, String?>()

        // Read system call log
        val projection = arrayOf(
            CallLog.Calls.NUMBER,
            CallLog.Calls.CACHED_NAME,
            CallLog.Calls.CACHED_PHOTO_URI,
            CallLog.Calls.TYPE
        )

        try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null,
                "${CallLog.Calls.DATE} DESC"
            )?.use { cursor ->
                val numberIndex = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                val nameIndex = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val photoIndex = cursor.getColumnIndex(CallLog.Calls.CACHED_PHOTO_URI)
                val typeIndex = cursor.getColumnIndex(CallLog.Calls.TYPE)

                while (cursor.moveToNext()) {
                    val number = cursor.getString(numberIndex) ?: continue
                    val name = cursor.getString(nameIndex)
                    val photo = cursor.getString(photoIndex)
                    val type = cursor.getInt(typeIndex)

                    // Count both incoming and outgoing, not missed
                    if (type == CallLog.Calls.INCOMING_TYPE ||
                        type == CallLog.Calls.OUTGOING_TYPE) {
                        val normalised = normaliseNumber(number)
                        frequencyMap[normalised] =
                            (frequencyMap[normalised] ?: 0) + 1
                        if (name != null) numberToName[normalised] = name
                        if (photo != null) numberToPhoto[normalised] = photo
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Sort by frequency, take top N
        return frequencyMap.entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { (number, count) ->
                FrequentContact(
                    name = numberToName[number] ?: number,
                    phoneNumber = number,
                    callCount = count,
                    photoUri = numberToPhoto[number]
                )
            }
    }

    private fun normaliseNumber(number: String): String {
        return number.filter { it.isDigit() || it == '+' }
    }
}
