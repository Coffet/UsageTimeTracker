package app.usagetime.tracker.export;

import app.usagetime.tracker.storage.SqliteUsageRepository;
import app.usagetime.tracker.storage.UsageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ExportServiceTest {
    @TempDir
    Path directory;

    @Test
    void exportsCsvSafelyAndJsonWithEscapedText() throws Exception {
        try (UsageRepository repository = new SqliteUsageRepository(directory.resolve("usage.db"))) {
            Instant start = Instant.parse("2026-06-02T09:00:00Z");
            long session = repository.startSession("formula", "=Open,\"App\"", start);
            repository.updateCategory("formula", "Line\nbreak");
            repository.finishSession(session, start.plusSeconds(42));

            Path csv = directory.resolve("export.csv");
            Path json = directory.resolve("export.json");
            ExportService.csv(repository, csv);
            ExportService.json(repository, json);

            String csvText = Files.readString(csv);
            String jsonText = Files.readString(json);
            assertTrue(csvText.contains("\"'=Open,\"\"App\"\"\""));
            assertTrue(jsonText.contains("\"schemaVersion\": 1"));
            assertTrue(jsonText.contains("Line\\nbreak"));
            assertTrue(jsonText.contains("=Open,\\\"App\\\""));
        }
    }

    @Test
    void marksAnOpenSessionAsInProgressInsteadOfGivingItAnEndTime() throws Exception {
        try (UsageRepository repository = new SqliteUsageRepository(directory.resolve("open.db"))) {
            Instant start = Instant.parse("2026-06-02T09:00:00Z");
            repository.startSession("editor", "Editor", start);

            Path json = directory.resolve("open.json");
            ExportService.json(repository, json);

            String jsonText = Files.readString(json);
            assertTrue(jsonText.contains("\"endedAt\": null"));
            assertTrue(jsonText.contains("\"inProgress\": true"));
            assertTrue(jsonText.contains("\"durationSeconds\": 0"));
        }
    }
}
