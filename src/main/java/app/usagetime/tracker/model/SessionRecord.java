package app.usagetime.tracker.model;

import java.time.Instant;

public record SessionRecord(
        String appId,
        String name,
        String category,
        Instant startedAt,
        Instant endedAt,
        long seconds,
        boolean inProgress
) {
}
