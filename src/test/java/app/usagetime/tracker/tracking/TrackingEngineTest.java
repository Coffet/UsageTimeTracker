package app.usagetime.tracker.tracking;

import app.usagetime.tracker.model.DetectedApp;
import app.usagetime.tracker.model.ForegroundSample;
import app.usagetime.tracker.platform.ForegroundProvider;
import app.usagetime.tracker.storage.SqliteUsageRepository;
import app.usagetime.tracker.storage.UsageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TrackingEngineTest {
    @TempDir
    Path directory;

    @Test
    void manualPausePublishesConciseStatus() {
        try (UsageRepository repository = new SqliteUsageRepository(directory.resolve("pause.db"))) {
            TrackingEngine engine = new TrackingEngine(
                    new NoopProvider(), repository, Clock.systemUTC(), ignored -> { }, () -> { });
            engine.setPaused(true);
            assertEquals("Tracking is paused.", engine.status());
            engine.close();
        }
    }

    @Test
    void endsSessionAtIdleThresholdRatherThanAtTheNextPoll() {
        try (UsageRepository repository = new SqliteUsageRepository(directory.resolve("usage.db"))) {
            TrackingEngine engine = new TrackingEngine(
                    new NoopProvider(), repository, Clock.systemUTC(), ignored -> { }, () -> { });
            Instant start = Instant.parse("2026-06-02T09:00:00Z");
            DetectedApp editor = new DetectedApp("editor", "Editor");
            engine.process(new ForegroundSample(editor, 0), start);
            for (long elapsed = 30; elapsed <= 450; elapsed += 30) {
                long idleMillis = elapsed <= 150 ? 0 : (elapsed - 150) * 1_000;
                if (elapsed == 450) {
                    idleMillis -= 1_000;
                }
                engine.process(new ForegroundSample(editor, idleMillis), start.plusSeconds(elapsed));
            }
            engine.process(new ForegroundSample(null, 330_000), start.plusSeconds(480));

            var sessions = repository.sessionsBetween(start, start.plusSeconds(9 * 60L));
            assertEquals(1, sessions.size());
            assertEquals(start.plusSeconds(450), sessions.get(0).endedAt());
            assertEquals(450, sessions.get(0).seconds());
            engine.close();
        }
    }

    @Test
    void countsOnlyTheSampledForegroundApplicationWhenSeveralAppsAreOpen() {
        try (UsageRepository repository = new SqliteUsageRepository(directory.resolve("switch.db"))) {
            TrackingEngine engine = new TrackingEngine(
                    new NoopProvider(), repository, Clock.systemUTC(), ignored -> { }, () -> { });
            Instant start = Instant.parse("2026-06-02T09:00:00Z");
            engine.process(new ForegroundSample(new DetectedApp("editor", "Editor"), 0), start);
            engine.process(new ForegroundSample(new DetectedApp("browser", "Browser"), 0),
                    start.plusSeconds(30));
            engine.process(new ForegroundSample(null, 5 * 60_000L), start.plusSeconds(60));

            var sessions = repository.sessionsBetween(start, start.plusSeconds(61));
            assertEquals(2, sessions.size());
            assertEquals("Browser", sessions.get(0).name());
            assertEquals(30, sessions.get(0).seconds());
            assertEquals("Editor", sessions.get(1).name());
            assertEquals(30, sessions.get(1).seconds());
            engine.close();
        }
    }

    @Test
    void doesNotCountAnIntervalWhenForegroundSamplingFails() {
        try (UsageRepository repository = new SqliteUsageRepository(directory.resolve("sampling-error.db"))) {
            Instant start = Instant.parse("2026-06-02T09:00:00Z");
            TrackingEngine engine = new TrackingEngine(
                    new NoopProvider(), repository,
                    Clock.fixed(start.plusSeconds(30), ZoneOffset.UTC),
                    ignored -> { }, () -> { });
            engine.process(new ForegroundSample(new DetectedApp("editor", "Editor"), 0), start);
            engine.process(new ForegroundSample(new DetectedApp("editor", "Editor"), 0), start.plusSeconds(10));

            engine.poll();

            var sessions = repository.sessionsBetween(start, start.plusSeconds(31));
            assertEquals(1, sessions.size());
            assertEquals(start.plusSeconds(10), sessions.get(0).endedAt());
            assertEquals(10, sessions.get(0).seconds());
            engine.close();
        }
    }

    @Test
    void doesNotCountLongUnobservedGapsBetweenSamples() {
        try (UsageRepository repository = new SqliteUsageRepository(directory.resolve("long-gap.db"))) {
            TrackingEngine engine = new TrackingEngine(
                    new NoopProvider(), repository, Clock.systemUTC(), ignored -> { }, () -> { });
            Instant start = Instant.parse("2026-06-02T09:00:00Z");
            engine.process(new ForegroundSample(new DetectedApp("editor", "Editor"), 0), start);
            engine.process(new ForegroundSample(new DetectedApp("editor", "Editor"), 0),
                    start.plusSeconds(10));

            engine.process(new ForegroundSample(new DetectedApp("editor", "Editor"), 0),
                    start.plusSeconds(600));
            engine.process(new ForegroundSample(null, 5 * 60_000L), start.plusSeconds(630));

            var sessions = repository.sessionsBetween(start, start.plusSeconds(631));
            assertEquals(2, sessions.size());
            assertEquals(start.plusSeconds(600), sessions.get(0).startedAt());
            assertEquals(30, sessions.get(0).seconds());
            assertEquals(start.plusSeconds(10), sessions.get(1).endedAt());
            engine.close();
        }
    }

    private static final class NoopProvider implements ForegroundProvider {
        @Override
        public ForegroundSample sample() throws IOException {
            throw new IOException("Not used in this test");
        }

        @Override
        public String supportDescription() {
            return "Test provider";
        }
    }
}
