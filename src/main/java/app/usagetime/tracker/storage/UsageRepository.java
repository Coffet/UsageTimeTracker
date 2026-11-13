package app.usagetime.tracker.storage;

import app.usagetime.tracker.model.AppUsage;
import app.usagetime.tracker.model.SessionRecord;
import app.usagetime.tracker.model.TrackedApp;

import java.time.Instant;
import java.util.List;

public interface UsageRepository extends AutoCloseable {
    long startSession(String appId, String appName, Instant startedAt);

    void checkpointSession(long sessionId, Instant at);

    void finishSession(long sessionId, Instant endedAt);

    void recoverOpenSessions();

    List<AppUsage> usageBetween(Instant from, Instant to);

    List<SessionRecord> sessionsBetween(Instant from, Instant to);

    List<TrackedApp> applications();

    void updateCategory(String appId, String category);

    void updateGoal(String appId, int goalMinutes);

    void clearUsageData();

    String setting(String key, String defaultValue);

    void saveSetting(String key, String value);

    @Override
    void close();
}
