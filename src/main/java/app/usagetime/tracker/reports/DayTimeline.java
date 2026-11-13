package app.usagetime.tracker.reports;

import app.usagetime.tracker.model.SessionRecord;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Clips foreground sessions to one local day and expresses them as minute-of-day
 * blocks for a 24-hour timeline strip. Positions use local wall-clock minutes, so
 * on a spring-forward day the skipped hour simply holds no blocks and on a
 * fall-back day both passes through the repeated hour land on the same minutes.
 */
public final class DayTimeline {
    public static final int MINUTES_PER_DAY = 24 * 60;

    public record Block(String appId, String name, String category,
                        int startMinute, int endMinuteExclusive, boolean inProgress) {
    }

    private DayTimeline() {
    }

    public static List<Block> blocksForDay(List<SessionRecord> sessions, LocalDate day, ZoneId zone) {
        Objects.requireNonNull(sessions);
        Objects.requireNonNull(day);
        Objects.requireNonNull(zone);
        Instant dayStart = day.atStartOfDay(zone).toInstant();
        Instant dayEnd = day.plusDays(1).atStartOfDay(zone).toInstant();
        List<Block> blocks = new ArrayList<>();
        for (SessionRecord session : sessions) {
            Instant start = session.startedAt().isBefore(dayStart) ? dayStart : session.startedAt();
            Instant end = session.endedAt().isAfter(dayEnd) ? dayEnd : session.endedAt();
            if (!start.isBefore(end)) {
                continue;
            }
            int startMinute = minuteOfDay(start, zone, false);
            int endMinute = end.equals(dayEnd) ? MINUTES_PER_DAY : minuteOfDay(end, zone, true);
            if (endMinute <= startMinute) {
                endMinute = Math.min(MINUTES_PER_DAY, startMinute + 1);
            }
            blocks.add(new Block(session.appId(), session.name(), session.category(),
                    startMinute, endMinute, session.inProgress()));
        }
        blocks.sort(Comparator.comparingInt(Block::startMinute));
        return blocks;
    }

    private static int minuteOfDay(Instant instant, ZoneId zone, boolean roundUp) {
        ZonedDateTime local = instant.atZone(zone);
        int minute = local.getHour() * 60 + local.getMinute();
        if (roundUp && (local.getSecond() > 0 || local.getNano() > 0)) {
            minute += 1;
        }
        return minute;
    }
}