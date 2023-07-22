package common

import org.jetbrains.annotations.Debug
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * HumanDate - Utility class for converting Instants and EpochMillis to human-readable
 * localized-time-zone date/time strings.
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.12 Kotlin Conversion
 */

@Debug.Renderer(text = "TimeAgoStr: {toTimeAgoStr(Instant.now())}",
    childrenArray = "dateTimeInstant.toArray()",
    hasChildren = "")
class HumanDate(
    val dateTimeInstant: Instant,
    val timeZone: ZoneId = ZoneId.of("UTC")
) {
    constructor(epochMillis: Long) : this(Instant.ofEpochMilli(epochMillis))
    constructor(epochMillis: Long, timeZone: ZoneId) : this(Instant.ofEpochMilli(epochMillis), timeZone)

    fun toDateTimeStr(): String {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .format(
                LocalDateTime.ofInstant(
                    dateTimeInstant,
                    timeZone
                )
            )
    }

    fun toDateStr(): String {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd")
            .format(
                LocalDateTime.ofInstant(
                    dateTimeInstant,
                    timeZone
                )
            )
    }

    fun toTimeStr(): String {
        return DateTimeFormatter.ofPattern("HH:mm:ss")
            .format(
                LocalDateTime.ofInstant(
                    dateTimeInstant,
                    timeZone
                )
            )
    }

    @JvmOverloads
    fun toTimeAgoStr(nowInstant: Instant? = Instant.now()): String {
        val diff = nowInstant!!.toEpochMilli() - dateTimeInstant.toEpochMilli()

        if (diff < 0) {
            return "in the future"
        }
        if (diff < 1000) {
            return "just now"
        }
        if (diff < 60 * 1000) {
            return (diff / 1000).toString() + " seconds ago"
        }
        if (diff < 60 * 60 * 1000) {
            return (diff / (60 * 1000)).toString() + " minutes ago"
        }
        if (diff < 24 * 60 * 60 * 1000) {
            return (diff / (60 * 60 * 1000)).toString() + " hours ago"
        }
        if (diff < 30 * 24 * 60 * 60 * 1000L) {
            return (diff / (24 * 60 * 60 * 1000)).toString() + " days ago"
        }
        return if (diff < 365 * 24 * 60 * 60 * 1000L) {
            (diff / (30 * 24 * 60 * 60 * 1000L)).toString() + " months ago"
        } else (diff / (365 * 24 * 60 * 60 * 1000L)).toString() + " years ago"
    }

    fun toTimeAgoStr(nowMillis: Long): String {
        return toTimeAgoStr(Instant.ofEpochMilli(nowMillis))
    }

    fun millis(): Long {
        return dateTimeInstant.toEpochMilli()
    }
}
