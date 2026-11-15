package app.usagetime.tracker.reports;

import app.usagetime.tracker.model.SessionRecord;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Buckets foreground seconds into a 7-by-24 grid of local day and hour for one
 * week. Both passes through a repeated fall-back hour accumulate into the same
 * local hour cell (a cell can therefore hold more than 60 minutes that day); the
 * skipped spring-forward hour simply stays at zero.
 */
public final class WeekHeatmap {
    public static final int DAYS = 7;
    public static final int HOURS = 24;

    private WeekHeatmap() {
    }

    public static long[][] secondsPerHour(List<SessionRecord> sessions, LocalDate weekStart, ZoneId zone) {
        Cell[][] cells = cells(sessions, weekStart, zone);
        long[][] seconds = new long[DAYS][HOURS];
        for (int day = 0; day < DAYS; day++) {
            for (int hour = 0; hour < HOURS; hour++) {
                seconds[day][hour] = cells[day][hour].seconds();
            }
        }
        return seconds;
    }

    public static Cell[][] cells(List<SessionRecord> sessions, LocalDate weekStart, ZoneId zone) {
        Objects.requireNonNull(sessions);
        Objects.requireNonNull(weekStart);
        Objects.requireNonNull(zone);
        Instant rangeStart = weekStart.atStartOfDay(zone).toInstant();
        Instant rangeEnd = weekStart.plusDays(DAYS).atStartOfDay(zone).toInstant();
        CellAccumulator[][] grid = new CellAccumulator[DAYS][HOURS];
        for (int day = 0; day < DAYS; day++) {
            for (int hour = 0; hour < HOURS; hour++) {
                grid[day][hour] = new CellAccumulator();
            }
        }
        for (SessionRecord session : sessions) {
            Instant start = session.startedAt().isBefore(rangeStart) ? rangeStart : session.startedAt();
            Instant end = session.endedAt().isAfter(rangeEnd) ? rangeEnd : session.endedAt();
            Instant cursor = start;
            while (cursor.isBefore(end)) {
                ZonedDateTime local = cursor.atZone(zone);
                int dayIndex = (int) ChronoUnit.DAYS.between(weekStart, local.toLocalDate());
                Instant nextHour = local.truncatedTo(ChronoUnit.HOURS).plusHours(1).toInstant();
                Instant stepEnd = nextHour.isBefore(end) ? nextHour : end;
                long seconds = stepEnd.getEpochSecond() - cursor.getEpochSecond();
                if (seconds > 0) {
                    CellAccumulator cell = grid[dayIndex][local.getHour()];
                    cell.seconds += seconds;
                    cell.apps.compute(session.appId(), (id, previous) ->
                            new AppTime(id, session.name(), seconds + (previous == null ? 0 : previous.seconds())));
                }
                cursor = stepEnd;
            }
        }
        Cell[][] result = new Cell[DAYS][HOURS];
        for (int day = 0; day < DAYS; day++) {
            for (int hour = 0; hour < HOURS; hour++) {
                CellAccumulator cell = grid[day][hour];
                List<AppTime> apps = cell.apps.values().stream()
                        .sorted(Comparator.comparingLong(AppTime::seconds).reversed()
                                .thenComparing(AppTime::name, String.CASE_INSENSITIVE_ORDER)
                                .thenComparing(AppTime::appId))
                        .toList();
                result[day][hour] = new Cell(cell.seconds, apps);
            }
        }
        return result;
    }

    public record AppTime(String appId, String name, long seconds) {
    }

    public record Cell(long seconds, List<AppTime> apps) {
        public Cell {
            apps = List.copyOf(apps);
        }
    }

    private static final class CellAccumulator {
        private long seconds;
        private final Map<String, AppTime> apps = new HashMap<>();
    }

    /**
     * Intensity bucket for rendering: 0 = none, 1 = under 15 minutes,
     * 2 = 15-29, 3 = 30-44, 4 = 45 minutes or more.
     */
    public static int intensityBucket(long seconds) {
        if (seconds <= 0) {
            return 0;
        }
        long minutes = seconds / 60;
        if (minutes < 15) {
            return 1;
        }
        if (minutes < 30) {
            return 2;
        }
        if (minutes < 45) {
            return 3;
        }
        return 4;
    }
}