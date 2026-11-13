package app.usagetime.tracker.platform.linux;

import app.usagetime.tracker.model.DetectedApp;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class LinuxX11ProviderTest {
    @Test
    void keepsCaseSensitiveExecutablePathsAsDistinctApplications() {
        DetectedApp uppercase = LinuxX11Provider.detectedApp("/opt/Example/Editor");
        DetectedApp lowercase = LinuxX11Provider.detectedApp("/opt/example/editor");

        assertNotEquals(uppercase.id(), lowercase.id());
        assertEquals("Editor", uppercase.name());
        assertEquals("editor", lowercase.name());
    }
}
