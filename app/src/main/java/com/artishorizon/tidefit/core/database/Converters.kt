package com.artishorizon.tidefit.core.database

import androidx.room.TypeConverter
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate

class Converters {
    private val listSerializer = ListSerializer(String.serializer())

    @TypeConverter fun instantToLong(value: Instant?): Long? = value?.toEpochMilli()
    @TypeConverter fun longToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    /** ISO-8601 text sorts chronologically, so range queries on dates work in SQL. */
    @TypeConverter fun dateToString(value: LocalDate?): String? = value?.toString()
    @TypeConverter fun stringToDate(value: String?): LocalDate? = value?.let(LocalDate::parse)

    @TypeConverter fun listToString(value: List<String>): String = Json.encodeToString(listSerializer, value)
    @TypeConverter fun stringToList(value: String): List<String> =
        if (value.isBlank()) emptyList() else Json.decodeFromString(listSerializer, value)
}
