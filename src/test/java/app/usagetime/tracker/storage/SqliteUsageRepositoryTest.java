package app.usagetime.tracker.storage;

import app.usagetime.tracker.model.AppUsage;
import app.usagetime.tracker.model.SessionRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SqliteUsageRepositoryTest {
    @TempDir
    Path directory;

    @Test
    void clipsSessionsToRequestedRangeAndReturnsCurrentAppSettings() {
        try (UsageRepository repository = new SqliteUsageRepository(directory.resolve("usage.db"))) {
            Instant start = Instant.parse("2026-06-01T23:59:30Z");
            long sessionId = repository.startSession("editor", "Editor", start);
            repository.updateCategory("editor", "Work");
            repository.updateGoal("editor", 90);
            repository.finishSession(sessionId, start.plusSeconds(90));

            List<AppUsage> usage = repository.usageBetween(
                    Instant.parse("2026-06-02T00:00:00Z"),
                    Instant.parse("2026-06-02T00:01:00Z")
            );

            assertEquals(1, usage.size());
            assertEquals(60, usage.get(0).seconds());
            assertEquals("Work", usage.get(0).category());
            assertEquals(90, usage.get(0).goalMinutes());
        }
    }

    @Test
    void categoryCanBeChangedOrRemovedWithoutSettingAGoal() {
        Path database = directory.resolve("categories.db");
        Instant start = Instant.parse("2026-06-02T09:00:00Z");
        try (UsageRepository repository = new SqliteUsageRepository(database)) {
            long sessionId = repository.startSession("editor", "Editor", start);
            repository.finishSession(sessionId, start.plusSeconds(60));
            repository.updateCategory("editor", "  Work  ");
        }
        try (UsageRepository repository = new SqliteUsageRepository(database)) {
            AppUsage app = repository.usageBetween(start, start.plusSeconds(120)).get(0);
            assertEquals("Work", app.category());
            assertEquals(0, app.goalMinutes());

            repository.updateCategory("editor", "");
            app = repository.usageBetween(start, start.plusSeconds(120)).get(0);
            assertEquals("", app.category());
            assertEquals(0, app.goalMinutes());
        }
    }

    @Test
    void categorizesKnownAppsLocallyAndPreservesOverridesAcrossRestarts() {
        Path database = directory.resolve("automatic-categories.db");
        Instant start = Instant.parse("2026-06-02T09:00:00Z");
        try (UsageRepository repository = new SqliteUsageRepository(database)) {
            long browser = repository.startSession("browser", "Firefox.exe", start);
            repository.finishSession(browser, start.plusSeconds(60));
            long unknown = repository.startSession("other", "Unknown Application", start);
            repository.finishSession(unknown, start.plusSeconds(30));

            List<SessionRecord> sessions = repository.sessionsBetween(start, start.plusSeconds(120));
            assertEquals("Browser", sessions.stream().filter(s -> s.appId().equals("browser"))
                    .findFirst().orElseThrow().category());
            assertEquals("", sessions.stream().filter(s -> s.appId().equals("other"))
                    .findFirst().orElseThrow().category());
            repository.updateCategory("browser", "Research");
        }
        try (UsageRepository repository = new SqliteUsageRepository(database)) {
            assertEquals("Research", repository.usageBetween(start, start.plusSeconds(120)).stream()
                    .filter(app -> app.appId().equals("browser")).findFirst().orElseThrow().category());
            repository.updateCategory("browser", "");
            assertEquals("Browser", repository.usageBetween(start, start.plusSeconds(120)).stream()
                    .filter(app -> app.appId().equals("browser")).findFirst().orElseThrow().category());
        }
    }

    @Test
    void listsTrackedApplicationsEvenBeforeTheyAccumulateTime() {
        Instant start = Instant.parse("2026-06-02T09:00:00Z");
        try (UsageRepository repository = new SqliteUsageRepository(directory.resolve("apps.db"))) {
            repository.startSession("browser", "Firefox.exe", start);
            repository.startSession("unknown", "Unrecognized App", start);
            repository.updateCategory("unknown", "Study");

            var apps = repository.applications();
            assertEquals(2, apps.size());
            assertEquals("Browser", apps.get(0).category());
            assertEquals("Study", apps.get(1).category());
            assertEquals("unknown", apps.get(1).appId());
        }
    }

    @Test
    void recoveryKeepsOnlyTheLastPersistedCheckpoint() {
        Path database = directory.resolve("recovery.db");
        Instant start = Instant.parse("2026-06-02T09:00:00Z");
        try (UsageRepository repository = new SqliteUsageRepository(database)) {
            long sessionId = repository.startSession("browser", "Browser", start);
            repository.checkpointSession(sessionId, start.plusSeconds(24));
        }

        try (UsageRepository repository = new SqliteUsageRepository(database)) {
            repository.recoverOpenSessions();
            List<SessionRecord> sessions = repository.sessionsBetween(start, start.plusSeconds(60));

            assertEquals(1, sessions.size());
            assertEquals(start.plusSeconds(24), sessions.get(0).endedAt());
            assertEquals(24, sessions.get(0).seconds());
        }
    }

    @Test
    void includesCheckpointedInProgressSessionsInUsageTotals() {
        try (UsageRepository repository = new SqliteUsageRepository(directory.resolve("live-usage.db"))) {
            Instant start = Instant.parse("2026-06-02T09:00:00Z");
            long sessionId = repository.startSession("editor", "Editor", start);
            repository.checkpointSession(sessionId, start.plusSeconds(24));

            List<AppUsage> usage = repository.usageBetween(start, start.plusSeconds(30));

            assertEquals(1, usage.size());
            assertEquals(24, usage.get(0).seconds());
        }
    }

    @Test
    void clearingUsageRemovesSessionsAndAppSettingsButKeepsPreferences() {
        try (UsageRepository repository = new SqliteUsageRepository(directory.resolve("clear.db"))) {
            Instant start = Instant.parse("2026-06-02T09:00:00Z");
            long sessionId = repository.startSession("editor", "Editor", start);
            repository.updateCategory("editor", "Work");
            repository.updateGoal("editor", 60);
            repository.saveSetting("theme", "dark");
            repository.finishSession(sessionId, start.plusSeconds(60));

            repository.clearUsageData();

            assertEquals(0, repository.sessionsBetween(start, start.plusSeconds(120)).size());
            assertEquals(0, repository.usageBetween(start, start.plusSeconds(120)).size());
            assertEquals("dark", repository.setting("theme", "light"));

            long nextSession = repository.startSession("editor", "Editor", start.plusSeconds(120));
            repository.finishSession(nextSession, start.plusSeconds(180));
            AppUsage resetApp = repository.usageBetween(start, start.plusSeconds(240)).get(0);
            assertEquals("", resetApp.category());
            assertEquals(0, resetApp.goalMinutes());
        }
    }

    @Test
    void refusesToOpenDatabaseFromANewerSchemaVersion() throws Exception {
        Path database = directory.resolve("newer-schema.db");
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA user_version = 2");
        }

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> new SqliteUsageRepository(database));
    }
}
