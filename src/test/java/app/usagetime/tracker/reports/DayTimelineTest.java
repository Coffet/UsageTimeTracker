package app.usagetime.tracker.reports;

import app.usagetime.tracker.model.SessionRecord;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DayTimelineTest {
    private static final ZoneId UTC = ZoneId.of("UTC");

    private static SessionRecord session(String name, String start, String end, boolean inProgress) {
        Instant started = Instant.parse(start);
        Instant ended = Instant.parse(end);
        return new SessionRecord(name.toLowerCase(), name, null, started, ended,
                ended.getEpochSecond() - started.getEpochSecond(), inProgress);
    }

    @Test
    void clipsSessionsToTheSelectedDayAndSortsByStart() {
        LocalDate day = LocalDate.of(2026, 6, 17);
        List<DayTimeline.Block> blocks = DayTimeline.blocksForDay(List.of(
                session("Editor", "2026-06-16T23:00:00Z", "2026-06-17T01:00:00Z", false),
                session("Browser", "2026-06-17T09:00:00Z", "2026-06-17T09:30:00Z", false)),
                day, UTC);

        assertEquals(2, blocks.size());
        assertEquals(0, blocks.get(0).startMinute());
        assertEquals(60, blocks.get(0).endMinuteExclusive());
        assertEquals(540, blocks.get(1).startMinute());
        assertEquals(570, blocks.get(1).endMinuteExclusive());
    }

    @Test
    void sessionRunningPastMidnightEndsAtMinute1440() {
        LocalDate day = LocalDate.of(2026, 6, 17);
        List<DayTimeline.Block> blocks = DayTimeline.blocksForDay(List.of(
                session("Editor", "2026-06-17T22:15:00Z", "2026-06-18T00:30:00Z", false)),
                day, UTC);

        assertEquals(1, blocks.size());
        assertEquals(22 * 60 + 15, blocks.get(0).startMinute());
        assertEquals(DayTimeline.MINUTES_PER_DAY, blocks.get(0).endMinuteExclusive());
    }

    @Test
    void marksInProgressSessions() {
        LocalDate day = LocalDate.of(2026, 6, 17);
        List<DayTimeline.Block> blocks = DayTimeline.blocksForDay(List.of(
                session("Editor", "2026-06-17T10:00:00Z", "2026-06-17T10:05:00Z", true)),
                day, UTC);

        assertTrue(blocks.get(0).inProgress());
    }

    @Test
    void skipsSessionsOutsideTheDay() {
        LocalDate day = LocalDate.of(2026, 6, 17);
        List<DayTimeline.Block> blocks = DayTimeline.blocksForDay(List.of(
                session("Editor", "2026-06-16T10:00:00Z", "2026-06-16T11:00:00Z", false)),
                day, UTC);

        assertTrue(blocks.isEmpty());
    }

    @Test
    void springForwardDayPositionsBlocksByLocalWallClock() {
        ZoneId zone = ZoneId.of("America/Los_Angeles");
        LocalDate day = LocalDate.of(2026, 3, 8);
        // 1:30 AM PST to 3:30 AM PDT is one real hour; 2 AM does not exist locally.
        List<DayTimeline.Block> blocks = DayTimeline.blocksForDay(List.of(
                session("Editor", "2026-03-08T09:30:00Z", "2026-03-08T10:30:00Z", false)),
                day, zone);

        assertEquals(1, blocks.size());
        assertEquals(90, blocks.get(0).startMinute());
        assertEquals(210, blocks.get(0).endMinuteExclusive());
    }

    @Test
    void fallBackDayLandsBothPassesOfTheRepeatedHourOnTheSameMinutes() {
        ZoneId zone = ZoneId.of("America/Los_Angeles");
        LocalDate day = LocalDate.of(2026, 11, 1);
        List<DayTimeline.Block> blocks = DayTimeline.blocksForDay(List.of(
                session("Editor", "2026-11-01T08:15:00Z", "2026-11-01T08:45:00Z", false),
                session("Browser", "2026-11-01T09:15:00Z", "2026-11-01T09:45:00Z", false)),
                day, zone);

        assertEquals(2, blocks.size());
        assertEquals(75, blocks.get(0).startMinute());
        assertEquals(105, blocks.get(0).endMinuteExclusive());
        assertEquals(75, blocks.get(1).startMinute());
        assertEquals(105, blocks.get(1).endMinuteExclusive());
    }
}