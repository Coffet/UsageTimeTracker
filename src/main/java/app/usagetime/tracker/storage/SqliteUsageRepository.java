package app.usagetime.tracker.storage;

import app.usagetime.tracker.model.AppUsage;
import app.usagetime.tracker.model.AppCategories;
import app.usagetime.tracker.model.SessionRecord;
import app.usagetime.tracker.model.TrackedApp;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class SqliteUsageRepository implements UsageRepository {
    private static final Logger LOGGER = Logger.getLogger(SqliteUsageRepository.class.getName());
    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC);
    private final Connection connection;

    public SqliteUsageRepository(Path databasePath) {
        Connection opened;
        try {
            Path parent = databasePath.toAbsolutePath().getParent();
            if (parent != null) {
                java.nio.file.Files.createDirectories(parent);
            }
            opened = DriverManager.getConnection("jdbc:sqlite:" + databasePath.toAbsolutePath());
            try {
                initializeSchema(opened);
            } catch (SQLException error) {
                try {
                    opened.close();
                } catch (SQLException closeError) {
                    error.addSuppressed(closeError);
                }
                throw error;
            }
        } catch (SQLException | java.io.IOException error) {
            throw new IllegalStateException("Could not open the local usage database: " + error.getMessage(), error);
        }
        connection = opened;
    }

    @Override
    public synchronized long startSession(String appId, String appName, Instant startedAt) {
        try {
            try (PreparedStatement app = connection.prepareStatement(
                    "INSERT INTO apps (app_id, name) VALUES (?, ?) " +
                            "ON CONFLICT(app_id) DO UPDATE SET name = excluded.name")) {
                app.setString(1, appId);
                app.setString(2, appName);
                app.executeUpdate();
            }
            try (PreparedStatement session = connection.prepareStatement(
                    "INSERT INTO sessions (app_id, started_at, ended_at, seconds, last_seen_at, is_open) " +
                            "VALUES (?, ?, ?, 0, ?, 1)",
                    Statement.RETURN_GENERATED_KEYS)) {
                String timestamp = timestamp(startedAt);
                session.setString(1, appId);
                session.setString(2, timestamp);
                session.setString(3, timestamp);
                session.setString(4, timestamp);
                session.executeUpdate();
                try (ResultSet keys = session.getGeneratedKeys()) {
                    if (keys.next()) {
                        return keys.getLong(1);
                    }
                }
            }
            throw new IllegalStateException("SQLite did not return the new session id.");
        } catch (SQLException error) {
            throw databaseError("Could not start a usage session", error);
        }
    }

    @Override
    public synchronized void checkpointSession(long sessionId, Instant at) {
        updateSessionTime(sessionId, at, false);
    }

    @Override
    public synchronized void finishSession(long sessionId, Instant endedAt) {
        updateSessionTime(sessionId, endedAt, true);
    }

    @Override
    public synchronized void recoverOpenSessions() {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "UPDATE sessions SET ended_at = last_seen_at, is_open = 0 WHERE is_open = 1");
        } catch (SQLException error) {
            throw databaseError("Could not recover interrupted usage sessions", error);
        }
    }

    @Override
    public synchronized List<AppUsage> usageBetween(Instant from, Instant to) {
        Map<String, UsageAccumulator> totals = new LinkedHashMap<>();
        for (SessionRecord session : sessionsBetween(from, to)) {
            Instant start = session.startedAt().isBefore(from) ? from : session.startedAt();
            Instant end = session.endedAt().isAfter(to) ? to : session.endedAt();
            long seconds = Math.max(0, Duration.between(start, end).toSeconds());
            if (seconds == 0) {
                continue;
            }
            UsageAccumulator accumulator = totals.computeIfAbsent(session.appId(),
                    ignored -> new UsageAccumulator(session.appId(), session.name(), session.category()));
            accumulator.seconds += seconds;
        }
        return totals.values().stream()
                .map(item -> new AppUsage(item.appId, item.name, item.category, item.seconds, item.goalMinutes))
                .sorted(Comparator.comparingLong(AppUsage::seconds).reversed())
                .toList();
    }

    @Override
    public synchronized List<SessionRecord> sessionsBetween(Instant from, Instant to) {
        List<SessionRecord> sessions = new ArrayList<>();
        String sql = "SELECT s.app_id, a.name, a.category, s.started_at, s.ended_at, s.seconds, s.is_open " +
                "FROM sessions s JOIN apps a ON a.app_id = s.app_id " +
                "WHERE s.started_at < ? AND s.ended_at > ? ORDER BY s.started_at DESC";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, timestamp(to));
            statement.setString(2, timestamp(from));
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    String name = rows.getString("name");
                    String category = rows.getString("category");
                    sessions.add(new SessionRecord(
                            rows.getString("app_id"),
                            name,
                            effectiveCategory(name, category),
                            Instant.parse(rows.getString("started_at")),
                            Instant.parse(rows.getString("ended_at")),
                            rows.getLong("seconds"),
                            rows.getInt("is_open") == 1
                    ));
                }
            }
            return sessions;
        } catch (SQLException error) {
            throw databaseError("Could not read usage sessions", error);
        }
    }

    @Override
    public synchronized List<TrackedApp> applications() {
        List<TrackedApp> apps = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(
                     "SELECT app_id, name, category FROM apps ORDER BY name COLLATE NOCASE, app_id")) {
            while (rows.next()) {
                String name = rows.getString("name");
                String category = rows.getString("category");
                apps.add(new TrackedApp(rows.getString("app_id"), name,
                        effectiveCategory(name, category)));
            }
            return apps;
        } catch (SQLException error) {
            throw databaseError("Could not read tracked applications", error);
        }
    }

    @Override
    public synchronized void updateCategory(String appId, String category) {
        updateAppField(appId, "category", category.trim());
    }

    private static String effectiveCategory(String appName, String category) {
        return category.isBlank() ? AppCategories.detect(appName) : category;
    }

    @Override
    public synchronized void updateGoal(String appId, int goalMinutes) {
        if (goalMinutes < 0 || goalMinutes > 1440) {
            throw new IllegalArgumentException("Daily goals must be between 0 and 1,440 minutes.");
        }
        updateAppField(appId, "goal_minutes", goalMinutes);
    }

    @Override
    public synchronized void clearUsageData() {
        try (Statement statement = connection.createStatement()) {
            statement.execute("BEGIN");
            try {
                statement.executeUpdate("DELETE FROM sessions");
                statement.executeUpdate("DELETE FROM apps");
                statement.execute("COMMIT");
            } catch (SQLException error) {
                statement.execute("ROLLBACK");
                throw error;
            }
        } catch (SQLException error) {
            throw databaseError("Could not delete tracked usage data", error);
        }
    }

    @Override
    public synchronized String setting(String key, String defaultValue) {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT value FROM settings WHERE key = ?")) {
            statement.setString(1, key);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getString(1) : defaultValue;
            }
        } catch (SQLException error) {
            throw databaseError("Could not read local settings", error);
        }
    }

    @Override
    public synchronized void saveSetting(String key, String value) {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO settings (key, value) VALUES (?, ?) " +
                        "ON CONFLICT(key) DO UPDATE SET value = excluded.value")) {
            statement.setString(1, key);
            statement.setString(2, value);
            statement.executeUpdate();
        } catch (SQLException error) {
            throw databaseError("Could not save local settings", error);
        }
    }

    @Override
    public synchronized void close() {
        try {
            connection.close();
        } catch (SQLException error) {
            throw databaseError("Could not close the local usage database", error);
        }
    }

    private static void initializeSchema(Connection connection) throws SQLException {
        int schemaVersion;
        try (Statement statement = connection.createStatement()) {
            try (ResultSet version = statement.executeQuery("PRAGMA user_version")) {
                schemaVersion = version.next() ? version.getInt(1) : 0;
            }
            if (schemaVersion > 1) {
                throw new SQLException("Database schema version " + schemaVersion + " is newer than this app supports.");
            }
            statement.execute("PRAGMA journal_mode = WAL");
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("CREATE TABLE IF NOT EXISTS apps (" +
                    "app_id TEXT PRIMARY KEY, name TEXT NOT NULL, category TEXT NOT NULL DEFAULT '', " +
                    "goal_minutes INTEGER NOT NULL DEFAULT 0 CHECK(goal_minutes BETWEEN 0 AND 1440))");
            statement.execute("CREATE TABLE IF NOT EXISTS sessions (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, app_id TEXT NOT NULL REFERENCES apps(app_id), " +
                    "started_at TEXT NOT NULL, ended_at TEXT NOT NULL, seconds INTEGER NOT NULL DEFAULT 0, " +
                    "last_seen_at TEXT NOT NULL, is_open INTEGER NOT NULL DEFAULT 0)");
            statement.execute("CREATE INDEX IF NOT EXISTS sessions_started_at_idx ON sessions(started_at)");
            statement.execute("CREATE TABLE IF NOT EXISTS settings (key TEXT PRIMARY KEY, value TEXT NOT NULL)");
            if (schemaVersion == 0) {
                statement.execute("PRAGMA user_version = 1");
            }
        }
    }

    private void updateSessionTime(long sessionId, Instant at, boolean close) {
        try {
            Instant start;
            try (PreparedStatement lookup = connection.prepareStatement(
                    "SELECT started_at FROM sessions WHERE id = ? AND is_open = 1")) {
                lookup.setLong(1, sessionId);
                try (ResultSet row = lookup.executeQuery()) {
                    if (!row.next()) {
                        throw new IllegalStateException("The active usage session no longer exists.");
                    }
                    start = Instant.parse(row.getString(1));
                }
            }
            String sql = close
                    ? "UPDATE sessions SET ended_at = ?, last_seen_at = ?, seconds = ?, is_open = 0 WHERE id = ? AND is_open = 1"
                    : "UPDATE sessions SET ended_at = ?, last_seen_at = ?, seconds = ? WHERE id = ? AND is_open = 1";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                String timestamp = timestamp(at);
                statement.setString(1, timestamp);
                statement.setString(2, timestamp);
                statement.setLong(3, Math.max(0, Duration.between(start, at).toSeconds()));
                statement.setLong(4, sessionId);
                if (statement.executeUpdate() == 0) {
                    throw new IllegalStateException("The active usage session no longer exists.");
                }
            }
        } catch (SQLException error) {
            throw databaseError("Could not update a usage session", error);
        }
    }

    private void updateAppField(String appId, String field, Object value) {
        if (!field.equals("category") && !field.equals("goal_minutes")) {
            throw new IllegalArgumentException("Unsupported application field.");
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE apps SET " + field + " = ? WHERE app_id = ?")) {
            statement.setObject(1, value);
            statement.setString(2, appId);
            statement.executeUpdate();
        } catch (SQLException error) {
            throw databaseError("Could not update application settings", error);
        }
    }

    private IllegalStateException databaseError(String message, SQLException error) {
        LOGGER.log(Level.SEVERE, message, error);
        return new IllegalStateException(message + ": " + error.getMessage(), error);
    }

    private final class UsageAccumulator {
        private final String appId;
        private final String name;
        private final String category;
        private final int goalMinutes;
        private long seconds;

        private UsageAccumulator(String appId, String name, String category) {
            this.appId = appId;
            this.name = name;
            this.category = category;
            this.goalMinutes = readGoal(appId);
        }
    }

    private int readGoal(String appId) {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT goal_minutes FROM apps WHERE app_id = ?")) {
            statement.setString(1, appId);
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? row.getInt(1) : 0;
            }
        } catch (SQLException error) {
            throw databaseError("Could not read an application goal", error);
        }
    }

    private static String timestamp(Instant instant) {
        return TIMESTAMP.format(instant);
    }
}
