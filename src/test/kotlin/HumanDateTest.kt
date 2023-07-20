import common.HumanDate
import java.time.Instant
import java.time.ZoneId
import java.util.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.util.stream.Stream

/**
 * HumanDateTest - Unit tests for HumanDate class.
 *
 * @author Chris Athanas (realityexpanderdev@gmail.com)
 * @since 0.12 Kotlin Conversion
 */

const val ONE_SECOND = 1000L
const val ONE_MINUTE = ONE_SECOND * 60
const val ONE_HOUR = ONE_MINUTE * 60
const val ONE_DAY = ONE_HOUR * 24
const val ONE_MONTH = ONE_DAY * 30
const val ONE_YEAR = ONE_DAY * 365

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HumanDateTest {

////    @ParameterizedTest(name = "isPalindrome should return true for {0}")
////    @ValueSource(strings = ["Bob", "racecar", "Malayalam", ""])

    @ParameterizedTest(name = "given \"{0}\" should return {1}")
    @MethodSource("data")
    fun `Calling toDateTimeStr returns DateTime string is Success`(
        testDateTimeInstant: Instant,
        expectedDateTimeString: String,
        expectedDateStr: String,
        expectedTimeStr: String,
        expectedTimeAgoStr: String
    ) {
        assertEquals(
            expectedDateTimeString,
            HumanDate(testDateTimeInstant, ZoneId.of("UTC")).toDateTimeStr()
        )
    }

    @ParameterizedTest(name = "given \"{0}\" should return {2}")
    @MethodSource("data")
    fun `Calling toDateStr returns Date string is Success`(
        testDateTimeInstant: Instant,
        expectedDateTimeString: String,
        expectedDateStr: String,
        expectedTimeStr: String,
        expectedTimeAgoStr: String
    ) {
        assertEquals(
            expectedDateStr,
            HumanDate(testDateTimeInstant, ZoneId.of("UTC")).toDateStr()
        )
    }

    @ParameterizedTest(name = "given \"{0}\" should return {3}")
    @MethodSource("data")
    fun `Calling toTimeStr returns Time string is Success`(
        testDateTimeInstant: Instant,
        expectedDateTimeString: String,
        expectedDateStr: String,
        expectedTimeStr: String,
        expectedTimeAgoStr: String
    ) {
        assertEquals(
            expectedTimeStr,
            HumanDate(testDateTimeInstant, ZoneId.of("UTC")).toTimeStr()
        )
    }

    @ParameterizedTest(name = "given \"{0}\" should return {4}")
    @MethodSource("data")
    fun `Calling toTimeAgoStr for Two Years Ago is Success`(
        testDateTimeInstant: Instant,
        expectedDateTimeString: String,
        expectedDateStr: String,
        expectedTimeStr: String,
        expectedTimeAgoStr: String
    ) {
        // • ARRANGE
        val now = testDateTimeInstant.toEpochMilli()
        val oneYearAgo = testDateTimeInstant.toEpochMilli() - ONE_YEAR * 2

        // • ACT & ASSERT
        assertEquals(
            expectedTimeAgoStr,
            HumanDate(oneYearAgo, ZoneId.of("UTC")).toTimeAgoStr(now)
        )
    }

    @Test
    fun `Calling ToTimeAgo on All Time period Strings`() {
        // • ARRANGE
        val now = Instant.parse("2019-01-01T00:00:00.00Z").toEpochMilli()
        val timeInTheFuture = now + ONE_SECOND
        val timeAgoJustNow = now - ONE_SECOND + 500 // 500ms
        val timeAgoSeconds = now - ONE_SECOND * 10
        val timeAgoMinutes = now - ONE_MINUTE * 10
        val timeAgoHours = now - ONE_HOUR * 10
        val timeAgoDays = now - ONE_DAY * 10
        val timeAgoMonths = now - ONE_MONTH * 10
        val timeAgoYears = now - ONE_YEAR * 10

        // • ACT & ASSERT
        assertEquals("in the future", HumanDate(timeInTheFuture).toTimeAgoStr(now))
        assertEquals("just now", HumanDate(timeAgoJustNow).toTimeAgoStr(now))
        assertEquals("10 seconds ago", HumanDate(timeAgoSeconds).toTimeAgoStr(now))
        assertEquals("10 minutes ago", HumanDate(timeAgoMinutes).toTimeAgoStr(now))
        assertEquals("10 hours ago", HumanDate(timeAgoHours).toTimeAgoStr(now))
        assertEquals("10 days ago", HumanDate(timeAgoDays).toTimeAgoStr(now))
        assertEquals("10 months ago", HumanDate(timeAgoMonths).toTimeAgoStr(now))
        assertEquals("10 years ago", HumanDate(timeAgoYears).toTimeAgoStr(now))
    }

    companion object {
        @JvmStatic
        fun data(): Stream<Arguments> = Stream.of(
            Arguments.of(
                Instant.parse("2020-02-01T00:00:00.00Z"),
                "2020-02-01 00:00:00",
                "2020-02-01",
                "00:00:00",
                "2 years ago"
            ),
            Arguments.of(
                Instant.parse("2020-02-01T00:00:01.00Z"),
                "2020-02-01 00:00:01",
                "2020-02-01",
                "00:00:01",
                "2 years ago"
            ),
            Arguments.of(
                Instant.parse("2020-02-01T00:01:00.00Z"),
                "2020-02-01 00:01:00",
                "2020-02-01",
                "00:01:00",
                "2 years ago"
            ),
            Arguments.of(
                Instant.parse("2020-02-01T01:00:00.00Z"),
                "2020-02-01 01:00:00",
                "2020-02-01",
                "01:00:00",
                "2 years ago"
            ),
            Arguments.of(
                Instant.parse("2020-02-02T00:00:00.00Z"),
                "2020-02-02 00:00:00",
                "2020-02-02",
                "00:00:00",
                "2 years ago"
            ),
            Arguments.of(
                Instant.parse("2020-03-01T00:00:00.00Z"),
                "2020-03-01 00:00:00",
                "2020-03-01",
                "00:00:00",
                "2 years ago"
            ),
            Arguments.of(
                Instant.parse("2021-02-01T00:00:35.00Z"),
                "2021-02-01 00:00:35",
                "2021-02-01",
                "00:00:35",
                "2 years ago"
            ),
            Arguments.of(
                Instant.parse("2021-02-01T00:59:00.00Z"),
                "2021-02-01 00:59:00",
                "2021-02-01",
                "00:59:00",
                "2 years ago"
            ),
            Arguments.of(
                Instant.parse("2022-02-01T00:00:00.00Z"),
                "2022-02-01 00:00:00",
                "2022-02-01",
                "00:00:00",
                "2 years ago"
            )
        )
    }
}