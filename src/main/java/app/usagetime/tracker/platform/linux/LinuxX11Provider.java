package app.usagetime.tracker.platform.linux;

import app.usagetime.tracker.model.DetectedApp;
import app.usagetime.tracker.model.ForegroundSample;
import app.usagetime.tracker.platform.ForegroundProvider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LinuxX11Provider implements ForegroundProvider {
    private static final Pattern WINDOW_ID = Pattern.compile("0x[0-9a-fA-F]+");
    private static final Pattern PID = Pattern.compile("_NET_WM_PID\\(CARDINAL\\) = (\\d+)");
    private static final long COMMAND_TIMEOUT_SECONDS = 2;
    private static final Set<String> LOCK_SCREENS = Set.of(
            "gnome-screensaver", "gnome-screensaver-dialog", "kscreenlocker",
            "kscreenlocker_greet", "swaylock", "i3lock", "i3lock-color",
            "light-locker", "xscreensaver", "xlock", "waylock");
    private final int ownPid = (int) ProcessHandle.current().pid();

    @Override
    public ForegroundSample sample() throws IOException {
        if ("wayland".equalsIgnoreCase(System.getenv("XDG_SESSION_TYPE"))) {
            throw new IOException("Wayland foreground-app detection is not universally available. X11 sessions are supported.");
        }
        String activeWindow = run("xprop", "-root", "_NET_ACTIVE_WINDOW");
        Matcher windowMatcher = WINDOW_ID.matcher(activeWindow);
        if (!windowMatcher.find()) {
            throw new IOException("The X11 window manager did not report an active window.");
        }

        String windowId = windowMatcher.group();
        if (windowId.equalsIgnoreCase("0x0")) {
            return new ForegroundSample(null, idleMillis());
        }
        String properties = run("xprop", "-id", windowId, "_NET_WM_PID");
        Matcher pidMatcher = PID.matcher(properties);
        if (!pidMatcher.find()) {
            return new ForegroundSample(null, idleMillis());
        }

        int pid = Integer.parseInt(pidMatcher.group(1));
        if (pid == ownPid) {
            return new ForegroundSample(null, idleMillis());
        }
        Path executable = Path.of("/proc", Integer.toString(pid), "exe");
        String command = Files.exists(executable) ? Files.readSymbolicLink(executable).toString() : "Process " + pid;
        String name = Path.of(command).getFileName().toString();
        if (LOCK_SCREENS.contains(name.toLowerCase(Locale.ROOT))) {
            return new ForegroundSample(null, idleMillis());
        }
        return new ForegroundSample(
                new DetectedApp(command.toLowerCase(Locale.ROOT), name),
                idleMillis()
        );
    }

    @Override
    public String supportDescription() {
        return "Linux X11: requires xprop and xprintidle. Wayland support depends on compositor-specific APIs.";
    }

    private long idleMillis() throws IOException {
        String value = run("xprintidle").trim();
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException error) {
            throw new IOException("xprintidle returned an invalid idle duration.", error);
        }
    }

    private static String run(String... command) throws IOException {
        Process process;
        try {
            process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .start();
        } catch (IOException error) {
            throw new IOException("Required system command is unavailable: " + command[0], error);
        }
        try {
            if (!process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IOException("System command timed out: " + command[0]);
            }
            String output = new String(process.getInputStream().readAllBytes());
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
