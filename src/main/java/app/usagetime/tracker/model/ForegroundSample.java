package app.usagetime.tracker.model;

public record ForegroundSample(DetectedApp app, long idleMillis) {
    public ForegroundSample {
        if (idleMillis < 0) {
            throw new IllegalArgumentException("Idle duration must not be negative");
        }
    }
}
