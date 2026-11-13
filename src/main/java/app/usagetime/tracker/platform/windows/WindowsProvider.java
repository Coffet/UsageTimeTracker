package app.usagetime.tracker.platform.windows;

import app.usagetime.tracker.model.DetectedApp;
import app.usagetime.tracker.model.ForegroundSample;
import app.usagetime.tracker.platform.ForegroundProvider;
import com.sun.jna.Native;
import com.sun.jna.PointerType;
import com.sun.jna.Structure;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

public final class WindowsProvider implements ForegroundProvider {
    private static final Set<String> LOCK_SCREENS = Set.of(
            "logonui.exe", "lockapp.exe", "scrnsave.scr", "photosscreensaver.scr");
    private final int ownPid = (int) ProcessHandle.current().pid();

    @Override
    public ForegroundSample sample() throws IOException {
        try {
            HWND window = User32.INSTANCE.GetForegroundWindow();
            if (window == null) {
                return new ForegroundSample(null, idleMillis());
            }

            IntByReference processId = new IntByReference();
            User32.INSTANCE.GetWindowThreadProcessId(window, processId);
            int pid = processId.getValue();
            if (pid <= 0 || pid == ownPid) {
                return new ForegroundSample(null, idleMillis());
            }

            String command = ProcessHandle.of(pid)
                    .flatMap(handle -> handle.info().command())
                    .orElse("Process " + pid);
            String name = Path.of(command).getFileName().toString();
            if (LOCK_SCREENS.contains(name.toLowerCase(Locale.ROOT))) {
                return new ForegroundSample(null, idleMillis());
            }
            return new ForegroundSample(
                    new DetectedApp(command.toLowerCase(Locale.ROOT), name),
                    idleMillis()
            );
        } catch (UnsatisfiedLinkError | RuntimeException error) {
            throw new IOException("Windows foreground-app detection failed: " + error.getMessage(), error);
        }
    }

    @Override
    public String supportDescription() {
        return "Windows: foreground app and idle time are detected locally.";
    }

    private long idleMillis() throws IOException {
        LastInputInfo info = new LastInputInfo();
        info.cbSize = info.size();
        if (!User32.INSTANCE.GetLastInputInfo(info)) {
            throw new IOException("Windows could not read the last-input time.");
        }
        long elapsed = Integer.toUnsignedLong(Kernel32.INSTANCE.GetTickCount() - info.dwTime);
        return elapsed;
    }

    private interface User32 extends StdCallLibrary {
        User32 INSTANCE = Native.load("user32", User32.class);

        HWND GetForegroundWindow();

        int GetWindowThreadProcessId(HWND window, IntByReference processId);

        boolean GetLastInputInfo(LastInputInfo info);
    }

    private interface Kernel32 extends StdCallLibrary {
        Kernel32 INSTANCE = Native.load("kernel32", Kernel32.class);

        int GetTickCount();
    }

    public static final class HWND extends PointerType {
        public HWND() {
        }
    }

    @Structure.FieldOrder({"cbSize", "dwTime"})
    public static final class LastInputInfo extends Structure {
        public int cbSize;
        public int dwTime;
    }
}
