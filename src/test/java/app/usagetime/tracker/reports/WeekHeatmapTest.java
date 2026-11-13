package app.usagetime.tracker.reports;

import app.usagetime.tracker.model.SessionRecord;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WeekHeatmapTest {
    private static final ZoneId UTC = ZoneId.of("UTC");

    private static SessionRecord session(String name, String start, String end) {
        Instant started = Instant.parse(start);
        Instant ended = Instant.parse(end);
        return new SessionRecord(name.toLowerCase(), name, null, started, ended,
                ended.getEpochSecond() - started.getEpochSecond(), false);
    }

    @Test
    void bucketsSecondsIntoLocalDayAndHourCells() {
        LocalDate weekStart = LocalDate.of(2026, 6, 15); // Monday
        long[][] grid = WeekHeatmap.secondsPerHour(List.of(
                session("Editor", "2026-06-15T00:30:00Z", "2026-06-15T01:30:00Z")),
                weekStart, UTC);

        assertEquals(1800, grid[0][0]);
        assertEquals(1800, grid[0][1]);
        long total = 0;
        for (long[] day : grid) {
            for (long cell : day) {
                total += cell;
            }
        }
        assertEquals(3600, total);
    }

    @Test
    void clipsSessionsToTheWeekBounds() {
        LocalDate weekStart = LocalDate.of(2026, 6, 15);
        long[][] grid = WeekHeatmap.secondsPerHour(List.of(
                session("Editor", "2026-06-14T23:00:00Z", "2026-06-15T01:00:00Z"),
                session("Browser", "2026-06-21T23:30:00Z", "2026-06-22T02:00:00Z")),
                weekStart, UTC);

        assertEquals(3600, grid[0][0]);
        assertEquals(1800, grid[6][23]);
    }

    @Test
    void ignoresSessionsOutsideTheWeek() {
        LocalDate weekStart = LocalDate.of(2026, 6, 15);
        long[][] grid = WeekHeatmap.secondsPerHour(List.of(
                session("Editor", "2026-06-10T10:00:00Z", "2026-06-10T11:00:00Z")),
                weekStart, UTC);

        for (long[] day : grid) {
            for (long cell : day) {
                assertEquals(0, cell);
            }
        }
    }

    @Test
    void springForwardSkippedHourStaysEmpty() {
        ZoneId zone = ZoneId.of("America/Los_Angeles");
        LocalDate weekStart = LocalDate.of(2026, 3, 2); // Monday of the 2026-03-08 transition week
        // 1:00 AM PST to 4:00 AM PDT is two real hours; 2 AM is skipped.
        long[][] grid = WeekHeatmap.secondsPerHour(List.of(
                session("Editor", "2026-03-08T09:00:00Z", "2026-03-08T11:00:00Z")),
                weekStart, zone);

        assertEquals(3600, grid[6][1]);
        assertEquals(0, grid[6][2]);
        assertEquals(3600, grid[6][3]);
    }

    @Test
    void fallBackRepeatedHourAccumulatesBothPasses() {
        ZoneId zone = ZoneId.of("America/Los_Angeles");
        LocalDate weekStart = LocalDate.of(2026, 10, 26); // Monday of the 2026-11-01 transition week
        // 12:30 AM PDT to 2:30 AM PST is three real hours; 1 AM happens twice.
        long[][] grid = WeekHeatmap.secondsPerHour(List.of(
                session("Editor", "2026-11-01T07:30:00Z", "2026-11-01T10:30:00Z")),
                weekStart, zone);

        assertEquals(1800, grid[6][0]);
        assertEquals(7200, grid[6][1]);
        assertEquals(1800, grid[6][2]);
    }

    @Test
    void intensityBucketFollowsTheLegendRanges() {
        assertEquals(0, WeekHeatmap.intensityBucket(0));
        assertEquals(1, WeekHeatmap.intensityBucket(59));
        assertEquals(1, WeekHeatmap.intensityBucket(60));
        assertEquals(1, WeekHeatmap.intensityBucket(14 * 60));
        assertEquals(2, WeekHeatmap.intensityBucket(15 * 60));
        assertEquals(3, WeekHeatmap.intensityBucket(30 * 60));
        assertEquals(4, WeekHeatmap.intensityBucket(45 * 60));
        assertEquals(4, WeekHeatmap.intensityBucket(7200));
    }
}