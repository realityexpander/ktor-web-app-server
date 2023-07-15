package common;

import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * HumanDate - Utility class for converting Instants and EpochMillis to human-readable localized-time-zone date/time strings.
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.11
 */

public class HumanDate {

    private final Instant dateTimeInstant;
    private final ZoneId timeZone;         // the local time zone for the user

    public HumanDate(Instant dateTimeInstant, ZoneId timeZone) {
        this.dateTimeInstant = dateTimeInstant;

        if (timeZone != null) {
            this.timeZone = timeZone;
        } else {
            this.timeZone = ZoneId.systemDefault();
        }
    }
    public HumanDate(Instant dateTimeInstant) {
        this(dateTimeInstant, null);
    }
    public HumanDate(long epochMillis) {
        this(Instant.ofEpochMilli(epochMillis), null);
    }
    public HumanDate(long epochMillis, ZoneId timeZone) {
        this(Instant.ofEpochMilli(epochMillis), timeZone);
    }

    public String toDateTimeStr() {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .format(LocalDateTime.ofInstant(
                    dateTimeInstant,
                    timeZone
                ));
    }

    public String toDateStr() {
        return DateTimeFormatter.ofPattern("yyyy-MM-dd")
                .format(LocalDateTime.ofInstant(
                    dateTimeInstant,
                    timeZone
                ));
    }

    public String toTimeStr() {
        return DateTimeFormatter.ofPattern("HH:mm:ss")
                .format(LocalDateTime.ofInstant(
                    dateTimeInstant,
                    timeZone
                ));
    }

    public String toTimeAgoStr(@Nullable Instant nowInstant) {
        if (nowInstant == null) {
            nowInstant = Instant.now();
        }
        long diff = nowInstant.toEpochMilli() - dateTimeInstant.toEpochMilli();

        if (diff < 0) {
            return "in the future";
        }
        if (diff < 1000) {
            return "just now";
        }
        if (diff < 60 * 1000) {
            return diff / 1000 + " seconds ago";
        }
        if (diff < 60 * 60 * 1000) {
            return diff / (60 * 1000) + " minutes ago";
        }
        if (diff < 24 * 60 * 60 * 1000) {
            return diff / (60 * 60 * 1000) + " hours ago";
        }
        if (diff < 30 * 24 * 60 * 60 * 1000L) {
            return diff / (24 * 60 * 60 * 1000) + " days ago";
        }
        if (diff < 365 * 24 * 60 * 60 * 1000L) {
            return diff / (30 * 24 * 60 * 60 * 1000L) + " months ago";
        }
        return diff / (365 * 24 * 60 * 60 * 1000L) + " years ago";
    }
    public String toTimeAgoStr(long nowMillis) {
        return toTimeAgoStr(Instant.ofEpochMilli(nowMillis));
    }
    public String toTimeAgoStr() {
        return toTimeAgoStr(Instant.now());
    }

    public long millis() {
        return dateTimeInstant.toEpochMilli();
    }
}
