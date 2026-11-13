package app.usagetime.tracker.platform.mac;

import app.usagetime.tracker.model.DetectedApp;
import app.usagetime.tracker.model.ForegroundSample;
import app.usagetime.tracker.platform.ForegroundProvider;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MacOsProvider implements ForegroundProvider {
    private static final int OWN_PID = (int) ProcessHandle.current().pid();
    private static final Pattern IDLE_NANOS = Pattern.compile("\"HIDIdleTime\"\\s*=\\s*(\\d+)");
    private static final Set<String> LOCK_SCREENS = Set.of(
            "com.apple.loginwindow", "com.apple.screensaver.engine", "loginwindow", "screensaverengine");

    @Override
    public ForegroundSample sample() throws IOException {
        String result = run("osascript", "-e",
                "tell application \"System Events\" to set p to first application process whose frontmost is true",
                "-e", "get (name of p) & \"|\" & (bundle identifier of p) & \"|\" & (unix id of p)");
        String[] parts = result.trim().split("\\|", 3);
        if (parts.length != 3) {
            throw new IOException("macOS did not return the frontmost application.");
        }

        int pid;
        try {
            pid = Integer.parseInt(parts[2].trim());
        } catch (NumberFormatException error) {
            throw new IOException("macOS returned an invalid foreground process id.", error);
        }
        long idleMillis = idleMillis();
        if (pid == OWN_PID) {
            return new ForegroundSample(null, idleMillis);
        }
        String name = parts[0].trim();
        String bundleId = parts[1].trim();
        String identity = bundleId.isEmpty() || bundleId.equalsIgnoreCase("missing value")
                ? name.toLowerCase(Locale.ROOT)
                : bundleId.toLowerCase(Locale.ROOT);
        if (LOCK_SCREENS.contains(identity) || LOCK_SCREENS.contains(name.toLowerCase(Locale.ROOT))) {
            return new ForegroundSample(null, idleMillis);
        }
        String id = "macos:" + identity;
        return new ForegroundSample(new DetectedApp(id, name), idleMillis);
    }

    @Override
    public String supportDescription() {
        return "macOS: uses local System Events and HID idle-time data. Accessibility permission may be required.";
    }

    private long idleMillis() throws IOException {
        String output = run("ioreg", "-c", "IOHIDSystem", "-d", "4");
        Matcher matcher = IDLE_NANOS.matcher(output);
        if (!matcher.find()) {
            throw new IOException("macOS did not expose HID idle-time data.");
        }
        try {
            return Long.parseLong(matcher.group(1)) / 1_000_000L;
        } catch (NumberFormatException error) {
            throw new IOException("macOS returned an invalid idle duration.", error);
        }
    }

    private static String run(String... command) throws IOException {
        Process process;
        try {
            process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
        } catch (IOException error) {
            throw new IOException("Required macOS command is unavailable: " + command[0], error);
        }
        try {
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("macOS command timed out: " + command[0]);
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (process.exitValue() != 0) {
                throw new IOException(command[0] + " failed: " + output.trim());
            }
            return output;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while running " + command[0], error);
        }
    }
}
