package app.usagetime.tracker.reports;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReportRangeTest {
    @Test
    void weekAndMonthRangesIncludeTheSelectedDateAndStartOnExpectedDay() {
        LocalDate selected = LocalDate.of(2026, 6, 17);
        ZoneId zone = ZoneId.of("America/Los_Angeles");

        ReportRange week = ReportRange.throughDate("Week", selected, zone);
        ReportRange month = ReportRange.throughDate("Month", selected, zone);

        assertEquals(LocalDate.of(2026, 6, 15),
                week.startInclusive().atZone(zone).toLocalDate());
        assertEquals(selected.plusDays(1), week.endExclusive().atZone(zone).toLocalDate());
        assertEquals(LocalDate.of(2026, 6, 1),
                month.startInclusive().atZone(zone).toLocalDate());
        assertEquals(selected.plusDays(1), month.endExclusive().atZone(zone).toLocalDate());
    }

    @Test
    void dailyRangeUsesLocalMidnightAcrossDaylightSavingTransitions() {
        ZoneId zone = ZoneId.of("America/Los_Angeles");
        ReportRange range = ReportRange.throughDate("Day", LocalDate.of(2026, 3, 8), zone);

        assertEquals(23, ChronoUnit.HOURS.between(range.startInclusive(), range.endExclusive()));
    }

    @Test
    void rejectsUnknownPeriods() {
        assertThrows(IllegalArgumentException.class, () ->
                ReportRange.throughDate("Year", LocalDate.of(2026, 1, 1), ZoneId.of("UTC")));
    }
}
