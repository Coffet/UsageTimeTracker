package app.usagetime.tracker.packaging;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class TrimSqliteNativesTest {
    @TempDir
    Path lib;

    @Test
    void keepsCurrentPlatformNativesAndOtherResources() throws Exception {
        Set<String> expected = currentNativeEntries();
        Path jar = createJar(expected, false);

        Result result = runTrimmer();
        assertEquals(0, result.exitCode(), result.output());
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            assertNotNull(zip.getEntry("org/sqlite/JDBC.class"));
            for (String entry : expected) {
                assertNotNull(zip.getEntry(entry));
            }
            assertNull(zip.getEntry("org/sqlite/native/Other/other/native.bin"));
        }
    }

    @Test
    void rejectsMissingNativeOrSignedJarWithoutReplacingOriginal() throws Exception {
        Path jar = createJar(Set.of(), false);
        byte[] original = Files.readAllBytes(jar);
        assertNotEquals(0, runTrimmer().exitCode());
        assertArrayEquals(original, Files.readAllBytes(jar));

        Files.delete(jar);
        jar = createJar(currentNativeEntries(), true);
        original = Files.readAllBytes(jar);
        assertNotEquals(0, runTrimmer().exitCode());
        assertArrayEquals(original, Files.readAllBytes(jar));
    }

    private Path createJar(Set<String> entries, boolean signed) throws IOException {
        Path jar = lib.resolve("sqlite-jdbc-test.jar");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(jar))) {
            for (String name : entries) {
                addEntry(zip, name);
            }
            addEntry(zip, "org/sqlite/JDBC.class");
            addEntry(zip, "org/sqlite/native/Other/other/native.bin");
            if (signed) {
                addEntry(zip, "META-INF/TEST.SF");
            }
        }
        return jar;
    }

    private static void addEntry(ZipOutputStream zip, String name) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(1);
        zip.closeEntry();
    }

    private Result runTrimmer() throws Exception {
        Path java = Path.of(System.getProperty("java.home"), "bin", "java");
        Process process = new ProcessBuilder(java.toString(), "scripts/TrimSqliteNatives.java", lib.toString())
                .redirectErrorStream(true).start();
        byte[] output = process.getInputStream().readAllBytes();
        int exitCode = process.waitFor();
        return new Result(exitCode, new String(output, StandardCharsets.UTF_8));
    }

    private record Result(int exitCode, String output) {}

    private static Set<String> currentNativeEntries() {
        String arch = switch (System.getProperty("os.arch").toLowerCase(Locale.ROOT)) {
            case "amd64", "x86_64" -> "x86_64";
            case "aarch64", "arm64" -> "aarch64";
            case "x86", "i386", "i686" -> "x86";
            default -> throw new IllegalStateException("Unsupported test architecture");
        };
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        String root = "org/sqlite/native/";
        if (os.startsWith("windows")) {
            return Set.of(root + "Windows/" + arch + "/native.bin");
        }
        if (os.startsWith("mac") || os.startsWith("darwin")) {
            return Set.of(root + "Mac/" + arch + "/native.bin");
        }
        if (os.startsWith("linux")) {
            return Set.of(root + "Linux/" + arch + "/native.bin", root + "Linux-Musl/" + arch + "/native.bin");
        }
        throw new IllegalStateException("Unsupported test OS: " + os);
    }
}
