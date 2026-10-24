package com.local.unifiedinbox.util

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

object TimeFormat {
    private val utcFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            .withZone(ZoneOffset.UTC)

    private val localFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
            .withZone(ZoneId.systemDefault())

    private val timeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)
            .withZone(ZoneId.systemDefault())

    private val monthDayFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)
            .withZone(ZoneId.systemDefault())

    private val fullDateFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)
            .withZone(ZoneId.systemDefault())

    fun isoUtc(epochMillis: Long): String = utcFormatter.format(Instant.ofEpochMilli(epochMillis))

    fun isoUtcOrNull(epochMillis: Long?): String? =
        epochMillis?.let { utcFormatter.format(Instant.ofEpochMilli(it)) }

    fun local(epochMillis: Long): String =
        localFormatter.format(Instant.ofEpochMilli(epochMillis))

    fun localTime(epochMillis: Long): String =
        timeFormatter.format(Instant.ofEpochMilli(epochMillis))

    fun monthDay(epochMillis: Long): String =
        monthDayFormatter.format(Instant.ofEpochMilli(epochMillis))

    fun fullDate(epochMillis: Long): String =
        fullDateFormatter.format(Instant.ofEpochMilli(epochMillis))
}
