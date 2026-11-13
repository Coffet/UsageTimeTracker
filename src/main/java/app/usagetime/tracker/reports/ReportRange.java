package app.usagetime.tracker.reports;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

public record ReportRange(Instant startInclusive, Instant endExclusive) {
    public ReportRange {
        Objects.requireNonNull(startInclusive);
        Objects.requireNonNull(endExclusive);
        if (!startInclusive.isBefore(endExclusive)) {
            throw new IllegalArgumentException("Report range must have a positive duration.");
        }
    }

    public static ReportRange throughDate(String period, LocalDate selectedDate, ZoneId zone) {
        Objects.requireNonNull(selectedDate);
        Objects.requireNonNull(zone);
        LocalDate startDate = switch (period) {
            case "Week" -> selectedDate.minusDays(selectedDate.getDayOfWeek().getValue() - 1L);
            case "Month" -> selectedDate.withDayOfMonth(1);
            case "Day" -> selectedDate;
            default -> throw new IllegalArgumentException("Unknown report period: " + period);
        };
        return new ReportRange(
                startDate.atStartOfDay(zone).toInstant(),
                selectedDate.plusDays(1).atStartOfDay(zone).toInstant()
        );
    }
}
