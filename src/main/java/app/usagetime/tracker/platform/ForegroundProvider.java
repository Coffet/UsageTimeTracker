package app.usagetime.tracker.platform;

import app.usagetime.tracker.model.ForegroundSample;

import java.io.IOException;

public interface ForegroundProvider {
    ForegroundSample sample() throws IOException;

    String supportDescription();
}
