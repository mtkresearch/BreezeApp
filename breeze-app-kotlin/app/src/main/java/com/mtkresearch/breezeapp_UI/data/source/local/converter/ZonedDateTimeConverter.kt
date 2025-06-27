package com.mtkresearch.breezeapp_UI.data.source.local.converter

import androidx.room.TypeConverter
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Room TypeConverter for [ZonedDateTime] to allow it to be stored in the database.
 * It converts ZonedDateTime to an ISO-8601 string and back.
 */
class ZonedDateTimeConverter {
    private val formatter = DateTimeFormatter.ISO_ZONED_DATE_TIME

    @TypeConverter
    fun fromZonedDateTime(date: ZonedDateTime?): String? {
        return date?.format(formatter)
    }

    @TypeConverter
    fun toZonedDateTime(dateString: String?): ZonedDateTime? {
        return dateString?.let {
            try {
                ZonedDateTime.parse(it, formatter)
            } catch (e: Exception) {
                null
            }
        }
    }
} 