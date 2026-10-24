package com.cosmos.unreddit.data.remote.api.reddit.graphql.mapper

import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal object GraphQlTimestamp {
    fun toEpochSeconds(value: String): Long = parseOrNull(value) ?: INVALID_EPOCH_SECONDS

    fun parseOrNull(value: String): Long? {
        if (!ISO_8601_TIMESTAMP.matches(value)) return null
        val withoutFraction: String = value.replace(FRACTIONAL_SECONDS, "")
        val normalizedTimestamp: String = withoutFraction
            .replace(COLON_OFFSET, "$1$2")
            .replace(UTC_SUFFIX, UTC_OFFSET)
        val parsePosition: ParsePosition = ParsePosition(0)
        val parsedDate: Date? = synchronized(TIMESTAMP_FORMATTER) {
            TIMESTAMP_FORMATTER.parse(normalizedTimestamp, parsePosition)
        }
        if (parsedDate == null || parsePosition.index != normalizedTimestamp.length) {
            return null
        }
        return parsedDate.time / MILLISECONDS_PER_SECOND
    }

    private const val TIMESTAMP_PATTERN: String = "yyyy-MM-dd'T'HH:mm:ssZ"
    private const val UTC_OFFSET: String = "+0000"
    private const val INVALID_EPOCH_SECONDS: Long = 0L
    private const val MILLISECONDS_PER_SECOND: Long = 1000L
    private val ISO_8601_TIMESTAMP: Regex =
        Regex("""^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:Z|[+-]\d{2}:?\d{2})$""")
    private val FRACTIONAL_SECONDS: Regex = Regex("""\.\d+""")
    private val COLON_OFFSET: Regex = Regex("""([+-]\d{2}):(\d{2})$""")
    private val UTC_SUFFIX: Regex = Regex("""Z$""")
    private val TIMESTAMP_FORMATTER: SimpleDateFormat =
        SimpleDateFormat(TIMESTAMP_PATTERN, Locale.US).apply { isLenient = false }
}
