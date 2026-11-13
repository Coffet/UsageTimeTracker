package app.usagetime.tracker.platform;

import app.usagetime.tracker.platform.linux.LinuxX11Provider;
import app.usagetime.tracker.platform.mac.MacOsProvider;
import app.usagetime.tracker.platform.windows.WindowsProvider;

import java.io.IOException;
import java.util.Locale;

public final class PlatformProviders {
    private PlatformProviders() {
    }

    public static ForegroundProvider create() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("mac") || os.contains("darwin")) {
            return new MacOsProvider();
        }
        if (os.contains("win")) {
            return new WindowsProvider();
        }
        if (os.contains("linux")) {
            return new LinuxX11Provider();
        }
        return new UnsupportedProvider("This operating system is not supported.");
    }

    private record UnsupportedProvider(String supportDescription) implements ForegroundProvider {
        @Override
        public app.usagetime.tracker.model.ForegroundSample sample() throws IOException {
            throw new IOException(supportDescription);
        }
    }
}
