package app.usagetime.tracker.export;

import app.usagetime.tracker.model.SessionRecord;
import app.usagetime.tracker.storage.UsageRepository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

public final class ExportService {
    private ExportService() {
    }

    public static void csv(UsageRepository repository, Path destination) throws IOException {
        List<SessionRecord> sessions = repository.sessionsBetween(Instant.EPOCH, Instant.now());
        StringBuilder output = new StringBuilder(
                "app,category,started_at,ended_at,duration_seconds,in_progress\n");
        for (SessionRecord session : sessions) {
            output.append(csvField(session.name())).append(',')
                    .append(csvField(session.category())).append(',')
                    .append(csvField(session.startedAt().toString())).append(',')
                    .append(csvField(session.inProgress() ? "" : session.endedAt().toString())).append(',')
                    .append(session.seconds()).append(',')
                    .append(session.inProgress()).append('\n');
        }
        Files.writeString(destination, output.toString(), StandardCharsets.UTF_8);
    }

    public static void json(UsageRepository repository, Path destination) throws IOException {
        List<SessionRecord> sessions = repository.sessionsBetween(Instant.EPOCH, Instant.now());
        StringBuilder output = new StringBuilder("{\n  \"schemaVersion\": 1,\n  \"sessions\": [");
        for (int index = 0; index < sessions.size(); index++) {
            SessionRecord session = sessions.get(index);
            output.append(index == 0 ? "\n" : ",\n")
                    .append("    {\"app\": \"").append(jsonString(session.name()))
                    .append("\", \"category\": \"").append(jsonString(session.category()))
                    .append("\", \"startedAt\": \"").append(session.startedAt())
                    .append("\", \"endedAt\": ")
                    .append(session.inProgress() ? "null" : "\"" + session.endedAt() + "\"")
                    .append(", \"inProgress\": ").append(session.inProgress())
                    .append(", \"durationSeconds\": ").append(session.seconds()).append('}');
        }
        if (!sessions.isEmpty()) {
            output.append('\n');
        }
        output.append("  ]\n}\n");
        Files.writeString(destination, output.toString(), StandardCharsets.UTF_8);
    }

    private static String csvField(String value) {
        String safe = value == null ? "" : value;
        if (safe.matches("^[\\s\\p{Cc}]*[=+@-].*")) {
            safe = "'" + safe;
        }
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    private static String jsonString(String value) {
        StringBuilder escaped = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (character < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) character));
                    } else {
                        escaped.append(character);
                    }
                }
            }
        }
        return escaped.toString();
    }
}
